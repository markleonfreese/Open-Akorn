import json
import unittest

from ha_mqtt import HomeAssistantMqttAdapter
from monitor import FRAME, Snapshot, decode


def live_snapshot(now=10):
    snapshot = Snapshot()
    snapshot.publish(decode(FRAME, 20), now=now)
    return snapshot.get(now=now + 1)


class HomeAssistantMqttTests(unittest.TestCase):
    def setUp(self):
        self.adapter = HomeAssistantMqttAdapter("patio-grill", "local-gateway")

    def test_discovery_is_retained_sensor_only_and_requires_all_availability(self):
        configs = self.adapter.discovery()
        self.assertEqual(len(configs), 6)
        self.assertTrue(all(message.retain for message in configs))
        for message in configs:
            self.assertTrue(message.topic.startswith("homeassistant/sensor/"))
            config = json.loads(message.payload)
            self.assertEqual(config["availability_mode"], "all")
            self.assertEqual(len(config["availability"]), 3)
            self.assertIn("state_topic", config)
            self.assertNotIn("command_topic", config)
            self.assertNotIn("value_template", config)
            self.assertEqual(config["unit_of_measurement"], "°C")

    def test_live_values_are_ephemeral_and_probe_sentinels_stay_unavailable(self):
        messages = self.adapter.state(live_snapshot())
        state_messages = [m for m in messages if "/state/" in m.topic]
        availability = {m.topic.rsplit("/", 1)[-1]: m.payload for m in messages if "/availability/" in m.topic}
        self.assertTrue(state_messages)
        self.assertTrue(all(not m.retain for m in state_messages))
        self.assertIn("pit_current", availability)
        self.assertEqual(availability["probe2_current"], "offline")
        self.assertFalse(any("raw_hex" in m.payload for m in state_messages))
        self.assertNotIn("/command", " ".join(m.topic for m in messages))

    def test_stale_or_disconnected_snapshot_sends_availability_only(self):
        for snapshot in (
            {"state": "stale", "age_seconds": 6, "reading": None},
            {"state": "disconnected", "age_seconds": 1, "reading": None},
            {"state": "waiting", "age_seconds": None, "reading": None},
        ):
            with self.subTest(state=snapshot["state"]):
                messages = self.adapter.state(snapshot)
                self.assertFalse(any("/state/" in m.topic for m in messages))
                self.assertEqual(messages[1].payload, "offline")
                self.assertTrue(all(m.retain for m in messages))
                self.assertTrue(all(m.payload == "offline" for m in messages[1:]))

    def test_demo_data_never_marks_a_grill_online_or_publishes_temperature(self):
        messages = self.adapter.state(live_snapshot(), demo=True)
        self.assertFalse(any("/state/" in m.topic for m in messages))
        self.assertEqual(messages[1].payload, "offline")
        self.assertTrue(all(m.payload == "offline" for m in messages[1:]))

    def test_gateway_and_device_availability_are_independent(self):
        messages = self.adapter.state(live_snapshot(), gateway_online=False)
        by_topic = {m.topic: m.payload for m in messages if m.topic.endswith("/availability") or "/availability/" in m.topic}
        self.assertEqual(by_topic[self.adapter.gateway_availability], "offline")
        self.assertEqual(by_topic[self.adapter.device_availability], "online")
        self.assertFalse(any("/state/" in m.topic for m in messages))

    def test_invalid_sensor_values_are_unavailable_not_zero(self):
        snapshot = live_snapshot()
        reading = dict(snapshot["reading"])
        reading["pit"] = dict(reading["pit"], current_c=float("inf"))
        snapshot["reading"] = reading
        messages = self.adapter.state(snapshot)
        self.assertNotIn("/state/pit_current", " ".join(m.topic for m in messages))
        pit_availability = next(m for m in messages if m.topic.endswith("/availability/pit_current"))
        self.assertEqual(pit_availability.payload, "offline")

    def test_gateway_last_will_is_retained_offline(self):
        self.assertEqual(self.adapter.last_will.topic, self.adapter.gateway_availability)
        self.assertEqual(self.adapter.last_will.payload, "offline")
        self.assertTrue(self.adapter.last_will.retain)

    def test_fake_broker_observes_only_retained_config_and_availability(self):
        broker_messages = []
        publisher = lambda topic, payload, *, retain: broker_messages.append((topic, payload, retain))
        self.adapter.publish(publisher, self.adapter.discovery())
        self.adapter.publish(publisher, self.adapter.state(live_snapshot()))
        state = [entry for entry in broker_messages if "/state/" in entry[0]]
        availability = [entry for entry in broker_messages if "/availability" in entry[0]]
        configs = [entry for entry in broker_messages if entry[0].startswith("homeassistant/")]
        self.assertTrue(configs and availability and state)
        self.assertTrue(all(entry[2] for entry in configs + availability))
        self.assertTrue(all(not entry[2] for entry in state))

    def test_identifiers_cannot_inject_mqtt_topic_segments(self):
        for bad_id in ("", "../other", "a/b", "a+", "a#", "192.168.1.2"):
            with self.subTest(device_id=bad_id), self.assertRaises(ValueError):
                HomeAssistantMqttAdapter(bad_id)


if __name__ == "__main__":
    unittest.main()
