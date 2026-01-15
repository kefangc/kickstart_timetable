package com.kickstart.timetable.schedule.model;

import java.time.Duration;
import java.time.LocalDateTime;

public record TimeSlot(
        LocalDateTime start,
        LocalDateTime end
) {
    public long durationMinutes() {
        return Duration.between(start, end).toMinutes();
    }

    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end) && end.isAfter(other.start);
    }
}
