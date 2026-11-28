"""Additional read-only regression fixtures; no physical device or broker."""
import json
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from monitor import FRAME, Snapshot, decode, server


class ProtocolFixtureTests(unittest.TestCase):
    def test_all_probe_sentinels_are_distinct_from_zero(self):
        frame = bytearray(FRAME)
        for offset in (0, 6, 10):
            frame[offset:offset + 4] = bytes.fromhex('03e81000')
        result = decode(bytes(frame), 20)
        for probe in ('pit', 'probe1', 'probe2'):
            with self.subTest(probe=probe):
                self.assertIsNone(result[probe]['current_f'])
                self.assertIsNone(result[probe]['current_c'])
                self.assertIsNone(result[probe]['target_f'])
                self.assertIsNone(result[probe]['target_c'])

    def test_alarm_flag_and_reserved_byte(self):
        self.assertFalse(decode(FRAME, 20)['alarm'])
        active = FRAME[:4] + b'\x00' + FRAME[5:]
        self.assertTrue(decode(active, 20)['alarm'])
        with self.assertRaises(ValueError):
            decode(FRAME[:5] + b'\x01' + FRAME[6:], 20)

    def test_example_misaligned_frame_rejected(self):
        # Not proof of universal alignment: raw TCP has no established checksum.
        with self.assertRaises(ValueError):
            decode(b'\xff' + FRAME[:-1], 20)

    def test_expired_snapshot_never_exposes_last_reading(self):
        state = Snapshot()
        state.publish(decode(FRAME, 20), now=100)
        self.assertIsNotNone(state.get(now=101)['reading'])
        self.assertIsNone(state.get(now=106)['reading'])
        state.disconnect()
        self.assertIsNone(state.get(now=101)['reading'])


class ReadOnlyHttpTests(unittest.TestCase):
    def test_loopback_demo_and_post_rejection(self):
        state = Snapshot()
        state.publish(decode(FRAME, 20))
        httpd = server(state, True, 0)
        worker = threading.Thread(target=httpd.serve_forever, daemon=True)
        worker.start()
        base = f'http://127.0.0.1:{httpd.server_port}'
        try:
            self.assertEqual(httpd.server_address[0], '127.0.0.1')
            with urlopen(base + '/api/status', timeout=3) as response:
                payload = json.load(response)
                self.assertTrue(payload['demo'])
                self.assertEqual(payload['schema_version'], 1)
                self.assertEqual(payload['source'], 'demo')
                self.assertNotIn('raw_hex', payload['reading'])
                self.assertEqual(response.headers['Cache-Control'], 'no-store')
            with self.assertRaises(HTTPError) as error:
                urlopen(Request(base + '/api/command', data=b'{}',
                                method='POST'), timeout=3)
            self.assertEqual(error.exception.code, 405)
            error.exception.close()
            state.disconnect()
            with urlopen(base + '/api/status', timeout=3) as response:
                payload = json.load(response)
                self.assertEqual(payload['state'], 'disconnected')
                self.assertIsNone(payload['reading'])
        finally:
            httpd.shutdown()
            httpd.server_close()
            worker.join(3)


if __name__ == '__main__':
    unittest.main()
