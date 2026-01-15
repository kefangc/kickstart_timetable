package com.kickstart.timetable.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Extract top-level JSON arrays from a string.
 *
 * This is a small utility to recover from LLM outputs that may contain extra text.
 * It scans for '[' ... matching ']' while respecting JSON string quoting/escaping.
 */
public final class JsonArrayExtractor {

    private JsonArrayExtractor() {}

    public static List<String> extractArrays(String text, int maxArrays) {
        if (text == null) return List.of();

        String cleaned = stripCodeFences(text).trim();
        List<String> arrays = new ArrayList<>();

        boolean inString = false;
        boolean escape = false;
        int depth = 0;
        int start = -1;

        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }

            if (c == '\\') {
                if (inString) escape = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) continue;

            if (c == '[') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == ']') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        arrays.add(cleaned.substring(start, i + 1));
                        start = -1;
                        if (arrays.size() >= maxArrays) break;
                    }
                }
            }
        }

        return arrays;
    }

    private static String stripCodeFences(String s) {
        // Remove ```json ... ``` fences if present
        String out = s.replace("```json", "```").replace("```JSON", "```");
        if (out.contains("```")) {
            // crude remove: keep content between first and last fence
            int first = out.indexOf("```");
            int last = out.lastIndexOf("```");
            if (first >= 0 && last > first) {
                out = out.substring(first + 3, last);
            }
        }
        return out;
    }
}
