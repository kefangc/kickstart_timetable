package com.kickstart.timetable.util;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Light (pastel) ARGB palette similar to the sample JSON ("25秋_浅色.json").
 */
public final class PastelPalette {
    private PastelPalette() {}

    public static final List<String> ARGB = List.of(
            "#ffbbdefb", // blue
            "#ffb3e5fc", // light cyan
            "#ffb2ebf2", // cyan
            "#ffb2dfdb", // teal
            "#ffc8e6c9", // green
            "#ffdcedc8", // light green
            "#fffff9c4", // yellow
            "#ffffe0b2", // orange
            "#ffffccbc", // peach
            "#fff8bbd0", // pink
            "#ffe1bee7", // purple
            "#ffd1c4e9"  // deep purple
    );

    /**
     * Pick a palette color by index (0..N). This matches the sample JSON where each course id gets
     * a stable pastel color.
     */
    public static String pickByIndex(int idx) {
        int i = Math.floorMod(idx, ARGB.size());
        return ARGB.get(i);
    }

    /**
     * Deterministically pick a palette color for a given course name.
     */
    public static String pickColor(String courseName) {
        if (courseName == null) return ARGB.get(0);
        int h = stableHash(courseName);
        int idx = Math.floorMod(h, ARGB.size());
        return ARGB.get(idx);
    }

    private static int stableHash(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        int h = 0;
        for (byte value : b) {
            h = 31 * h + (value & 0xff);
        }
        return h;
    }
}
