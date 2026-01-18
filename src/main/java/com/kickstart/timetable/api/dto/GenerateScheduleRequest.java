package com.kickstart.timetable.api.dto;

public class GenerateScheduleRequest {
    private String currentDateTime;
    private CourseTablePayload courseTable;
    private java.util.List<TaskPayload> tasks;

    public String getCurrentDateTime() {
        return currentDateTime;
    }

    public void setCurrentDateTime(String currentDateTime) {
        this.currentDateTime = currentDateTime;
    }

    public CourseTablePayload getCourseTable() {
        return courseTable;
    }

    public void setCourseTable(CourseTablePayload courseTable) {
        this.courseTable = courseTable;
    }

    public java.util.List<TaskPayload> getTasks() {
        return tasks;
    }

    public void setTasks(java.util.List<TaskPayload> tasks) {
        this.tasks = tasks;
    }
}
