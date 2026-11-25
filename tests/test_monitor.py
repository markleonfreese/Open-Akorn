import unittest
from monitor import decode, read_frames, Snapshot
from io import BytesIO

# Community-documented core example plus synthetic AKORN fan extension.
FRAME = bytes.fromhex('006900e110000045006003e81000100010321100')

class MonitorTests(unittest.TestCase):
    def test_temperature_and_sentinels(self):
        v = decode(FRAME, 20)
        self.assertEqual(v['pit']['current_c'], 40.6)
        self.assertEqual(v['pit']['target_f'], 225)
        self.assertIsNone(v['probe2']['current_c'])
        self.assertIsNone(v['probe2']['target_c'])
        self.assertEqual(v['fan_mode'], 'auto')
        self.assertIsNone(v['fan_percent'])

    def test_manual_fan(self):
        v = decode(FRAME[:16] + bytes([0, 50, 17, 0]), 20)
        self.assertEqual(v['fan_percent'], 50)

    def test_legacy_does_not_invent_fan_mode(self):
        self.assertIsNone(decode(FRAME[:16], 16)['fan_mode'])

    def test_wrong_size_and_bad_header(self):
        for frame in (FRAME[:-1], FRAME + b'x', FRAME[:4] + b'\x99' + FRAME[5:]):
            with self.assertRaises(ValueError):
                decode(frame, 20)

    def test_unknown_temperature_rejected(self):
        with self.assertRaises(ValueError):
            decode(b'\xff\xff' + FRAME[2:], 20)

    def test_manual_speed_rejected(self):
        with self.assertRaises(ValueError):
            decode(FRAME[:16] + bytes([0, 101, 17, 0]), 20)

    def test_stream_coalescing_and_truncation(self):
        self.assertEqual(list(read_frames(BytesIO(FRAME * 2), 20)), [FRAME, FRAME])
        with self.assertRaises(EOFError):
            list(read_frames(BytesIO(FRAME[:-1]), 20))

    def test_stream_fragmentation(self):
        class Fragmented(BytesIO):
            def read(self, size):
                return super().read(min(size, 3))
        self.assertEqual(list(read_frames(Fragmented(FRAME), 20)), [FRAME])

    def test_stale_and_disconnected_never_live(self):
        state = Snapshot()
        self.assertEqual(state.get()['state'], 'waiting')
        state.publish(decode(FRAME, 20), 100)
        self.assertEqual(state.get(101)['state'], 'live')
        self.assertEqual(state.get(106)['state'], 'stale')
        state.disconnect()
        self.assertEqual(state.get(101)['state'], 'disconnected')

if __name__ == '__main__':
    unittest.main()
