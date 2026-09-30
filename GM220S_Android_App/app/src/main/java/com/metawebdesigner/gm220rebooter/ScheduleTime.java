package com.metawebdesigner.gm220rebooter;
import java.util.Calendar;

public final class ScheduleTime {
    private ScheduleTime() {}
    /** Bits 0..6 represent Sunday..Saturday in the phone's current timezone. */
    public static long next(long now, int hour, int minute, int days) {
        if ((days & 127) == 0) return 0;
        for (int offset = 0; offset <= 7; offset++) {
            Calendar next = Calendar.getInstance();
            next.setTimeInMillis(now);
            next.add(Calendar.DAY_OF_YEAR, offset);
            next.set(Calendar.HOUR_OF_DAY, hour);
            next.set(Calendar.MINUTE, minute);
            next.set(Calendar.SECOND, 0);
            next.set(Calendar.MILLISECOND, 0);
            int bit = 1 << (next.get(Calendar.DAY_OF_WEEK) - 1);
            if ((days & bit) != 0 && next.getTimeInMillis() > now) return next.getTimeInMillis();
        }
        return 0;
    }
}
