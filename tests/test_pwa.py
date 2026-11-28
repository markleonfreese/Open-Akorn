"""PWA shell routes and cache policy are tested without an external service."""
import json
import re
import threading
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.request import urlopen

from monitor import Snapshot, server


class PwaTests(unittest.TestCase):
    def setUp(self):
        self.httpd = server(Snapshot(), False, 0, source='tcp')
        self.worker = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.worker.start()
        self.base = f'http://127.0.0.1:{self.httpd.server_port}'

    def tearDown(self):
        self.httpd.shutdown()
        self.httpd.server_close()
        self.worker.join(3)

    def test_manifest_has_install_icons_and_local_shell(self):
        with urlopen(self.base + '/manifest.webmanifest', timeout=3) as response:
            manifest = json.load(response)
            self.assertEqual(response.headers['Content-Type'], 'application/manifest+json')
            self.assertEqual(response.headers['Cache-Control'], 'no-cache')
        self.assertEqual(manifest['display'], 'standalone')
        self.assertEqual(manifest['start_url'], '/')
        sizes = {icon['sizes'] for icon in manifest['icons']}
        self.assertTrue({'192x192', '512x512'} <= sizes)

    def test_app_shell_assets_are_served_but_status_is_not_cached(self):
        for path in ('/', '/app.js', '/styles.css', '/service-worker.js', '/icon-192.png', '/icon-512.png'):
            with self.subTest(path=path), urlopen(self.base + path, timeout=3) as response:
                self.assertEqual(response.status, 200)
                self.assertEqual(response.headers['Cache-Control'], 'no-cache')
                self.assertGreater(int(response.headers['Content-Length']), 0)

        with urlopen(self.base + '/api/status', timeout=3) as response:
            payload = json.load(response)
            self.assertEqual(response.headers['Cache-Control'], 'no-store')
        self.assertEqual(payload['schema_version'], 1)
        self.assertEqual(payload['model'], 'akorn-auto')
        self.assertEqual(payload['source'], 'tcp')
        self.assertFalse(payload['demo'])
        self.assertIsNone(payload['reading'])

    def test_live_region_excludes_connection_age_that_changes_each_poll(self):
        html = (Path(__file__).resolve().parents[1] / 'pwa' / 'index.html').read_text()
        connection = re.search(
            r'<section class="connection"(?P<attributes>[^>]*)>(?P<body>.*?)</section>',
            html,
            re.DOTALL,
        )
        self.assertIsNotNone(connection)
        self.assertNotIn('aria-live', connection.group('attributes'))
        self.assertEqual(connection.group('body').count('aria-live="polite"'), 1)
        status = re.search(r'<strong id="statusText"(?P<attributes>[^>]*)>', connection.group('body'))
        self.assertIsNotNone(status)
        self.assertIn('role="status"', status.group('attributes'))
        self.assertIn('aria-live="polite"', status.group('attributes'))
        self.assertIn('aria-atomic="true"', status.group('attributes'))
        self.assertNotRegex(status.group(0), r'id="ageText"')

    def test_local_timer_is_explicitly_local_and_has_bounded_minutes(self):
        root = Path(__file__).resolve().parents[1]
        html = (root / 'pwa' / 'index.html').read_text()
        script = (root / 'pwa' / 'app.js').read_text()
        self.assertIn('id="cookTimerForm"', html)
        self.assertIn('id="cookTimerMinutes" type="number" min="1" max="1440"', html)
        self.assertIn('Optional browser alert requires permission', html)
        self.assertIn('if (!Number.isInteger(minutes) || minutes < 1 || minutes > 1440)', script)
        self.assertIn('cookTimerDeadline = Date.now() + minutes * 60_000', script)
        self.assertIn("cookTimerFinishedAnnounced ? 'Timer finished'", script)
        self.assertIn('Notification.requestPermission()', script)
        self.assertIn('registration.showNotification', script)
        self.assertIsNone(re.search(r'fetch\([^)]*(command|setTemp|fan|timer)', script, re.IGNORECASE))

    def test_graph_is_current_page_session_bounded_and_preserves_gaps(self):
        root = Path(__file__).resolve().parents[1]
        html = (root / 'pwa' / 'index.html').read_text()
        script = (root / 'pwa' / 'app.js').read_text()
        self.assertIn('id="temperatureChart"', html)
        self.assertIn('°C · fresh samples from this page session. Gaps stay visible.', html)
        self.assertIn('if (graphSource !== null && source !== graphSource) temperatureSamples = []', script)
        self.assertIn('if (temperatureSamples.length > 720) temperatureSamples.shift()', script)
        self.assertIn('sample.time - previousTime > 15_000', script)
        self.assertIn('Number.isFinite(item?.current_c)', script)

    def test_android_timer_and_graph_are_local_and_accessible(self):
        root = Path(__file__).resolve().parents[1]
        layout = (root / 'android' / 'app' / 'src' / 'main' / 'res' / 'layout' / 'monitor_dashboard.xml').read_text()
        timer = (root / 'android' / 'app' / 'src' / 'main' / 'java' / 'com' / 'openakorn' / 'app' / 'LocalCookTimer.kt').read_text()
        history = (root / 'android' / 'app' / 'src' / 'main' / 'java' / 'com' / 'openakorn' / 'app' / 'TemperatureHistory.kt').read_text()
        strings = (root / 'android' / 'app' / 'src' / 'main' / 'res' / 'values' / 'strings.xml').read_text()
        self.assertIn('android:id="@+id/startCookTimerButton"', layout)
        self.assertIn('android:id="@+id/temperatureGraph"', layout)
        self.assertIn('android:accessibilityHeading="true"', layout)
        self.assertIn('no controller or alarm-command integration', timer.lower())
        self.assertIn('runs on this device', strings.lower())
        self.assertIn('class TemperatureHistory', history)
        self.assertIn('private val capacity: Int = 720', history)

    def test_android_timer_notification_is_opt_in_local_and_inexact(self):
        root = Path(__file__).resolve().parents[1]
        manifest = ET.parse(root / 'android/app/src/main/AndroidManifest.xml').getroot()
        permissions = {node.attrib.get('{http://schemas.android.com/apk/res/android}name') for node in manifest.findall('uses-permission')}
        self.assertIn('android.permission.POST_NOTIFICATIONS', permissions)
        self.assertNotIn('android.permission.SCHEDULE_EXACT_ALARM', permissions)
        app = manifest.find('application')
        receiver = app.find('receiver')
        self.assertEqual(receiver.attrib.get('{http://schemas.android.com/apk/res/android}exported'), 'false')

        notifications = (root / 'android/app/src/main/java/com/openakorn/app/CookTimerNotifications.kt').read_text()
        activity = (root / 'android/app/src/main/java/com/openakorn/app/MainActivity.kt').read_text()
        strings = (root / 'android/app/src/main/res/values/strings.xml').read_text().lower()
        self.assertIn('setAndAllowWhileIdle', notifications)
        self.assertNotIn('setExact', notifications)
        self.assertIn('requestPermissions(', activity)
        self.assertIn('post_notifications', activity.lower())
        self.assertIn('reboot or force-stop can cancel', strings)
        self.assertIn('does not indicate that food is safe', strings)

    def test_pwa_timer_alert_control_has_large_touch_target_and_browser_only_copy(self):
        root = Path(__file__).resolve().parents[1]
        html = (root / 'pwa/index.html').read_text()
        css = (root / 'pwa/styles.css').read_text()
        self.assertIn('id="enableTimerNotifications"', html)
        self.assertRegex(css, r'\.timer-notification-button\s*\{[^}]*min-height:\s*48px')
        self.assertIn('missed if suspended or closed', html.lower())


if __name__ == '__main__':
    unittest.main()
