package com.kickstart.timetable.service;

import com.kickstart.timetable.api.dto.BellScheduleNode;
import com.kickstart.timetable.api.dto.CourseDefinition;
import com.kickstart.timetable.api.dto.CourseRule;
import com.kickstart.timetable.api.dto.CourseTableConfig;
import com.kickstart.timetable.api.dto.CourseTablePayload;
import com.kickstart.timetable.api.dto.GenerateScheduleRequest;
import com.kickstart.timetable.api.dto.TaskPayload;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SchedulePlannerService {
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm");
    private static final LocalTime DEFAULT_DAY_START = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_DAY_END = LocalTime.of(23, 0);
    private static final int TASK_BUFFER_MINUTES = 15;
    private static final int DAILY_TASK_LIMIT_MINUTES = 240;
    private static final int MIN_SPLIT_TASK_MINUTES = 45;
    private static final int MIN_DAILY_TASK_LIMIT_MINUTES = 120;
    private static final int MAX_SPLIT_SEGMENTS = 2;
    private static final int PREFERRED_SLOT_BONUS = 600;
    private static final int PREFERRED_DUE_BONUS = 250;
    private static final int CUTOFF_CLOSENESS_BONUS = 300;
    private static final int MAX_BACKTRACK_TASKS = 2;
    private static final LocalTime DAYTIME_START = LocalTime.of(9, 0);
    private static final LocalTime DAYTIME_END = LocalTime.of(18, 0);
    private static final int DAYTIME_BONUS = 200;

    public List<Map<String, Object>> generateSchedule(GenerateScheduleRequest request) {
        LocalDateTime now = parseDateTime(request.getCurrentDateTime());
        if (now == null) {
            now = LocalDateTime.now(ZoneId.systemDefault());
        }

        ScheduleContext context = buildContext(request, now);
        List<TimeBlock> availableSlots = buildAvailableSlots(context);
        List<ScheduledItem> scheduledTasks = scheduleTasks(context, availableSlots);

        return scheduledTasks.stream()
                .sorted(Comparator.comparing((ScheduledItem item) -> item.start).thenComparing(item -> item.title))
                .map(ScheduledItem::toMap)
                .toList();
    }

    private ScheduleContext buildContext(GenerateScheduleRequest request, LocalDateTime now) {
        LocalDate startDate = now.toLocalDate();
        LocalDate endDate = computeScheduleEndDate(now, request.getTasks(), startDate.plusDays(3));

        CourseTablePayload courseTable = request.getCourseTable();
        CourseTableConfig config = courseTable == null ? null : courseTable.getTableConfig();
        LocalDate semesterStart = parseDate(config == null ? null : config.getStartDate());

        Map<Integer, BellScheduleNode> nodeLookup = new HashMap<>();
        if (courseTable != null && courseTable.getTimeNodes() != null) {
            for (BellScheduleNode node : courseTable.getTimeNodes()) {
                nodeLookup.put(node.getNode(), node);
            }
        }

        Map<Integer, CourseDefinition> courseLookup = new HashMap<>();
        if (courseTable != null && courseTable.getCourses() != null) {
            for (CourseDefinition course : courseTable.getCourses()) {
                courseLookup.put(course.getId(), course);
            }
        }

        List<ScheduledItem> courses = new ArrayList<>();
        if (courseTable != null && courseTable.getCourseTimes() != null) {
            for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
                int weekNumber = computeWeekNumber(semesterStart, date);
                if (weekNumber <= 0) {
                    continue;
                }
                int dayOfWeek = date.getDayOfWeek().getValue();
                for (CourseRule rule : courseTable.getCourseTimes()) {
                    if (!matchesRule(rule, weekNumber, dayOfWeek)) {
                        continue;
                    }
                    TimeBlock block = toCourseBlock(rule, date, nodeLookup);
                    if (block == null) {
                        continue;
                    }
                    CourseDefinition def = courseLookup.get(rule.getId());
                    String title = def == null ? "课程" : def.getCourseName();
                    String details = formatCourseDetails(rule);
                    courses.add(ScheduledItem.course(title, details, block.start, block.end));
                }
            }
        }

        List<TaskPayload> tasks = request.getTasks() == null ? List.of() : request.getTasks();
        List<ScheduledItem> fixedTasks = new ArrayList<>();
        List<TaskPayload> pendingTasks = new ArrayList<>();
        for (TaskPayload task : tasks) {
            LocalDateTime scheduledAt = parseDateTime(task.getScheduledDateTime());
            if (scheduledAt != null) {
                int minutes = task.getEstimatedMinutes() > 0 ? task.getEstimatedMinutes() : MIN_SPLIT_TASK_MINUTES;
                LocalDateTime end = scheduledAt.plusMinutes(minutes);
                fixedTasks.add(ScheduledItem.task(task, scheduledAt, end));
            } else {
                pendingTasks.add(task);
            }
        }

        Map<LocalDate, Integer> courseMinutesByDate = summarizeCourseMinutes(courses);
        Map<LocalDate, Integer> fixedTaskMinutesByDate = summarizeFixedTaskMinutes(fixedTasks);
        List<TimeWindow> preferredTaskWindows = buildPreferredTaskWindows(courseTable);
        return new ScheduleContext(now, startDate, endDate, courses, pendingTasks, fixedTasks, courseMinutesByDate,
                fixedTaskMinutesByDate, preferredTaskWindows);
    }

    private LocalDate computeScheduleEndDate(LocalDateTime now, List<TaskPayload> tasks, LocalDate fallback) {
        if (tasks == null || tasks.isEmpty()) {
            return fallback;
        }
        LocalDate maxDue = fallback;
        for (TaskPayload task : tasks) {
            LocalDateTime due = parseDateTime(task.getDueDateTime());
            if (due != null && due.toLocalDate().isAfter(maxDue)) {
                maxDue = due.toLocalDate();
            }
        }
        LocalDate minDate = now.toLocalDate();
        if (maxDue.isBefore(minDate)) {
            return minDate.plusDays(1);
        }
        return maxDue.plusDays(1);
    }

    private List<TimeBlock> buildAvailableSlots(ScheduleContext context) {
        List<TimeBlock> blocked = new ArrayList<>();
        for (ScheduledItem course : context.courses) {
            blocked.add(new TimeBlock(course.start, course.end));
        }
        for (ScheduledItem task : context.fixedTasks) {
            blocked.add(new TimeBlock(task.start, task.end));
        }

        List<TimeBlock> available = new ArrayList<>();
        for (LocalDate date = context.startDate; date.isBefore(context.endDate); date = date.plusDays(1)) {
            LocalDateTime dayStart = LocalDateTime.of(date, DEFAULT_DAY_START);
            LocalDateTime dayEnd = LocalDateTime.of(date, DEFAULT_DAY_END);
            TimeBlock window = new TimeBlock(dayStart, dayEnd);
            List<TimeBlock> slots = new ArrayList<>();
            slots.add(window);

            for (TimeBlock block : blocked) {
                slots = subtract(slots, block);
            }

            LocalDateTime now = context.now;
            slots = slots.stream()
                    .map(slot -> slot.trimBefore(now))
                    .filter(slot -> slot != null && slot.durationMinutes() > 0)
                    .filter(slot -> !slot.start.toLocalDate().isBefore(context.now.toLocalDate()))
                    .toList();

            available.addAll(slots);
        }

        available.sort(Comparator.comparing(block -> block.start));
        return applyPreferredWindows(available, context.preferredTaskWindows);
    }

    private List<ScheduledItem> scheduleTasks(ScheduleContext context, List<TimeBlock> availableSlots) {
        List<TaskPayload> tasks = context.tasks.stream()
                .sorted(taskComparator())
                .toList();

        ScheduleRun initialRun = runScheduling(tasks, context, availableSlots, List.of(), context.fixedTasks);
        if (initialRun.pending.isEmpty()) {
            return initialRun.scheduled;
        }

        List<TaskPayload> deferred = initialRun.scheduledTasks.stream()
                .skip(Math.max(0, initialRun.scheduledTasks.size() - MAX_BACKTRACK_TASKS))
                .toList();
        List<TaskPayload> relaxed = new ArrayList<>(initialRun.pending);
        relaxed.addAll(deferred);
        List<TaskPayload> reordered = new ArrayList<>();
        for (TaskPayload task : tasks) {
            if (!deferred.contains(task)) {
                reordered.add(task);
            }
        }
        reordered.addAll(deferred);

        ScheduleRun retryRun = runScheduling(reordered, context, availableSlots, relaxed, context.fixedTasks);
        return retryRun.scheduled;
    }

    private ScheduleRun runScheduling(List<TaskPayload> tasks, ScheduleContext context,
                                      List<TimeBlock> availableSlots, List<TaskPayload> relaxedTasks,
                                      List<ScheduledItem> fixedTasks) {
        List<TimeBlock> slots = new ArrayList<>(availableSlots);
        List<ScheduledItem> scheduled = new ArrayList<>(fixedTasks);
        Map<LocalDate, Integer> dailyLoad = new HashMap<>();
        for (ScheduledItem task : fixedTasks) {
            int minutes = (int) Duration.between(task.start, task.end).toMinutes();
            incrementDailyLoad(dailyLoad, task.start.toLocalDate(), minutes);
        }
        List<TaskPayload> scheduledTasks = new ArrayList<>();
        List<TaskPayload> pending = new ArrayList<>();

        for (TaskPayload task : tasks) {
            boolean relaxed = relaxedTasks.contains(task);
            if (scheduleTask(task, context, slots, dailyLoad, scheduled, relaxed)) {
                scheduledTasks.add(task);
            } else {
                pending.add(task);
            }
        }

        return new ScheduleRun(scheduled, pending, scheduledTasks);
    }

    private boolean scheduleTask(TaskPayload task, ScheduleContext context, List<TimeBlock> slots,
                                 Map<LocalDate, Integer> dailyLoad, List<ScheduledItem> scheduled,
                                 boolean relaxed) {
        int remaining = Math.max(task.getEstimatedMinutes(), MIN_SPLIT_TASK_MINUTES);
        LocalDateTime due = parseDateTime(task.getDueDateTime());
        if (due == null) {
            due = LocalDateTime.of(context.endDate.minusDays(1), DEFAULT_DAY_END);
        }
        LocalDateTime preferredDue = preferEarlierThanDue(due);
        final int requiredMinutes = remaining;
        boolean ignoreDailyLimit = relaxed;
        TimeBlock selected = findSlot(slots, due, preferredDue, requiredMinutes, context, dailyLoad, ignoreDailyLimit);
        if (selected != null) {
            LocalDateTime end = selected.start.plusMinutes(requiredMinutes);
            scheduled.add(ScheduledItem.task(task, selected.start, end));
            List<TimeBlock> updatedSlots = consumeSlotWithBuffer(slots, selected, new TimeBlock(selected.start, end));
            slots.clear();
            slots.addAll(updatedSlots);
            incrementDailyLoad(dailyLoad, selected.start.toLocalDate(), requiredMinutes);
            return true;
        }

        AllocationAttempt attempt = allocateSplittable(slots, dailyLoad, remaining, due, preferredDue, context, ignoreDailyLimit);
        List<TimeBlock> allocations = attempt.allocations;
        if (!allocations.isEmpty()) {
            slots.clear();
            slots.addAll(attempt.slots);
            dailyLoad.clear();
            dailyLoad.putAll(attempt.dailyLoad);
        }

        if (allocations.isEmpty()) {
            return false;
        }

        for (int i = 0; i < allocations.size(); i++) {
            TimeBlock block = allocations.get(i);
            String suffix = allocations.size() > 1 ? "（" + (i + 1) + "/" + allocations.size() + "）" : "";
            scheduled.add(ScheduledItem.task(task, block.start, block.end, suffix));
        }
        return true;
    }

    private Comparator<TaskPayload> taskComparator() {
        return Comparator.<TaskPayload>comparingInt(task -> priorityWeight(task.getPriority())).reversed()
                .thenComparing(task -> Optional.ofNullable(parseDateTime(task.getDueDateTime()))
                        .orElse(LocalDateTime.MAX))
                .thenComparing(TaskPayload::getEstimatedMinutes, Comparator.reverseOrder());
    }

    private int remainingDailyCapacity(ScheduleContext context, Map<LocalDate, Integer> dailyLoad, LocalDate date) {
        int used = dailyLoad.getOrDefault(date, 0);
        int limit = dailyTaskLimitForDate(context, date);
        return Math.max(0, limit - used);
    }

    private boolean canFitWithinDailyLimit(ScheduleContext context, Map<LocalDate, Integer> dailyLoad, LocalDate date, int minutes) {
        return remainingDailyCapacity(context, dailyLoad, date) >= minutes;
    }

    private void incrementDailyLoad(Map<LocalDate, Integer> dailyLoad, LocalDate date, int minutes) {
        dailyLoad.put(date, dailyLoad.getOrDefault(date, 0) + minutes);
    }

    private TimeBlock findSlot(List<TimeBlock> slots, LocalDateTime cutoff, LocalDateTime preferredDue, int minutes,
                               ScheduleContext context, Map<LocalDate, Integer> dailyLoad, boolean ignoreDailyLimit) {
        TimeBlock best = null;
        long bestScore = Long.MIN_VALUE;
        for (int i = slots.size() - 1; i >= 0; i--) {
            TimeBlock slot = slots.get(i);
            LocalDateTime end = slot.start.plusMinutes(minutes);
            if (end.isAfter(cutoff)) {
                continue;
            }
            if (slot.durationMinutes() < minutes) {
                continue;
            }
            if (!ignoreDailyLimit && !canFitWithinDailyLimit(context, dailyLoad, slot.start.toLocalDate(), minutes)) {
                continue;
            }
            long score = scoreSlot(slot, end, cutoff, preferredDue, context, dailyLoad);
            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }

    private AllocationAttempt allocateSplittable(List<TimeBlock> slots, Map<LocalDate, Integer> dailyLoad,
                                                 int remaining, LocalDateTime cutoff, LocalDateTime preferredDue,
                                                 ScheduleContext context, boolean ignoreDailyLimit) {
        List<TimeBlock> allocations = new ArrayList<>();
        List<TimeBlock> tempSlots = new ArrayList<>(slots);
        Map<LocalDate, Integer> tempLoad = new HashMap<>(dailyLoad);
        int remainingMinutes = remaining;
        List<SlotCandidate> candidates = new ArrayList<>();
        for (TimeBlock slot : tempSlots) {
            if (remainingMinutes <= 0) {
                break;
            }
            if (slot.start.isAfter(cutoff)) {
                continue;
            }
            int allocMinutes = (int) Math.min(remainingMinutes, slot.durationMinutes());
            if (!ignoreDailyLimit) {
                allocMinutes = Math.min(allocMinutes, remainingDailyCapacity(context, tempLoad, slot.start.toLocalDate()));
            }
            if (remainingMinutes <= MIN_SPLIT_TASK_MINUTES * 2 && allocMinutes >= remainingMinutes) {
                allocMinutes = remainingMinutes;
            } else if (remainingMinutes > MIN_SPLIT_TASK_MINUTES * 2
                    && remainingMinutes - allocMinutes < MIN_SPLIT_TASK_MINUTES) {
                allocMinutes = remainingMinutes - MIN_SPLIT_TASK_MINUTES;
            }
            if (allocMinutes < MIN_SPLIT_TASK_MINUTES && remainingMinutes > MIN_SPLIT_TASK_MINUTES) {
                continue;
            }
            if (allocMinutes <= 0) {
                continue;
            }
            LocalDateTime end = slot.start.plusMinutes(allocMinutes);
            if (end.isAfter(cutoff)) {
                continue;
            }
            long score = scoreSlot(slot, end, cutoff, preferredDue, context, tempLoad);
            candidates.add(new SlotCandidate(slot, allocMinutes, score));
        }

        candidates.sort(Comparator.comparingLong((SlotCandidate candidate) -> candidate.score).reversed());
        int maxSegments = maxSplitSegments(remainingMinutes);
        for (SlotCandidate candidate : candidates) {
            if (remainingMinutes <= 0) {
                break;
            }
            TimeBlock slot = candidate.slot;
            if (!tempSlots.contains(slot)) {
                continue;
            }
            if (allocations.size() >= maxSegments) {
                break;
            }
            int allocMinutes = Math.min(remainingMinutes, candidate.allocMinutes);
            if (!ignoreDailyLimit) {
                allocMinutes = Math.min(allocMinutes, remainingDailyCapacity(context, tempLoad, slot.start.toLocalDate()));
            }
            if (allocMinutes < MIN_SPLIT_TASK_MINUTES && remainingMinutes > MIN_SPLIT_TASK_MINUTES) {
                continue;
            }
            if (allocMinutes <= 0) {
                continue;
            }
            if (allocations.size() == maxSegments - 1 && remainingMinutes - allocMinutes > 0) {
                continue;
            }
            LocalDateTime end = slot.start.plusMinutes(allocMinutes);
            if (end.isAfter(cutoff)) {
                continue;
            }
            allocations.add(new TimeBlock(slot.start, end));
            tempSlots = consumeSlotWithBuffer(tempSlots, slot, new TimeBlock(slot.start, end));
            incrementDailyLoad(tempLoad, slot.start.toLocalDate(), allocMinutes);
            remainingMinutes -= allocMinutes;
        }
        return new AllocationAttempt(allocations, tempSlots, tempLoad, remainingMinutes);
    }

    private LocalDateTime preferEarlierThanDue(LocalDateTime due) {
        LocalDateTime preferred = due.minusHours(6);
        return preferred.isAfter(due) ? due : preferred;
    }

    private long scoreSlot(TimeBlock slot, LocalDateTime end, LocalDateTime cutoff, LocalDateTime preferredDue,
                           ScheduleContext context, Map<LocalDate, Integer> dailyLoad) {
        long score = 0;
        if (slot.preferred) {
            score += PREFERRED_SLOT_BONUS;
        }
        if (!end.isAfter(preferredDue)) {
            score += PREFERRED_DUE_BONUS;
        }
        long minutesToCutoff = Duration.between(end, cutoff).toMinutes();
        if (minutesToCutoff >= 0) {
            score += Math.max(0, CUTOFF_CLOSENESS_BONUS - minutesToCutoff);
        }
        int remainingCapacity = remainingDailyCapacity(context, dailyLoad, slot.start.toLocalDate());
        score += remainingCapacity / 5L;
        if (remainingCapacity >= 180 && isDaytime(slot.start.toLocalTime())) {
            score += DAYTIME_BONUS;
        }
        return score;
    }

    private boolean isDaytime(LocalTime time) {
        return !time.isBefore(DAYTIME_START) && time.isBefore(DAYTIME_END);
    }

    private int dailyTaskLimitForDate(ScheduleContext context, LocalDate date) {
        int courseMinutes = context.courseMinutesByDate.getOrDefault(date, 0);
        int fixedMinutes = context.fixedTaskMinutesByDate.getOrDefault(date, 0);
        int limit = DAILY_TASK_LIMIT_MINUTES - courseMinutes - fixedMinutes;
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            limit += 120;
        } else if (courseMinutes <= 60) {
            limit += 60;
        }
        return Math.max(MIN_DAILY_TASK_LIMIT_MINUTES, limit);
    }

    private Map<LocalDate, Integer> summarizeFixedTaskMinutes(List<ScheduledItem> fixedTasks) {
        Map<LocalDate, Integer> totals = new HashMap<>();
        for (ScheduledItem task : fixedTasks) {
            LocalDate date = task.start.toLocalDate();
            int minutes = (int) Duration.between(task.start, task.end).toMinutes();
            totals.put(date, totals.getOrDefault(date, 0) + minutes);
        }
        return totals;
    }

    private int maxSplitSegments(int estimatedMinutes) {
        if (estimatedMinutes <= 180) {
            return 2;
        }
        if (estimatedMinutes <= 300) {
            return 3;
        }
        return 4;
    }

    private List<TimeWindow> buildPreferredTaskWindows(CourseTablePayload courseTable) {
        if (courseTable == null || courseTable.getTimeNodes() == null) {
            return List.of();
        }
        return courseTable.getTimeNodes().stream()
                .filter(node -> node.getNode() >= 1 && node.getNode() <= 10)
                .sorted(Comparator.comparingInt(BellScheduleNode::getNode))
                .map(node -> new TimeWindow(parseLocalTime(node.getStartTime()), parseLocalTime(node.getEndTime())))
                .filter(window -> window.start != null && window.end != null && window.end.isAfter(window.start))
                .toList();
    }

    private List<TimeBlock> applyPreferredWindows(List<TimeBlock> slots, List<TimeWindow> windows) {
        if (windows == null || windows.isEmpty()) {
            return slots;
        }
        List<TimeBlock> result = new ArrayList<>();
        for (TimeBlock slot : slots) {
            List<TimeBlock> segments = new ArrayList<>();
            segments.add(slot);
            for (TimeWindow window : windows) {
                List<TimeBlock> next = new ArrayList<>();
                for (TimeBlock segment : segments) {
                    next.addAll(splitByPreferredWindow(segment, window));
                }
                segments = next;
            }
            result.addAll(segments);
        }
        result.sort(Comparator.comparing(block -> block.start));
        return result;
    }

    private List<TimeBlock> splitByPreferredWindow(TimeBlock slot, TimeWindow window) {
        if (slot.preferred) {
            return List.of(slot);
        }
        LocalDate date = slot.start.toLocalDate();
        LocalDateTime windowStart = LocalDateTime.of(date, window.start);
        LocalDateTime windowEnd = LocalDateTime.of(date, window.end);
        TimeBlock windowBlock = new TimeBlock(windowStart, windowEnd, true);
        if (!slot.overlaps(windowBlock)) {
            return List.of(slot);
        }
        List<TimeBlock> result = new ArrayList<>();
        if (windowBlock.start.isAfter(slot.start)) {
            result.add(new TimeBlock(slot.start, windowBlock.start, false));
        }
        LocalDateTime overlapStart = slot.start.isAfter(windowBlock.start) ? slot.start : windowBlock.start;
        LocalDateTime overlapEnd = slot.end.isBefore(windowBlock.end) ? slot.end : windowBlock.end;
        if (overlapEnd.isAfter(overlapStart)) {
            result.add(new TimeBlock(overlapStart, overlapEnd, true));
        }
        if (windowBlock.end.isBefore(slot.end)) {
            result.add(new TimeBlock(windowBlock.end, slot.end, false));
        }
        return result;
    }

    private Map<LocalDate, Integer> summarizeCourseMinutes(List<ScheduledItem> courses) {
        Map<LocalDate, Integer> totals = new HashMap<>();
        for (ScheduledItem course : courses) {
            LocalDate date = course.start.toLocalDate();
            int minutes = (int) Duration.between(course.start, course.end).toMinutes();
            totals.put(date, totals.getOrDefault(date, 0) + minutes);
        }
        return totals;
    }

    private int priorityWeight(String priority) {
        if (priority == null) {
            return 0;
        }
        return switch (priority.toLowerCase(Locale.ROOT)) {
            case "urgent" -> 4;
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }

    private boolean matchesRule(CourseRule rule, int weekNumber, int dayOfWeek) {
        if (rule == null) {
            return false;
        }
        if (rule.getDay() != dayOfWeek) {
            return false;
        }
        if (weekNumber < rule.getStartWeek() || weekNumber > rule.getEndWeek()) {
            return false;
        }
        int type = rule.getType();
        if (type == 1 && weekNumber % 2 == 0) {
            return false;
        }
        if (type == 2 && weekNumber % 2 != 0) {
            return false;
        }
        return true;
    }

    private TimeBlock toCourseBlock(CourseRule rule, LocalDate date, Map<Integer, BellScheduleNode> nodeLookup) {
        BellScheduleNode startNode = nodeLookup.get(rule.getStartNode());
        BellScheduleNode endNode = nodeLookup.get(rule.getStartNode() + rule.getStep() - 1);
        if (startNode == null || endNode == null) {
            return null;
        }
        LocalTime start = parseLocalTime(startNode.getStartTime());
        LocalTime end = parseLocalTime(endNode.getEndTime());
        if (start == null || end == null) {
            return null;
        }
        LocalDateTime startDateTime = LocalDateTime.of(date, start);
        LocalDateTime endDateTime = LocalDateTime.of(date, end);
        if (!end.isAfter(start)) {
            endDateTime = endDateTime.plusDays(1);
        }
        return new TimeBlock(startDateTime, endDateTime);
    }

    private String formatCourseDetails(CourseRule rule) {
        List<String> parts = new ArrayList<>();
        if (rule.getRoom() != null && !rule.getRoom().isBlank()) {
            parts.add(rule.getRoom());
        }
        if (rule.getTeacher() != null && !rule.getTeacher().isBlank()) {
            parts.add(rule.getTeacher());
        }
        return parts.stream().collect(Collectors.joining(" · "));
    }

    private LocalDateTime parseDateTime(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(input, ISO_DATE_TIME).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(input, ISO_DATE_TIME);
        } catch (Exception ignored) {
        }
        return null;
    }

    private LocalDate parseDate(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(input);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalTime parseLocalTime(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(input, TIME_ONLY);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int computeWeekNumber(LocalDate semesterStart, LocalDate date) {
        if (semesterStart == null) {
            return 1;
        }
        long days = ChronoUnit.DAYS.between(semesterStart, date);
        if (days < 0) {
            return -1;
        }
        return (int) (days / 7) + 1;
    }

    private List<TimeBlock> subtract(List<TimeBlock> slots, TimeBlock block) {
        List<TimeBlock> result = new ArrayList<>();
        for (TimeBlock slot : slots) {
            if (!slot.overlaps(block)) {
                result.add(slot);
                continue;
            }
            if (block.start.isAfter(slot.start)) {
                result.add(new TimeBlock(slot.start, block.start, slot.preferred));
            }
            if (block.end.isBefore(slot.end)) {
                result.add(new TimeBlock(block.end, slot.end, slot.preferred));
            }
        }
        return result;
    }

    private List<TimeBlock> consumeSlot(List<TimeBlock> slots, TimeBlock slot, TimeBlock allocation) {
        return slots.stream()
                .filter(existing -> !existing.equals(slot))
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                    if (allocation.start.isAfter(slot.start)) {
                        list.add(new TimeBlock(slot.start, allocation.start, slot.preferred));
                    }
                    if (allocation.end.isBefore(slot.end)) {
                        list.add(new TimeBlock(allocation.end, slot.end, slot.preferred));
                    }
                    list.sort(Comparator.comparing(block -> block.start));
                    return list;
                }));
    }

    private List<TimeBlock> consumeSlotWithBuffer(List<TimeBlock> slots, TimeBlock slot, TimeBlock allocation) {
        LocalDateTime bufferedStart = allocation.start.minusMinutes(TASK_BUFFER_MINUTES);
        LocalDateTime bufferedEnd = allocation.end.plusMinutes(TASK_BUFFER_MINUTES);
        LocalDateTime safeStart = bufferedStart.isAfter(slot.start) ? bufferedStart : slot.start;
        LocalDateTime safeEnd = bufferedEnd.isBefore(slot.end) ? bufferedEnd : slot.end;
        return slots.stream()
                .filter(existing -> !existing.equals(slot))
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                    if (safeStart.isAfter(slot.start)) {
                        list.add(new TimeBlock(slot.start, safeStart, slot.preferred));
                    }
                    if (safeEnd.isBefore(slot.end)) {
                        list.add(new TimeBlock(safeEnd, slot.end, slot.preferred));
                    }
                    list.sort(Comparator.comparing(block -> block.start));
                    return list;
                }));
    }

    private static class ScheduleContext {
        private final LocalDateTime now;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final List<ScheduledItem> courses;
        private final List<TaskPayload> tasks;
        private final List<ScheduledItem> fixedTasks;
        private final Map<LocalDate, Integer> courseMinutesByDate;
        private final Map<LocalDate, Integer> fixedTaskMinutesByDate;
        private final List<TimeWindow> preferredTaskWindows;
        private ScheduleContext(LocalDateTime now, LocalDate startDate, LocalDate endDate,
                                List<ScheduledItem> courses, List<TaskPayload> tasks, List<ScheduledItem> fixedTasks,
                                Map<LocalDate, Integer> courseMinutesByDate,
                                Map<LocalDate, Integer> fixedTaskMinutesByDate,
                                List<TimeWindow> preferredTaskWindows) {
            this.now = now;
            this.startDate = startDate;
            this.endDate = endDate;
            this.courses = courses;
            this.tasks = tasks;
            this.fixedTasks = fixedTasks;
            this.courseMinutesByDate = courseMinutesByDate;
            this.fixedTaskMinutesByDate = fixedTaskMinutesByDate;
            this.preferredTaskWindows = preferredTaskWindows;
        }
    }

    private static class SlotCandidate {
        private final TimeBlock slot;
        private final int allocMinutes;
        private final long score;

        private SlotCandidate(TimeBlock slot, int allocMinutes, long score) {
            this.slot = slot;
            this.allocMinutes = allocMinutes;
            this.score = score;
        }
    }

    private static class AllocationAttempt {
        private final List<TimeBlock> allocations;
        private final List<TimeBlock> slots;
        private final Map<LocalDate, Integer> dailyLoad;
        private final int remaining;

        private AllocationAttempt(List<TimeBlock> allocations, List<TimeBlock> slots,
                                  Map<LocalDate, Integer> dailyLoad, int remaining) {
            this.allocations = allocations;
            this.slots = slots;
            this.dailyLoad = dailyLoad;
            this.remaining = remaining;
        }
    }

    private static class ScheduleRun {
        private final List<ScheduledItem> scheduled;
        private final List<TaskPayload> pending;
        private final List<TaskPayload> scheduledTasks;

        private ScheduleRun(List<ScheduledItem> scheduled, List<TaskPayload> pending, List<TaskPayload> scheduledTasks) {
            this.scheduled = scheduled;
            this.pending = pending;
            this.scheduledTasks = scheduledTasks;
        }
    }

    private static class TimeBlock {
        private final LocalDateTime start;
        private final LocalDateTime end;
        private final boolean preferred;

        private TimeBlock(LocalDateTime start, LocalDateTime end) {
            this(start, end, false);
        }

        private TimeBlock(LocalDateTime start, LocalDateTime end, boolean preferred) {
            this.start = start;
            this.end = end;
            this.preferred = preferred;
        }

        private long durationMinutes() {
            return Duration.between(start, end).toMinutes();
        }

        private boolean overlaps(TimeBlock other) {
            return start.isBefore(other.end) && end.isAfter(other.start);
        }

        private TimeBlock trimBefore(LocalDateTime cutoff) {
            if (end.isBefore(cutoff) || end.isEqual(cutoff)) {
                return null;
            }
            if (start.isBefore(cutoff)) {
                return new TimeBlock(cutoff, end, preferred);
            }
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TimeBlock timeBlock = (TimeBlock) o;
            return Objects.equals(start, timeBlock.start)
                    && Objects.equals(end, timeBlock.end)
                    && preferred == timeBlock.preferred;
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end, preferred);
        }
    }

    private static class TimeWindow {
        private final LocalTime start;
        private final LocalTime end;

        private TimeWindow(LocalTime start, LocalTime end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class ScheduledItem {
        private final String id;
        private final String type;
        private final String title;
        private final String details;
        private final String dueDateTime;
        private final String priority;
        private final int estimatedMinutes;
        private final LocalDateTime start;
        private final LocalDateTime end;

        private ScheduledItem(String id, String type, String title, String details, String dueDateTime,
                              String priority, int estimatedMinutes, LocalDateTime start, LocalDateTime end) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.details = details;
            this.dueDateTime = dueDateTime;
            this.priority = priority;
            this.estimatedMinutes = estimatedMinutes;
            this.start = start;
            this.end = end;
        }

        private static ScheduledItem course(String title, String details, LocalDateTime start, LocalDateTime end) {
            String id = "COURSE-" + start.toLocalDate() + "-" + title;
            return new ScheduledItem(id, "COURSE", title, details, null, null, 0, start, end);
        }

        private static ScheduledItem task(TaskPayload task, LocalDateTime start, LocalDateTime end) {
            return task(task, start, end, "");
        }

        private static ScheduledItem task(TaskPayload task, LocalDateTime start, LocalDateTime end, String suffix) {
            String id = (task.getId() == null || task.getId().isBlank()) ? "TASK-" + start : task.getId();
            String title = task.getTitle() == null ? "任务" : task.getTitle();
            if (!suffix.isBlank()) {
                title = title + suffix;
            }
            String details = buildTaskDetails(task);
            return new ScheduledItem(id, "TASK", title, details, task.getDueDateTime(), task.getPriority(),
                    task.getEstimatedMinutes(), start, end);
        }

        private static String buildTaskDetails(TaskPayload task) {
            Map<String, String> parts = new LinkedHashMap<>();
            if (task.getPriority() != null && !task.getPriority().isBlank()) {
                parts.put("优先级", task.getPriority());
            }
            if (task.getCourseId() != null && !task.getCourseId().isBlank()) {
                parts.put("课程", task.getCourseId());
            }
            if (task.getType() != null && !task.getType().isBlank()) {
                parts.put("类型", task.getType());
            }
            return parts.entrySet().stream()
                    .map(entry -> entry.getKey() + ":" + entry.getValue())
                    .collect(Collectors.joining(" · "));
        }

        private Map<String, Object> toMap() {
            Map<String, Object> out = new HashMap<>();
            out.put("id", id);
            out.put("type", type);
            out.put("title", title);
            out.put("day", formatDay(start.getDayOfWeek()));
            out.put("date", start.toLocalDate().toString());
            out.put("startTime", start.toLocalTime().format(TIME_ONLY));
            out.put("startDateTime", start.format(ISO_DATE_TIME));
            out.put("dueDateTime", dueDateTime == null ? "" : dueDateTime);
            out.put("priority", priority == null ? "" : priority);
            out.put("estimatedMinutes", estimatedMinutes);
            out.put("details", details == null ? "" : details);
            return out;
        }

        private static String formatDay(DayOfWeek day) {
            return switch (day) {
                case MONDAY -> "Monday";
                case TUESDAY -> "Tuesday";
                case WEDNESDAY -> "Wednesday";
                case THURSDAY -> "Thursday";
                case FRIDAY -> "Friday";
                case SATURDAY -> "Saturday";
                case SUNDAY -> "Sunday";
            };
        }
    }
}
