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
Review the disconnected Home Assistant message adapter and its fake-publisher tests in the existing draft. Before adding a real MQTT client, select an operator-controlled local broker, receive explicit approval to use its endpoint/credentials, and verify a cold-grill device. Keep one gateway as the only TCP reader; retain discovery and availability only; never retain temperatures or add command entities. No deployment or merge without review. Physical compatibility remains unverified.
