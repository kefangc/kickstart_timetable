package com.kickstart.timetable.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kickstart.timetable.util.BellScheduleDefaults;
import com.kickstart.timetable.util.ImportFormatDefaults;
import com.kickstart.timetable.util.JsonArrayExtractor;
import com.kickstart.timetable.util.PastelPalette;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TimetableAiService {

    private final PaddleLayoutParsingClient paddle;
    private final AiStudioChatClient ai;
    private final ObjectMapper om;

    public TimetableAiService(PaddleLayoutParsingClient paddle, AiStudioChatClient ai, ObjectMapper om) {
        this.paddle = paddle;
        this.ai = ai;
        this.om = om;
    }

    public Map<String, Object> parseTimetableFromImage(byte[] imageBytes) {
        String markdown = paddle.extractMarkdown(imageBytes, 1);
        String ndjson = markdownToNdjson(markdown);

        Map<String, Object> out = new HashMap<>();
        out.put("ndjson", ndjson);
        // 给前端调试用（你可以删掉）
        out.put("markdown", markdown);
        return out;
    }

    /**
     * Convert OCR markdown into NDJSON compatible with the user's sample file format.
     *
     * Output lines:
     * 1) meta object
     * 2) bellSchedule array
     * 3) table config object
     * 4) courseDefs array
     * 5) scheduleRules array
     *
     * Important:
     * - Do NOT use thinking models for this step; they may consume the output budget in reasoning_content
     *   and leave message.content empty.
     * - Use response_format=json_object (AIStudio structured output feature) with a supported model.
     */
    private String markdownToNdjson(String markdownText) {
        String system = "你是课表结构化抽取器。只输出一个 JSON 对象（不要解释、不要代码块、不要多余文本）。";

        String user = "给你一段来自课表截图 OCR 得到的 markdown 文本，请抽取并输出一个 JSON 对象，必须包含以下三个字段：" +
                "\n\n" +
                "1) bellScheduleData：数组。元素字段：node(int), startTime(\"HH:mm\"), endTime(\"HH:mm\"), timeTable(固定1)" +
                "\n2) courseDefs：数组。元素字段：id(从0递增), courseName(string), color(可以输出空字符串\"\"), tableId(固定1), credit(0.0), note(\"\")" +
                "\n3) scheduleRules：数组。元素字段：id(引用courseDefs的id), day(1=周一..7=周日), startNode(开始节次), step(连续节次数), " +
                "startWeek, endWeek, type(0=全周,1=单周,2=双周), room, teacher, tableId(固定1), ownTime(false), level(0), startTime(\"\"), endTime(\"\")" +
                "\n\n规则：" +
                "\n- 课程名尽量保留班级/编号，如：离散数学[06]" +
                "\n- 周次：如 1-18周(单) => startWeek=1,endWeek=18,type=1；5-13周 => type=0" +
                "\n- 节次：如 1-2节 => startNode=1, step=2；5-8节 => startNode=5, step=4" +
                "\n- 如果无法从 markdown 判断 bellScheduleData，请输出空数组 []（后端会自动填默认作息表）" +
                "\n- 如果信息缺失，用空字符串或合理默认值补齐，但必须保证 JSON 可解析" +
                "\n\nmarkdown 文本如下：\n<<<OCR_MARKDOWN_TEXT>>>\n" + markdownText;

        // Use structured output (json_object) with a supported model.
        String raw = ai.chatStructuredJsonObject(system, user, 0.1, 8192);
        return normalizeToNdjson(raw);
    }

    private String normalizeToNdjson(String raw) {
        ArrayNode bell = null;
        ArrayNode courseDefs = null;
        ArrayNode rules = null;

        // 0) Prefer parsing as a JSON object: { bellScheduleData:[], courseDefs:[], scheduleRules:[] }
        JsonNode obj = tryParseFirstJsonObject(raw);
        if (obj != null && obj.isObject()) {
            JsonNode b = obj.get("bellScheduleData");
            JsonNode c = obj.get("courseDefs");
            JsonNode r = obj.get("scheduleRules");
            if (b != null && b.isArray()) bell = (ArrayNode) b;
            if (c != null && c.isArray()) courseDefs = (ArrayNode) c;
            if (r != null && r.isArray()) rules = (ArrayNode) r;
        }

        // 1) Back-compat: if model returned NDJSON or loose arrays, extract arrays.
        if (bell == null && courseDefs == null && rules == null) {
            List<String> arrays = JsonArrayExtractor.extractArrays(raw, 5);
            for (String a : arrays) {
                try {
                    JsonNode node = om.readTree(a);
                    if (!node.isArray()) continue;
                    ArrayNode arr = (ArrayNode) node;
                    if (arr.isEmpty()) continue;
                    JsonNode first = arr.get(0);
                    if (first.has("node") && first.has("startTime") && first.has("endTime")) {
                        bell = arr;
                    } else if (first.has("courseName")) {
                        courseDefs = arr;
                    } else if (first.has("day") && first.has("startNode") && first.has("tableId")) {
                        rules = arr;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // 2) bellSchedule: ALWAYS use the exact default schedule from the user's sample file.
        // Why: LLMs often hallucinate a bellScheduleData even when the screenshot doesn't contain it,
        // which causes small mismatches (e.g., 节次11/12). For "export" compatibility we keep it fixed.
        bell = BellScheduleDefaults.buildDefault(om);

        final int tableId = 3;
        final int timeTable = 1;

        if (courseDefs == null) courseDefs = om.createArrayNode();
        if (rules == null) rules = om.createArrayNode();

        // 3) Normalize courseDefs: remap id from 0..N, tableId=tableId, fill pastel colors (by id order)
        Map<Integer, Integer> idRemap = new HashMap<>();
        ArrayNode normalizedCourseDefs = om.createArrayNode();
        int nextId = 0;
        for (JsonNode n : courseDefs) {
            if (!(n instanceof ObjectNode o)) continue;
            int oldId = o.path("id").isInt() ? o.path("id").asInt() : nextId;
            int newId = nextId++;
            idRemap.put(oldId, newId);

            ObjectNode c = om.createObjectNode();
            c.put("id", newId);
            String rawName = o.path("courseName").asText("");
            String name = normalizeText(rawName);
            if (name.startsWith("本")) name = name.substring(1);
            c.put("courseName", name);
            c.put("color", PastelPalette.pickByIndex(newId));
            c.put("tableId", tableId);
            c.put("credit", 0.0);
            c.put("note", "");
            normalizedCourseDefs.add(c);
        }

        // 4) Normalize scheduleRules: remap id, clamp ranges, dedupe
        ArrayNode normalizedRules = om.createArrayNode();
        Set<String> seen = new HashSet<>();
        for (JsonNode n : rules) {
            if (!(n instanceof ObjectNode o)) continue;
            int oldId = o.path("id").isInt() ? o.path("id").asInt() : -1;
            Integer newId = idRemap.get(oldId);
            if (newId == null) {
                continue;
            }

            ObjectNode r = om.createObjectNode();
            r.put("id", newId);
            r.put("day", clampInt(o.path("day").asInt(1), 1, 7));
            r.put("startNode", Math.max(1, o.path("startNode").asInt(1)));
            r.put("step", Math.max(1, o.path("step").asInt(2)));
            r.put("startWeek", Math.max(1, o.path("startWeek").asInt(1)));
            r.put("endWeek", Math.max(r.get("startWeek").asInt(), o.path("endWeek").asInt(r.get("startWeek").asInt())));
            r.put("type", clampInt(o.path("type").asInt(0), 0, 2));
            String room = normalizeText(o.path("room").asText(""));
            String teacher = normalizeText(o.path("teacher").asText(""));
            r.put("room", room);
            r.put("teacher", teacher);
            r.put("tableId", tableId);
            r.put("ownTime", false);
            r.put("level", 0);
            r.put("startTime", "");
            r.put("endTime", "");

            String key = newId + "|" + r.get("day").asInt() + "|" + r.get("startNode").asInt() + "|" + r.get("step").asInt() +
                    "|" + r.get("startWeek").asInt() + "|" + r.get("endWeek").asInt() + "|" + r.get("type").asInt() +
                    "|" + room + "|" + teacher;
            if (seen.add(key)) {
                normalizedRules.add(r);
            }
        }

        // 5) Assemble NDJSON (5 lines)
        try {
            ObjectNode meta = ImportFormatDefaults.buildMeta(om);
            ObjectNode tableCfg = ImportFormatDefaults.buildTableConfig(
                    om,
                    tableId,
                    "25秋",
                    "2025-9-1",
                    20,
                    20,
                    timeTable
            );

            return om.writeValueAsString(meta) + "\n" +
                    om.writeValueAsString(bell) + "\n" +
                    om.writeValueAsString(tableCfg) + "\n" +
                    om.writeValueAsString(normalizedCourseDefs) + "\n" +
                    om.writeValueAsString(normalizedRules);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("NDJSON 序列化失败", e);
        }
    }

    /**
     * Normalize OCR/LLM strings to be closer to the sample file:
     * - trim
     * - convert half-width parentheses to full-width
     * - collapse repeated whitespace
     */
    private String normalizeText(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replace('(', '（').replace(')', '）');
        // collapse whitespace
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private JsonNode tryParseFirstJsonObject(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        // Fast path: already starts with '{'
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) return null;

        String candidate = s.substring(start, end + 1);
        try {
            return om.readTree(candidate);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
