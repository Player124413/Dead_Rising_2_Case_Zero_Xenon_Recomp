import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/android'))
import native_cache


def fixture_elf():
    data = bytearray(120)
    data[:6] = b'\x7fELF\x02\x01'
    struct.pack_into('<HH', data, 16, 3, 183)
    struct.pack_into('<Q', data, 32, 64)
    struct.pack_into('<HH', data, 54, 56, 1)
    struct.pack_into('<I', data, 64, 1)
    struct.pack_into('<QQQ', data, 96, 120, 120, 16384)
    return data


class NativeCacheTest(unittest.TestCase):
    def run_cache(self, deps, operation, key='inputs'):
        with patch.object(sys, 'argv', ['native_cache.py', operation, '--deps', str(deps), '--ndk', str(deps)]), \
             patch.object(native_cache, 'input_key', return_value=key):
            return native_cache.main()

    def fixture(self, deps):
        for relative in native_cache.LIBRARIES:
            path = deps / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(fixture_elf())
        for relative in ['ffmpeg-build/config.h', 'ffmpeg-build/ffbuild/config.mak', 'ffmpeg-install/include/libavcodec/avcodec.h']:
            path = deps / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('synthetic configuration/header\n')

    def test_missing_changed_and_incomplete_outputs_cannot_hit(self):
        with tempfile.TemporaryDirectory() as d:
            deps = Path(d)
            self.assertEqual(self.run_cache(deps, 'check'), 1)
            self.fixture(deps)
            self.assertEqual(self.run_cache(deps, 'record'), 0)
            self.assertEqual(self.run_cache(deps, 'check'), 0)
            self.assertEqual(self.run_cache(deps, 'check', key='different options'), 1)
            (deps / native_cache.LIBRARIES[0]).write_bytes(b'changed or corrupted output')
            self.assertEqual(self.run_cache(deps, 'check'), 1)
            with self.assertRaises(ValueError): self.run_cache(deps, 'record')

    def test_dirty_sources_do_not_poison_clean_cache(self):
        with tempfile.TemporaryDirectory() as d:
            deps = Path(d)
            self.fixture(deps)
            self.assertEqual(self.run_cache(deps, 'record'), 0)
            self.assertEqual(self.run_cache(deps, 'check', key=None), 1)
            self.assertEqual(self.run_cache(deps, 'record', key=None), 0)
            self.assertEqual(self.run_cache(deps, 'check'), 1)
            self.assertFalse((deps / '.native-deps-complete.json').exists())


if __name__ == '__main__':
    unittest.main()
