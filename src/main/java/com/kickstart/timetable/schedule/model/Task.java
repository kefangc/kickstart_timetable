package com.kickstart.timetable.schedule.model;

import java.time.LocalDateTime;

public record Task(
        String id,
        LocalDateTime ddl,
        int durationMin,
        int importance,
        TaskType type,
        String courseId
) {
}
