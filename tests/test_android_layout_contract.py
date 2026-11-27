"""Guard Android layout, touch-target, and accessibility resource contracts."""

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'android' / 'app' / 'src' / 'main' / 'res'
ANDROID = '{http://schemas.android.com/apk/res/android}'


def parse(path):
    return ET.parse(path).getroot()


def referenced_names(root, attribute, prefix):
    names = set()
    for node in root.iter():
        value = node.attrib.get(ANDROID + attribute, '')
        if value.startswith(prefix):
            names.add(value[len(prefix):])
    return names


def luminance(hex_color):
    values = [int(hex_color[index:index + 2], 16) / 255 for index in (1, 3, 5)]
    linear = [value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4 for value in values]
    return sum(weight * value for weight, value in zip((0.2126, 0.7152, 0.0722), linear))


def contrast_ratio(first, second):
    light, dark = sorted((luminance(first), luminance(second)), reverse=True)
    return (light + 0.05) / (dark + 0.05)


class AndroidLayoutContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.panels = {
            name: parse(RES / 'layout' / f'{name}.xml')
            for name in ('monitor_dashboard', 'connection_setup')
        }
        cls.string_names = {
            node.attrib['name']
            for node in parse(RES / 'values' / 'strings.xml').findall('string')
        }
        cls.string_values = {
            node.attrib['name']: node.text
            for node in parse(RES / 'values' / 'strings.xml').findall('string')
        }

    def test_phone_and_tablet_layouts_share_the_same_dashboard_and_setup_panels(self):
        phone = parse(RES / 'layout' / 'activity_main.xml')
        tablet = parse(RES / 'layout-w600dp-land' / 'activity_main.xml')
        expected = ['monitor_dashboard', 'connection_setup']
        for layout in (phone, tablet):
            with self.subTest(orientation=layout.attrib.get(ANDROID + 'orientation', 'phone')):
                self.assertEqual(
                    [node.attrib['layout'].removeprefix('@layout/') for node in layout.iter('include')],
                    expected,
                )

        self.assertEqual(tablet.attrib[ANDROID + 'orientation'], 'horizontal')
        self.assertEqual(tablet.attrib[ANDROID + 'baselineAligned'], 'false')
        scroll_views = [node for node in tablet if node.tag.endswith('ScrollView')]
        self.assertEqual(len(scroll_views), 2)
        self.assertTrue(all(float(node.attrib[ANDROID + 'layout_weight']) > 0 for node in scroll_views))

    def test_activity_view_references_resolve_in_the_shared_panels(self):
        activity = (ROOT / 'android/app/src/main/java/com/openakorn/app/MainActivity.kt').read_text()
        used = set(re.findall(r'R\.id\.(\w+)', activity))
        declared = {
            node.attrib[ANDROID + 'id'].removeprefix('@+id/')
            for panel in self.panels.values()
            for node in panel.iter()
            if ANDROID + 'id' in node.attrib
        }
        self.assertTrue(used)
        self.assertLessEqual(used, declared)

    def test_actions_and_connection_inputs_keep_large_touch_targets(self):
        for panel in self.panels.values():
            for node in panel.iter():
                if node.tag.endswith(('Button', 'EditText')):
                    value = node.attrib.get(ANDROID + 'layout_height', '')
                    self.assertTrue(value.endswith('dp'), f'{node.tag} height must use dp')
                    self.assertGreaterEqual(float(value[:-2]), 48)

    def test_wifi_setup_explains_manual_ip_and_existing_network_requirement(self):
        setup_texts = [node.attrib.get(ANDROID + 'text') for node in self.panels['connection_setup'].iter()]
        self.assertIn('@string/wifi_connection_help', setup_texts)
        help_text = self.string_values['wifi_connection_help'].lower()
        self.assertIn('router', help_text)
        self.assertIn('already be connected', help_text)
        self.assertIn('does not set up wi-fi', help_text)

    def test_dynamic_connection_alarm_and_fan_status_are_accessible_live_regions(self):
        ids = {
            node.attrib[ANDROID + 'id'].removeprefix('@+id/'): node
            for panel in self.panels.values()
            for node in panel.iter()
            if ANDROID + 'id' in node.attrib
        }
        for name in ('statusText', 'detailText', 'fanStatusText', 'alarmStatusText'):
            with self.subTest(view=name):
                self.assertEqual(ids[name].attrib[ANDROID + 'accessibilityLiveRegion'], 'polite')

    def test_layout_strings_exist_and_primary_text_colors_meet_contrast_target(self):
        for panel in self.panels.values():
            self.assertLessEqual(referenced_names(panel, 'text', '@string/'), self.string_names)
            self.assertLessEqual(referenced_names(panel, 'hint', '@string/'), self.string_names)

        colors = {
            node.attrib['name']: node.text.strip()
            for node in parse(RES / 'values' / 'colors.xml').findall('color')
        }
        self.assertGreaterEqual(contrast_ratio(colors['akorn_muted'], colors['akorn_cream']), 4.5)
        self.assertGreaterEqual(contrast_ratio('#FFFFFF', colors['akorn_green']), 4.5)
        self.assertGreaterEqual(contrast_ratio(colors['akorn_alarm'], colors['akorn_card']), 4.5)

        css = (ROOT / 'pwa' / 'styles.css').read_text()
        variables = dict(re.findall(r'--([\w-]+):\s*(#[\da-fA-F]{6})\s*;', css))
        safety_note = re.search(r'^\.safety-note\s*\{(?P<rules>[^}]+)\}', css, re.MULTILINE)
        safety_background = re.search(r'background:\s*(#[\da-fA-F]{6})', safety_note.group('rules'))
        self.assertIsNotNone(safety_background)
        for background in ('cream', 'paper', safety_background.group(1)):
            with self.subTest(surface=background):
                self.assertGreaterEqual(contrast_ratio(variables['muted'], variables.get(background, background)), 4.5)

    def test_pwa_reported_pill_keeps_readable_vertical_padding(self):
        css = (ROOT / 'pwa' / 'styles.css').read_text()
        pill = re.search(r'^\.reported-pill\s*\{(?P<rules>[^}]+)\}', css, re.MULTILINE)
        self.assertIsNotNone(pill)
        padding = re.search(r'padding:\s*(\d+)px\s+(\d+)px', pill.group('rules'))
        self.assertIsNotNone(padding)
        self.assertGreaterEqual(int(padding.group(1)), 8)


if __name__ == '__main__':
    unittest.main()
