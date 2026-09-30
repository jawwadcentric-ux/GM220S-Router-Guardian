package com.metawebdesigner.gm220rebooter;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import org.json.JSONArray;

/** Serializes the unchanged router client and pins each operation to the selected Wi-Fi. */
public final class RouterActions {
    public interface PermissionCheck { boolean getAsBoolean(); }
    public static final Object LOCK = new Object();
    public static final class Outcome {
        public final boolean ok;
        public final boolean retryable;
        public final String message;
        Outcome(boolean ok, boolean retryable, String message) {
            this.ok = ok; this.retryable = retryable; this.message = message;
        }
    }
    private RouterActions() {}
    public static Outcome perform(Context c, boolean reboot, String source, PermissionCheck permitted) {
        synchronized (LOCK) {
            SecurePrefs prefs = new SecurePrefs(c);
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            Network previous = cm.getBoundNetworkForProcess();
            String reason = source.equals("watchdog") ? "Three consecutive internet failures"
                : source.equals("manual") ? "User request"
                : source.equals("retry") ? "Retry after a temporary reachability failure"
                : "Scheduled restart";
            Outcome result;
            try {
                result = run(c, prefs, cm, reboot, source, permitted);
            } catch (Exception ignored) {
                result = new Outcome(false, false, "Unable to complete request. Check settings and try again.");
            } finally { cm.bindProcessToNetwork(previous); }
            if (reboot) {
                new HistoryStore(c).add(source, reason, result.message, result.ok);
                if (source.equals("scheduled") || source.equals("retry")) prefs.raw().edit().putLong("last_scheduled_attempt", System.currentTimeMillis())
                    .putString("last_auto_result", result.message).apply();
            }
            return result;
        }
    }
    private static Outcome run(Context c, SecurePrefs prefs, ConnectivityManager cm, boolean reboot, String source, PermissionCheck permitted) {
        if (!permitted.getAsBoolean()) return new Outcome(false, false, "Action cancelled");
        Network network = NetworkHealth.wifi(c);
        if (network == null) return new Outcome(false, true, "Connect to the router's Wi-Fi first");
        boolean automatic = !source.equals("manual");
        if (automatic && !NetworkHealth.allowed(c, prefs)) return new Outcome(false, false, "Saved Wi-Fi does not match or its name is unavailable");
        String base = prefs.raw().getString("router", "http://192.168.1.1");
        String user = prefs.getSecret("username", ""), pass = prefs.getSecret("password", "");
        if (user.isEmpty() || pass.isEmpty()) return new Outcome(false, false, "Enter and save router credentials in Settings");
        if (!NetworkHealth.reachable(network, base)) {
            prefs.raw().edit().putString("router_status", "Router unreachable").apply();
            return new Outcome(false, true, "Router unreachable. It may be restarting.");
        }
        prefs.raw().edit().putString("router_status", "Router reachable").apply();
        if (!cm.bindProcessToNetwork(network)) return new Outcome(false, false, "Wi-Fi changed; try again");
        RouterClient.Result tested = RouterClient.test(base, user, pass);
        if (!tested.ok) {
            String status = tested.message.startsWith("LOGIN OK") ? "Session token unavailable; check credentials or wait for router recovery" : "Authentication or session failed; check credentials";
            prefs.raw().edit().putString("router_status", status).apply();
            return new Outcome(false, false, status);
        }
        prefs.raw().edit().putString("router_status", "Router login successful").putLong("last_contact", System.currentTimeMillis()).apply();
        if (!reboot) return new Outcome(true, false, "Connection successful. Login and dynamic reboot token verified.");
        if (!permitted.getAsBoolean() || !network.equals(NetworkHealth.wifi(c)) || (automatic && !NetworkHealth.allowed(c, prefs)))
            return new Outcome(false, false, "Action cancelled or Wi-Fi changed");
        long now = System.currentTimeMillis();
        if (automatic) {
            String block = reserveAutomatic(prefs, now);
            if (block != null) return new Outcome(false, false, block);
        }
        // Reserve before calling the verified client: an ambiguous disconnect must still consume a slot.
        prefs.raw().edit().putLong("last_restart_attempt", now).commit();
        RouterClient.Result sent = RouterClient.reboot(base, user, pass);
        if (sent.ok) {
            prefs.raw().edit().putLong("last_restart", now).apply();
            return new Outcome(true, false, "Restart command attempted. Router recovery is not yet confirmed.");
        }
        return new Outcome(false, false, "Restart not confirmed. Check connection before trying again.");
    }
    private static String reserveAutomatic(SecurePrefs prefs, long now) {
        SharedPreferences raw = prefs.raw();
        JSONArray recent = new JSONArray();
        try {
            JSONArray prior = new JSONArray(raw.getString("automatic_attempts", "[]"));
            for (int i = 0; i < prior.length(); i++) {
                long stamp = prior.optLong(i);
                if (now - stamp < 86_400_000L) recent.put(stamp);
            }
        } catch (Exception ignored) { return "Safety history unavailable; automatic restart blocked"; }
        String blocked = SafetyPolicy.blocked(now, raw.getLong("last_restart_attempt", 0), recent.length(),
            raw.getInt("max_reboots", 2), raw.getInt("cooldown", 30));
        if (blocked != null) return blocked;
        recent.put(now);
        if (!raw.edit().putString("automatic_attempts", recent.toString()).commit()) return "Unable to save safety state";
        return null;
    }
}
