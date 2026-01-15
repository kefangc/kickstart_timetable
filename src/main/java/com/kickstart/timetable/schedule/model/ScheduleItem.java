package com.kickstart.timetable.schedule.model;

import java.time.LocalDateTime;

public record ScheduleItem(
        String taskId,
        LocalDateTime start,
        LocalDateTime end
) {
}
