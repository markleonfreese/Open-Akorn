# Verification — 26 Nov 2026 AWST

## Current local implementation

- Repository branch: `poc/local-monitor`; implementation commit `27d9e561a96c744e2ca69e742ff7be37de00bc43` (fixture ignore correction on top of `ec410e7af1b511f5833191ebbc3d47fec47f90b5`).
- Python 3.14.6: `python3 -m unittest discover -s tests -v` — 30 tests passed, including shared protocol fixtures and PWA HTTP routes.
- `python3 -m compileall -q monitor.py ha_mqtt.py tests` — passed.
- Node: `node --test tests/pwa-sw.test.mjs` — 4 tests passed. `node --check pwa/app.js` and `node --check pwa/service-worker.js` — passed.
- Android: hosted job in run [45782865683](https://github.com/markleonfreese/Open-Akorn/actions/runs/45782865683) passed `testDebugUnitTest assembleDebug` with ten JVM tests on the exact implementation commit. A local build/test had passed before the fixture tracking issue was found; this shell no longer has a Java runtime configured, so the latest Android rerun is the hosted result.
- Android artifact: `android/app/build/outputs/apk/debug/app-debug.apk`, debug-signed, 2.7 MB, SHA-256 `d2de828e2a7b5e670dfae0293f21ae0e178ba0703b15e9ae2b4ff432bfc6ae49` (recompute after the final build).
- PWA browser smoke: local demo rendered in the in-app browser at `127.0.0.1`; simulation was clearly identified and pit/probe Celsius, targets, fan/alarm status and safety note appeared. The manifest link, local icons, and app shell were visible. HTTP tests confirm manifest/icons/app shell routes; unit tests confirm API no-store and service-worker network-only behavior. A real device install and offline browser-profile check remain unverified.
- `git diff --check` and Python manifest/XML parsing — passed after documentation whitespace cleanup.

## Hosted PR verification

GitHub Actions run [45782865683](https://github.com/markleonfreese/Open-Akorn/actions/runs/45782865683) on implementation commit `27d9e561a96c744e2ca69e742ff7be37de00bc43`: Python 3.11 and 3.13 each passed all 30 tests, Python compile and four PWA cache tests; Android passed ten JVM tests and packaged the debug APK. The preceding run [45782388692](https://github.com/markleonfreese/Open-Akorn/actions/runs/45782388692) failed because the repository-wide `*.csv` ignore rule omitted the shared fixture; commit `27d9e56` narrows that rule and tracks the fixture. This failure was diagnosed from job logs, then the corrected head passed.

## Not verified / acceptance gates

- No Android phone, Lenovo tablet or emulator runtime was tested. Installing the Android emulator image triggered a separate SDK license acceptance prompt; it was not accepted, and the image was not installed. The Activity layout, Android permission dialogs, screen-off/process lifecycle and measured memory/startup behavior therefore remain device-runtime checks.
- No live TCP grill, BLE controller, physical model/firmware, probe readings, offline operation or cold-grill acceptance was tested. Follow `docs/bench.md`; tests and the simulator are not hardware evidence.
- No PWA was installed to a device or tested offline in a real service-worker-enabled browser profile. Automated service-worker cache tests and a rendered local browser smoke passed; install prompt/device behavior remains unverified.
- No broker, Home Assistant runtime, authenticated LAN exposure, provisioning, grill commands, deployment or merge was performed.
