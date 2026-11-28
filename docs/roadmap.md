# Roadmap

## POC
- Recovered community fork and pinned protocol; independent APK reference analysis.
- Read-only explicit-IP TCP adapter, synthetic sample, Celsius web dashboard and JSON telemetry. Standard library only; local browser only.
- Shared byte fixtures and unit-explicit normalized status contract consumed by Python, Kotlin and the PWA; tests cover valid/invalid frames, sentinels, partial/coalesced TCP frames and freshness.
- Lightweight native Android demo/read-only TCP/BLE preview and localhost installable PWA. Both are simulator-tested; physical grill and target phone/tablet acceptance remain pending.

## Next gate
1. Owner verifies the current APK on phone/tablet, including save/load/forget profiles, and records usability/accessibility/performance. Profiles are sequential only; no automatic connection or concurrent grill sessions.
2. Owner performs the cold-grill acceptance in `docs/bench.md` and `docs/owner-quickstart.md`; verify model, firmware, live readings, probes, stale clearing, offline operation and reconnect. Compare the Android preview on the actual phone/tablet. Any mismatch blocks live use.
3. Based on physical results, decide whether to improve BLE reconnect or use the existing Python gateway as the single transport owner. Do not add simultaneous direct clients.
4. Reassess target-write parity only after completed physical acceptance and separate explicit authorization. Keep provisioning, fan override, reset, OTA, cloud emulation and unattended fire automation out of this prototype. Preserve the factory controller.

## Prototype status
The parity implementation is code-complete for currently safe local features, pending owner-device and physical acceptance. Python gateway, shared fixtures, Android APK and PWA have local/CI checks. Physical device compatibility, owner usability, actual Android 10/11/12+ permissions, profile switching, Lenovo performance and phone-over-network access are still unverified. Simulated success is not hardware acceptance.
