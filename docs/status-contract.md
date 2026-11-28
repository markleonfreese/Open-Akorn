# Normalised telemetry contract

Version 1 is the shared meaning of a status sample across the Python gateway, Android app and PWA. The PWA consumes `/api/status`; it does not decode grill frames. The Android app uses the same field names and freshness rules while it owns a direct connection.

```json
{
  "schema_version": 1,
  "model": "akorn-auto",
  "source": "tcp",
  "demo": false,
  "state": "live",
  "age_seconds": 0.2,
  "reading": {
    "pit": {"current_f": 105, "current_c": 40.6, "target_f": 225, "target_c": 107.2},
    "probe1": {"current_f": 69, "current_c": 20.6, "target_f": 96, "target_c": 35.6},
    "probe2": {"current_f": null, "current_c": null, "target_f": null, "target_c": null},
    "alarm": false,
    "fan_mode": "auto",
    "fan_percent": null
  }
}
```

- `source` is `demo`, `tcp`, or `ble`; `demo` is always explicit and can never be interpreted as a connected grill.
- `state` is `waiting`, `connecting`, `live`, `stale`, `disconnected`, or `unavailable`. `reading` is `null` unless the latest valid sample is fresh.
- Current probe sentinel 1000 and target sentinel 4096 map to JSON `null`; a measured zero remains zero. Celsius is derived once from native Fahrenheit and rounded to one decimal place.
- `fan_percent` is present only for the reported manual mode. It is not fan RPM. These values are observed status only, never commands or safety guidance.
- The Python API sends `Cache-Control: no-store`. The PWA service worker may cache only versioned application assets. It must always fetch `/api/status` from the network and clear readings on timeout or loss of the gateway.
- Shared byte-level examples are in [`protocol-fixtures/status.csv`](../protocol-fixtures/status.csv). Their provenance is community protocol documentation plus synthetic variants; none is physical-device evidence.
