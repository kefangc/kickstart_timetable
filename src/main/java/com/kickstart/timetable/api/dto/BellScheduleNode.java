package com.kickstart.timetable.api.dto;

public class BellScheduleNode {
    private String endTime;
    private int node;
    private String startTime;
    private int timeTable;

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public int getNode() {
        return node;
    }

    public void setNode(int node) {
        this.node = node;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public int getTimeTable() {
        return timeTable;
    }

    public void setTimeTable(int timeTable) {
        this.timeTable = timeTable;
    }
}
