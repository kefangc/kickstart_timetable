package com.kickstart.timetable.api.dto;

import java.util.List;

public class ScheduleAutoResponse {
    private List<SchedulePlanResponse> plans;
    private List<String> overloadTasks;

    public ScheduleAutoResponse(List<SchedulePlanResponse> plans, List<String> overloadTasks) {
        this.plans = plans;
        this.overloadTasks = overloadTasks;
    }

    public List<SchedulePlanResponse> getPlans() {
        return plans;
    }

    public void setPlans(List<SchedulePlanResponse> plans) {
        this.plans = plans;
    }

    public List<String> getOverloadTasks() {
        return overloadTasks;
    }

    public void setOverloadTasks(List<String> overloadTasks) {
        this.overloadTasks = overloadTasks;
    }
}
