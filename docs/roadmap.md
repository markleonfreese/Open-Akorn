# Roadmap

## POC
- Recovered community fork and pinned protocol; independent APK reference analysis.
- Read-only explicit-IP TCP adapter, synthetic sample, Celsius web dashboard and JSON telemetry. Standard library only; local browser only.
- Shared byte fixtures and unit-explicit normalized status contract consumed by Python, Kotlin and the PWA; tests cover valid/invalid frames, sentinels, partial/coalesced TCP frames and freshness.
- Lightweight native Android demo/read-only TCP/BLE preview and localhost installable PWA. Both are simulator-tested; physical grill and target phone/tablet acceptance remain pending.

## Next gate
1. Owner performs the cold-grill acceptance in `docs/bench.md` and `docs/owner-quickstart.md`; verify model, firmware, live readings, probes, stale clearing, offline operation and reconnect. Compare the Android preview on the actual phone/tablet. Any mismatch blocks live use.
2. Based on physical results, decide whether to improve BLE reconnect or use the existing Python gateway as the single transport owner. Do not add simultaneous direct clients.
3. Only then consider user-approved foreground monitoring, local timers/alerts, local history, authenticated private-LAN access or Home Assistant broker wiring. Preserve loopback defaults and do not retain temperatures.
4. Keep provisioning, ESP32, target writes and all fan/power/timer/alarm commands out of this prototype. Preserve the factory controller.

## Prototype status
The offline prototype is code-complete pending review and CI: Python gateway, shared fixtures, Android APK and PWA have local unit/browser smoke coverage. Physical device compatibility, owner usability, actual Android 10/11/12+ permissions, Lenovo performance and phone-over-network access are still unverified. Simulated success is not hardware acceptance.
