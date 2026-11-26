# Open-Akorn

Experimental, local-first companion for the Char-Griller AKORN Auto-Kamado. Preserve the original controller, sensors and safety mechanisms. No account, cloud service or hardware modification is required by this POC.

## Run the simulator (Python 3.11+; standard library only)
```sh
python monitor.py --demo
```
Open http://127.0.0.1:8080. The demo uses a static synthetic sample, not a physical grill. The browser presents Celsius and the JSON endpoint exposes both Celsius and Fahrenheit.

## Optional BLE status monitor
The separately sourced BLE path needs the optional [Bleak](https://bleak.readthedocs.io/) package. On the same computer, install it with `python -m pip install bleak`, then select the exact advertised name observed on your own already-provisioned controller:
```sh
python monitor.py --ble-name 'Akorn-EXACT-NAME'
```
This subscribes only to the documented status-notification characteristic; it does not provision, pair by PIN, or send controller commands. If the controller is not found or the status payload differs, the dashboard stays disconnected. BLE behavior is only mock-tested; no physical device has been checked.

## Cold-grill hardware experiment
Follow [the bench procedure](docs/bench.md). For your own already-provisioned device, close the original app, use the known private LAN IP, and run:
```sh
python monitor.py --ip 192.168.1.50 --frame-size 20
```
Replace the example address with the grill's real address. Do not use a guessed frame size on another model. Stop if readings disagree with the panel. Never port-forward the grill or dashboard.

The local TCP adapter retries after a disconnect. Its loopback simulator test exercises fragmented frames, coalesced frames and a reconnect; this is synthetic transport evidence, not a live-device test.

```sh
python -m unittest discover -s tests -v
```

See [research and source provenance](docs/research.md), [roadmap](docs/roadmap.md) and [agent instructions](AGENTS.md). A surviving community implementation, [chargrillerd](https://github.com/grzbiet/cg980app/tree/2236faf93f3d2b0663764a0379131d97aef77c2e), already reports Auto AKORN support; use its documented protocol as evidence rather than reinventing it.

## Home Assistant MQTT message adapter (not connected)

[`ha_mqtt.py`](docs/ha-mqtt.md) prepares sensor-only MQTT discovery and availability messages for a future local bridge. Its tests use an in-memory fake publisher. This repository does not connect to a broker, configure credentials, publish to Home Assistant, or integrate the adapter into the monitor process. Measurement messages are non-retained; retained availability makes stale/disconnected probes unavailable instead of reusing an old temperature. The demo is never identified as a live grill.

**Not a safety controller.** No command API, remote fan control, provisioning, unattended fire operation or automatic deployment in this POC. Physical compatibility is unverified. No APK or decompiled source is included.
