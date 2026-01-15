package com.kickstart.timetable.service;

import com.kickstart.timetable.api.dto.ScheduleAutoRequest;
import com.kickstart.timetable.api.dto.ScheduleAutoResponse;
import com.kickstart.timetable.api.dto.ScheduleItemResponse;
import com.kickstart.timetable.api.dto.SchedulePlanResponse;
import com.kickstart.timetable.api.dto.TaskRequest;
import com.kickstart.timetable.api.dto.TimeBlockRequest;
import com.kickstart.timetable.schedule.engine.GreedySchedulerEngine;
import com.kickstart.timetable.schedule.engine.SchedulerEngine;
import com.kickstart.timetable.schedule.model.ScheduleItem;
import com.kickstart.timetable.schedule.model.SchedulePlan;
import com.kickstart.timetable.schedule.model.SchedulingMode;
import com.kickstart.timetable.schedule.model.SchedulingPreference;
import com.kickstart.timetable.schedule.model.Task;
import com.kickstart.timetable.schedule.model.TaskType;
import com.kickstart.timetable.schedule.model.TimeBlock;
import com.kickstart.timetable.schedule.model.TimeSlot;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ScheduleService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd['T'HH:mm]", Locale.CHINA);

    private final SchedulerEngine schedulerEngine;

    public ScheduleService() {
        this.schedulerEngine = new GreedySchedulerEngine();
    }

    public ScheduleAutoResponse generateAutoPlans(ScheduleAutoRequest request) {
        SchedulingPreference preference = parsePreference(request);
        List<Task> tasks = parseTasks(request.getTasks());
        List<TimeBlock> blocked = parseBlocks(request.getTimetableBlocked());
        List<TimeSlot> freeSlots = buildFreeSlots(request, blocked, preference);

        List<SchedulePlan> plans = schedulerEngine.generate(tasks, blocked, freeSlots, preference);
        List<SchedulePlanResponse> planResponses = new ArrayList<>();
        for (SchedulePlan plan : plans) {
            planResponses.add(new SchedulePlanResponse(plan.planId(), plan.label(), toItemResponses(plan.items())));
        }

        List<String> overloadTasks = resolveOverloadTasks(tasks, plans);
        return new ScheduleAutoResponse(planResponses, overloadTasks);
    }

    private SchedulingPreference parsePreference(ScheduleAutoRequest request) {
        String modeRaw = request.getPreferences() == null ? null : request.getPreferences().getMode();
        SchedulingMode mode = parseMode(modeRaw);
        LocalTime dayStart = parseTime(request.getPreferences() == null ? null : request.getPreferences().getDayStart(), LocalTime.of(8, 0));
        LocalTime dayEnd = parseTime(request.getPreferences() == null ? null : request.getPreferences().getDayEnd(), LocalTime.of(23, 30));
        if (!dayEnd.isAfter(dayStart)) {
            dayEnd = LocalTime.of(23, 30);
        }
        return new SchedulingPreference(mode, dayStart, dayEnd);
    }

    private SchedulingMode parseMode(String raw) {
        if (raw == null) {
            return SchedulingMode.BALANCED;
        }
        try {
            return SchedulingMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SchedulingMode.BALANCED;
        }
    }

    private List<Task> parseTasks(List<TaskRequest> tasks) {
        List<Task> results = new ArrayList<>();
        if (tasks == null) {
            return results;
        }
        for (TaskRequest task : tasks) {
            String id = task.getId() == null ? "task-" + results.size() : task.getId();
            LocalDateTime ddl = parseDateTime(task.getDdl());
            int duration = task.getDurationMin() == null ? 60 : Math.max(task.getDurationMin(), 15);
            int importance = task.getImportance() == null ? 1 : Math.max(task.getImportance(), 1);
            TaskType type = parseTaskType(task.getType());
            results.add(new Task(id, ddl, duration, importance, type, task.getCourseId()));
        }
        return results;
    }

    private List<TimeBlock> parseBlocks(List<TimeBlockRequest> blocks) {
        List<TimeBlock> results = new ArrayList<>();
        if (blocks == null) {
            return results;
        }
        for (TimeBlockRequest block : blocks) {
            LocalDateTime start = parseDateTime(block.getStart());
            LocalDateTime end = parseDateTime(block.getEnd());
            if (start == null || end == null || !end.isAfter(start)) {
                continue;
            }
            results.add(new TimeBlock(start, end, block.getType()));
        }
        return results;
    }

    private List<TimeSlot> buildFreeSlots(ScheduleAutoRequest request, List<TimeBlock> blocks, SchedulingPreference preference) {
        LocalDate start = parseDate(request.getRange() == null ? null : request.getRange().getStart());
        LocalDate end = parseDate(request.getRange() == null ? null : request.getRange().getEnd());
        if (start == null || end == null || end.isBefore(start)) {
            return List.of();
        }

        List<TimeSlot> freeSlots = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            freeSlots.add(new TimeSlot(date.atTime(preference.dayStart()), date.atTime(preference.dayEnd())));
        }

        return subtractBlocked(freeSlots, blocks);
    }

    private List<TimeSlot> subtractBlocked(List<TimeSlot> freeSlots, List<TimeBlock> blocks) {
        List<TimeSlot> slots = new ArrayList<>(freeSlots);
        for (TimeBlock block : blocks) {
            List<TimeSlot> updated = new ArrayList<>();
            TimeSlot used = new TimeSlot(block.start(), block.end());
            for (TimeSlot slot : slots) {
                if (!slot.overlaps(used)) {
                    updated.add(slot);
                    continue;
                }
                if (used.start().isAfter(slot.start())) {
                    updated.add(new TimeSlot(slot.start(), used.start()));
                }
                if (used.end().isBefore(slot.end())) {
                    updated.add(new TimeSlot(used.end(), slot.end()));
                }
            }
            slots = updated;
        }
        slots.sort((a, b) -> a.start().compareTo(b.start()));
        return slots;
    }

    private List<ScheduleItemResponse> toItemResponses(List<ScheduleItem> items) {
        List<ScheduleItemResponse> responses = new ArrayList<>();
        for (ScheduleItem item : items) {
            responses.add(new ScheduleItemResponse(item.taskId(), item.start().toString(), item.end().toString()));
        }
        return responses;
    }

    private List<String> resolveOverloadTasks(List<Task> tasks, List<SchedulePlan> plans) {
        if (plans.isEmpty()) {
            List<String> ids = new ArrayList<>();
            for (Task task : tasks) {
                ids.add(task.id());
            }
            return ids;
        }
        Set<String> scheduled = new HashSet<>();
        for (ScheduleItem item : plans.get(0).items()) {
            scheduled.add(item.taskId());
        }
        List<String> overload = new ArrayList<>();
        for (Task task : tasks) {
            if (!scheduled.contains(task.id())) {
                overload.add(task.id());
            }
        }
        return overload;
    }

    private TaskType parseTaskType(String raw) {
        if (raw == null) {
            return TaskType.FOCUS;
        }
        try {
            return TaskType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TaskType.FOCUS;
        }
    }

    private LocalDate parseDate(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), DATE_TIME);
        } catch (DateTimeParseException e) {
            LocalDate date = parseDate(raw.trim());
            return date == null ? null : date.atStartOfDay();
        }
    }

    private LocalTime parseTime(String raw, LocalTime fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return LocalTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }
}
