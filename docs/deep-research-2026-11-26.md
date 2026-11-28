# Deep research supplement — 26 November 2026, 09:52 AWST

## Findings and decision changes

### Independent chipset corroboration (verified documentation, physical chip not verified)
Opulinks' official [Wi-Fi provisioning guide](https://opulinks-tech.github.io/QuickDev-Framework-User-Guide/application-guide/wifi_provision_via_ble.html) explicitly defines the OPL GATT service 0xAAAA and input/output characteristics 0xBBB0/0xBBB1. This matches APK static references and the pinned [community implementation](https://github.com/grzbiet/cg980app/tree/2236faf93f3d2b0663764a0379131d97aef77c2e). The [QuickDev framework](https://github.com/Opulinks-Tech/QuickDev-Framework) supports OPL1000 A3 and OPL2500 A0 and includes network manager, BLE manager, Wi-Fi provisioning and reference mobile app. The [older A2 SDK release notes](https://github.com/Opulinks-Tech/OPL1000A2-SDK/blob/master/Release_Notes.md) mention opulinks_iot_app.apk and BLEWIFI examples. The [2018 BLE setup guide mirror](https://manuals.plus/m/95350005c2f36596ce6e8770190ee39557bffd30157c02dcdf78edb3e5b3c37c) describes BLE network onboarding on OPL1000. SDK-level agreement increases confidence about communications ancestry, not equivalence of firmware, PIN rules, all provisioning opcodes or control messages. Investigate reference app solely as a possible owner-operated provisioning alternative; no OTA, reset or firmware flashing. Original controller remains authoritative.

### Australian manufacturer constraints (verified text, E6480 manual)
Primary source: [Australian E6480 manual](https://chargrilleraustralia.com.au/wp-content/uploads/2026/03/E6480_Auto-Kamado_E_A.2_11-15-22.pdf), printed sections C-9 to C-12. Text was retrieved; page screenshot service returned cache miss. Confirm user's actual model before applying.
- Pit target maximum 700°F (approximately 371°C); not a recommended default or a universally safe temperature.
- Meat probe target maximum 225°F (approximately 107°C); manual warns probe can be damaged above 250°F (approximately 121°C). Do not use it as an ambient pit probe.
- Heat deflector maximum 450°F (approximately 232°C).
- Manual fan mode overrides temperature automation. Keep fan mode observational only; remote manual fan override is out of MVP scope.
- Physical damper adjustment remains an operator task. No unattended operation; no app override of factory interlocks or alarms.
- Dedicated 2.4 GHz Wi-Fi per manual; mixed-band network may fail, BLE-only is legitimate fallback.
- [Manufacturer FAQ](https://www.chargriller.com/pages/auto-kamado-frequently-asked-questions-faqs) says controller estimates centre-of-grate temperature algorithmically, and readings vary with setup/environment. Do not present the existing telemetry as raw calibrated sensor measurements.
- Upstream protocol.md reports 50–350°F target range for food probes; its config/code permits this. The manufacturer limits take precedence for future policy, not the community command acceptance range. Do not implement writes in this packet. Timer/power are also unsupported until independently established. Use manufacturer shutdown/pause instructions, not smart plug power cuts.

### Radio hardware and service longevity
[FCC filing 2AYE4CGG9800102021A](https://fccid.io/2AYE4CGG9800102021A), dated 2020-12-20, lists 2.4 GHz Wi-Fi and Bluetooth and links to internal photos. This is a Gravity-associated filing, not proof of AKORN board identity. Internal photo exhibit was not retrievable (cache miss). [Factory replacement controller part 900839](https://www.chargriller.com/products/900839-controller-assembly-auto-kamado) listed sold out on retrieved US page; Australian availability unverified. Retain original hardware; no reason to purchase a board yet.
[Official app announcement](https://www.chargriller.com/blogs/tips-and-care/how-to-install-the-char-griller-app) confirms US discontinuation and temporary AU/NZ support, without guaranteeing current backend function. [AppBrain Android listing](https://www.appbrain.com/app/char-griller/com.jdkj.grill) and [Chrome-Stats archive](https://chrome-stats.com/d/com.jdkj.grill) identify version 2.0.8 and removal in 2025 (different reported dates: Aug 11 and Sep 2 respectively; do not treat either as exact shutdown date). [Australian iOS listing](https://apps.apple.com/au/app/char-griller/id1551650169) still visible in retrieved index; no proof installation or backend works. [Original Reddit community release](https://www.reddit.com/r/smoking/comments/1tbh4jz/chargriller_gravity_980_app/) independently supports community project provenance. No verified dedicated AKORN Home Assistant integration found in this bounded search.

## Integration decision: one gateway, multiple consumers

Preferred MVP: Python gateway owns a single local TCP session (or BLE notification session if Wi-Fi unavailable), validates frame layout and freshness, exposes local web dashboard and publishes read-only HA telemetry. No additional hardware if a suitable always-on computer is available.
- [HA MQTT integration](https://www.home-assistant.io/integrations/mqtt/) supports discovery and availability. Proposed device-scoped topics, stable non-secret local device IDs, retained discovery config, last-will offline, explicit device availability separate from gateway availability, no retained stale temperatures and no command topic. On invalid frames, transport loss or expiry mark sensors unavailable, not zero. Demo data must never masquerade as live HA device.
- [HA REST](https://www.home-assistant.io/integrations/rest/) provides a simpler read-only prototype if HA shares a reachable secured endpoint; 127.0.0.1 on one host/container is not reachable from another. Do not change current loopback binding to expose unprotected access. Consider authenticated private LAN binding only in subsequent bounded work.
- [ESPHome Bluetooth proxy](https://esphome.io/components/bluetooth_proxy/) supports active GATT connections on ESP32; proxy is not an AKORN decoder. [BLE client](https://esphome.io/components/ble_client/) is an alternative for a dedicated ESP32 telemetry gateway, subject to real BLE tests. Avoid multiple clients on a controller; do not replace PID.
- Other manufacturers' integrations (architecture reference, incompatible protocols): [Pit Boss](https://github.com/dknowles2/ha-pitboss), [Masterbuilt](https://github.com/dipseth/masterbuilt_smoker_connect), [Kamado Joe](https://github.com/rellerton/kamado-joe-ha). Do not copy their protocol or assume compatibility.

### Suggested entity contract (proposed)
Pit temperature, pit target (observational), meat probe 1/2 temperature and targets, reported alarm, reported fan mode and manual speed (nullable), link state, reading age, last-seen. Preserve native Fahrenheit values internally and convert once for Celsius display. Never infer actual fan RPM, lid state or sensor health from an undocumented field. No switches, climate actuation, power, fan, alarm silence or automatic grill actions.

## Evidence and next acceptance gate

Repo inspected: draft PR #1, head 65e613565ff910fd002022b47d5916121794e658; README and monitor.py now contain optional Bleak notification monitoring for explicit Akorn name, 24-byte notification validation and synthetic TCP reconnect test documentation. No command path observed in inspected BLE lines. GitHub Actions lookup returned no PR-triggered runs for this head; docs/verification.md still records the original nine-test baseline and hashes, not the current branch. Do not represent it as current test evidence. Re-run current suite and update verification with exact commit and actual results.

Priority order:
1. Verify latest PR tests locally, record evidence; do not reimplement BLE monitoring.
2. Cold grill with no fuel: record model, firmware if visible, panel units and BLE name privately. First try explicit-IP or BLE notification read-only path. Compare 30+ samples with physical panel and disconnected probes. Verify offline operation and reconnect; no actuation. See docs/bench.md.
3. Only if Wi-Fi onboarding is needed, separately research official OPL reference source and community flow. Compare actual protocol via sanitised capture. Owner explicitly initiates provisioning with own credentials/PIN; no automated reset/OTA or authentication bypass. Never publish raw HCI captures.
4. Read-only HA MQTT bridge with simulated broker tests and device/gateway availability semantics; no deployment until owner approval.
5. Improve mobile dashboard and record UX acceptance. Physical cold-grill validation is still the critical dependency.

No new physical tests, full APK decompilation, network provisioning, firmware flashing, paid dispatch or live deployment in this research pass.
