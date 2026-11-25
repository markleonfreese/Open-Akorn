# Roadmap

## POC
- Recovered community fork and pinned protocol; independent APK reference analysis.
- Read-only explicit-IP TCP adapter, synthetic sample, Celsius web dashboard and JSON telemetry. Standard library only; local browser only.
- Tests for valid/invalid frames, sentinels, partial/coalesced TCP frames and freshness. Hardware test pending. No installable PWA yet.

## MVP
1. Reproduce community implementation on actual AKORN cold, record device/firmware. Confirm frame size and UDP discovery; use explicit device selection.
2. Implement BLE scan and notification monitoring using optional Bleak, then an explicitly invoked provisioning workflow after local owner approval. Wi-Fi credentials only in memory; no automatic reset/disconnect or unknown commands. Compare upstream MIT implementation, preserve notices if reused.
3. Robust frame boundary identification, long-running reconnect/cancellation, stale and malformed frames, sanitised logs, session history/graphs, user-visible alarms with honest freshness; accessibility and mobile acceptance. Separate simulation from real device.
4. Local authenticated LAN UI for dad, simple Celsius-first views; use private home VPN if remote access required, never raw internet exposure. Home Assistant read-only MQTT discovery/availability or a local polling integration; one gateway owns the TCP connection. No cloud dependency. Browser/PWA installability only after suitable secure-origin deployment; no external scripts.
5. Optional, separate controlled setpoint experiment: supervised cold-only tests, known model, explicit approval, bounds in manufacturer-supported range, confirmation from subsequent telemetry rather than optimistic success; no automatic retries. Disable all unverified timer/power/fan and alarm-silence paths. Do not implement unattended fire control.
6. ESP32 as gateway only if always-on host is inconvenient. Preserve factory controller. Hardware replacement is a separate project requiring verified electrical design and safety review.

## Definition of done
Local device verified on actual model, repeatable offline operation, measured disconnect recovery, explicit device status/freshness, no stale displayed temperatures, no cloud dependency, owner usability acceptance, test evidence and a documented safe operational envelope. Simulated success does not equal hardware completion.

## Next bounded engineering packet
Use upstream pinned copy and APK source facts to implement/test optional BLE monitoring and a fake TCP server with realistic fragmentation and disconnect/reconnect. Keep read-only. Add a repeatable capture workflow. No APK in repo, no live grill writes, no deploy/merge. Run local tests and produce evidence in a draft PR; physical test remains owner-dependent.
