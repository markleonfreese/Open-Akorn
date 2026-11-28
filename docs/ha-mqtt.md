# Home Assistant MQTT message adapter

`ha_mqtt.py` is a read-only, transport-agnostic message adapter. It returns Home Assistant sensor discovery messages and telemetry/availability messages for a future caller-provided MQTT publisher. It has no MQTT package, broker connection, configuration, credentials, subscription or runtime wiring. The tests use an in-memory fake publisher only.

## Message contract

- Each entity uses three availability topics: gateway, physical device and that sensor. Home Assistant requires all three (`availability_mode: all`) before showing an entity as available. This distinguishes a stopped gateway from a disconnected grill and an unplugged probe.
- Discovery configuration and availability are retained. Temperature values are never retained. When telemetry is stale, disconnected, simulated or malformed, the adapter publishes offline availability and no numeric reading; it never publishes `0` as a fallback.
- Demo data cannot mark the grill or any probe online. The fixed sensor set contains temperatures and observational targets only. There are no command topics, switches, control entities or write calls.
- IDs are stable operator-chosen topic segments. Do not use IP addresses, MAC addresses, passwords or other private identifiers.
- The returned retained gateway `offline` message is suitable for an MQTT Last Will. A future client must configure it as its Will and publish `online` only after startup. This module does not create that connection.
- One gateway must own the existing TCP reader. The message adapter does not open another TCP socket.

Home Assistant documents retained discovery, multiple availability topics and `availability_mode` in the [MQTT integration reference](https://www.home-assistant.io/integrations/mqtt/). The warning about retained state is why this adapter does not retain measurements.

## Verification boundary

The tests verify message shape, retain flags, availability semantics, sentinels, invalid values and demo suppression against synthetic monitor snapshots. They do not verify an MQTT broker, Home Assistant entity creation, network permissions, a real grill, device compatibility or safe use. A real broker client and physical cold-grill validation remain separate, owner-approved steps.
