# Open-Akorn

Experimental, local-first companion for the Char-Griller AKORN Auto-Kamado. Preserve the original controller, sensors and safety mechanisms. No account, cloud service or hardware modification is required by this POC.

## Run the simulator (Python 3.11+; standard library only)
```sh
python3 monitor.py --demo
```
Open http://127.0.0.1:8080 on the same computer. The demo uses a shared synthetic fixture, not a physical grill. The responsive page is an installable PWA on supported browsers. Its service worker caches only the app shell; current telemetry always comes from the local gateway and disappears when the gateway is offline.

To explore other PWA states, pass simulated Celsius values and conditions on the command line. For example:

```sh
python3 monitor.py --demo --demo-pit-c 110 --demo-pit-target-c 121.1 \
  --demo-probe1-c 21 --demo-probe1-target-c 35.6 \
  --demo-disconnect-probe 2 --demo-alarm
```

Use `--demo-connection-failure` to start with no telemetry, or `--demo-disconnect-after 20` to simulate a connection loss after about 20 seconds. These options only alter synthetic local data; they never connect to or command a grill. Omit them to use the shared default fixture.

## Android phone and tablet preview

The native Kotlin app supports Android 10/API 29 and newer. It has a shared-fixture demo with a local configuration dialog for temperatures, disconnected probes, alarm state and simulated connection failure, plus explicit read-only TCP and Bluetooth connections. Android can save up to 12 locally named connection profiles; selecting a profile loads its details but never connects automatically, and connecting replaces the current session. Profiles are not backed up and do not store Bluetooth addresses. The app does not embed Python, use a WebView, scan networks, run in the background, or send controller commands. TCP accepts a manually entered private IPv4 address. BLE requires the exact observed `Akorn-*` name and subscribes to status notifications only.

Install a JDK 17 or newer and Android SDK Platform 36, and accept the SDK license in the SDK Manager. Build and run the Android unit tests with:

```sh
cd android
./gradlew testDebugUnitTest assembleDebug
```

The debug-signed APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. It is intended for local testing, not release distribution. The app disconnects from the grill when backgrounded. Its local cook-timer notification is separate from grill monitoring and can be delayed or cancelled by Android; it is not a safety alarm.

The PWA runs on loopback and is available only on the computer running the gateway. Phone/tablet access to a gateway on another device requires a separately approved authenticated HTTPS setup; it is not enabled.

The dashboard also has a local cook timer, a bounded current-session temperature graph, local cook-session history and Android saved grill profiles. Both apps retain up to 8 sessions with at most 720 samples per session, recorded about once per minute; simulation data is excluded and history can be cleared in the UI. Android stores history in app-private preferences; the PWA stores it in browser local storage on that device. Neither stores a grill address or name in session history. Profiles store a name plus the selected private IPv4 address or advertised Bluetooth name on that device only; loading a profile never connects automatically, and connecting closes the current session first. The PWA follows one loopback gateway target. Timers and graphs do not send grill commands. Android can request permission for an inexact device-local timer notification; Android may delay it, and reboot or force-stop can cancel it. The PWA can request browser notification permission, but its page timer can be delayed or missed when the page is suspended or closed. Neither alert is a safety alarm. Open-Akorn does not change pit/probe targets; target changes remain behind the physical acceptance and separate authorization gate.

## Optional BLE status monitor
The separately sourced BLE path needs the optional [Bleak](https://bleak.readthedocs.io/) package. On the same computer, install it with `python -m pip install bleak`, then select the exact advertised name observed on your own already-provisioned controller:
```sh
python monitor.py --ble-name 'Akorn-EXACT-NAME'
```
This subscribes only to the documented status-notification characteristic; it does not provision, pair by PIN, or send controller commands. If the controller is not found or the status payload differs, the dashboard stays disconnected. BLE behavior is only mock-tested; no physical device has been checked.

## Cold-grill hardware experiment

Start with the [owner quick-start guide](docs/owner-quickstart.md) for installation, demo, Wi-Fi/BLE and a private acceptance checklist. See the [Deep Research report and pinned upstream comparison](docs/deep-research-report-2026-11-26.md).
Follow [the bench procedure](docs/bench.md). For your own already-provisioned device, close the original app, use the known private LAN IP, and run:
```sh
python monitor.py --ip 192.168.1.50 --frame-size 20
```
Replace the example address with the grill's real address. Do not use a guessed frame size on another model. Stop if readings disagree with the panel. Never port-forward the grill or dashboard.

The local TCP adapter retries after a disconnect. Its loopback simulator test exercises fragmented frames, coalesced frames and a reconnect; this is synthetic transport evidence, not a live-device test.

```sh
python3 -m unittest discover -s tests -v
node --test tests/pwa-sw.test.mjs
(cd android && ./gradlew testDebugUnitTest assembleDebug)
```

See [research and source provenance](docs/research.md), [roadmap](docs/roadmap.md) and [agent instructions](AGENTS.md). A surviving community implementation, [chargrillerd](https://github.com/grzbiet/cg980app/tree/2236faf93f3d2b0663764a0379131d97aef77c2e), already reports Auto AKORN support; use its documented protocol as evidence rather than reinventing it.

## Home Assistant MQTT message adapter (not connected)

[`ha_mqtt.py`](docs/ha-mqtt.md) prepares sensor-only MQTT discovery and availability messages for a future local bridge. Its tests use an in-memory fake publisher. This repository does not connect to a broker, configure credentials, publish to Home Assistant, or integrate the adapter into the monitor process. Measurement messages are non-retained; retained availability makes stale/disconnected probes unavailable instead of reusing an old temperature. The demo is never identified as a live grill.

**Not a safety controller.** No command API, remote fan control, provisioning, unattended fire operation or automatic deployment in this POC. Physical compatibility is unverified. No APK or decompiled source is included.
