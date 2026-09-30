package com.metawebdesigner.gm220rebooter;

import android.content.Context;
import android.net.Network;
import androidx.work.*;

public final class GuardianWorker extends Worker {
    public GuardianWorker(Context c, WorkerParameters p) { super(c, p); }
    private boolean permitted(SecurePrefs p, String source, long generation) {
        return !isStopped() && p.raw().getBoolean(source.equals("watchdog") ? "watchdog" : "enabled", false)
            && p.raw().getLong("generation", 0) == generation;
    }
    @Override public Result doWork() {
        Context c = getApplicationContext();
        SecurePrefs p = new SecurePrefs(c);
        String kind = getInputData().getString("kind");
        long generation = "watchdog".equals(kind) ? p.raw().getLong("generation", 0) : getInputData().getLong("generation", -1);
        try {
            if ("recovery".equals(kind)) { recover(c, p, generation); return Result.success(); }
            boolean retryRun = "scheduled".equals(kind) && getInputData().getInt("retry", 0) > 0;
            String source = "watchdog".equals(kind) ? "watchdog" : retryRun ? "retry" : "scheduled";
            if (!permitted(p, source, generation)) return Result.success();
            if (source.equals("watchdog")) {
                synchronized (RouterActions.LOCK) {
                    if (!NetworkHealth.allowed(c, p)) { p.raw().edit().putInt("failures", 0).putString("watchdog_status", "Waiting for the router's Wi-Fi").apply(); return Result.success(); }
                    long now = System.currentTimeMillis(), last = p.raw().getLong("last_check", 0);
                    if (now - last < p.raw().getInt("interval", 15) * 60_000L) return Result.success();
                    Network network = NetworkHealth.wifi(c);
                    boolean online = NetworkHealth.internet(c, network);
                    if (network == null || !network.equals(NetworkHealth.wifi(c))) { p.raw().edit().putInt("failures", 0).apply(); return Result.success(); }
                    long networkId = network.getNetworkHandle();
                    int priorFailures = p.raw().getLong("watch_network", -1) == networkId ? p.raw().getInt("failures", 0) : 0;
                    int failures = online ? 0 : Math.min(3, priorFailures + 1);
                    p.raw().edit().putInt("failures", failures).putLong("last_check", now).putLong("internet_checked", now).putLong("watch_network", networkId)
                        .putString("internet_status", online ? "Internet working" : "No internet detected")
                        .putString("watchdog_status", online ? "Healthy • monitoring" : "Failure " + failures + " of 3").apply();
                    if (!SafetyPolicy.watchdogReady(failures)) return Result.success();
                }
            }
            RouterActions.Outcome outcome = RouterActions.perform(c, true, source, () -> permitted(p, source, generation));
            if (!permitted(p, source, generation)) return Result.success();
            if (outcome.ok) {
                p.raw().edit().putInt("failures", 0).putString("watchdog_status", "Waiting for recovery").apply();
                AlarmScheduler.cancelRetry(c);
                GuardianNotifications.show(c, source.equals("watchdog") ? "Watchdog restart triggered" : "Scheduled restart command sent", outcome.message);
                Automation.recovery(c, source, 0, generation, p.raw().getLong("last_restart", 0));
            } else {
                if ((source.equals("scheduled") || source.equals("retry")) && outcome.retryable)
                    AlarmScheduler.scheduleRetry(c, getInputData().getInt("retry", 0) + 1);
                if (!source.equals("watchdog")) GuardianNotifications.show(c, "Automatic restart needs attention", outcome.message);
                p.raw().edit().putString("watchdog_status", outcome.message).apply();
            }
        } catch (Exception ignored) {
            new HistoryStore(c).add("system", "Background operation", "Background check could not complete", false);
        }
        return Result.success();
    }
    private void recover(Context c, SecurePrefs p, long generation) {
        String source = getInputData().getString("source");
        if (source == null || !permitted(p, source, generation)) return;
        if (getInputData().getLong("restart", -1) != p.raw().getLong("last_restart", 0)) return;
        boolean online = NetworkHealth.allowed(c, p) && NetworkHealth.internet(c, NetworkHealth.wifi(c));
        RouterActions.Outcome tested = online ? RouterActions.perform(c, false, source, () -> permitted(p, source, generation)) : null;
        if (!permitted(p, source, generation)) return;
        if (online && tested != null && tested.ok) {
            p.raw().edit().putInt("failures", 0).putString("watchdog_status", "Recovery verified")
                .putString("internet_status", "Internet working").putLong("last_recovery", System.currentTimeMillis()).apply();
            new HistoryStore(c).add(source, "Post-restart verification", "Router and internet reachable again", true);
            GuardianNotifications.show(c, "Router connection restored", "Router login and internet checks passed after the restart attempt.");
        } else if (getInputData().getInt("attempt", 0) < 2) {
            Automation.recovery(c, source, getInputData().getInt("attempt", 0) + 1, generation, getInputData().getLong("restart", 0));
        } else {
            p.raw().edit().putString("watchdog_status", "Recovery not confirmed").apply();
            new HistoryStore(c).add(source, "Post-restart verification", "Recovery not confirmed after three checks", false);
            GuardianNotifications.show(c, "Router recovery needs attention", "Internet recovery could not be confirmed. Check the router and Wi-Fi.");
        }
    }
}
