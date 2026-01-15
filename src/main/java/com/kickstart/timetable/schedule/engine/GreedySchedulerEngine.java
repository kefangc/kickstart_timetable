package com.kickstart.timetable.schedule.engine;

import com.kickstart.timetable.schedule.model.ScheduleItem;
import com.kickstart.timetable.schedule.model.SchedulePlan;
import com.kickstart.timetable.schedule.model.SchedulingMode;
import com.kickstart.timetable.schedule.model.SchedulingPreference;
import com.kickstart.timetable.schedule.model.Task;
import com.kickstart.timetable.schedule.model.TaskType;
import com.kickstart.timetable.schedule.model.TimeBlock;
import com.kickstart.timetable.schedule.model.TimeSlot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GreedySchedulerEngine implements SchedulerEngine {
    @Override
    public List<SchedulePlan> generate(List<Task> tasks, List<TimeBlock> timetableBlocked, List<TimeSlot> freeSlots,
                                       SchedulingPreference pref) {
        List<SchedulePlan> plans = new ArrayList<>();
        plans.add(buildPlan("p1", "均衡型", tasks, freeSlots, SchedulingMode.BALANCED));
        plans.add(buildPlan("p2", "赶进度型", tasks, freeSlots, SchedulingMode.URGENT));
        plans.add(buildPlan("p3", "轻松型", tasks, freeSlots, SchedulingMode.RELAXED));
        return plans;
    }

    private SchedulePlan buildPlan(String planId, String label, List<Task> tasks, List<TimeSlot> freeSlots,
                                   SchedulingMode mode) {
        List<Task> sortedTasks = new ArrayList<>(tasks);
        sortedTasks.sort(taskComparator());

        List<TimeSlot> available = cloneSlots(freeSlots);
        List<ScheduleItem> items = new ArrayList<>();
        Map<LocalDate, Integer> dailyLoad = new HashMap<>();

        for (Task task : sortedTasks) {
            Allocation allocation = findBestSlot(task, available, dailyLoad, mode);
            if (allocation == null) {
                continue;
            }
            items.add(new ScheduleItem(task.id(), allocation.start, allocation.end));
            dailyLoad.merge(allocation.start.toLocalDate(), task.durationMin(), Integer::sum);
            available = subtractSlot(available, new TimeSlot(allocation.start, allocation.end));
        }

        items.sort(Comparator.comparing(ScheduleItem::start));
        return new SchedulePlan(planId, label, items);
    }

    private Allocation findBestSlot(Task task, List<TimeSlot> slots, Map<LocalDate, Integer> dailyLoad,
                                    SchedulingMode mode) {
        double bestScore = -1;
        Allocation best = null;
        for (TimeSlot slot : slots) {
            if (slot.durationMinutes() < task.durationMin()) {
                continue;
            }
            LocalDateTime start = slot.start();
            LocalDateTime end = start.plusMinutes(task.durationMin());
            if (end.isAfter(slot.end())) {
                continue;
            }
            if (task.ddl() != null && end.isAfter(task.ddl())) {
                continue;
            }
            double score = scoreSlot(task, start, dailyLoad, mode);
            if (score > bestScore || (Math.abs(score - bestScore) < 0.001 && isEarlier(start, best))) {
                bestScore = score;
                best = new Allocation(start, end);
            }
        }
        return best;
    }

    private boolean isEarlier(LocalDateTime candidate, Allocation best) {
        if (best == null) {
            return true;
        }
        return candidate.isBefore(best.start);
    }

    private double scoreSlot(Task task, LocalDateTime start, Map<LocalDate, Integer> dailyLoad, SchedulingMode mode) {
        double typeMatch = typeMatchScore(task.type(), start.toLocalTime());
        double ddlUrgency = ddlUrgencyScore(task.ddl(), start.toLocalDate());
        double balanceScore = balanceScore(dailyLoad.getOrDefault(start.toLocalDate(), 0));

        Weights weights = weightsForMode(mode);
        return weights.typeWeight * typeMatch + weights.urgencyWeight * ddlUrgency + weights.balanceWeight * balanceScore;
    }

    private double typeMatchScore(TaskType type, LocalTime start) {
        boolean isDaytime = start.isBefore(LocalTime.of(18, 0));
        if (type == TaskType.FOCUS) {
            return isDaytime ? 1.0 : 0.5;
        }
        return isDaytime ? 0.7 : 1.0;
    }

    private double ddlUrgencyScore(LocalDateTime ddl, LocalDate date) {
        if (ddl == null) {
            return 0.2;
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(date, ddl.toLocalDate()));
        return 1.0 / (1 + days);
    }

    private double balanceScore(int minutes) {
        double hours = minutes / 60.0;
        return 1.0 / (1.0 + hours);
    }

    private Weights weightsForMode(SchedulingMode mode) {
        return switch (mode) {
            case URGENT -> new Weights(0.2, 0.6, 0.2);
            case RELAXED -> new Weights(0.5, 0.2, 0.3);
            case BALANCED -> new Weights(0.4, 0.4, 0.2);
        };
    }

    private Comparator<Task> taskComparator() {
        return Comparator.<Task>comparingInt(Task::importance).reversed()
                .thenComparing(task -> task.ddl() == null ? LocalDateTime.MAX : task.ddl())
                .thenComparingInt(Task::durationMin);
    }

    private List<TimeSlot> cloneSlots(List<TimeSlot> slots) {
        List<TimeSlot> copies = new ArrayList<>(slots.size());
        for (TimeSlot slot : slots) {
            copies.add(new TimeSlot(slot.start(), slot.end()));
        }
        copies.sort(Comparator.comparing(TimeSlot::start));
        return copies;
    }

    private List<TimeSlot> subtractSlot(List<TimeSlot> slots, TimeSlot used) {
        List<TimeSlot> updated = new ArrayList<>();
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
        updated.sort(Comparator.comparing(TimeSlot::start));
        return updated;
    }

    private record Allocation(LocalDateTime start, LocalDateTime end) {
    }

    private record Weights(double typeWeight, double urgencyWeight, double balanceWeight) {
    }
}
