package com.metawebdesigner.gm220rebooter;

import android.app.*;
import android.content.*;
import android.os.Build;

public final class AlarmScheduler {
    public static final String ACTION_DAILY = "com.metawebdesigner.gm220guardian.DAILY";
    public static final String ACTION_RETRY = "com.metawebdesigner.gm220guardian.RETRY";
    private static PendingIntent intent(Context c, boolean retry, int count) {
        Intent i = new Intent(c, RebootAlarmReceiver.class).setAction(retry ? ACTION_RETRY : ACTION_DAILY)
            .putExtra("retry_count", count).putExtra("generation", new SecurePrefs(c).raw().getLong("generation", 0));
        return PendingIntent.getBroadcast(c, retry ? 221 : 220, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static long nextMillis(int hour, int minute) { return ScheduleTime.next(System.currentTimeMillis(), hour, minute, 127); }
    private static void set(Context c, long at, PendingIntent pi) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException ignored) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); }
    }
    public static void schedule(Context c) {
        SecurePrefs p = new SecurePrefs(c);
        if (!p.raw().getBoolean("enabled", false)) { cancelAll(c); return; }
        long at = ScheduleTime.next(System.currentTimeMillis(), p.raw().getInt("hour", 5), p.raw().getInt("minute", 0), p.raw().getInt("days", 127));
        if (at == 0) { cancelAll(c); return; }
        set(c, at, intent(c, false, 0));
        p.raw().edit().putLong("next_run", at).apply();
    }
    public static void scheduleRetry(Context c, int count) {
        SecurePrefs p = new SecurePrefs(c);
        if (!p.raw().getBoolean("enabled", false) || count < 1 || count > p.raw().getInt("retries", 3)) return;
        long at = System.currentTimeMillis() + p.raw().getInt("retry_minutes", 10) * 60_000L;
        set(c, at, intent(c, true, count));
        p.raw().edit().putLong("retry_run", at).putInt("retry_count", count).apply();
    }
    public static void cancelRetry(Context c) {
        c.getSystemService(AlarmManager.class).cancel(intent(c, true, 0));
        new SecurePrefs(c).raw().edit().remove("retry_run").remove("retry_count").apply();
    }
    public static void cancelAll(Context c) {
        c.getSystemService(AlarmManager.class).cancel(intent(c, false, 0));
        cancelRetry(c);
        new SecurePrefs(c).raw().edit().remove("next_run").apply();
    }
    public static String formatNext(Context c) {
        SecurePrefs p = new SecurePrefs(c);
        long next = p.raw().getLong("next_run", 0), retry = p.raw().getLong("retry_run", 0);
        if (retry > 0) next = next == 0 ? retry : Math.min(next, retry);
        return next == 0 ? "Not scheduled" : HistoryStore.time(next);
    }
}
