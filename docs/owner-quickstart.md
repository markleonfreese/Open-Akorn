# Owner quick start: cold AKORN test

**No charcoal, no ignition, no heat.** The prototype is monitoring only, not a safety device. Keep the factory controller and wiring intact. Use the demo before going near the grill; do not test during a cook.

## 1. Install and check the simulator

On macOS, Windows, Linux or Raspberry Pi OS, install Git and Python 3.11 or newer. Python's standard library is enough for the demo. In Terminal (macOS/Linux/Raspberry Pi) or PowerShell (Windows):

```sh
git clone --branch poc/local-monitor --single-branch https://github.com/markleonfreese/Open-Akorn.git
cd Open-Akorn
python3 --version
python3 -m unittest discover -s tests -v
python3 monitor.py --demo
```

On Windows, use `py -3` in place of `python3` for each Python command. Open http://127.0.0.1:8080 in a browser on the same computer. On Raspberry Pi, use a browser running on the Pi; this gateway is loopback-only, so another phone/tablet cannot reach it. Expect SIMULATION, pit 40.6°C, target 107.2°C, meat 1 at 20.6°C and meat 2 disconnected. Press Ctrl+C to stop. If tests fail, record the output and `git rev-parse HEAD` before stopping.

The PWA simulator can also exercise chosen readings, disconnected probes, alarm state and connection loss. Replace the basic `--demo` command with this example to try those states:

```sh
python3 monitor.py --demo --demo-pit-c 110 --demo-pit-target-c 121.1 \
  --demo-probe1-c 21 --demo-probe1-target-c 35.6 \
  --demo-disconnect-probe 2 --demo-alarm
```

Use `--demo-connection-failure` to show startup without telemetry, or `--demo-disconnect-after 20` to show readings disappear after about 20 seconds. Temperatures accept -17.8 to 398.9°C. On Windows replace `python3` with `py -3`. These scenarios are synthetic and apply to the Python/PWA gateway. On Android, tap **Configure simulation**, set Celsius readings, choose probe/alarm/connection states, then tap **Run simulation**. **Try demo** still opens the shared default fixture. No simulation option connects to or changes a grill.

## 2. Install the Android preview

The current owner-preview APK is version `0.1.0` (`versionCode=1`, app id `com.openakorn.app`), debug-signed, 2,856,996 bytes. Download artifact `open-akorn-debug-3e11b0835b2ca5f78ac7121c4bb8fe87b3ab435f` from [successful PR run 46149733487](https://github.com/markleonfreese/Open-Akorn/actions/runs/46149733487). It is built from docs head `3e11b0835b2ca5f78ac7121c4bb8fe87b3ab435f` and contains app source `74aa81f57b52ed159cf6358e6317e243424d2a84`, including the configurable simulator, optional local timer notification, bounded local cook-session graphs and named grill profiles. SHA-256: `029bd4a5d0b7c6f2180f215e6161add5d6c361e9f63a9b44c85b24176e386cc1`. A verified copy on the development Mac is `/Users/markfreese/Downloads/Open-Akorn-debug-0.1.0-3e11b08.apk`. The artifact expires seven days after the run; keep the local copy for the visit. This debug preview is for supervised testing, not a release build.

If an earlier Open-Akorn preview is already installed, uninstall it first in Android Settings → Apps → Open-Akorn → Uninstall. CI signs each debug APK with a different temporary key, so a newer preview cannot update the older install in place. The demo does not save cooking history or connection settings. Live sessions are saved locally on this device, up to 8 sessions with samples about once per minute; use **View local sessions** and **Clear history** to review or delete them. Uninstalling the app removes its private session data. Copy the current APK to the owner-controlled phone/tablet (for example by USB), open it in Files, review Android's install prompt and install the package. This is a debug preview, not a Play Store release. Start with **Try demo**. Do not use older `27d9e56` or `d7e2a8f` APK copies for connection testing; they predate the BLE permission-revocation fix.

The APK supports Android 10/API 29 and newer. The Wi-Fi option uses the controller's existing local network connection: find its IP in the router device list; Open-Akorn does not provision Wi-Fi or request a password. TCP asks for a manually entered private IPv4 address (for example, `192.168.1.50`) and has no runtime permission prompt. For Bluetooth, tap **Find nearby AKORN devices** to scan for up to eight seconds; choose the advertised `Akorn-*` name in the list to connect. The scan does not connect automatically or show device addresses. If discovery is unavailable, enter the exact advertised name in the manual field and tap **Connect Bluetooth**. BLE requests Nearby devices when first used on Android 12+, or Location permission for BLE scanning on Android 10–11. Denying permission leaves BLE disconnected. The app disconnects when backgrounded; reopen it and tap Connect again. Use one connection at a time. Live cook sessions are retained locally on this device, up to 8 sessions with samples about once per minute; **View local sessions** opens their graphs and **Clear history** deletes them. Simulation is excluded. Starting a cook timer may request notification permission on Android 13+. If allowed, an inexact local alert may fire; Android may delay it, and reboot or force-stop can cancel it. It is only a convenience reminder, not a safety alarm.

To save a grill on this device, enter its private Wi-Fi IP or Bluetooth advertised name, tap **Save this Wi-Fi setup** or **Save this Bluetooth setup**, and give it a label. **Saved grills** loads the selected details into the connection form; tap the matching **Connect** button to switch. Selecting a profile never connects by itself, and connecting closes the current session first. Up to 12 profiles are stored locally; they do not transfer to another device or survive uninstall. The app does not store Bluetooth addresses. Phone/tablet profile UI, discovery, install flow, permissions and performance have not yet been checked on target devices.

To build the latest PR source locally, install JDK 17+ and Android SDK Platform 36, accept that platform's SDK licence in the SDK Manager, then build and run fixture tests. On this Mac, Gradle could not build locally because no Android SDK is installed; no SDK licence was accepted.

```sh
cd android
./gradlew testDebugUnitTest assembleDebug
```

The debug-signed APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. Use **Try demo** first after installing on an owner-controlled phone or tablet. The app disconnects when backgrounded and must be reconnected when reopened. It permits one direct TCP or BLE session at a time. For actual grill testing, continue only with the cold-grill procedure below and the factory controller unchanged.

## 3. Try the existing Wi-Fi connection first

Power the cold controller on. Record model and firmware if visible. If already connected to home Wi-Fi, find its address in your own router's connected devices list. Close the original app; only one TCP client is reportedly supported. Do not scan the subnet or change router settings.

```sh
python3 monitor.py --ip 192.168.1.50 --frame-size 20
```

Replace the example IP with the actual private address. Open the same browser URL. If the monitor cannot connect or values differ from the controller, stop and record what happened. Do not guess alternate frame sizes or issue commands.

## 4. Bluetooth fallback

If Wi-Fi is unavailable, use Bluetooth on the laptop. Install the optional dependency into a virtual environment:

```sh
python3 -m venv .venv
```

Activate it (macOS/Linux: run `source .venv/bin/activate`; Windows PowerShell: run `.\.venv\Scripts\Activate.ps1`, or use `.\.venv\Scripts\python.exe` directly). Then:

```sh
python3 -m pip install bleak
python3 -c "import asyncio; from bleak import BleakScanner; print(asyncio.run(BleakScanner.discover(timeout=8)))"
```

On Windows, use `py -3` in place of `python3`. This calls Bleak's documented async scanner API ([Bleak scanner documentation](https://bleak.readthedocs.io/en/stable/api/scanner.html)). BLE support depends on the computer's adapter and OS; current Tier 1 support is Linux with BlueZ 5.55+, macOS 10.15+, and Windows 11 ([Bleak platform support](https://bleak.readthedocs.io/en/stable/)). Find the exact `Akorn-*` advertisement name in the output; keep device addresses private. If scanning fails, stop and use the OS Bluetooth settings or a BLE scanner. Do not pair with unknown devices or enter network credentials. Grant normal Bluetooth permissions if prompted.

```sh
python3 monitor.py --ble-name 'Akorn-EXACT-NAME'
```

Replace the example with the exact name observed. This subscribes to status notifications only; it does not provision or send grill commands. If no telemetry appears, stop. BLE reconnection currently requires restarting the monitor. No physical BLE session has been verified.

## 5. Cold-grill checklist and results

Use this checklist with the full procedure in [bench.md](bench.md). Keep the grill cold and the factory controller unchanged. Stop at any mismatch; do not add fuel or ignite.

- [ ] Record model, firmware, Android version, phone/tablet model, and test commit; keep identifiers private.
- [ ] Start with **Try demo**, confirm SIMULATION is clear, and verify both Celsius and disconnected-probe display.
- [ ] Choose one transport (TCP or BLE); close the official app and other monitor clients first.
- [ ] Compare 30+ consecutive pit/probe readings and targets with the panel. Change a target on the physical controller only and check the reported value follows.
- [ ] Unplug a probe only while cold; confirm its reading becomes unavailable. Check natural alarm/fan status only; do not provoke a fault.
- [ ] Stop the monitor or disconnect the link; confirm readings clear. Reconnect manually and record startup/reconnect behavior.
- [ ] Confirm the factory controller remains usable and unchanged. Do not proceed past any disagreement.
- [ ] For PWA checks, stop the local gateway and confirm temperatures disappear. Test offline app-shell startup separately; the PWA on a laptop only serves that laptop.

Copy the table into a private note and share only sanitised observations. Keep MAC/serial/IP, SSID, passwords and raw private captures out of public reports.

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

The dashboard is an installable PWA on supported browsers. On the same computer, start a demo monitor with `python3 monitor.py --demo --port 18080` (on Windows use `py -3 monitor.py --demo --port 18080`), then open `http://127.0.0.1:18080/` and choose **Install app** in the page or Chrome's install icon. Launch **Open-Akorn Local Monitor** from the installed-app list. Chrome associates the installed app with its origin and port, so use the same port when reopening it. Verify the demo appears, stop the monitor and confirm readings clear to blank/unknown, then restart it on the same port and confirm fresh demo readings return. The service worker caches the app shell only. If the local gateway is unreachable, current temperatures disappear rather than showing old values. The monitor binds to `127.0.0.1`, so the PWA works on the same computer only; private-network phone/tablet access requires a separately approved authenticated HTTPS design.

## 6. Stop

Ctrl+C closes the monitor. Confirm the original controller remains usable. If the controller is not provisioned and BLE-only fails, stop and report the result; provisioning is a separate owner-approved operation, not part of this test.
