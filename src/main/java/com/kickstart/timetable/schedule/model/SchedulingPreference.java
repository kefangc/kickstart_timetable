package com.kickstart.timetable.schedule.model;

import java.time.LocalTime;

public record SchedulingPreference(
        SchedulingMode mode,
        LocalTime dayStart,
        LocalTime dayEnd
) {
}
