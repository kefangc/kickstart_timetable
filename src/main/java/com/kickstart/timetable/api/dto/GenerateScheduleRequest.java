package com.kickstart.timetable.api.dto;

import java.util.List;
import java.util.Map;

public class GenerateScheduleRequest {
    private List<Map<String, Object>> courses;
    private List<Map<String, Object>> tasks;

    public List<Map<String, Object>> getCourses() { return courses; }
    public void setCourses(List<Map<String, Object>> courses) { this.courses = courses; }

    public List<Map<String, Object>> getTasks() { return tasks; }
    public void setTasks(List<Map<String, Object>> tasks) { this.tasks = tasks; }
}
