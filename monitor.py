"""Experimental read-only local monitor. No grill command path."""
import argparse
import asyncio
import ipaddress
import json
import socket
import struct
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

FRAME = bytes.fromhex('006900e110000045006003e81000100010321100')
BLE_STATUS_UUID = '0000bbb1-0000-1000-8000-00805f9b34fb'
BLE_STATUS_HEADER = bytes.fromhex('00191400')


def decode(frame, size):
    if size not in (16, 20) or len(frame) != size:
        raise ValueError('unexpected frame size')
    if frame[4] not in (0, 16) or frame[5] != 0:
        raise ValueError('unexpected status header')

    def temperature(offset, sentinel):
        value = struct.unpack_from('>H', frame, offset)[0]
        if value == sentinel:
            return None, None
        if not 0 <= value <= 750:
            raise ValueError('unexpected temperature')
        return value, round((value - 32) * 5 / 9, 1)

    def probe(offset):
        current_f, current_c = temperature(offset, 1000)
        target_f, target_c = temperature(offset + 2, 4096)
        return dict(current_f=current_f, current_c=current_c,
                    target_f=target_f, target_c=target_c)

    mode = speed = None
    if size == 20:
        if frame[16] not in (0, 16):
            raise ValueError('unexpected fan mode')
        mode = 'auto' if frame[16] == 16 else 'manual'
        if mode == 'manual':
            if frame[17] > 100:
                raise ValueError('unexpected fan speed')
            speed = frame[17]
    return dict(pit=probe(0), probe1=probe(6), probe2=probe(10),
                alarm=frame[4] == 0, fan_mode=mode, fan_percent=speed,
                raw_hex=frame.hex(), frame_size=size)


def decode_ble_status_notification(payload):
    """Decode the documented 20-byte Auto-Akorn status notification only."""
    if len(payload) != 24 or payload[:4] != BLE_STATUS_HEADER:
        raise ValueError('unexpected BLE status notification')
    return decode(payload[4:], 20)


def read_frames(stream, size):
    if size not in (16, 20):
        raise ValueError('unsupported frame size')
    while True:
        frame = b''
        while len(frame) < size:
            chunk = stream.read(size - len(frame))
            if not chunk:
                if frame:
                    raise EOFError('truncated status frame')
                return
            frame += chunk
        yield frame


class Snapshot:
    def __init__(self):
        self.lock = threading.Lock()
        self.value = None
        self.timestamp = None
        self.state = 'waiting'

    def publish(self, value, now=None):
        with self.lock:
            self.value = value
            self.timestamp = time.monotonic() if now is None else now
            self.state = 'live'

    def disconnect(self):
        with self.lock:
            self.state = 'disconnected'

    def get(self, now=None):
        with self.lock:
            age = None if self.timestamp is None else max(0, (time.monotonic() if now is None else now) - self.timestamp)
            state = 'stale' if self.state == 'live' and age > 5 else self.state
            return dict(state=state, age_seconds=age, reading=self.value if state == 'live' else None)


def receive(ip, size, state, stop_event=None, retry_delay=5, port=3333):
    while stop_event is None or not stop_event.is_set():
        try:
            with socket.create_connection((ip, port), timeout=5) as sock:
                with sock.makefile('rb') as stream:
                    for frame in read_frames(stream, size):
                        state.publish(decode(frame, size))
        except (OSError, EOFError, ValueError) as exc:
            print('Telemetry unavailable:', exc, flush=True)
        finally:
            state.disconnect()
        if stop_event is None:
            time.sleep(retry_delay)
        elif stop_event.wait(retry_delay):
            return


async def receive_ble(device_name, state, bleak_api=None, scan_timeout=10):
    """Subscribe to one explicitly named Auto-Akorn status characteristic."""
    if bleak_api is None:
        try:
            import bleak as bleak_api
        except ImportError as exc:
            raise RuntimeError("BLE monitoring requires the optional 'bleak' package") from exc

    try:
        device = await bleak_api.BleakScanner.find_device_by_name(
            device_name, timeout=scan_timeout,
        )
        if device is None:
            raise OSError('BLE device not found')

        disconnected = asyncio.Event()
        async with bleak_api.BleakClient(
            device, disconnected_callback=lambda _: disconnected.set(),
        ) as client:
            def on_notification(_sender, data):
                try:
                    value = decode_ble_status_notification(bytes(data))
                except ValueError as exc:
                    print('Ignoring invalid BLE status:', exc, flush=True)
                    return
                state.publish(value)

            await client.start_notify(BLE_STATUS_UUID, on_notification)
            await disconnected.wait()
    finally:
        state.disconnect()


def receive_ble_worker(device_name, state):
    try:
        asyncio.run(receive_ble(device_name, state))
    except Exception:
        # Avoid printing BLE library errors that can contain local device IDs.
        print('BLE telemetry unavailable; check device visibility and Bluetooth permissions.', flush=True)
        state.disconnect()


PAGE = '''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Open-Akorn</title><style>body{font:18px system-ui;max-width:760px;margin:auto;padding:24px;background:#faf9f6;color:#222}section{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px}article{background:white;border:1px solid #ddd;border-radius:12px;padding:18px}strong{font-size:34px;display:block}#status{font-weight:bold}small{display:block;margin-top:20px}</style><h1>Open-Akorn</h1><p id="status">Waiting for telemetry</p><section id="cards"></section><p id="fan"></p><small>Experimental monitoring only. Use the factory controller and supervise the grill. No controls are enabled.</small><script>
const status=document.querySelector('#status'), cards=document.querySelector('#cards'), fan=document.querySelector('#fan');
function render(s){status.textContent=(s.demo?'SIMULATION | ':'UNVERIFIED HARDWARE | ')+s.state;cards.replaceChildren();fan.textContent='';if(!s.reading)return;let r=s.reading;for(let [key,name] of [['pit','Pit'],['probe1','Meat 1'],['probe2','Meat 2']]){let p=r[key],a=document.createElement('article'),v=document.createElement('strong');a.append(name);v.textContent=p.current_c===null?'Not connected':p.current_c.toFixed(1)+' °C';a.append(v,'Target: '+(p.target_c===null?'Not set':p.target_c.toFixed(1)+' °C'));cards.append(a)}fan.textContent='Fan: '+(r.fan_mode||'Unknown')+(r.fan_percent===null?'':' '+r.fan_percent+'%')+' | Alarm: '+(r.alarm?'ACTIVE':'Not reported active')}
async function poll(){try{let r=await fetch('/api/status',{cache:'no-store',signal:AbortSignal.timeout(3000)});if(!r.ok)throw Error();render(await r.json())}catch{render({state:'unavailable'})}setTimeout(poll,1000)}poll();</script></html>'''


def server(state, demo, port):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path == '/api/status':
                body = json.dumps(dict(state.get(), demo=demo)).encode()
                kind = 'application/json'
            elif self.path == '/':
                body = PAGE.encode()
                kind = 'text/html; charset=utf-8'
            else:
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header('Content-Type', kind)
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self):
            self.send_error(405, 'Read-only monitor')

    return ThreadingHTTPServer(('127.0.0.1', port), Handler)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument('--demo', action='store_true')
    modes.add_argument('--ip', type=ipaddress.IPv4Address)
    modes.add_argument('--ble-name', help='exact advertised Auto-Akorn name (Akorn-...)')
    parser.add_argument('--frame-size', type=int, choices=(16, 20), default=20)
    parser.add_argument('--port', type=int, default=8080)
    args = parser.parse_args()
    if args.ip and not any(args.ip in ipaddress.ip_network(n) for n in ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16')):
        parser.error('use an explicit private LAN address for your own grill')
    if args.ble_name and (not args.ble_name.startswith('Akorn-') or args.frame_size != 20):
        parser.error('BLE status monitoring requires an exact Akorn-* name and 20-byte frames')
    state = Snapshot()
    httpd = server(state, args.demo, args.port)
    def simulate():
        while True:
            state.publish(decode(FRAME[:args.frame_size], args.frame_size))
            time.sleep(1)
    if args.demo:
        target, worker_args = simulate, ()
    elif args.ble_name:
        target, worker_args = receive_ble_worker, (args.ble_name, state)
    else:
        target, worker_args = receive, (str(args.ip), args.frame_size, state)
    thread = threading.Thread(target=target, args=worker_args, daemon=True)
    thread.start()
    print(f'Open http://127.0.0.1:{httpd.server_port}', flush=True)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()


if __name__ == '__main__':
    main()
