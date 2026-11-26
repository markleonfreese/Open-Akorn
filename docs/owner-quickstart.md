# Owner quick start: cold AKORN test

**No charcoal, no ignition, no heat.** The prototype is monitoring only, not a safety device. Use a laptop near the grill. Keep the factory controller and wiring intact. Do not use a live cook to test this.

## 1. Install and check the demo

Install Python 3.11 or newer and Git if not already installed. In Terminal (macOS/Linux) or PowerShell (Windows):

```sh
git clone -b poc/local-monitor https://github.com/markleonfreese/Open-Akorn.git
cd Open-Akorn
python --version
python -m unittest discover -s tests -v
python monitor.py --demo
```

Use python3 instead of python if your system requires it, or py -3 on Windows. Open http://127.0.0.1:8080 on the same laptop. Expect SIMULATION, pit 40.6°C, target 107.2°C, meat 1 20.6°C and meat 2 not connected. Press Ctrl+C to stop. If tests fail, do not proceed; record the output and commit (git rev-parse HEAD).

## 2. Try the existing Wi-Fi connection first

Power the cold controller on. Record model and firmware if visible. If already connected to home Wi-Fi, find its address in your own router's connected devices list. Close the original app; only one TCP client is reportedly supported. Do not scan the subnet or change router settings.

```sh
python monitor.py --ip 192.168.1.50 --frame-size 20
```

Replace the example IP with the actual private address. Open the same browser URL. If the monitor cannot connect or values differ from the controller, stop and record what happened. Do not guess alternate frame sizes or issue commands.

## 3. Bluetooth fallback

If Wi-Fi is unavailable, use Bluetooth on the laptop. Install the optional dependency into a virtual environment:

```sh
python -m venv .venv
```

Activate it (macOS/Linux: source .venv/bin/activate; Windows PowerShell: .venv\\Scripts\\Activate.ps1, or use .venv\\Scripts\\python.exe directly). Then:

```sh
python -m pip install bleak
python -m bleak
```

The Bleak CLI may vary by installed version; if discovery does not work, use the OS Bluetooth settings or a BLE scanner to identify the exact Akorn-* advertisement. Do not pair with unknown devices or enter network credentials. Grant normal Bluetooth permissions if prompted.

```sh
python monitor.py --ble-name 'Akorn-EXACT-NAME'
```

Replace the example with the exact name observed. This subscribes to status notifications only; it does not provision or send grill commands. If no telemetry appears, stop. BLE reconnection currently requires restarting the monitor. No physical BLE session has been verified.

## 4. Record the results

Use [bench.md](bench.md) for the full gate. Observe 30 consecutive readings. Compare pit current/target, both meat probes, unplugged probes and targets changed using the physical controller only. Confirm stale/disconnected state hides readings. If authorised to manage your own network, test offline LAN operation without changing shared security/DNS settings. Power-cycle cold, and check reconnect. Stop if anything differs.

Copy this table into a private note. Do not include MAC, serial, SSID, passwords, full network captures or private IP in a public issue.

| Check | Observed / pass / fail |
|---|---|
| Exact model and firmware if visible | |
| Test commit and Python version | |
| TCP or BLE | |
| 30+ consecutive samples match panel | |
| Probe 1 and 2 current/target match | |
| Unplugged probes reported unavailable | |
| Physical target change follows telemetry | |
| Network loss hides readings | |
| Cold power cycle/reconnect | |
| Offline LAN operation | |
| Original controller unchanged | |
| Errors / discrepancies | |

For a single current reading, open http://127.0.0.1:8080/api/status. The raw_hex field is status telemetry; share only sanitised status samples after reviewing them. No raw HCI or network captures in Git. Do not treat the dashboard as safety guidance. No controls are enabled.

## 5. Stop

Ctrl+C closes the monitor. Confirm the original controller remains usable. If the controller is not provisioned and BLE-only fails, stop and report the result; provisioning is a separate owner-approved operation, not part of this test.
