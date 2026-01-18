package com.kickstart.timetable.api.dto;

import java.util.List;

public class CourseTablePayload {
    private CourseTableMeta timeTable;
    private List<BellScheduleNode> timeNodes;
    private CourseTableConfig tableConfig;
    private List<CourseDefinition> courses;
    private List<CourseRule> courseTimes;

    public CourseTableMeta getTimeTable() {
        return timeTable;
    }

    public void setTimeTable(CourseTableMeta timeTable) {
        this.timeTable = timeTable;
    }

    public List<BellScheduleNode> getTimeNodes() {
        return timeNodes;
    }

    public void setTimeNodes(List<BellScheduleNode> timeNodes) {
        this.timeNodes = timeNodes;
    }

    public CourseTableConfig getTableConfig() {
        return tableConfig;
    }

    public void setTableConfig(CourseTableConfig tableConfig) {
        this.tableConfig = tableConfig;
    }

    public List<CourseDefinition> getCourses() {
        return courses;
    }

    public void setCourses(List<CourseDefinition> courses) {
        this.courses = courses;
    }

    public List<CourseRule> getCourseTimes() {
        return courseTimes;
    }

    public void setCourseTimes(List<CourseRule> courseTimes) {
        this.courseTimes = courseTimes;
    }
}
