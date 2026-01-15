package com.kickstart.timetable.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Defaults that mimic the user's sample import file ("25秋_浅色.json").
 */
public final class ImportFormatDefaults {
    private ImportFormatDefaults() {}

    /** Build the line-1 meta object. */
    public static ObjectNode buildMeta(ObjectMapper om) {
        ObjectNode o = om.createObjectNode();
        o.put("courseLen", 45);
        o.put("id", 1);
        o.put("name", "默认");
        o.put("sameBreakLen", false);
        o.put("sameLen", true);
        o.put("theBreakLen", 10);
        return o;
    }

    /** Build the line-3 table config object. */
    public static ObjectNode buildTableConfig(
            ObjectMapper om,
            int tableId,
            String tableName,
            String startDate,
            int maxWeek,
            int nodes,
            int timeTable
    ) {
        ObjectNode o = om.createObjectNode();
        o.put("background", "");
        o.put("courseTextColor", -1);
        o.put("id", tableId);
        o.put("itemAlpha", 50);
        o.put("itemHeight", 64);
        o.put("itemTextSize", 12);
        o.put("maxWeek", maxWeek);
        o.put("nodes", nodes);
        o.put("showOtherWeekCourse", false);
        o.put("showSat", true);
        o.put("showSun", true);
        o.put("showTime", false);
        o.put("startDate", startDate);
        o.put("strokeColor", -2130706433);
        o.put("sundayFirst", false);
        o.put("tableName", tableName);
        o.put("textColor", -16777216);
        o.put("timeTable", timeTable);
        o.put("type", 0);
        o.put("widgetCourseTextColor", -1);
        o.put("widgetItemAlpha", 50);
        o.put("widgetItemHeight", 64);
        o.put("widgetItemTextSize", 12);
        o.put("widgetStrokeColor", -2130706433);
        o.put("widgetTextColor", -16777216);
        return o;
    }
}
