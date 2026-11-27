# Verification — 27 Nov 2026 AWST

## Build and automated evidence

- Repository branch: `poc/local-monitor`. Hosted run 45923115627 tested head `630509e535fa9ccd8e1b5a3f16ff0b4d0eafad5c`; the APK contains Android implementation source commit `27d9e561a96c744e2ca69e742ff7be37de00bc43`.
- Python 3.14.6: `python3 -m unittest discover -s tests -v` — 30 tests passed; `python3 -m compileall -q monitor.py ha_mqtt.py tests` — passed.
- Node: `node --test tests/pwa-sw.test.mjs` — 4 tests passed; `node --check pwa/app.js` and `node --check pwa/service-worker.js` — passed.
- Hosted PR run [45923115627](https://github.com/markleonfreese/Open-Akorn/actions/runs/45923115627) on the current head passed all jobs: Python 3.11 and 3.13 each ran 30 tests, compiled the sources and passed four PWA service-worker tests; Android unit tests and `assembleDebug` succeeded. Earlier green runs are retained in the PR history. Run [45782388692](https://github.com/markleonfreese/Open-Akorn/actions/runs/45782388692) found the shared CSV fixture was excluded by `*.csv`; commit `27d9e56` corrected the ignore rule and the next run passed.
- Android source review: the app requests Bluetooth permissions only when BLE is selected (Nearby devices on Android 12+, Location for BLE scanning on Android 10–11); TCP uses the normal Internet permission. `onStop()` disconnects and clears the active transport. These are source-level findings, not observed Android runtime behavior.
- APK: debug-signed version `0.1.0` (`versionCode=1`, app id `com.openakorn.app`), 2,800,408 bytes. The generated APK and Downloads handoff copy have the same SHA-256: `d2de828e2a7b5e670dfae0293f21ae0e178ba0703b15e9ae2b4ff432bfc6ae49`. ZIP integrity check passed. The owner copy is `Open-Akorn-debug-0.1.0-27d9e56.apk` in the local Downloads folder; it is intentionally not committed or uploaded.
- PR #1 is still a draft. At review check, PR discussion comments, review submissions and inline review threads were all empty; there was no Copilot feedback to resolve.

## PWA browser evidence

- Chrome opened the local demo at `http://127.0.0.1:18080/`; it showed SIMULATION, Celsius readings, probe 2 disconnected, and the monitoring-only safety note.
- On localhost the page was a secure context; the active service worker was `activated` and controlled the page. The manifest had `display: standalone` and `start_url: /`. Cache Storage contained only the app shell (HTML, JavaScript, CSS, manifest and icons); no telemetry response was cached.
- On 27 Nov 2026, Chrome reported “Successfully installed” for **Open-Akorn Local Monitor** at `http://127.0.0.1:18080/`. Selecting **Open tab in app** launched a separate standalone app window with no browser address bar or tabs.
- The first standalone launch was performed while the local gateway was stopped; no process was listening on port 18080. The cached app shell opened and showed “Waiting for local gateway”, blank pit/probe readings, and unknown fan/alarm state. This verifies installed-app offline shell launch and connection-loss freshness in this local Chrome/macOS profile. No live telemetry remained visible.
- For a recovery check, the loopback demo was started on port 18080. The installed window updated to SIMULATION with pit 40.6°C (target 107.2°C), meat probe 1 at 20.6°C (target 35.6°C), meat probe 2 disconnected, reported fan mode auto and alarm not active. Stopping that demo returned the app to “Waiting for local gateway”, blank readings and unknown fan/alarm; the local server log showed repeated `GET /api/status` requests while it was running. This confirms readings resume from the same loopback gateway and clear again after connection loss.
- Automated service-worker tests also verify shell caching and network-only status fetches. PWA browser evidence used the local demo only; no grill or phone/tablet connected.

## Not verified / owner acceptance gates

- No Android phone, Lenovo tablet or emulator runtime was available; `adb` and a configured local Java runtime were absent. The Android SDK system-image licence was not accepted. Therefore screen-size/font-scale layout, measured startup/memory, install/permission prompts and app lifecycle behavior on either target device remain unverified. The app uses a vertically scrolling native layout and is built for API 29+; no measured performance claim is made.
- No actual Android APK installation was performed. Install the local debug APK on the owner-controlled devices and use **Try demo** first; follow [owner-quickstart.md](owner-quickstart.md).
- No live TCP/BLE grill, exact firmware, probe readings, cold-grill acceptance or physical safety behavior was tested. Simulator and transport unit tests do not establish hardware compatibility. Use [bench.md](bench.md), one client at a time, factory controller intact, no fuel or ignition.
- No broker or Home Assistant runtime was connected. `ha_mqtt.py` remains a tested message adapter only. No provisioning, grill commands, LAN exposure, deployment or merge was performed.
