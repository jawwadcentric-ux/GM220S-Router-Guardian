package com.metawebdesigner.gm220rebooter;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends GuardianActivity {
    private static final ExecutorService ACTIONS = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private TextView download, upload, network, speedSummary, routerStatus, internetStatus, wifiStatus,
        watchdogStatus, timeline, automation, result;
    private Button test, reboot;
    private SpeedSparkline sparkline;
    private NetworkSpeedMonitor monitor;
    private String appliedTheme;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        appliedTheme = prefs.raw().getString("theme", "System");
        page("Guardian", "Your connection at a glance");
        navigation(TAB_DASHBOARD);
        buildSpeedCard();

        if (prefs.getSecret("username", "").isEmpty() || prefs.getSecret("password", "").isEmpty()) {
            LinearLayout welcome = card("Finish setup");
            detail(welcome, "Save your GM220-S login before using router controls.");
            button(welcome, "Set up router", true, () -> openTab(SettingsActivity.class));
        }

        LinearLayout health = card("Quick status");
        LinearLayout top = horizontal(); health.addView(top);
        routerStatus = statusTile(top, "Router"); internetStatus = statusTile(top, "Internet");
        LinearLayout bottom = horizontal(); health.addView(bottom);
        wifiStatus = statusTile(bottom, "Network"); watchdogStatus = statusTile(bottom, "Watchdog");

        LinearLayout actions = card("Quick actions");
        LinearLayout first = horizontal(); actions.addView(first);
        test = actionButton(first, android.R.drawable.ic_menu_view, "Test", false, () -> runAction(false));
        reboot = actionButton(first, android.R.drawable.ic_popup_sync, "Restart", true, this::confirmRestart);
        LinearLayout second = horizontal(); actions.addView(second);
        actionButton(second, android.R.drawable.ic_menu_recent_history, "History", false, () -> openTab(HistoryActivity.class));
        actionButton(second, android.R.drawable.ic_menu_preferences, "Settings", false, () -> openTab(SettingsActivity.class));
        result = detail(actions, "Router controls are ready.");
        result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        LinearLayout recent = card("Router summary");
        timeline = detail(recent, "");
        button(recent, "Open Router", false, () -> openTab(RouterActivity.class));

        LinearLayout protection = card("Automation");
        automation = detail(protection, "");
        installShortcuts();
        Automation.restore(this);
        if (state == null) handleShortcut(getIntent());
    }

    private void buildSpeedCard() {
        LinearLayout speed = card("Live speed");
        speed.setBackground(rounded(dark ? Color.parseColor("#17352F") : Color.parseColor("#E1F5EF"), 22));
        LinearLayout values = horizontal(); speed.addView(values);
        LinearLayout down = metric(values, "↓", "Download"); download = (TextView) down.getChildAt(1);
        LinearLayout up = metric(values, "↑", "Upload"); upload = (TextView) up.getChildAt(1);
        network = detail(speed, "Finding active network…");
        sparkline = new SpeedSparkline(this, accent, dark ? 0xFFFFC46B : 0xFFB05D00);
        LinearLayout.LayoutParams chart = new LinearLayout.LayoutParams(-1, dp(72)); chart.topMargin = dp(8);
        speed.addView(sparkline, chart);
        speedSummary = detail(speed, "Live activity appears while this screen is open.");
    }

    private LinearLayout metric(LinearLayout parent, String icon, String title) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.START);
        TextView caption = label(icon + "  " + title, 13, muted, true); box.addView(caption);
        TextView value = label("0 Kbps", 27, ink, true); value.setPadding(0, dp(6), 0, dp(6)); box.addView(value);
        parent.addView(box, new LinearLayout.LayoutParams(0, -2, 1f)); return box;
    }

    private TextView statusTile(LinearLayout parent, String title) {
        TextView tile = label(title + "\nChecking", 14, ink, true); tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(12), dp(12), dp(10), dp(12)); tile.setBackground(rounded(background, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(72), 1f); lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        parent.addView(tile, lp); return tile;
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleShortcut(intent); }
    private void handleShortcut(Intent intent) {
        if ("guardian.RESTART".equals(intent.getAction())) confirmRestart();
        if ("guardian.CHECK".equals(intent.getAction())) runAction(false);
    }

    private void confirmRestart() {
        new AlertDialog.Builder(this).setTitle("Restart your router?")
            .setMessage("Everyone using this router will lose their connection briefly.")
            .setNegativeButton("Cancel", null).setPositiveButton("Restart", (dialog, which) -> runAction(true)).show();
    }

    private void runAction(boolean restart) {
        if (!BUSY.compareAndSet(false, true)) { message("A router operation is already running"); return; }
        test.setEnabled(false); reboot.setEnabled(false);
        result.setText(restart ? "Sending restart request…" : "Checking internet and router login…");
        ACTIONS.execute(() -> {
            RouterActions.Outcome outcome;
            try {
                if (!restart) {
                    android.net.Network wifi = NetworkHealth.wifi(getApplicationContext());
                    boolean online = NetworkHealth.internet(getApplicationContext(), wifi);
                    prefs.raw().edit().putString("internet_status", wifi == null ? "Router Wi-Fi unavailable" : online ? "Online" : "Offline")
                        .putLong("internet_checked", System.currentTimeMillis()).apply();
                }
                outcome = RouterActions.perform(getApplicationContext(), restart, "manual", () -> !isDestroyed());
            } catch (Exception ignored) { outcome = new RouterActions.Outcome(false, false, "Could not finish. Check Wi-Fi and try again."); }
            BUSY.set(false); RouterActions.Outcome done = outcome;
            runOnUiThread(() -> { if (!isDestroyed()) { result.setText((done.ok ? "✓  " : "!  ") + done.message); refresh(); } });
        });
    }

    private void startSpeedMonitor() {
        if (!prefs.raw().getBoolean("speed_enabled", true)) {
            download.setText("Off"); upload.setText("Off"); network.setText("Live speed monitor disabled");
            speedSummary.setText("Enable it in Settings."); sparkline.setVisibility(View.GONE); return;
        }
        sparkline.setVisibility(prefs.raw().getBoolean("speed_chart", true) ? View.VISIBLE : View.GONE);
        long seconds = prefs.raw().getInt("speed_interval", 2);
        monitor = new NetworkSpeedMonitor(this, seconds * 1000L, this::showSpeed);
        monitor.start();
    }

    private void showSpeed(NetworkSpeedMonitor.Sample sample) {
        download.setText(formatRate(sample.downBytesPerSecond)); upload.setText(formatRate(sample.upBytesPerSecond));
        String name = prefs.raw().getBoolean("speed_network_name", true) ? "  •  " + sample.networkName : "";
        network.setText(sample.connection + name);
        sparkline.add(sample.downBytesPerSecond, sample.upBytesPerSecond);
        if (prefs.raw().getBoolean("speed_summary", true)) {
            String time = DateFormat.getTimeFormat(this).format(new Date());
            speedSummary.setText("Session  ↓ " + formatBytes(sample.receivedBytes) + "   ↑ " + formatBytes(sample.sentBytes)
                + "\nAverage  ↓ " + formatRate((long) sample.averageDownBytesPerSecond) + "   ↑ " + formatRate((long) sample.averageUpBytesPerSecond)
                + "  •  " + time);
        } else speedSummary.setText(sample.available ? "Real-time device activity" : "Waiting for an active internet connection");
    }

    private String formatRate(long bytesPerSecond) {
        double bits = bytesPerSecond * 8.0;
        if (bits >= 1_000_000) return String.format(Locale.getDefault(), "%.1f Mbps", bits / 1_000_000.0);
        if (bits >= 1_000) return String.format(Locale.getDefault(), "%.0f Kbps", bits / 1_000.0);
        return String.format(Locale.getDefault(), "%.0f bps", bits);
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1_000_000_000L) return String.format(Locale.getDefault(), "%.2f GB", bytes / 1_000_000_000.0);
        if (bytes >= 1_000_000L) return String.format(Locale.getDefault(), "%.1f MB", bytes / 1_000_000.0);
        if (bytes >= 1_000L) return String.format(Locale.getDefault(), "%.0f KB", bytes / 1_000.0);
        return bytes + " B";
    }

    private void refresh() {
        boolean wifi = NetworkHealth.wifi(this) != null;
        routerStatus.setText("Router\n" + (wifi ? prefs.raw().getString("router_status", "Ready") : "Wi-Fi needed"));
        internetStatus.setText("Internet\n" + prefs.raw().getString("internet_status", "Not checked"));
        wifiStatus.setText("Network\n" + NetworkHealth.transport(this).replace("  •  ", " "));
        watchdogStatus.setText("Watchdog\n" + (prefs.raw().getBoolean("watchdog", false) ? "On" : "Off"));
        timeline.setText("Last contact  •  " + HistoryStore.time(prefs.raw().getLong("last_contact", 0))
            + "\nLast restart  •  " + HistoryStore.time(prefs.raw().getLong("last_restart", 0))
            + "\nNext restart  •  " + AlarmScheduler.formatNext(this));
        automation.setText("Schedule  •  " + (prefs.raw().getBoolean("enabled", false) ? "On" : "Off")
            + "     Watchdog  •  " + (prefs.raw().getBoolean("watchdog", false) ? "On" : "Off"));
        test.setEnabled(!BUSY.get()); reboot.setEnabled(!BUSY.get());
    }

    @Override protected void onResume() {
        super.onResume();
        if (!prefs.raw().getString("theme", "System").equals(appliedTheme)) { recreate(); return; }
        AlarmScheduler.schedule(this); refresh(); startSpeedMonitor();
    }
    @Override protected void onPause() { if (monitor != null) { monitor.stop(); monitor = null; } super.onPause(); }

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
