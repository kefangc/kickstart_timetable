package com.kickstart.timetable.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kickstart.timetable.api.dto.GenerateScheduleRequest;
import com.kickstart.timetable.api.dto.TaskPayload;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulePlannerServiceTest {

    @Test
    void generatesScheduleFromRandomizedTasks() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        GenerateScheduleRequest request = readSampleRequest(mapper);

        Random random = new Random(42L);
        LocalDate baseDate = LocalDate.of(2025, 9, 10);
        LocalTime currentTime = LocalTime.of(10 + random.nextInt(6), random.nextBoolean() ? 0 : 30);
        LocalDateTime currentDateTime = LocalDateTime.of(baseDate, currentTime);

        request.setCurrentDateTime(OffsetDateTime.of(currentDateTime, ZoneOffset.ofHours(8)).toString());
        request.setTasks(randomTasks(random, baseDate));

        SchedulePlannerService planner = new SchedulePlannerService();
        List<Map<String, Object>> schedule = planner.generateSchedule(request);

        assertFalse(schedule.isEmpty(), "Schedule should include course/task blocks");
        assertTrue(schedule.stream().noneMatch(item -> isLateNight(item)), "Schedule should avoid late-night blocks");
        assertTrue(schedule.stream().noneMatch(item -> isTaskBeforeNow(item, currentDateTime)),
                "Tasks should not be scheduled before current time");
        assertTrue(schedule.stream().allMatch(item -> hasDateTimes(item)),
                "Tasks should include startDateTime");
    }

    private GenerateScheduleRequest readSampleRequest(ObjectMapper mapper) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/sample-schedule-request.json")) {
            if (input == null) {
                throw new IOException("Missing sample-schedule-request.json resource");
            }
            return mapper.readValue(input, GenerateScheduleRequest.class);
        }
    }

    private List<TaskPayload> randomTasks(Random random, LocalDate baseDate) {
        String[] titles = {"离散数学作业", "英语听说练习", "数据结构预习", "课程笔记整理", "编程练习"};
        String[] priorities = {"High", "Medium", "Low"};
        String[] types = {"focus", "light"};
        String[] courses = {"离散数学[06]", "大学英语听说（4）[03]", "数据结构与算法[01]", "形势与政策（3）[40]"};

        List<TaskPayload> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TaskPayload task = new TaskPayload();
            task.setId("task-" + (i + 1));
            task.setTitle(titles[i]);
            task.setEstimatedMinutes(45 + random.nextInt(90));
            task.setPriority(priorities[random.nextInt(priorities.length)]);
            task.setType(types[random.nextInt(types.length)]);
            task.setCourseId(courses[random.nextInt(courses.length)]);
            task.setScheduledDateTime("");

            LocalDate dueDate = baseDate.plusDays(1 + random.nextInt(2));
            LocalTime dueTime = LocalTime.of(18 + random.nextInt(4), random.nextBoolean() ? 0 : 30);
            task.setDueDateTime(OffsetDateTime.of(dueDate, dueTime, ZoneOffset.ofHours(8)).toString());

            tasks.add(task);
        }
        return tasks;
    }

    private boolean isLateNight(Map<String, Object> item) {
        if (!"TASK".equals(item.get("type")) && !"COURSE".equals(item.get("type"))) {
            return false;
        }
        String startTime = (String) item.get("startTime");
        Integer estimatedMinutes = asInteger(item.get("estimatedMinutes"));
        if (startTime == null || estimatedMinutes == null) {
            return false;
        }
        LocalTime start = LocalTime.parse(startTime);
        LocalTime end = start.plusMinutes(estimatedMinutes);
        return start.isBefore(LocalTime.of(7, 0)) || end.isAfter(LocalTime.of(23, 0));
    }

    private boolean isTaskBeforeNow(Map<String, Object> item, LocalDateTime now) {
        if (!"TASK".equals(item.get("type"))) {
            return false;
        }
        String startDateTime = (String) item.get("startDateTime");
        if (startDateTime != null && !startDateTime.isBlank()) {
            LocalDateTime start = LocalDateTime.parse(startDateTime);
            return start.isBefore(now);
        }
        String day = (String) item.get("day");
        String startTime = (String) item.get("startTime");
        if (day == null || startTime == null) {
            return false;
        }
        if (!day.equalsIgnoreCase(now.getDayOfWeek().name())) {
            return false;
        }
        LocalDateTime start = LocalDateTime.of(now.toLocalDate(), LocalTime.parse(startTime));
        return start.isBefore(now);
    }

    private boolean hasDateTimes(Map<String, Object> item) {
        if (!"TASK".equals(item.get("type"))) {
            return true;
        }
        String start = (String) item.get("startDateTime");
        return start != null && !start.isBlank();
    }

    private Integer asInteger(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
