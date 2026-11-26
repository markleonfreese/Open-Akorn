# Verification — 26 Nov 2026 AWST

## Build and automated evidence

- Repository branch: `poc/local-monitor`; current documentation commit is recorded in Git. The APK contains implementation source commit `27d9e561a96c744e2ca69e742ff7be37de00bc43`.
- Python 3.14.6: `python3 -m unittest discover -s tests -v` — 30 tests passed; `python3 -m compileall -q monitor.py ha_mqtt.py tests` — passed.
- Node: `node --test tests/pwa-sw.test.mjs` — 4 tests passed; `node --check pwa/app.js` and `node --check pwa/service-worker.js` — passed.
- Hosted CI run [45782865683](https://github.com/markleonfreese/Open-Akorn/actions/runs/45782865683) on implementation commit `27d9e561a96c744e2ca69e742ff7be37de00bc43`: Python 3.11 and 3.13 each passed all 30 tests, Python compile and four service-worker tests; Android passed ten JVM tests and `assembleDebug`. The preceding run [45782388692](https://github.com/markleonfreese/Open-Akorn/actions/runs/45782388692) found the shared CSV fixture was excluded by `*.csv`; commit `27d9e56` corrected the ignore rule and the next run passed.
- Android source review: the app requests Bluetooth permissions only when BLE is selected (Nearby devices on Android 12+, Location for BLE scanning on Android 10–11); TCP uses the normal Internet permission. `onStop()` disconnects and clears the active transport. These are source-level findings, not observed Android runtime behavior.
- APK: debug-signed version `0.1.0` (`versionCode=1`, app id `com.openakorn.app`), 2,800,408 bytes. The generated APK and Downloads handoff copy have the same SHA-256: `d2de828e2a7b5e670dfae0293f21ae0e178ba0703b15e9ae2b4ff432bfc6ae49`. ZIP integrity check passed. The owner copy is `Open-Akorn-debug-0.1.0-27d9e56.apk` in the local Downloads folder; it is intentionally not committed or uploaded.
- PR #1 is still a draft. At review check, PR discussion comments, review submissions and inline review threads were all empty; there was no Copilot feedback to resolve.

## PWA browser evidence

- Chrome opened the local demo at `http://127.0.0.1:18080/`; it showed SIMULATION, Celsius readings, probe 2 disconnected, and the monitoring-only safety note.
- On localhost the page was a secure context; the active service worker was `activated` and controlled the page. The manifest had `display: standalone` and `start_url: /`. Cache Storage contained only the app shell (HTML, JavaScript, CSS, manifest and icons); no telemetry response was cached.
- On 27 Nov 2026, Chrome reported “Successfully installed” for **Open-Akorn Local Monitor** at `http://127.0.0.1:18080/`. Selecting **Open tab in app** launched a separate standalone app window with no browser address bar or tabs.
- The standalone launch was performed while the local gateway was stopped; no process was listening on port 18080. The cached app shell opened and showed “Waiting for local gateway”, blank pit/probe readings, and unknown fan/alarm state. This verifies installed-app offline shell launch and connection-loss freshness in this local Chrome/macOS profile. No live telemetry remained visible.
- Automated service-worker tests also verify shell caching and network-only status fetches. PWA browser evidence used the local demo only; no grill or phone/tablet connected.

## Not verified / owner acceptance gates

- No Android phone, Lenovo tablet or emulator runtime was available; `adb` and a configured local Java runtime were absent. The Android SDK system-image licence was not accepted. Therefore screen-size/font-scale layout, measured startup/memory, install/permission prompts and app lifecycle behavior on either target device remain unverified. The app uses a vertically scrolling native layout and is built for API 29+; no measured performance claim is made.
- No actual Android APK installation was performed. Install the local debug APK on the owner-controlled devices and use **Try demo** first; follow [owner-quickstart.md](owner-quickstart.md).
- No live TCP/BLE grill, exact firmware, probe readings, cold-grill acceptance or physical safety behavior was tested. Simulator and transport unit tests do not establish hardware compatibility. Use [bench.md](bench.md), one client at a time, factory controller intact, no fuel or ignition.
- No broker or Home Assistant runtime was connected. `ha_mqtt.py` remains a tested message adapter only. No provisioning, grill commands, LAN exposure, deployment or merge was performed.
