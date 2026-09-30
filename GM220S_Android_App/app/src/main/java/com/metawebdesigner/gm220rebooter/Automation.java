package com.metawebdesigner.gm220rebooter;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public final class Automation {
    static final String WATCHDOG = "guardian-watchdog", SCHEDULE = "guardian-schedule", RECOVERY = "guardian-recovery";
    private Automation() {}
    public static void restore(Context c) {
        SecurePrefs p = new SecurePrefs(c);
        WorkManager wm = WorkManager.getInstance(c);
        if (p.raw().getBoolean("watchdog", false)) {
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(GuardianWorker.class, p.raw().getInt("interval", 15), TimeUnit.MINUTES)
                .setInputData(new Data.Builder().putString("kind", "watchdog").build()).addTag(WATCHDOG).build();
            wm.enqueueUniquePeriodicWork(WATCHDOG, ExistingPeriodicWorkPolicy.UPDATE, request);
        } else { wm.cancelUniqueWork(WATCHDOG); p.raw().edit().putInt("failures", 0).apply(); }
        if (!p.raw().getBoolean("enabled", false)) { AlarmScheduler.cancelAll(c); wm.cancelUniqueWork(SCHEDULE); }
        if (!p.raw().getBoolean("watchdog", false) && !p.raw().getBoolean("enabled", false)) wm.cancelAllWorkByTag(RECOVERY);
    }
    public static void settingsChanged(Context c) {
        SecurePrefs p = new SecurePrefs(c);
        p.raw().edit().putLong("generation", p.raw().getLong("generation", 0) + 1).putInt("failures", 0).commit();
        WorkManager.getInstance(c).cancelUniqueWork(SCHEDULE);
        WorkManager.getInstance(c).cancelAllWorkByTag(RECOVERY);
        AlarmScheduler.cancelAll(c);
        AlarmScheduler.schedule(c);
        restore(c);
    }
    static void enqueueScheduled(Context c, int retry) {
        long generation = new SecurePrefs(c).raw().getLong("generation", 0);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(GuardianWorker.class)
            .setInputData(new Data.Builder().putString("kind", "scheduled").putInt("retry", retry).putLong("generation", generation).build())
            .addTag(SCHEDULE).build();
        WorkManager.getInstance(c).enqueueUniqueWork(SCHEDULE, ExistingWorkPolicy.KEEP, request);
    }
    static void recovery(Context c, String source, int attempt, long generation, long restart) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(GuardianWorker.class).setInitialDelay(3, TimeUnit.MINUTES)
            .setInputData(new Data.Builder().putString("kind", "recovery").putString("source", source).putInt("attempt", attempt)
                .putLong("generation", generation).putLong("restart", restart).build()).addTag(RECOVERY).build();
        WorkManager.getInstance(c).enqueue(request);
    }
}
