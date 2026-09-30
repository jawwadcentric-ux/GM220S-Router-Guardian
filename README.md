# GM220-S Router Guardian

An Android utility with a device-wide live network activity monitor and guarded
management for one GM220-S XPON router. It tests router login, sends a confirmed
manual restart, schedules selected restart days, monitors internet health with a
guarded watchdog, and keeps a private local history.

Supported hardware: **GM220-S XPON V9.0**, verified firmware
**V9.0.10P1T1**. Other firmware may behave differently.

- Active development: `jawwadcentric-ux/GM220S-Router-Guardian`
- Read-only protocol reference: `jawwadcentric-ux/GM220S-Router-Rebooter`

The reference repository must remain unchanged. Guardian preserves its verified
login, cookie, redirect, dynamic `_SESSION_TOKEN`, and reboot POST behavior.
See [REFERENCE.md](REFERENCE.md) for the inspected snapshot and source hash.

## Features

- Clean four-section navigation: Dashboard, Router, History, and Settings.
- Live device network activity for Wi-Fi, mobile data, Ethernet, VPN, or another
  active internet connection, with download/upload rates, connection type,
  optional Wi-Fi name, session totals, recent averages, and a compact trend.
- Glanceable dashboard with compact status tiles, icon-led actions, router
  summary, and automation state. Detailed router information lives in Router.
- Manual Test connection and confirmation-protected Restart router actions.
- Scheduled restarts at a chosen time on selected weekdays.
- Retry after temporary Wi-Fi/router reachability failures: 10 minutes, up to
  three attempts by default. Authentication or uncertain reboot outcomes do not
  retry indefinitely.
- Optional WorkManager watchdog, off by default, checking every 15 minutes by
  default. It requires three consecutive failures on the same active Wi-Fi,
  then verifies router reachability and login before one restart attempt.
- Shared rolling safety limit for automatic actions: two restart attempts per
  24 hours and a 30-minute cooldown by default.
- Post-restart recovery checks. A sent command is never described as restored
  internet until router login and internet checks pass.
- Latest 200 history events with Manual, Scheduled, Watchdog, Retry, and System
  sources; clear history; CSV and text export through Android's share sheet.
- System, light, and dark themes; app shortcuts for Check connection and Restart
  router. The shortcut restart still requires confirmation.
- Android Keystore AES-GCM credential encryption, disabled app backup, protected
  settings screenshots, and optional device-lock confirmation.
- Optional binding of automatic actions to the current Wi-Fi name where Android
  exposes it. If the saved name is unavailable or different, automation pauses.
- No accounts, cloud service, analytics, ads, or telemetry.

## Setup

1. Install Guardian and connect the phone to the GM220-S Wi-Fi.
2. Open **Settings & automation** and enter the router address (default
   `http://192.168.1.1`), username, and password.
3. Save, return to the dashboard, and run **Test connection**.
4. Use **Restart router** once and confirm only when you are ready for a brief
   network interruption.
5. Enable scheduling or Watchdog only after the manual checks pass.
6. For scheduled timing, allow **Alarms & reminders** on Android 12+. Allow
   notifications on Android 13+ if you want automatic result notifications.
7. In the phone's app settings, choose unrestricted/background battery use or
   enable vendor auto-start where available.

## Permissions

| Permission | Purpose |
| --- | --- |
| `INTERNET` | Local router HTTP plus bounded HTTPS internet checks |
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | Identify active Wi-Fi and network health |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Optional Wi-Fi-name binding on Android versions that require it |
| `WAKE_LOCK` | Used by WorkManager while background work runs |
| `RECEIVE_BOOT_COMPLETED` | Restore enabled alarms/work after device restart |
| `SCHEDULE_EXACT_ALARM` | Exact scheduled restart when Android grants access |
| `POST_NOTIFICATIONS` | Automatic restart/recovery results on Android 13+ |

Location permission is requested only when the user chooses Wi-Fi-name binding.
When already granted, it also lets the dashboard display the current Wi-Fi name;
live rate monitoring itself does not require location. Guardian does not collect
or transmit location. Cleartext traffic stays enabled because this router's
verified local management protocol uses HTTP.

## Background behavior and limits

AlarmManager holds the selected calendar schedule. If exact-alarm access is not
available, Guardian uses an inexact idle-capable alarm and explains that timing
may be delayed. WorkManager performs the network work and periodic watchdog
checks. Enabled automation is rescheduled after device reboot, app replacement,
timezone changes, clock changes, and exact-alarm permission changes.

Android and phone-vendor battery controls can delay or suppress background work.
Force-stop disables background delivery until the app is opened again. A powered
off phone, missing router Wi-Fi, VPN, unavailable SSID binding, or revoked exact
alarm access can also prevent or delay an action. No Android app can guarantee
execution while the operating system blocks it.

## Build locally

Requirements: JDK 17, Gradle 8.7, Android SDK platform 35, and Android Build
Tools 34.0.0 or newer.

```text
cd GM220S_Android_App
gradle assembleDebug lintDebug
```

Set `ANDROID_HOME` or add an untracked `local.properties` containing `sdk.dir`.
The debug APK is written to
`GM220S_Android_App/app/build/outputs/apk/debug/app-debug.apk`.

## Build with GitHub Actions

1. Push this repository to **GM220S-Router-Guardian**.
2. Open **Actions** → **Build Android APK** → **Run workflow**.
3. Download the `GM220S-Router-Guardian` artifact from the successful run.

[`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) uses Java
17 and Gradle 8.7, builds and lints the debug app, runs the protocol and safety
fixtures, and uploads `app-debug.apk`. The separate manually triggered release
workflow creates an **unsigned** release APK. It intentionally contains no
keystore or signing password.

## Privacy and security

Router credentials are encrypted with a non-exportable Android Keystore key.
App backup and device transfer are disabled. Passwords, cookies, and session
tokens are not written to logs, history, notifications, exports, or intents.
History contains timestamps, trigger type, a fixed reason, success/failure, and
a user-safe result. Exported files therefore contain no router secrets.

## Known limitations

- Live speed is passive activity monitoring based on Android's device traffic
  counters. It shows traffic currently moving through the phone and does not
  download test data, measure maximum line capacity, or replace a benchmark such
  as Ookla. The session summary begins when the dashboard opens; Android does not
  expose a reliable permission-free per-network rolling 24-hour history on every
  supported version. Some devices may omit traffic from unsupported interfaces.
- Wi-Fi names may show as “Wi-Fi network” when Android withholds the SSID because
  Location permission or the device Location switch is off.

- Real-hardware login, restart, recovery time, and OEM background behavior must
  be accepted on the user's router and Android phone; automated tests use only a
  localhost protocol fixture.
- The verified router client treats an immediate disconnect after the reboot POST
  as a normal command attempt. Guardian records it as attempted and verifies
  recovery later; it cannot prove that the router honored the command immediately.
- Internet probes use two independent HTTPS endpoints plus Android's validated
  network signal. Captive portals and unusual DNS/firewall policies can affect
  results. One failed check never causes a reboot.
- Debug APK upgrades preserve data only when signed with the same debug key.
