# Owner quick start: cold AKORN test

**No charcoal, no ignition, no heat.** The prototype is monitoring only, not a safety device. Use a laptop near the grill. Keep the factory controller and wiring intact. Do not use a live cook to test this.

## 1. Install and check the demo

Install Python 3.11 or newer and Git if not already installed. In Terminal (macOS/Linux) or PowerShell (Windows):

```sh
git clone -b poc/local-monitor https://github.com/markleonfreese/Open-Akorn.git
cd Open-Akorn
python3 --version
python3 -m unittest discover -s tests -v
python3 monitor.py --demo
```

On Windows use `py -3` in place of `python3`. Open http://127.0.0.1:8080 on the same laptop. Expect SIMULATION, pit 40.6°C, target 107.2°C, meat 1 20.6°C and meat 2 not connected. Press Ctrl+C to stop. If tests fail, do not proceed; record the output and commit (`git rev-parse HEAD`).

## 1a. Build the Android preview

Install JDK 17+ and Android SDK Platform 36, accept its Android SDK license in the SDK Manager, then build the native app and its fixture tests:

```sh
cd android
./gradlew testDebugUnitTest assembleDebug
```

The debug-signed APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. Use **Try demo** first after installing on an owner-controlled phone or tablet. The app disconnects when backgrounded and must be reconnected when reopened. It permits one direct TCP or BLE session at a time. For actual grill testing, continue only with the cold-grill procedure below and the factory controller unchanged.

## 2. Try the existing Wi-Fi connection first

Power the cold controller on. Record model and firmware if visible. If already connected to home Wi-Fi, find its address in your own router's connected devices list. Close the original app; only one TCP client is reportedly supported. Do not scan the subnet or change router settings.

```sh
python3 monitor.py --ip 192.168.1.50 --frame-size 20
```

Replace the example IP with the actual private address. Open the same browser URL. If the monitor cannot connect or values differ from the controller, stop and record what happened. Do not guess alternate frame sizes or issue commands.

## 3. Bluetooth fallback

If Wi-Fi is unavailable, use Bluetooth on the laptop. Install the optional dependency into a virtual environment:

```sh
python3 -m venv .venv
```

Activate it (macOS/Linux: source .venv/bin/activate; Windows PowerShell: .venv\\Scripts\\Activate.ps1, or use .venv\\Scripts\\python.exe directly). Then:

```sh
python3 -m pip install bleak
python3 -m bleak
```

The Bleak CLI may vary by installed version; if discovery does not work, use the OS Bluetooth settings or a BLE scanner to identify the exact Akorn-* advertisement. Do not pair with unknown devices or enter network credentials. Grant normal Bluetooth permissions if prompted.

```sh
python3 monitor.py --ble-name 'Akorn-EXACT-NAME'
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

The dashboard is an installable PWA on supported browsers. It caches the app shell only. If the local gateway is unreachable, current temperatures disappear rather than showing old values. The monitor binds to `127.0.0.1`, so the PWA works on the same computer only; private-network phone/tablet access requires a separately approved authenticated HTTPS design.

## 5. Stop

Ctrl+C closes the monitor. Confirm the original controller remains usable. If the controller is not provisioned and BLE-only fails, stop and report the result; provisioning is a separate owner-approved operation, not part of this test.
