# Open-Akorn Deep Research Report — 26 November 2026

Research cut-off 11:09 AWST. Repository adaptation of the user-supplied Deep Research report (Pasted text(6).txt); source URLs replace chat-only citations. This is a faithful technical digest, not a verbatim archival copy. Follow-up source inspection and corrections below are explicitly labelled. No physical hardware verification.

## Conclusion and existing foundation

Retain the original factory controller, sensors, temperature regulation and interlocks. Replace the communications, UI and integration layer. The controller is independently usable without the mobile application. The vendor discontinued US app service and describes AU/NZ support as temporary, not guaranteed.

Existing draft [PR #1](https://github.com/markleonfreese/Open-Akorn/pull/1) contains read-only TCP/BLE monitoring, Celsius dashboard, stale data protection and synthetic transport tests. Physical AKORN hardware and hosted CI were unverified at report cut-off.

## Community implementations and provenance

The [May 2026 community announcement](https://www.reddit.com/r/smoking/comments/1tbh4jz/chargriller_gravity_980_app/) describes APK analysis, local BLE Wi-Fi provisioning, telemetry, browser graphs, CSV logging and alarms. An AKORN owner asked to adapt the project.

The surviving [grzbiet/cg980app](https://github.com/grzbiet/cg980app/tree/2236faf93f3d2b0663764a0379131d97aef77c2e) is identified as a fork of [kprojects/chargrillerd](https://github.com/kprojects/chargrillerd), which itself credits cg980app. Fork metadata does not establish chronology; recreation/renaming is plausible. The pinned grzbiet revision reorganises the application into app, BLE, monitor, protocol, history/storage and web modules and reports AKORN support plus Gravity temperature setting. The kprojects project reports Auto AKORN and Gravity 980, BLE provisioning, TCP monitoring/reconnect, history, browser alerts and control.

Both are MIT licensed; retain copyright and licence notices for reused code. Prefer selective reuse of verified protocol knowledge, parsers and fixtures rather than wholesale application replacement. The separate [ESP32 replacement controller](https://github.com/mantellian/chargriller-980-controller) is a hardware failure fallback, not the primary architecture.

## APK and Opulinks

The supplied Char-Griller_2.0.8_APKPure.apk SHA-256 is bc5983764a8802927a3d7f13b9b2c2c821b4ddb72e4df38babccd93fd1bdb4d2. Static DEX inspection, not installation/execution, found com.jdkj.grill, BleBlueImpl, ConnectWifiActivity, SetTempActivity, UUID references in com.jdkj.grill.impl.b, TCP socket classes, and a separate DatagramSocket path referencing 3333 (com.jdkj.grill.c.b.b.m). UDP purpose is not established. APK authenticity and full decompilation remain outstanding.

Three-source agreement (APK, community and [official Opulinks documentation](https://opulinks-tech.github.io/QuickDev-Framework-User-Guide/application-guide/wifi_provision_via_ble.html)):
- GATT service 0000AAAA-0000-1000-8000-00805F9B34FB.
- Write/data input 0000BBB0-0000-1000-8000-00805F9B34FB.
- Notify/data output 0000BBB1-0000-1000-8000-00805F9B34FB.

[QuickDev](https://github.com/Opulinks-Tech/QuickDev-Framework) supports OPL1000 A3 and OPL2500 A0; APK com.netlinkc.opl1000.WiFiMac strengthens OPL1000-family hypothesis. Physical chip revision unverified. SDK provisioning includes scan, connect/disconnect/reconnect, device information, Wi-Fi status and reset. Older SDK uses request IDs from 0x0001 and response IDs in 0x100x range. Char-Griller-specific activation, AKORN Wi-Fi-info and 0x19 status framing must not be conflated with generic SDK. No cloud-login bypass is required for local interoperability. Do not execute unknown commands, reset Wi-Fi or flash firmware. Owner-initiated provisioning only, with actual PIN where required and credentials kept private.

## Network protocol

[2021/2022 Home Assistant/Node-RED thread](https://www.reddit.com/r/homeassistant/comments/qm7s7i/) reports Gravity TCP 3333 and big-endian fields, MQTT forwarding, and an AKORN owner decoding the same interface. Community modern documentation reports 20-byte raw TCP records about once per second and 24-byte BLE notifications with a four-byte wrapper; single TCP client. Historical 16-byte frames must remain explicit, not guessed. No checksum established; TCP stream boundary ambiguity remains.

| Offset | Reported field |
|---|---|
| 0–1 | Pit current, big-endian Fahrenheit |
| 2–3 | Pit target |
| 4 | Alarm: 0x10 normal, 0x00 sounding |
| 5 | Reserved zero |
| 6–7 | Meat 1 current |
| 8–9 | Meat 1 target |
| 10–11 | Meat 2 current |
| 12–13 | Meat 2 target |
| 14 | Gravity fan status; apparently static on AKORN |
| 15 | Gravity door/turbo; apparently static on AKORN |
| 16 | AKORN fan mode 0x10 auto / 0x00 manual |
| 17 | Manual fan speed 0–100 |
| 18–19 | Unknown |

Current sentinel 1000 = unavailable; target sentinel 4096 = unset. Fan speed meaningful only in manual; do not infer actual RPM or lid state. BLE status wrapper reported 00 19 14 00. All field semantics remain unverified on the owner's unit. UDP 3333 may be discovery/reachability; passive owner-network observation only, no unknown sends.

Community write descriptions differ between revisions. Timer/power framing is particularly uncertain and incorrect writes reportedly caused errors and loss of Wi-Fi. Do not implement operating-state writes.

## Manufacturer limits and architecture

[Australian E6480 manual](https://chargrilleraustralia.com.au/wp-content/uploads/2026/03/E6480_Auto-Kamado_E_A.2_11-15-22.pdf) documents maximum grill target 700°F/371°C, meat target 225°F/107°C, meat probe damage above 250°F/121°C, and manual fan override of automatic regulation. Manufacturer limits take precedence over broader community command ranges. [Official FAQ](https://www.chargriller.com/pages/auto-kamado-frequently-asked-questions-faqs) describes algorithmic grate-temperature estimation. [Australian app instructions](https://chargrilleraustralia.com.au/how-to-use-char-griller-app/) describe Wi-Fi/BLE and one mobile device at a time. Preserve factory logic; no unattended operation.

One gateway owns a local TCP or BLE session, normalises and validates telemetry, and serves a local dashboard/history and optional MQTT. [HA MQTT](https://www.home-assistant.io/integrations/mqtt/) discovery and availability support stable identifiers and unavailable states. No retained live temperatures, no command topics. Distinguish gateway/device/probe availability. [ESPHome BLE proxy](https://esphome.io/components/bluetooth_proxy/) supports active GATT but is not an AKORN decoder. ESP32 gateway optional later. Browser-only Web Bluetooth is not a universal mobile solution (secure context, user gesture, limited Safari/iOS support); see [MDN Web Bluetooth](https://developer.mozilla.org/en-US/docs/Web/API/Web_Bluetooth_API). Prefer ordinary web UI backed by local gateway.

## Engineering priorities from report

Extend existing PR. Explicit model/frame profile; fixture tests for sentinels, alarm, fan, fragmentation, coalescing, leading bytes, malformed frames, reconnect and stale readings; passive UDP diagnostics; BLE status hardening; offline provisioning research with owner approval before actual network changes; read-only MQTT discovery and availability; fake device; CI; cold-hardware test instructions. Do not duplicate existing work. No operating-state writes, firmware flashing, OTA, login bypass, reset, public exposure or deployment. Report exact commit/test evidence. Physical acceptance precedes remote target control.

## Cold-grill gate

No charcoal, ignition, heat or deliberate fault. Identify model, firmware and BLE name privately. Compare 30+ consecutive telemetry samples against panel, both probes and unplugged sentinel, targets changed on physical controller only. Observe fan fields without commanding them. Confirm original controller unchanged, power-cycle cold, verify reconnect, offline operation on owner-controlled network, and stale clearing on disconnect. Any mismatch blocks progression. Only after this passes and separate explicit approval, consider a supervised cold target-setting experiment. No fan, timer, power or alarm commands.

## Source register

- [Existing research](research.md), [supplement](deep-research-2026-11-26.md), [bench](bench.md).
- [Pinned grzbiet protocol](https://github.com/grzbiet/cg980app/blob/2236faf93f3d2b0663764a0379131d97aef77c2e/protocol.md).
- [kprojects/chargrillerd](https://github.com/kprojects/chargrillerd), [community Reddit](https://www.reddit.com/r/smoking/comments/1tbh4jz/chargriller_gravity_980_app/), [older HA thread](https://www.reddit.com/r/homeassistant/comments/qm7s7i/).
- [Opulinks SDK](https://github.com/Opulinks-Tech/QuickDev-Framework), [provisioning](https://opulinks-tech.github.io/QuickDev-Framework-User-Guide/application-guide/wifi_provision_via_ble.html).
- [AU product](https://chargrilleraustralia.com.au/products/akorn-auto-kamado-charcoal-grill/), [manual](https://chargrilleraustralia.com.au/wp-content/uploads/2026/03/E6480_Auto-Kamado_E_A.2_11-15-22.pdf), [app instructions](https://chargrilleraustralia.com.au/how-to-use-char-griller-app/), [shutdown announcement](https://www.chargriller.com/blogs/tips-and-care/how-to-install-the-char-griller-app).
- [HA MQTT](https://www.home-assistant.io/integrations/mqtt/), [ESPHome proxy](https://esphome.io/components/bluetooth_proxy/), [replacement controller](https://github.com/mantellian/chargriller-980-controller).

## Follow-up direct repository comparison (after report)

Inspected kprojects/chargrillerd main at 4656ac85f292fae9a0b4928b017bfd9801c29ed6, README, LICENSE, protocol.md and monolithic chargrillerd.py (2551 lines; modular cgriller paths not present in this revision). MIT copyright 2026 Kennedy Projects. [Pinned protocol](https://github.com/kprojects/chargrillerd/blob/4656ac85f292fae9a0b4928b017bfd9801c29ed6/protocol.md) agrees on 20-byte status and 24-byte BLE wrapper. It differs from the grzbiet protocol/report on TCP command envelope (six vs four bytes) and food-probe target range (50–250 vs 50–350°F). Neither is authoritative for future writes; manufacturer max 225°F and hardware validation prevail. kprojects reports same board and Opulinks OUI; this is community evidence, not verified board identity.

| Feature | kprojects reference | Open-Akorn head 8aafe7a | Decision |
|---|---|---|---|
| TCP status | 20-byte buffer heuristics | Explicit 16/20, read-only | Keep explicit until capture validates boundaries |
| BLE status | Scan/provision and GATT writes | Exact Akorn name, BBB1 subscribe | Keep read-only; verify service and reconnect later |
| Provisioning | Activation, scan, credentials, disconnect/auth | Absent | Research only until needed; no wholesale reuse |
| Dashboard | LAN bind 0.0.0.0, POST commands | Loopback, POST rejected | Preserve current security boundary |
| History/alarms | CSV, sessions, notifications | Minimal dashboard | Defer until physical telemetry |
| MQTT | Not established in inspected reference | In-memory message adapter | Broker integration deferred |
| Discovery | BLE and subnet scanning | Explicit IP/name | No blind subnet scan; passive diagnostics later |

PR head inspected 8aafe7acda7275d59f3d77fb2d7f1ecc005df3b5. Verification document reports 22 passing tests at implementation commit faf8d7b608002ed760d38b9c18b0b4e0fb55fb1d, not independently rerun here. MQTT message adapter exists but does not connect to broker or monitor. No PR-triggered CI runs returned. Remaining narrow questions: exact firmware/frame length, UDP payload/purpose, chipset, BLE subscription behaviour, offline operation and bytes 16–19. Prioritise owner acceptance over architecture expansion.
