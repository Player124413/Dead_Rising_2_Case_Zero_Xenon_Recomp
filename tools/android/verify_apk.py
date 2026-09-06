#!/usr/bin/env python3
"""Fail closed on missing/wrong-ABI libraries, 4 KB-only ELF segments or game data."""
import argparse
import json
import struct
import zipfile
from pathlib import Path
from package_inputs import LIBRARIES


def verify(path, mode):
    with zipfile.ZipFile(path) as apk:
        names = apk.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate APK entries')
        info = json.loads(apk.read('assets/build-info.json'))
        if info['mode'] != mode or info['guest_code'] != ('recompiled' if mode == 'game' else 'stub'):
            raise ValueError('Build mode/stub metadata mismatch')
        for name in names:
            p = Path(name)
            if p.suffix.lower() in ('.xex', '.xexp', '.stfs', '.iso', '.dsf') or p.name in ('default_image.bin', 'ppc_recomp.0.cpp'):
                raise ValueError(f'Game/save data in APK: {name}')
            if name.startswith('lib/'):
                if not name.startswith('lib/arm64-v8a/') or not name.endswith('.so'):
                    raise ValueError(f'Unexpected native input: {name}')
                data = apk.read(name)
                if data[:6] != b'\x7fELF\x02\x01' or data[16:20] != b'\x03\0\xb7\0':
                    raise ValueError(f'Not an AArch64 ELF shared object: {name}')
                phoff = struct.unpack_from('<Q', data, 32)[0]
                phentsize, phnum = struct.unpack_from('<HH', data, 54)
                if phentsize < 56 or not phnum or phoff + phentsize * phnum > len(data):
                    raise ValueError(f'Invalid ELF program headers: {name}')
                loads = 0
                for i in range(phnum):
                    off = phoff + i * phentsize
                    kind = struct.unpack_from('<I', data, off)[0]
                    if kind == 1:
                        loads += 1
                        align = struct.unpack_from('<Q', data, off + 48)[0]
                        if align < 16384:
                            raise ValueError(f'ELF not 16 KB page-compatible: {name}')
                if not loads:
                    raise ValueError(f'No ELF LOAD segment: {name}')
        for lib in LIBRARIES:
            if f'lib/arm64-v8a/lib{lib}.so' not in names:
                raise ValueError(f'Missing lib{lib}.so')
        if 'assets/licenses/NOTICE.txt' not in names:
            raise ValueError('Missing license notice')
    print(f'OK: {mode} APK, complete ARM64 native set, 16 KB ELF alignment, no game/save inputs')


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('apk', type=Path)
    ap.add_argument('--mode', choices=['diagnostic', 'game'], required=True)
    args = ap.parse_args()
    verify(args.apk, args.mode)
