package com.metawebdesigner.gm220rebooter;

/** Pure policy, shared by all automatic reboot sources. */
public final class SafetyPolicy {
    private SafetyPolicy() {}

    public static String blocked(long now, long lastAttempt, int count, int maximum, int cooldownMinutes) {
        if (count >= maximum) return "Daily automatic restart limit reached";
        if (lastAttempt > 0 && (now < lastAttempt || now - lastAttempt < cooldownMinutes * 60_000L))
            return "Restart cooldown active";
        return null;
    }

    public static boolean watchdogReady(int failures) { return failures >= 3; }
}
