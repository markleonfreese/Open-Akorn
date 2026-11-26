"""PWA shell routes and cache policy are tested without an external service."""
import json
import threading
import unittest
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


if __name__ == '__main__':
    unittest.main()
