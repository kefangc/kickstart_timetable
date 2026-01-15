package com.kickstart.timetable.api.dto;

import java.util.List;

public class SchedulePlanResponse {
    private String planId;
    private String label;
    private List<ScheduleItemResponse> items;

    public SchedulePlanResponse(String planId, String label, List<ScheduleItemResponse> items) {
        this.planId = planId;
        this.label = label;
        this.items = items;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<ScheduleItemResponse> getItems() {
        return items;
    }

    public void setItems(List<ScheduleItemResponse> items) {
        this.items = items;
    }
}
