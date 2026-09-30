# Verified router implementation

Read-only upstream: https://github.com/jawwadcentric-ux/GM220S-Router-Rebooter

Snapshot inspected: `a825e2b` (main at retrieval on 2026-09-28).

All development belongs to https://github.com/jawwadcentric-ux/GM220S-Router-Guardian.
Never push changes, branches, workflows or pull requests to the reference repository.

The initial Guardian baseline copies RouterClient.java **byte for byte**.
SHA-256: `50D4C068D91461A1765E3255CCD1FF40B015D5CF8FA1C3FD9D87393171CB4684`.
The Java namespace is retained to avoid editing the communication source; the
Android application ID is `com.metawebdesigner.gm220guardian`, allowing both apps
to coexist. Credentials must be entered separately in Guardian.

## Inspected behavior

- GET login page; initialize `_TESTCOOKIESUPPORT=1`; merge cookies by name.
- Extract `Frm_Logintoken`, preserving the verified fallback of `0`.
- POST `frashnum`, `action=login`, `Frm_Logintoken`, `username`, `Password`.
- Preserve plaintext/SHA-256/legacy fallback order and redirect handling.
- Discover dynamic `_SESSION_TOKEN` through template/getpage/top/start/keepAlive.
- POST `/getpage.gch?pid=1002&nextpage=manager_dev_conf_t.gch` with the exact
  `IF_ACTION=devrestart`, `IF_ERRORSTR=SUCC`, `IF_ERRORPARAM=SUCC`,
  `IF_ERRORTYPE=-1281035768`, `flag=1`, `_SESSION_TOKEN` form.
- Preserve Origin, Referer, timeouts and reboot disconnect handling.

Also inspected: AlarmScheduler, SecurePrefs, BootReceiver, RebootAlarmReceiver,
MainActivity, manifest, themes, Gradle settings and GitHub Actions.

## Inherited limitations to account for outside the protocol

The reboot method deliberately tolerates a dropped POST connection and does not
validate the response code. Its positive result is a command attempt, not proof
of physical reboot or recovery. The login heuristic also recognizes the
`/start.ghtml` location; Test Connection adds the dynamic-token check. Do not
present these heuristics as independent proof of hardware health.

The local fixture tests expected requests and session handling; real-hardware
acceptance still requires the user's router and credentials. Never store actual
credentials, cookies, session tokens or captured router pages in this repository.
