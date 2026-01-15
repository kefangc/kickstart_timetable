package com.kickstart.timetable.api.dto;

import java.util.List;

public class ScheduleAutoRequest {
    private ScheduleRange range;
    private List<TaskRequest> tasks;
    private List<TimeBlockRequest> timetableBlocked;
    private SchedulingPreferenceRequest preferences;

    public ScheduleRange getRange() {
        return range;
    }

    public void setRange(ScheduleRange range) {
        this.range = range;
    }

    public List<TaskRequest> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskRequest> tasks) {
        this.tasks = tasks;
    }

    public List<TimeBlockRequest> getTimetableBlocked() {
        return timetableBlocked;
    }

    public void setTimetableBlocked(List<TimeBlockRequest> timetableBlocked) {
        this.timetableBlocked = timetableBlocked;
    }

    public SchedulingPreferenceRequest getPreferences() {
        return preferences;
    }

    public void setPreferences(SchedulingPreferenceRequest preferences) {
        this.preferences = preferences;
    }
}
