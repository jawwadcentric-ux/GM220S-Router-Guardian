package com.metawebdesigner.gm220rebooter;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.*;
import android.widget.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends GuardianActivity {
    private static final ExecutorService ACTIONS = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private TextView connection, internet, wifi, timeline, automation, result;
    private Button test, reboot;
    private String appliedTheme;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        appliedTheme = prefs.raw().getString("theme", "System");
        page("Router Guardian", "GM220-S XPON  •  Your home connection, in view");
        if (prefs.getSecret("username", "").isEmpty() || prefs.getSecret("password", "").isEmpty()) {
            LinearLayout welcome = card("Welcome to Router Guardian");
            detail(welcome, "Start by saving your GM220-S address and credentials. Then run Test connection before enabling scheduled restarts or Watchdog.");
            button(welcome, "Set up router", true, () -> startActivity(new Intent(this, SettingsActivity.class)));
        }
        LinearLayout health = card("◉  Connection health");
        connection = detail(health, "Router • Not checked");
        internet = detail(health, "Internet • Not checked");
        wifi = detail(health, "Wi-Fi • Checking");
        LinearLayout actions = card("↻  Router controls");
        test = button(actions, "Test connection", false, () -> runAction(false));
        reboot = button(actions, "Restart router", true, this::confirmRestart);
        result = detail(actions, "Test your connection before enabling automatic restarts.");
        result.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout activity = card("◷  Recent activity");
        timeline = detail(activity, "");
        button(activity, "View history", false, () -> startActivity(new Intent(this, HistoryActivity.class)));
        LinearLayout protection = card("◇  Automation");
        automation = detail(protection, "");
        button(protection, "Settings & automation", false, () -> startActivity(new Intent(this, SettingsActivity.class)));
        detail(protection, "Automatic restarts are off until you enable them. This app manages one router and stores its settings on this phone.");
        installShortcuts();
        Automation.restore(this);
        if (state == null) handleShortcut(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleShortcut(intent); }
    private void handleShortcut(Intent intent) {
        if ("guardian.RESTART".equals(intent.getAction())) confirmRestart();
        if ("guardian.CHECK".equals(intent.getAction())) runAction(false);
    }
    private void confirmRestart() {
        new AlertDialog.Builder(this).setTitle("Restart your router?")
            .setMessage("Everyone using this router will lose their connection briefly. Continue?")
            .setNegativeButton("Cancel", null).setPositiveButton("Restart", (dialog, which) -> runAction(true)).show();
    }
    private void runAction(boolean restart) {
        if (!BUSY.compareAndSet(false, true)) { message("A router operation is already running"); return; }
        test.setEnabled(false); reboot.setEnabled(false);
        result.setText(restart ? "Sending restart request…" : "Checking Wi-Fi, internet and router login…");
        android.content.Context app = getApplicationContext();
        ACTIONS.execute(() -> {
            RouterActions.Outcome outcome;
            try {
                if (!restart) {
                    android.net.Network network = NetworkHealth.wifi(app);
                    boolean online = NetworkHealth.internet(app, network);
                    new SecurePrefs(app).raw().edit().putString("internet_status", network == null ? "Connect to Wi-Fi to check" : online ? "Internet working" : "No internet detected")
                        .putLong("internet_checked", System.currentTimeMillis()).apply();
                }
                outcome = RouterActions.perform(app, restart, "manual", () -> !isDestroyed());
            } catch (Exception ignored) { outcome = new RouterActions.Outcome(false, false, "Could not finish. Check Wi-Fi and try again."); }
            BUSY.set(false);
            final RouterActions.Outcome completed = outcome;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                result.setText((completed.ok ? "✓  " : "!  ") + completed.message);
                result.setTextColor(completed.ok ? accent : (dark ? 0xFFFFCE89 : 0xFF8A4900));
                refresh();
            });
        });
    }
    private void refresh() {
        boolean onWifi = NetworkHealth.wifi(this) != null;
        connection.setText("Router  •  " + (onWifi ? prefs.raw().getString("router_status", "Not checked") : "Check unavailable without Wi-Fi"));
        internet.setText("Internet  •  " + (onWifi ? prefs.raw().getString("internet_status", "Not checked") : "Router Wi-Fi not active"));
        wifi.setText(NetworkHealth.transport(this));
        timeline.setText("Last successful contact\n" + HistoryStore.time(prefs.raw().getLong("last_contact", 0))
            + "\n\nLast restart command\n" + HistoryStore.time(prefs.raw().getLong("last_restart", 0))
            + "\n\nNext scheduled restart\n" + AlarmScheduler.formatNext(this));
        automation.setText("Scheduled restart  •  " + (prefs.raw().getBoolean("enabled", false) ? (prefs.raw().getInt("days",127)==127 ? "Daily" : "Selected days") : "Off")
            + "\nWatchdog  •  " + (prefs.raw().getBoolean("watchdog", false) ? prefs.raw().getString("watchdog_status", "Monitoring enabled") : "Off")
            + "\nProtection  •  " + prefs.raw().getInt("max_reboots", 2) + " automatic restarts / 24h, " + prefs.raw().getInt("cooldown", 30) + " min cooldown");
        test.setEnabled(!BUSY.get()); reboot.setEnabled(!BUSY.get());
    }
    @Override protected void onResume() {
        super.onResume();
        if (!prefs.raw().getString("theme", "System").equals(appliedTheme)) { recreate(); return; }
        AlarmScheduler.schedule(this);
        refresh();
    }
    private void installShortcuts() {
        if (Build.VERSION.SDK_INT < 25) return;
        ShortcutManager manager = getSystemService(ShortcutManager.class);
        ShortcutInfo check = new ShortcutInfo.Builder(this, "check").setShortLabel("Check connection")
            .setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_view))
            .setIntent(new Intent(this, MainActivity.class).setAction("guardian.CHECK")).build();
        ShortcutInfo restart = new ShortcutInfo.Builder(this, "restart").setShortLabel("Restart router")
            .setIcon(Icon.createWithResource(this, android.R.drawable.ic_popup_sync))
            .setIntent(new Intent(this, MainActivity.class).setAction("guardian.RESTART")).build();
        try { manager.setDynamicShortcuts(Arrays.asList(check, restart)); } catch (Exception ignored) {}
    }
}
