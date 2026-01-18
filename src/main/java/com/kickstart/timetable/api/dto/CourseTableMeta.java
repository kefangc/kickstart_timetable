package com.kickstart.timetable.api.dto;

public class CourseTableMeta {
    private int courseLen;
    private int id;
    private String name;
    private boolean sameBreakLen;
    private boolean sameLen;
    private int theBreakLen;

    public int getCourseLen() {
        return courseLen;
    }

    public void setCourseLen(int courseLen) {
        this.courseLen = courseLen;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSameBreakLen() {
        return sameBreakLen;
    }

    public void setSameBreakLen(boolean sameBreakLen) {
        this.sameBreakLen = sameBreakLen;
    }

    public boolean isSameLen() {
        return sameLen;
    }

    public void setSameLen(boolean sameLen) {
        this.sameLen = sameLen;
    }

    public int getTheBreakLen() {
        return theBreakLen;
    }

    public void setTheBreakLen(int theBreakLen) {
        this.theBreakLen = theBreakLen;
    }
}
