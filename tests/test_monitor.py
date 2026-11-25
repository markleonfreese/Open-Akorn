import unittest
import asyncio
import socket
import threading
import time
from io import BytesIO
from types import SimpleNamespace

from monitor import (BLE_STATUS_HEADER, BLE_STATUS_UUID, FRAME, Snapshot,
                     decode, decode_ble_status_notification, read_frames,
                     receive, receive_ble)

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

    def test_ble_notification_requires_documented_wrapper(self):
        value = decode_ble_status_notification(BLE_STATUS_HEADER + FRAME)
        self.assertEqual(value, decode(FRAME, 20))
        for payload in (FRAME, BLE_STATUS_HEADER + FRAME[:-1],
                        b'\xff' * 4 + FRAME):
            with self.subTest(payload=payload.hex()), self.assertRaises(ValueError):
                decode_ble_status_notification(payload)

    def test_stale_and_disconnected_never_live(self):
        state = Snapshot()
        self.assertEqual(state.get()['state'], 'waiting')
        state.publish(decode(FRAME, 20), 100)
        self.assertEqual(state.get(101)['state'], 'live')
        self.assertEqual(state.get(106)['state'], 'stale')
        state.disconnect()
        self.assertEqual(state.get(101)['state'], 'disconnected')

class TransportTests(unittest.TestCase):
    def test_tcp_fragmentation_coalescing_and_reconnect(self):
        listener = socket.socket()
        listener.bind(('127.0.0.1', 0))
        listener.listen()
        listener.settimeout(3)
        port = listener.getsockname()[1]
        stop = threading.Event()
        published = threading.Event()
        accepted = []
        server_errors = []

        class CountingSnapshot(Snapshot):
            def __init__(self):
                super().__init__()
                self.count = 0

            def publish(self, value, now=None):
                super().publish(value, now)
                self.count += 1
                if self.count == 3:
                    published.set()
                    stop.set()

        state = CountingSnapshot()

        def fake_device():
            try:
                for index in range(2):
                    conn, _ = listener.accept()
                    accepted.append(index)
                    with conn:
                        if index == 0:
                            conn.sendall(FRAME[:7])
                            time.sleep(0.01)
                            conn.sendall(FRAME[7:])
                        else:
                            conn.sendall(FRAME * 2)
            except Exception as exc:
                server_errors.append(exc)
            finally:
                listener.close()

        server_thread = threading.Thread(target=fake_device, daemon=True)
        receiver_thread = threading.Thread(
            target=receive,
            args=('127.0.0.1', 20, state),
            kwargs={'stop_event': stop, 'retry_delay': 0, 'port': port},
            daemon=True,
        )
        server_thread.start()
        receiver_thread.start()
        self.assertTrue(published.wait(3), 'receiver did not process three frames')
        receiver_thread.join(3)
        server_thread.join(3)
        self.assertFalse(receiver_thread.is_alive())
        self.assertFalse(server_thread.is_alive())
        self.assertEqual(accepted, [0, 1])
        self.assertEqual(server_errors, [])
        self.assertEqual(state.count, 3)
        self.assertEqual(state.value, decode(FRAME, 20))
        self.assertEqual(state.get()['state'], 'disconnected')


class BleTests(unittest.IsolatedAsyncioTestCase):
    async def test_named_ble_subscription_is_status_read_only(self):
        state = Snapshot()
        calls = {}

        class FakeScanner:
            @staticmethod
            async def find_device_by_name(name, timeout):
                calls['scan'] = (name, timeout)
                return object()

        class FakeClient:
            def __init__(self, device, disconnected_callback):
                calls['device'] = device
                calls['disconnected_callback'] = disconnected_callback

            async def __aenter__(self):
                return self

            async def __aexit__(self, *_):
                return False

            async def start_notify(self, characteristic, callback):
                calls['characteristic'] = characteristic
                callback(characteristic, BLE_STATUS_HEADER + FRAME)
                calls['disconnected_callback'](self)

        api = SimpleNamespace(BleakScanner=FakeScanner, BleakClient=FakeClient)
        await receive_ble('Akorn-test-device', state, bleak_api=api,
                          scan_timeout=2.5)
        self.assertEqual(calls['scan'], ('Akorn-test-device', 2.5))
        self.assertEqual(calls['characteristic'], BLE_STATUS_UUID)
        self.assertEqual(state.value, decode(FRAME, 20))
        self.assertEqual(state.get()['state'], 'disconnected')
        # FakeClient deliberately exposes no write method: this path only subscribes.

    async def test_ble_missing_device_disconnects_without_connecting(self):
        state = Snapshot()

        class FakeScanner:
            @staticmethod
            async def find_device_by_name(name, timeout):
                return None

        api = SimpleNamespace(BleakScanner=FakeScanner)
        with self.assertRaisesRegex(OSError, 'BLE device not found'):
            await receive_ble('Akorn-missing', state, bleak_api=api)
        self.assertEqual(state.get()['state'], 'disconnected')


if __name__ == '__main__':
    unittest.main()
