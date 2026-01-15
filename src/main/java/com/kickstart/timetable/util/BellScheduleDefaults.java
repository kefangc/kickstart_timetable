package com.kickstart.timetable.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Default bell schedule matching the sample import JSON ("25秋_浅色.json").
 *
 * Note: The sample includes 60 nodes. The app only uses the early nodes (e.g., 1-12) for typical
 * university timetables, but keeping the full list makes our export compatible with the sample
 * format and is harmless for the frontend parser.
 */
public final class BellScheduleDefaults {
    private BellScheduleDefaults() {}

    public static ArrayNode buildDefault(ObjectMapper om) {
        ArrayNode arr = om.createArrayNode();

        // 1-12: standard day schedule
        add(arr, om, 1, "08:30", "09:15");
        add(arr, om, 2, "09:25", "10:10");
        add(arr, om, 3, "10:30", "11:15");
        add(arr, om, 4, "11:25", "12:10");
        add(arr, om, 5, "14:00", "14:45");
        add(arr, om, 6, "14:55", "15:40");
        add(arr, om, 7, "16:00", "16:45");
        add(arr, om, 8, "16:55", "17:40");
        add(arr, om, 9, "19:00", "19:45");
        add(arr, om, 10, "19:55", "20:40");
        add(arr, om, 11, "20:30", "21:15");
        add(arr, om, 12, "21:25", "22:10");

        // 13-28: late-night / extension nodes (copied from sample)
        add(arr, om, 13, "21:35", "22:20");
        add(arr, om, 14, "21:45", "22:30");
        add(arr, om, 15, "21:55", "22:40");
        add(arr, om, 16, "22:05", "22:50");
        add(arr, om, 17, "22:15", "23:00");
        add(arr, om, 18, "22:25", "23:10");
        add(arr, om, 19, "22:35", "23:20");
        add(arr, om, 20, "22:45", "23:30");
        add(arr, om, 21, "22:55", "23:40");
        add(arr, om, 22, "23:05", "23:50");
        add(arr, om, 23, "23:15", "00:00");
        add(arr, om, 24, "23:25", "00:00");
        add(arr, om, 25, "23:35", "00:00");
        add(arr, om, 26, "23:45", "00:00");
        add(arr, om, 27, "23:51", "00:00");
        add(arr, om, 28, "23:56", "00:00");

        // 29-60: padding nodes in sample (00:00-00:45)
        for (int node = 29; node <= 60; node++) {
            add(arr, om, node, "00:00", "00:45");
        }

        return arr;
    }

    private static void add(ArrayNode arr, ObjectMapper om, int node, String start, String end) {
        ObjectNode o = om.createObjectNode();
        o.put("node", node);
        o.put("startTime", start);
        o.put("endTime", end);
        o.put("timeTable", 1);
        arr.add(o);
    }
}
