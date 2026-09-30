package com.metawebdesigner.gm220rebooter;
import java.util.Calendar;
import java.util.TimeZone;

public final class SafetyAndScheduleTest {
    public static void main(String[] args) {
        long now = 10_000_000;
        require(!SafetyPolicy.watchdogReady(1) && !SafetyPolicy.watchdogReady(2) && SafetyPolicy.watchdogReady(3), "Three failures required");
        require(SafetyPolicy.blocked(now, 0, 2, 2, 30) != null, "Daily cap");
        require(SafetyPolicy.blocked(now, now - 29 * 60_000, 0, 2, 30) != null, "Cooldown");
        require(SafetyPolicy.blocked(now, now + 1, 0, 2, 30) != null, "Clock rollback must block");
        require(SafetyPolicy.blocked(now, now - 30 * 60_000, 1, 2, 30) == null, "Cooldown boundary");
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Karachi"));
            Calendar monday = Calendar.getInstance(); monday.clear(); monday.set(2026, Calendar.SEPTEMBER, 28, 4, 0);
            long start = monday.getTimeInMillis();
            long sameDay = ScheduleTime.next(start, 5, 0, 127);
            require(sameDay - start == 3_600_000L, "Daily future time");
            require(ScheduleTime.next(sameDay, 5, 0, 127) - sameDay == 86_400_000L, "No duplicate at exact minute");
            require(ScheduleTime.next(start, 5, 0, 1 << 2) - sameDay == 86_400_000L, "Selected Tuesday");
            require(ScheduleTime.next(start, 5, 0, 0) == 0, "No days disables scheduling");
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            Calendar dst = Calendar.getInstance(); dst.clear(); dst.set(2026, Calendar.MARCH, 7, 5, 0);
            require(ScheduleTime.next(dst.getTimeInMillis(), 5, 0, 127) - dst.getTimeInMillis() == 23 * 3_600_000L, "DST keeps local time");
        } finally { TimeZone.setDefault(original); }
        System.out.println("PASS: failure threshold, cap, cooldown, clock rollback, weekdays, daily boundary and DST");
    }
    private static void require(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
