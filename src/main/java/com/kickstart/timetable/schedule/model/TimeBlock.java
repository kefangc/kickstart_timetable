package com.kickstart.timetable.schedule.model;

import java.time.LocalDateTime;

public record TimeBlock(
        LocalDateTime start,
        LocalDateTime end,
        String type
) {
}
