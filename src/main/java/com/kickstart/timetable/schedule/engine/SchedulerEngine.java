package com.kickstart.timetable.schedule.engine;

import com.kickstart.timetable.schedule.model.SchedulePlan;
import com.kickstart.timetable.schedule.model.SchedulingPreference;
import com.kickstart.timetable.schedule.model.Task;
import com.kickstart.timetable.schedule.model.TimeBlock;
import com.kickstart.timetable.schedule.model.TimeSlot;

import java.util.List;

public interface SchedulerEngine {
    List<SchedulePlan> generate(
            List<Task> tasks,
            List<TimeBlock> timetableBlocked,
            List<TimeSlot> freeSlots,
            SchedulingPreference pref
    );
}
