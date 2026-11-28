# Research and protocol evidence — 2026-11-26 AWST

## Recovered implementation

- Original community announcement: https://www.reddit.com/r/smoking/comments/1tbh4jz/chargriller_gravity_980_app/ — author reports local BLE bootstrap, telemetry, web graphs, logging and alarms. Another contributor asked to adapt for AKORN; author agreed. Original https://github.com/cg980app/cg980app returned HTTP 404.
- Surviving copy: https://github.com/grzbiet/cg980app/tree/2236faf93f3d2b0663764a0379131d97aef77c2e (GitHub repository search and pinned commit retrieved directly). Upstream README, protocol, config, app, BLE, monitor and web modules inspected. LICENSE is MIT, copyright 2026 Kennedy Projects. This POC uses protocol facts and independently authored code; no upstream code or proprietary app content is redistributed.
- Upstream protocol: https://github.com/grzbiet/cg980app/blob/2236faf93f3d2b0663764a0379131d97aef77c2e/protocol.md — reports live testing of both models, BLE-only operation, Wi-Fi onboarding, direct TCP monitoring, chamber/food target changes, AKORN fan mode/speed, alarm silencing and distinct model behaviour. Not independently hardware-verified.
- Upstream implementation review: config defaults to all network interfaces, web.py accepts command POSTs, no authentication check observed in the inspected command handler. Do not expose it to internet or use controls without supervision. Its protocol docs and __init__/constants disagree about 16 vs 20 bytes; monitor.py uses stream boundary heuristics. Open-Akorn starts with explicit frame length and stops/reconnects on malformed data.
- Older Home Assistant thread https://www.reddit.com/r/homeassistant/comments/qm7s7i/ was cited earlier but could not be re-fetched in this investigation; do not rely on it as independent evidence. Public searches for the original project/forks were unsuccessful until GitHub repository search with fork:true.

## APK: independent static evidence

User-provided Char-Griller_2.0.8_APKPure.apk, SHA-256 bc5983764a8802927a3d7f13b9b2c2c821b4ddb72e4df38babccd93fd1bdb4d2. Origin/signing authenticity not independently verified. ZIP inspection found classes.dex and classes2.dex. No installation or execution. No APK or decompiled source published. JADX/APKTool not installed, dependency installation unavailable. A local lightweight DEX string-table and method instruction reference scanner located these facts:

- classes.dex defines application classes under com.jdkj.grill.
- com.jdkj.grill.impl.b static initialiser references UUIDs 0000aaaa-0000-1000-8000-00805f9b34fb, 0000bbb0-0000-1000-8000-00805f9b34fb, 0000bbb1-0000-1000-8000-00805f9b34fb; upstream independently assigns service, write and notify roles respectively.
- com.jdkj.grill.c.a.c.j references Socket.connect, getOutputStream and getInputStream. Nested c$b.run reads and c$c.run writes; c$d.run references sendUrgentData. This confirms a TCP client, but not its target port directly.
- com.jdkj.grill.c.b.b.m references DatagramSocket constructor and literal 3333; nested b$c.run references literal 3333 and DatagramSocket.send. b.g receives datagrams and inspects source address and port. UDP 3333 is therefore also an APK-evidenced lead (likely discovery; payload and purpose still need verification). TCP 3333 is community-evidenced.
- com.jdkj.grill.netstrap contains Wi-Fi MAC, scan, connect and data framing references including com.netlinkc.opl1000.WiFiMac. Strong lead for Opulinks OPL-series chipset; actual board revision not confirmed.
- com.jdkj.grill.ui.ConnectBleActivity, ConnectWifiActivity, SetTempActivity, SetTempWZDActivity and SettingWZDFragment are analysis targets. Local DeviceBeanDao includes Wi-Fi metadata and a password field: do not export that database.
- This is reference-level static analysis, not a complete decompilation or proof of control flow.

## Wire facts / hypotheses

Community specification reports BLE advertisements Akorn-*, Gravity980-* or BLEWIFI APP. Status notification has 4-byte framing followed by 16 or 20 bytes of telemetry. TCP port 3333 streams raw telemetry without client payload, generally one client at a time. Frame length 20 is the reported AKORN variant; old Gravity firmware may use 16. Confirm against physical unit. Each temperature field is unsigned big-endian Fahrenheit. Offset map (zero-based):
- 0 current pit, 2 pit target, 6 current meat 1, 8 meat 1 target, 10 current meat 2, 12 meat 2 target (each two bytes).
- Current sentinel 1000 = disconnected; target sentinel 4096 = unset. Bytes 4 and 5 are alarm (0 active, 16 silent) and reserved zero. 
- AKORN bytes 16 and 17: mode (16 auto, 0 manual) and speed (0–100 meaningful in manual); bytes 18–19 unknown. Byte 14 is not reliable actual fan state on AKORN, byte 15 does not imply a door sensor.
- Reported control: device-specific target changes, fan and silence. Timer/power have different framing; upstream reports incorrectly formed messages causing error and loss of Wi-Fi. Keep ALL command generation absent from current POC. Do not infer fan auto means fan physically spinning.
- Explicit validation rejects unexpected lengths, header, fan mode and implausible raw temperatures. Range is a conservative research assumption, NOT a certified safety limit. No checksum established; misalignment may still yield plausible bytes. Hardware tests must compare against panel; no automatic resynchronisation/fallback or control.

## Additional references

- Official US shutdown, AU/NZ support described as temporary: https://www.chargriller.com/blogs/tips-and-care/how-to-install-the-char-griller-app . Current AU backend health not tested.
- Official AKORN error guide (probe, overheat, fan, supply errors): https://www.chargriller.com/blogs/tips-and-care/guide-to-akorn-auto-kamado-error-codes .
- AU product: https://chargrilleraustralia.com.au/products/akorn-auto-kamado-charcoal-grill/ ; AU app instructions: https://chargrilleraustralia.com.au/how-to-use-char-griller-app/ .
- Gravity FCC documentation (not necessarily AKORN): https://fccid.io/2AYE4CGG9800102021A/Test-Report/WIFI-report-5049012 .
- Candidate chipset SDK and BLE provisioning docs: https://github.com/Opulinks-Tech/QuickDev-Framework and https://opulinks-tech.github.io/QuickDev-Framework-User-Guide/application-guide/wifi_provision_via_ble.html . Compare firmware framing, do not assume compatibility.
- Replacement hardware fallback: https://github.com/mantellian/chargriller-980-controller (Gravity 980 ESP32 WIP; does not prove AKORN sensor compatibility).
- Similar local-first projects, architecture references only: https://github.com/kalub92/OpenSpark , https://github.com/dipseth/masterbuilt_smoker_connect , https://github.com/rellerton/kamado-joe-ha .
- Android APK extraction: https://developer.android.com/tools/adb ; JADX: https://github.com/skylot/jadx ; Bleak: https://github.com/hbldh/bleak ; Android Bluetooth HCI snoop and Wireshark can corroborate static findings on owner-controlled hardware. Use actual device PIN if required, not bypassing device authentication. No need to patch vendor cloud login.
