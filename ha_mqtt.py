"""Transport-agnostic, read-only Home Assistant MQTT message adapter.

This module builds messages for an injected publisher; it does not connect to
an MQTT broker or subscribe to command topics.
"""

from dataclasses import dataclass
import json
import math
import re
from collections.abc import Callable, Mapping


FRESHNESS_SECONDS = 5


@dataclass(frozen=True)
class Publication:
    topic: str
    payload: str
    retain: bool


SENSORS = (
    ("pit_current", "Pit temperature", "pit", "current_c"),
    ("pit_target", "Pit target", "pit", "target_c"),
    ("probe1_current", "Meat probe 1", "probe1", "current_c"),
    ("probe1_target", "Meat probe 1 target", "probe1", "target_c"),
    ("probe2_current", "Meat probe 2", "probe2", "current_c"),
    ("probe2_target", "Meat probe 2 target", "probe2", "target_c"),
)


def _topic_segment(value, label):
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,48}", value):
        raise ValueError(f"{label} must be a stable non-secret MQTT topic segment")
    return value


def _number(value):
    return (
        isinstance(value, (int, float))
        and not isinstance(value, bool)
        and math.isfinite(value)
    )


class HomeAssistantMqttAdapter:
    """Build discovery, availability and ephemeral temperature messages."""

    def __init__(self, device_id="akorn", gateway_id="open-akorn", discovery_prefix="homeassistant"):
        self.device_id = _topic_segment(device_id, "device_id")
        self.gateway_id = _topic_segment(gateway_id, "gateway_id")
        self.discovery_prefix = _topic_segment(discovery_prefix, "discovery_prefix")
        self.base = f"open_akorn/{self.gateway_id}/{self.device_id}"
        self.gateway_availability = f"{self.base}/gateway/availability"
        self.device_availability = f"{self.base}/device/availability"

    @property
    def last_will(self):
        """Retained gateway-offline message for a future, operator-set client."""
        return Publication(self.gateway_availability, "offline", True)

    def discovery(self):
        """Return retained sensor-only Home Assistant discovery configs."""
        publications = []
        for sensor_id, name, _, _ in SENSORS:
            state_topic = f"{self.base}/state/{sensor_id}"
            sensor_availability = f"{self.base}/availability/{sensor_id}"
            unique_id = f"open_akorn_{self.gateway_id}_{self.device_id}_{sensor_id}"
            config = {
                "name": name,
                "unique_id": unique_id,
                "state_topic": state_topic,
                "device_class": "temperature",
                "unit_of_measurement": "°C",
                "availability": [
                    {"topic": self.gateway_availability},
                    {"topic": self.device_availability},
                    {"topic": sensor_availability},
                ],
                "availability_mode": "all",
                "payload_available": "online",
                "payload_not_available": "offline",
                "device": {
                    "identifiers": [f"open_akorn_{self.gateway_id}_{self.device_id}"],
                    "name": f"Open-Akorn {self.device_id}",
                    "manufacturer": "Char-Griller",
                    "model": "AKORN Auto-Kamado (read-only monitor)",
                },
            }
            topic = f"{self.discovery_prefix}/sensor/{unique_id}/config"
            publications.append(Publication(
                topic, json.dumps(config, sort_keys=True, separators=(",", ":")), True,
            ))
        return tuple(publications)

    def state(self, snapshot, *, gateway_online=True, demo=False):
        """Return availability and fresh state messages for one monitor snapshot.

        Availability is retained. Measurement values are never retained, and
        absent, malformed, stale or simulated values are never sent as zero.
        """
        if not isinstance(gateway_online, bool) or not isinstance(demo, bool):
            raise ValueError("gateway_online and demo must be booleans")
        snapshot = snapshot if isinstance(snapshot, Mapping) else {}
        reading = snapshot.get("reading")
        age = snapshot.get("age_seconds")
        fresh = (
            not demo
            and snapshot.get("state") == "live"
            and _number(age)
            and 0 <= age <= FRESHNESS_SECONDS
            and isinstance(reading, Mapping)
        )

        publications = [
            Publication(self.gateway_availability, "online" if gateway_online else "offline", True),
            Publication(self.device_availability, "online" if fresh else "offline", True),
        ]
        for sensor_id, _, probe, field in SENSORS:
            value = reading.get(probe, {}).get(field) if fresh and isinstance(reading.get(probe), Mapping) else None
            available = fresh and _number(value)
            publications.append(Publication(
                f"{self.base}/availability/{sensor_id}",
                "online" if available else "offline",
                True,
            ))
            if available and gateway_online:
                publications.append(Publication(
                    f"{self.base}/state/{sensor_id}", format(value, ".1f"), False,
                ))
        return tuple(publications)

    @staticmethod
    def publish(publisher: Callable, publications):
        """Send a prepared message batch through a caller-provided publisher."""
        for message in publications:
            publisher(message.topic, message.payload, retain=message.retain)

