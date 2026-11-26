# Verification — 26 Nov 2026 AWST

## Current local implementation

- Repository branch: `poc/local-monitor`, working tree based on PR #1 head `c6bd96cd28f8fb74af10d0a28e2745489aac86e5`. Exact implementation commit and its hosted checks will be recorded after the PR branch update.
- Python 3.14.6: `python3 -m unittest discover -s tests -v` — 30 tests passed, including shared protocol fixtures and PWA HTTP routes.
- `python3 -m compileall -q monitor.py ha_mqtt.py tests` — passed.
- Node: `node --test tests/pwa-sw.test.mjs` — 4 tests passed. `node --check pwa/app.js` and `node --check pwa/service-worker.js` — passed.
- Android: `cd android && ./gradlew --no-daemon testDebugUnitTest assembleDebug` — build passed; ten JVM tests passed across protocol, freshness, private-IP validation, TCP framing, invalid frames, and transport stop. SDK XML compatibility warning remains because Homebrew command-line tools report XML v4 while Gradle's SDK processor understands through v3.
- Android artifact: `android/app/build/outputs/apk/debug/app-debug.apk`, debug-signed, 2.7 MB, SHA-256 `d2de828e2a7b5e670dfae0293f21ae0e178ba0703b15e9ae2b4ff432bfc6ae49` (recompute after the final build).
- PWA browser smoke: local demo rendered in the in-app browser at `127.0.0.1`; simulation was clearly identified and pit/probe Celsius, targets, fan/alarm status and safety note appeared. HTTP test confirms manifest/icons/app shell routes; unit tests confirm API no-store and service-worker network-only behavior.
- `git diff --check` and Python manifest/XML parsing — passed after documentation whitespace cleanup.

## Prior hosted baseline

GitHub Actions run [45768950368](https://github.com/markleonfreese/Open-Akorn/actions/runs/45768950368) on base PR head `c6bd96cd28f8fb74af10d0a28e2745489aac86e5`: Python 3.11 and 3.13 each passed all 27 tests and Python compile steps. It does not cover the changes recorded above. The workflow now adds PWA cache tests and an Android build job; check its result on the pushed implementation commit before considering this update verified in CI.

## Not verified / acceptance gates

- No Android phone, Lenovo tablet or emulator runtime was tested. Installing the Android emulator image triggered a separate SDK license acceptance prompt; it was declined, and the image was not installed. The Activity layout, Android permission dialogs, screen-off/process lifecycle and measured memory/startup behavior therefore remain device-runtime checks.
- No live TCP grill, BLE controller, physical model/firmware, probe readings, offline operation or cold-grill acceptance was tested. Follow `docs/bench.md`; tests and the simulator are not hardware evidence.
- No PWA was installed to a device or tested offline in a real service-worker-enabled browser profile. Automated service-worker cache tests and a rendered local browser smoke passed; install prompt/device behavior remains unverified.
- No broker, Home Assistant runtime, authenticated LAN exposure, provisioning, grill commands, deployment or merge was performed.
