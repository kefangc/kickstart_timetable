package com.kickstart.timetable.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kickstart.timetable.util.JsonObjectExtractor;
import org.springframework.stereotype.Service;

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

}
