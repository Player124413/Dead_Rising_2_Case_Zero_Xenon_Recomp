#!/usr/bin/env python3
"""Fail closed on missing/wrong-ABI libraries, 4 KB-only ELF segments or game data."""
import argparse
import json
import struct
import zipfile
from pathlib import Path
from package_inputs import LIBRARIES

ROOT = Path(__file__).resolve().parents[2]


def support_inputs():
    paths = [ROOT / 'tools/release/prewarm.keys', *sorted((ROOT / 'tools/release/kbm_chips').glob('*.dxt'))]
    return {'assets/support/' + p.relative_to(ROOT).as_posix(): p for p in paths}


def verify_elf(data, name):
    if len(data) < 64 or data[:6] != b'\x7fELF\x02\x01' or data[16:20] != b'\x03\0\xb7\0':
        raise ValueError(f'Not an AArch64 ELF shared object: {name}')
    phoff = struct.unpack_from('<Q', data, 32)[0]
    phentsize, phnum = struct.unpack_from('<HH', data, 54)
    if phentsize < 56 or not phnum or phoff < 64 or phoff + phentsize * phnum > len(data):
        raise ValueError(f'Invalid ELF program headers: {name}')
    loads = 0
    for i in range(phnum):
        off = phoff + i * phentsize
        kind = struct.unpack_from('<I', data, off)[0]
        if kind == 1:
            loads += 1
            offset, address, _, filesz, memsz, align = struct.unpack_from('<QQQQQQ', data, off + 8)
            if align < 16384 or align & (align - 1) or offset % 16384 != address % 16384:
                raise ValueError(f'ELF not 16 KB page-compatible: {name}')
            if offset + filesz > len(data) or filesz > memsz or offset % align != address % align:
                raise ValueError(f'Invalid ELF LOAD segment: {name}')
    if not loads:
        raise ValueError(f'No ELF LOAD segment: {name}')


def verify(path, mode):
    with zipfile.ZipFile(path) as apk:
        names = apk.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate APK entries')
        info = json.loads(apk.read('assets/build-info.json'))
        if info['mode'] != mode or info['guest_code'] != ('recompiled' if mode == 'game' else 'stub'):
            raise ValueError('Build mode/stub metadata mismatch')
        native = {f'lib/arm64-v8a/lib{lib}.so' for lib in LIBRARIES}
        support = support_inputs()
        for name in names:
            if name.startswith('/') or '\\' in name or any(p in ('', '.', '..') for p in name.rstrip('/').split('/')):
                raise ValueError(f'Unsafe APK entry: {name}')
            if name.endswith('/'):
                continue
            p = Path(name)
            if p.suffix.lower() in ('.xex', '.xexp', '.stfs', '.iso', '.dsf', '.big') or p.name in ('default_image.bin', 'ppc_recomp.0.cpp'):
                raise ValueError(f'Game/save data in APK: {name}')
            if name.startswith('lib/'):
                if name not in native:
                    raise ValueError(f'Unexpected native input: {name}')
                verify_elf(apk.read(name), name)
            if name.startswith('assets/'):
                if name == 'assets/build-info.json' or name.startswith('assets/licenses/'):
                    continue
                if name not in support or apk.read(name) != support[name].read_bytes():
                    raise ValueError(f'Unknown/changed packaged support asset: {name}')
        for name in native | set(support):
            if name not in names:
                raise ValueError(f'Missing APK input: {name}')
        if 'assets/licenses/NOTICE.txt' not in names:
            raise ValueError('Missing license notice')
    print(f'OK: {mode} APK, complete ARM64 native set, 16 KB ELF alignment, allowlisted support assets')


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('apk', type=Path)
    ap.add_argument('--mode', choices=['diagnostic', 'game'], required=True)
    args = ap.parse_args()
    verify(args.apk, args.mode)
