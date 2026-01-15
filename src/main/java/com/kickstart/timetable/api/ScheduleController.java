package com.kickstart.timetable.api;

import com.kickstart.timetable.api.dto.ScheduleAutoRequest;
import com.kickstart.timetable.api.dto.ScheduleAutoResponse;
import com.kickstart.timetable.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedule")
@Tag(name = "排期接口", description = "启发式排期与优化方案")
public class ScheduleController {
    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @Operation(summary = "自动排期", description = "基于启发式算法生成多个排期方案")
    @PostMapping(value = "/auto", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ScheduleAutoResponse autoSchedule(@RequestBody ScheduleAutoRequest request) {
        return scheduleService.generateAutoPlans(request);
    }
}
