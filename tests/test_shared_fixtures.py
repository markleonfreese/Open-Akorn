"""The same language-neutral byte fixtures are consumed by Python and Android."""
import csv
import unittest
from pathlib import Path

from monitor import decode, decode_ble_status_notification


FIXTURES = Path(__file__).resolve().parents[1] / 'protocol-fixtures' / 'status.csv'


class SharedFixtureTests(unittest.TestCase):
    def test_all_shared_status_fixtures(self):
        with FIXTURES.open(newline='', encoding='utf-8') as handle:
            rows = list(csv.DictReader(handle))
        self.assertEqual(len(rows), 7)
        self.assertTrue(all(None not in row for row in rows))

        for fixture in rows:
            with self.subTest(fixture=fixture['name']):
                payload = bytes.fromhex(fixture['payload_hex'])
                if fixture['valid'] != 'true':
                    with self.assertRaises(ValueError):
                        self.decode_fixture(fixture, payload)
                    continue

                reading = self.decode_fixture(fixture, payload)
                self.assertEqual(self.integer(fixture['pit_current_f']), reading['pit']['current_f'])
                self.assertEqual(self.integer(fixture['pit_target_f']), reading['pit']['target_f'])
                self.assertEqual(self.integer(fixture['probe1_current_f']), reading['probe1']['current_f'])
                self.assertEqual(self.integer(fixture['probe1_target_f']), reading['probe1']['target_f'])
                self.assertEqual(self.integer(fixture['probe2_current_f']), reading['probe2']['current_f'])
                self.assertEqual(self.integer(fixture['probe2_target_f']), reading['probe2']['target_f'])
                self.assertEqual(fixture['alarm_active'] == 'true', reading['alarm'])
                self.assertEqual(fixture['fan_mode'] or None, reading['fan_mode'])
                self.assertEqual(self.integer(fixture['fan_percent']), reading['fan_percent'])

    @staticmethod
    def decode_fixture(fixture, payload):
        if fixture['transport'] == 'ble':
            return decode_ble_status_notification(payload)
        return decode(payload, int(fixture['frame_size']))

    @staticmethod
    def integer(value):
        return int(value) if value else None


if __name__ == '__main__':
    unittest.main()
