package com.kickstart.timetable.util;

/** Extract the first top-level JSON object from a string. */
public final class JsonObjectExtractor {
    private JsonObjectExtractor() {}

    public static String extractObject(String text) {
        if (text == null) return null;
        String cleaned = text.replace("```json", "```").replace("```JSON", "```");
        if (cleaned.contains("```")) {
            int first = cleaned.indexOf("```");
            int last = cleaned.lastIndexOf("```");
            if (first >= 0 && last > first) {
                cleaned = cleaned.substring(first + 3, last);
            }
        }
        cleaned = cleaned.trim();

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

            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        return cleaned.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }
}
