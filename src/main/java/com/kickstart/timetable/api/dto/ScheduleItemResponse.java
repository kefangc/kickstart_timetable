package com.kickstart.timetable.api.dto;

public class ScheduleItemResponse {
    private String taskId;
    private String start;
    private String end;

    public ScheduleItemResponse(String taskId, String start, String end) {
        this.taskId = taskId;
        this.start = start;
        this.end = end;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }
}
