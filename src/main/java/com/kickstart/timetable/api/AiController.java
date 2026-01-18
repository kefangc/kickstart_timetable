package com.kickstart.timetable.api;

import com.kickstart.timetable.api.dto.GenerateScheduleRequest;
import com.kickstart.timetable.api.dto.ParseTaskRequest;
import com.kickstart.timetable.service.AiAssistantService;
import com.kickstart.timetable.service.AiStudioChatClient;
import com.kickstart.timetable.service.SchedulePlannerService;
import com.kickstart.timetable.service.TimetableAiService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI 接口", description = "OCR+大模型解析与排期相关接口")
public class AiController {

    private final TimetableAiService timetableAiService;
    private final AiAssistantService aiAssistantService;
    private final AiStudioChatClient aiStudioChatClient;
    private final SchedulePlannerService schedulePlannerService;

    public AiController(TimetableAiService timetableAiService, AiAssistantService aiAssistantService, AiStudioChatClient aiStudioChatClient,
                        SchedulePlannerService schedulePlannerService) {
        this.timetableAiService = timetableAiService;
        this.aiAssistantService = aiAssistantService;
        this.aiStudioChatClient = aiStudioChatClient;
        this.schedulePlannerService = schedulePlannerService;
    }

    @Operation(summary = "AIStudio 连通性测试", description = "最小化调用大模型，返回解析出的 content 以及部分原始响应字段，便于确认 Key/域名/路径是否正确")
    @GetMapping(value = "/aistudio/ping", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> pingAiStudio(@RequestParam(name = "q", required = false) String q) {
        String system = "You are a concise assistant. Reply with exactly: OK";
        String user = (q == null || q.isBlank()) ? "ping" : q;

        long t0 = System.currentTimeMillis();
        var raw = aiStudioChatClient.chatRaw(system, user, 0.0, 256);
        long ms = System.currentTimeMillis() - t0;

        String content = aiStudioChatClient.extractFinalContent(raw);
        String reasoning = raw.at("/choices/0/message/reasoning_content").asText("");

        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", content != null && !content.isBlank());
        resp.put("latencyMs", ms);
        resp.put("content", content);
        // 仅用于调试：thinking 模型可能带 reasoning_content（不要在业务接口使用它）
        resp.put("reasoning_content", reasoning);
        resp.put("finish_reason", raw.at("/choices/0/finish_reason").asText(""));
        resp.put("model", raw.at("/model").asText(""));
        resp.put("id", raw.at("/id").asText(""));
        resp.put("usage", raw.path("usage"));
        resp.put("error", raw.path("error"));
        // 原始响应可能很大，这里只返回 choices[0] 方便定位字段结构
        resp.put("choice0", raw.at("/choices/0"));
        return resp;
    }

    @Operation(summary = "解析课表图片", description = "上传课表截图，后端调用PaddleOCR得到markdown，再调用大模型结构化为NDJSON并返回")
    @PostMapping(value = "/parse-schedule-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> parseScheduleImage(@RequestPart("file") MultipartFile file) throws Exception {
        return timetableAiService.parseTimetableFromImage(file.getBytes());
    }

    @Operation(summary = "解析课表图片（NDJSON原文）", description = "上传课表截图，返回与示例文件一致的5行NDJSON原文（适合直接保存为.json导入）。")
    @PostMapping(value = "/parse-schedule-image/ndjson", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "text/plain; charset=UTF-8")
    public org.springframework.http.ResponseEntity<String> parseScheduleImageNdjson(
            @RequestPart(value = "file", required = false) MultipartFile file,
            // 一些客户端/调试工具可能会用 image 作为字段名，这里做个兼容
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        MultipartFile f = (file != null ? file : image);
        if (f == null || f.isEmpty()) {
            return org.springframework.http.ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("ERROR: missing multipart file part. Please send form-data with key 'file'.");
        }

        try {
            Map<String, Object> res = timetableAiService.parseTimetableFromImage(f.getBytes());
            String ndjson = res.get("ndjson") == null ? "" : res.get("ndjson").toString();
            if (ndjson.isBlank()) {
                return org.springframework.http.ResponseEntity.status(500)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("ERROR: ndjson is blank (AI/OCR pipeline returned empty). Check server logs for details.");
            }
            return org.springframework.http.ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(ndjson);
        } catch (Exception e) {
            // 对于 text/plain 接口，直接把错误写入 body，避免用户下载到“空白文件”
            return org.springframework.http.ResponseEntity.status(500)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("ERROR: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    @Operation(summary = "解析自然语言任务", description = "把一段自然语言的作业/DDL描述解析为结构化字段")
    @PostMapping(value = "/parse-task", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> parseTask(@Valid @RequestBody ParseTaskRequest req) {
        return aiAssistantService.parseTask(req.getInput());
    }

    @Operation(summary = "生成智能排期", description = "根据课程与任务生成建议排期块")
    @PostMapping(value = "/generate-schedule", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> generateSchedule(@RequestBody GenerateScheduleRequest req) {
        return schedulePlannerService.generateSchedule(req);
    }
}
