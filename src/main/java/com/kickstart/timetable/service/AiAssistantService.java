package com.kickstart.timetable.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kickstart.timetable.util.JsonObjectExtractor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AiAssistantService {

    private final AiStudioChatClient ai;
    private final ObjectMapper om;

    public AiAssistantService(AiStudioChatClient ai, ObjectMapper om) {
        this.ai = ai;
        this.om = om;
    }

    /**
     * Parse a natural language task into structured JSON.
     */
    public Map<String, Object> parseTask(String input) {
        String system = "你是任务结构化抽取器。你只能输出JSON对象，禁止输出解释或多余文字。";
        String user = "请将下面的中文任务描述解析成JSON对象，字段必须为：" +
                "title(string), dueDate(string|null, ISO YYYY-MM-DD), priority(one of Low,Medium,High,Urgent)," +
                "estimatedDurationMinutes(int), relatedCourse(string|null)。\n" +
                "如果缺失dueDate则为null；estimatedDurationMinutes缺失时默认60；priority按紧急程度推断。\n\n" +
                "任务描述：" + input;

        String raw = ai.chat(system, user, 0.2, 1024);
        String json = JsonObjectExtractor.extractObject(raw);
        if (json == null) {
            throw new RuntimeException("LLM 未返回有效 JSON");
        }
        try {
            JsonNode n = om.readTree(json);
            return om.convertValue(n, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("解析任务 JSON 失败", e);
        }
    }

    /**
     * Generate schedule blocks for next 3 days. Returns JSON array.
     */
    public List<Map<String, Object>> generateSchedule(Object payload) {
        String system = "你是学生智能排期助手。你只能输出JSON数组，禁止输出解释或多余文字。";
        String user = "给定固定课程与待办任务，请为接下来3天生成建议日程（包含课程和任务）。" +
                "只允许安排在08:00-22:30之间，不得与课程冲突，优先安排更紧急任务。" +
                "输出JSON数组，每个元素字段：id(string), type('COURSE'|'TASK'), title(string), day(one of Monday..Sunday)," +
                "startTime('HH:mm'), endTime('HH:mm'), color(string, hex), details(string)。\n" +
                "输入JSON如下：\n" +
                safeStringify(payload);

        String raw = ai.chat(system, user, 0.4, 2048);
        // We want an array; reuse JsonArrayExtractor but take the first array
        try {
            String arrayText = com.kickstart.timetable.util.JsonArrayExtractor.extractArrays(raw, 1).stream().findFirst().orElse(null);
            if (arrayText == null) {
                throw new RuntimeException("LLM 未返回有效 JSON 数组");
            }
            JsonNode arr = om.readTree(arrayText);
            if (!arr.isArray()) throw new RuntimeException("LLM 输出不是数组");
            return om.convertValue(arr, List.class);
        } catch (Exception e) {
            throw new RuntimeException("解析排期 JSON 失败", e);
        }
    }

    private String safeStringify(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
