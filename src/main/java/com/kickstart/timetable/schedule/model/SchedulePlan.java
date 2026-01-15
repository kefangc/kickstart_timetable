package com.kickstart.timetable.schedule.model;

import java.util.List;

public record SchedulePlan(
        String planId,
        String label,
        List<ScheduleItem> items
) {
}
