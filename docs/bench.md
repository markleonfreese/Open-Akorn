# Cold-grill verification

No fuel, ignition or heat. Adult owner present. Keep factory wiring, firmware and interlocks unchanged. Test on a trusted private LAN; no inbound internet port forwarding. Do not use prototype readings for safety decisions.

1. Record exact model and firmware, display unit, controller label and observed BLE advertisement. Keep unique identifiers private.
2. Power original controller on cold; pair with official app if it still works. Identify IP from router DHCP lease list. Close official app; only one TCP client reportedly supported. Use a DHCP reservation if needed.
3. Run demo and tests first. Then run monitor.py --ip <known-private-ip> --frame-size 20; capture sanitised raw_hex samples from /api/status alongside panel readings. If TCP cannot connect, stop and investigate network/provisioning with BLE; do not blindly scan other people's networks.
4. Confirm at least 30 consecutive samples and both probe values/targets, unplugged meat probe sentinel, alarm indications if naturally present, and Celsius conversion. Change target on the physical dial only, with no fire; confirm telemetry follows it. No app writes. Check whether fan mode/speed changes naturally; do not deliberately trigger fault/overheat.
5. Close the monitor and confirm factory controls unchanged. Re-run with internet access to grill blocked on your own network (without changing shared DNS/security policy); document whether local telemetry persists. Power-cycle cold and check reconnect. Disconnect network and verify stale values disappear.
6. Record exact lengths, capture timing, expected vs actual observations, and any discrepancies. If 20-byte interpretation fails, record raw stream externally and establish boundaries before retrying; do not silently interpret as 16 bytes.
7. If not provisioned, inspect upstream BLE workflow first; ask for physical owner interaction and use actual PIN as required. Capture only status or sanitised metadata; Bluetooth captures can include Wi-Fi credentials. Do not publish captures containing passwords, local identifiers or private network metadata.

Pass gate: model verified, frame interpretation matches panel, no unintended device changes, reconnect and stale handling verified, operates offline. Any mismatch blocks use as live monitoring. Control and Home Assistant writes are separate explicitly approved work; no automated operation of the grill.
