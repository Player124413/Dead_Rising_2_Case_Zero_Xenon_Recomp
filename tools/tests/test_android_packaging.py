import importlib.util
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/android'))
from verify_apk import verify, verify_elf, support_inputs
from package_inputs import LIBRARIES


def elf(align=16384, machine=183):
    data = bytearray(120)
    data[:6] = b'\x7fELF\x02\x01'
    struct.pack_into('<HH', data, 16, 3, machine)
    struct.pack_into('<Q', data, 32, 64)
    struct.pack_into('<HH', data, 54, 56, 1)
    struct.pack_into('<I', data, 64, 1)
    struct.pack_into('<QQ', data, 96, len(data), len(data))
    struct.pack_into('<Q', data, 112, align)
    return data


class PackagingTest(unittest.TestCase):
    def make_apk(self, directory, *, align=16384, machine=183, omit='', extra=''):
        path = Path(directory) / 'test.apk'
        with zipfile.ZipFile(path, 'w') as z:
            z.writestr('assets/build-info.json', json.dumps({'mode': 'diagnostic', 'guest_code': 'stub'}))
            z.writestr('assets/licenses/NOTICE.txt', 'test notice')
            for name, file in support_inputs().items():
                z.writestr(name, file.read_bytes())
            for name in LIBRARIES:
                if name != omit:
                    z.writestr(f'lib/arm64-v8a/lib{name}.so', elf(align, machine))
            if extra:
                z.writestr(extra, 'not game data, a synthetic fixture')
        return path

    def test_valid_diagnostic(self):
        with tempfile.TemporaryDirectory() as d:
            verify(self.make_apk(d), 'diagnostic')

    def test_fail_closed(self):
        for kwargs in ({'align': 4096}, {'machine': 62}, {'omit': 'main'},
                       {'extra': 'assets/default.xex'}, {'extra': 'lib/x86_64/accidental.so'},
                       {'extra': 'lib/arm64-v8a/unknown.so'}, {'extra': 'assets/unknown'},
                       {'extra': '../outside.txt'}, {'extra': 'assets/game/data.bin'}):
            with self.subTest(kwargs=kwargs), tempfile.TemporaryDirectory() as d:
                with self.assertRaises(ValueError):
                    verify(self.make_apk(d, **kwargs), 'diagnostic')

    def test_malformed_segments(self):
        for position, value in ((72, 4096), (96, 999999), (104, 1), (112, 24576), (32, 999999)):
            data = elf()
            struct.pack_into('<Q', data, position, value)
            with self.subTest(position=position), self.assertRaises(ValueError):
                verify_elf(data, 'fixture.so')
        with self.assertRaises(ValueError): verify_elf(b'\x7fELF', 'short.so')

    def test_stub_cannot_be_game(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(ValueError):
                verify(self.make_apk(d), 'game')

    def test_translations_and_manifest(self):
        strings = ROOT / 'android/app/src/main/res'
        en = {x.attrib['name'] for x in ET.parse(strings / 'values/strings.xml').getroot()}
        ru = {x.attrib['name'] for x in ET.parse(strings / 'values-ru/strings.xml').getroot()}
        self.assertEqual(en, ru)
        manifest = ET.parse(ROOT / 'android/app/src/main/AndroidManifest.xml').getroot()
        ns = '{http://schemas.android.com/apk/res/android}'
        permissions = {x.attrib[ns + 'name'] for x in manifest.findall('uses-permission')}
        self.assertNotIn('android.permission.MANAGE_EXTERNAL_STORAGE', permissions)
        self.assertNotIn('android.permission.WRITE_EXTERNAL_STORAGE', permissions)
        for x in manifest.find('application').findall('activity'):
            if x.attrib[ns + 'name'] == '.GameActivity':
                self.assertEqual(x.attrib[ns + 'exported'], 'false')
                self.assertEqual(x.attrib[ns + 'process'], ':runtime')

    def test_dependency_pins(self):
        lock = json.loads((ROOT / 'tools/android/dependencies.json').read_text())
        for name, entry in lock.items():
            if isinstance(entry, dict):
                self.assertRegex(entry['commit'], r'^[0-9a-f]{40}$', name)
        gradle = (ROOT / 'android/app/build.gradle').read_text()
        self.assertIn(lock['ndk'], gradle)
        self.assertIn(f'minSdk {lock["api"]}', gradle)


if __name__ == '__main__':
    unittest.main()
