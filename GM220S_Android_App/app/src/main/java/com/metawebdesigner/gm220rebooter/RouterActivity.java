package com.metawebdesigner.gm220rebooter;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dedicated home for router-specific status and controls. */
public final class RouterActivity extends GuardianActivity {
    private static final ExecutorService ACTIONS = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private TextView status, details, result;
    private Button test, restart;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        page("Router", "GM220-S connection and controls");
        navigation(TAB_ROUTER);
        LinearLayout stateCard = card("Router status");
        status = label("Not checked", 24, ink, true); stateCard.addView(status);
        details = detail(stateCard, "");
        LinearLayout actions = card("Quick controls");
        LinearLayout row = horizontal();
        actions.addView(row);
        test = actionButton(row, android.R.drawable.ic_menu_view, "Test", false, () -> runAction(false));
        restart = actionButton(row, android.R.drawable.ic_popup_sync, "Restart", true, this::confirmRestart);
        result = detail(actions, "Use Test to verify the saved router login.");
        result.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout automation = card("Automation");
        detail(automation, "Schedule  •  " + (prefs.raw().getBoolean("enabled", false) ? "On" : "Off")
            + "\nWatchdog  •  " + (prefs.raw().getBoolean("watchdog", false) ? "On" : "Off"));
        button(automation, "Open settings", false, () -> openTab(SettingsActivity.class));
    }

    private void confirmRestart() {
        new AlertDialog.Builder(this).setTitle("Restart your router?")
            .setMessage("Everyone using this router will lose their connection briefly.")
            .setNegativeButton("Cancel", null).setPositiveButton("Restart", (d, w) -> runAction(true)).show();
    }

    private void runAction(boolean reboot) {
        if (!BUSY.compareAndSet(false, true)) { message("A router operation is already running"); return; }
        test.setEnabled(false); restart.setEnabled(false);
        result.setText(reboot ? "Sending restart request…" : "Testing router login…");
        ACTIONS.execute(() -> {
            RouterActions.Outcome outcome;
            try { outcome = RouterActions.perform(getApplicationContext(), reboot, "manual", () -> !isDestroyed()); }
            catch (Exception ignored) { outcome = new RouterActions.Outcome(false, false, "Could not finish. Check Wi-Fi and try again."); }
            BUSY.set(false); RouterActions.Outcome done = outcome;
            runOnUiThread(() -> { if (!isDestroyed()) { result.setText((done.ok ? "✓  " : "!  ") + done.message); refresh(); } });
        });
    }

    private void refresh() {
        boolean wifi = NetworkHealth.wifi(this) != null;
        status.setText(wifi ? prefs.raw().getString("router_status", "Ready to test") : "Router Wi-Fi unavailable");
        details.setText("Address  •  " + prefs.raw().getString("router", "http://192.168.1.1")
            + "\nLogin  •  " + (prefs.getSecret("username", "").isEmpty() ? "Not configured" : "Credentials saved")
            + "\nLast contact  •  " + HistoryStore.time(prefs.raw().getLong("last_contact", 0))
            + "\nLast restart  •  " + HistoryStore.time(prefs.raw().getLong("last_restart", 0))
            + "\nNext restart  •  " + AlarmScheduler.formatNext(this));
        test.setEnabled(!BUSY.get()); restart.setEnabled(!BUSY.get());
    }

    @Override protected void onResume() { super.onResume(); refresh(); }
}
