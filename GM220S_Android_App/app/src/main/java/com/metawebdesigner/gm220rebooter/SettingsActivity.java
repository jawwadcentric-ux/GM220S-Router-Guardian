package com.metawebdesigner.gm220rebooter;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import java.net.URI;
import java.util.Locale;

public final class SettingsActivity extends GuardianActivity {
    private EditText address, user, password, interval, maximum, cooldown, retries, retryMinutes;
    private Switch schedule, watchdog, notifications, protect;
    private final CheckBox[] weekdays = new CheckBox[7];
    private Spinner theme;
    private TextView time, bound, backgroundStatus;
    private int hour, minute;
    private String boundSsid;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (prefs.raw().getBoolean("protect_settings", false)) {
            page("Protected settings", "Confirm your phone's screen lock to continue.");
            KeyguardManager km = getSystemService(KeyguardManager.class);
            Intent intent = km.createConfirmDeviceCredentialIntent("Router settings", "Confirm your identity");
            if (intent == null) { message("Screen lock unavailable. Set a phone PIN to open protected settings."); finish(); }
            else startActivityForResult(intent, 55);
        } else build();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 55) { if (result == RESULT_OK) build(); else finish(); }
    }
    private EditText field(LinearLayout parent, String label, String value, boolean secret, boolean numeric) {
        detail(parent, label);
        EditText input = new EditText(this);
        input.setText(value); input.setTextColor(ink); input.setSingleLine(true); input.setMinHeight(dp(48));
        input.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        input.setSaveEnabled(false);
        if (Build.VERSION.SDK_INT >= 26) input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        parent.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }
    private Switch toggle(LinearLayout parent, String label, String key, boolean fallback) {
        Switch toggle = new Switch(this);
        toggle.setText(label); toggle.setTextColor(ink); toggle.setMinHeight(dp(52)); toggle.setChecked(prefs.raw().getBoolean(key, fallback));
        parent.addView(toggle, new LinearLayout.LayoutParams(-1, -2)); return toggle;
    }
    private void build() {
        hour = prefs.raw().getInt("hour", 5); minute = prefs.raw().getInt("minute", 0);
        boundSsid = prefs.raw().getString("bound_ssid", "");
        page("Settings", "Your router, your schedule. Changes apply when saved.");
        LinearLayout router = card("Router connection");
        address = field(router, "Router address", prefs.raw().getString("router", "http://192.168.1.1"), false, false);
        user = field(router, "Username", prefs.getSecret("username", ""), false, false);
        password = field(router, "Password", prefs.getSecret("password", ""), true, false);
        detail(router, "Credentials are encrypted with Android Keystore. Screenshots are disabled here.");
        protect = toggle(router, "Use phone screen lock for settings", "protect_settings", false);

        LinearLayout daily = card("Scheduled restart");
        schedule = toggle(daily, "Enable scheduled restart", "enabled", false);
        time = detail(daily, String.format(Locale.getDefault(), "%02d:%02d", hour, minute));
        button(daily, "Choose restart time", false, () -> new TimePickerDialog(this, (view, h, m) -> {
            hour = h; minute = m; time.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m));
        }, hour, minute, android.text.format.DateFormat.is24HourFormat(this)).show());
        String[] names = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        for (int i = 0; i < 7; i++) {
            weekdays[i] = new CheckBox(this); weekdays[i].setText(names[i]); weekdays[i].setTextColor(ink); weekdays[i].setMinHeight(dp(48));
            weekdays[i].setChecked((prefs.raw().getInt("days", 127) & (1 << i)) != 0); daily.addView(weekdays[i]);
        }
        button(daily, "Select every day", false, () -> { for (CheckBox day : weekdays) day.setChecked(true); });
        detail(daily, "Next: " + AlarmScheduler.formatNext(this) + "\nLast attempt: " + HistoryStore.time(prefs.raw().getLong("last_scheduled_attempt", 0))
            + "\n" + prefs.raw().getString("last_auto_result", "No automatic result yet"));

        LinearLayout watch = card("Smart watchdog");
        watchdog = toggle(watch, "Enable watchdog", "watchdog", false);
        detail(watch, "Three consecutive failed internet checks are required, followed by a router login check. Normal checks stay quiet. Android may delay background checks.");
        interval = field(watch, "Check interval in minutes (15–1440)", "" + prefs.raw().getInt("interval", 15), false, true);
        bound = detail(watch, bindingLabel());
        button(watch, "Bind automation to current Wi-Fi", false, this::bindWifi);
        button(watch, "Remove Wi-Fi binding", false, () -> { boundSsid = ""; bound.setText(bindingLabel()); });
        detail(watch, "A binding requires Android to reveal the Wi-Fi name. If unavailable in the background, automation safely pauses. Without a binding, only active Wi-Fi with a valid router session is eligible.");

        LinearLayout advanced = card("Advanced safeguards");
        maximum = field(advanced, "Maximum automatic restarts per rolling 24h (1–10)", "" + prefs.raw().getInt("max_reboots", 2), false, true);
        cooldown = field(advanced, "Cooldown after any restart, minutes (10–1440)", "" + prefs.raw().getInt("cooldown", 30), false, true);
        retries = field(advanced, "Maximum scheduled retries (0–3)", "" + prefs.raw().getInt("retries", 3), false, true);
        retryMinutes = field(advanced, "Retry delay in minutes (5–120)", "" + prefs.raw().getInt("retry_minutes", 10), false, true);
        detail(advanced, "Only unreachable-router or missing-Wi-Fi failures are retried. Authentication and uncertain reboot outcomes are not retried. Manual restarts remain available.");

        LinearLayout appearance = card("Appearance & notifications");
        theme = new Spinner(this);
        String[] themes = {"System", "Light", "Dark"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, themes);
        theme.setAdapter(adapter);
        for (int i = 0; i < themes.length; i++) if (themes[i].equals(prefs.raw().getString("theme", "System"))) theme.setSelection(i);
        theme.setMinimumHeight(dp(48)); appearance.addView(theme);
        notifications = toggle(appearance, "Automatic result notifications", "notifications", true);
        button(appearance, "Allow notifications", false, () -> {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 70);
            else open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        });

        LinearLayout help = card("Battery & background help");
        backgroundStatus = detail(help, ""); updateBackgroundStatus();
        detail(help, "Keep this phone powered on and connected to the router's Wi-Fi. Allow Alarms & reminders for precise alarm delivery. Select unrestricted battery use or enable auto-start if your phone offers it. WorkManager starts the network work after an alarm; Android can still delay it. Force-stopping the app stops automation until you reopen it.");
        button(help, "Alarms & reminders access", false, () -> {
            if (Build.VERSION.SDK_INT >= 31) open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
            else message("Exact alarms do not need special access on this Android version");
        });
        button(help, "Open app battery settings", false, () -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        LinearLayout about = card("About");
        detail(about, "GM220-S Router Guardian 1.0.0\nSupports GM220-S XPON hardware V9.0 with firmware V9.0.10P1T1.\n\nSingle-router utility. No accounts, cloud service, analytics, ads or telemetry. Router credentials remain encrypted on this phone. The verified router communication behavior is preserved from the read-only GM220S-Router-Rebooter reference project.");
        button(root, "Save settings", true, this::save);
        button(root, "Back to dashboard", false, this::finish);
    }
    private String bindingLabel() { return boundSsid.isEmpty() ? "Wi-Fi binding • Not set" : "Wi-Fi binding • " + boundSsid; }
    private void bindWifi() {
        if (NetworkHealth.wifi(this) == null) { message("Connect to your router's Wi-Fi first"); return; }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setTitle("Read Wi-Fi name")
                .setMessage("Android requires precise location permission and Location enabled to reveal the Wi-Fi name. Guardian uses this only to compare Wi-Fi names; it does not collect your location.")
                .setNegativeButton("Cancel", null).setPositiveButton("Continue", (d,w) -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 71)).show();
            return;
        }
        String ssid = NetworkHealth.ssid(this);
        if (ssid.isEmpty()) { message("Wi-Fi name unavailable. Check Location permission and the phone's Location switch."); return; }
        boundSsid = ssid; bound.setText(bindingLabel());
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 71 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) bindWifi();
        if (backgroundStatus != null) updateBackgroundStatus();
    }
    private int number(EditText input, int min, int max) {
        try { int value = Integer.parseInt(input.getText().toString()); if (value >= min && value <= max) return value; } catch (NumberFormatException ignored) {}
        input.setError("Enter " + min + "–" + max); input.requestFocus(); throw new IllegalArgumentException();
    }
    private void save() {
        try {
            String base = address.getText().toString().trim();
            if (!base.contains("://")) base = "http://" + base;
            URI uri = new URI(base);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
                address.setError("Use a router address such as http://192.168.1.1"); return;
            }
            if (user.getText().toString().trim().isEmpty() || password.getText().toString().isEmpty()) { message("Enter username and password"); return; }
            int days = 0;
            for (int i = 0; i < 7; i++) if (weekdays[i].isChecked()) days |= 1 << i;
            if (schedule.isChecked() && days == 0) { message("Select at least one schedule day"); return; }
            int check = number(interval, 15, 1440), max = number(maximum, 1, 10), cool = number(cooldown, 10, 1440);
            int retry = number(retries, 0, 3), delay = number(retryMinutes, 5, 120);
            if (protect.isChecked() && !getSystemService(KeyguardManager.class).isDeviceSecure()) { message("Set a phone PIN, password or pattern first"); return; }
            prefs.putCredentials(user.getText().toString().trim(), password.getText().toString());
            prefs.raw().edit().putString("router", base.replaceAll("/+$", "")).putBoolean("enabled", schedule.isChecked())
                .putBoolean("watchdog", watchdog.isChecked()).putBoolean("notifications", notifications.isChecked())
                .putBoolean("protect_settings", protect.isChecked()).putInt("hour", hour).putInt("minute", minute).putInt("days", days)
                .putInt("interval", check).putInt("max_reboots", max).putInt("cooldown", cool).putInt("retries", retry)
                .putInt("retry_minutes", delay).putString("bound_ssid", boundSsid).putString("theme", theme.getSelectedItem().toString()).commit();
            Automation.settingsChanged(this);
            message("Settings saved securely"); finish();
        } catch (IllegalArgumentException ignored) {
        } catch (Exception ignored) { message("Could not save settings securely. Please try again."); }
    }
    private void open(Intent intent) { try { startActivity(intent); } catch (Exception ignored) { message("This settings screen is unavailable on this phone"); } }
    private void updateBackgroundStatus() {
        boolean exact = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager.class).canScheduleExactAlarms();
        boolean battery = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        boolean notice = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled();
        backgroundStatus.setText("Alarm timing • " + (exact ? "Exact access available" : "May be delayed: exact access off")
            + "\nBattery • " + (battery ? "Optimization exemption enabled" : "Android may delay background work")
            + "\nNotifications • " + (notice ? "Allowed" : "Blocked by Android"));
    }
    @Override protected void onResume() { super.onResume(); if (backgroundStatus != null) updateBackgroundStatus(); }
}
