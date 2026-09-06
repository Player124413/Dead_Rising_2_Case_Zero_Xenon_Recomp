#!/usr/bin/env python3
"""Stage an explicit native library/license allowlist, never a recursive build-tree copy."""
import argparse
import json
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LIBRARIES = ['main', 'cz_support', 'cz_lzx', 'SDL2', 'c++_shared', 'avcodec', 'avutil', 'dxcompiler',
             'main_hook', 'hook_impl', 'file_redirect_hook', 'gsl_alloc_hook']


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--mode', choices=['diagnostic', 'game'], required=True)
    ap.add_argument('--deps', type=Path, required=True)
    ap.add_argument('--build', type=Path, required=True)
    args = ap.parse_args()
    dest = ROOT / 'android/generated' / args.mode
    dest.mkdir(parents=True, exist_ok=True)
    # Remove stale packaging outputs only, never inputs / PPC / game directories.
    for name in ('jniLibs', 'assets'):
        if (dest / name).exists():
            shutil.rmtree(dest / name)
    libs = dest / 'jniLibs/arm64-v8a'
    libs.mkdir(parents=True)
    for lib in LIBRARIES:
        source = args.build / 'jniLibs/arm64-v8a' / f'lib{lib}.so'
        with source.open('rb') as f:
            header = f.read(20)
        if header[:6] != b'\x7fELF\x02\x01' or header[18:20] != b'\xb7\0':
            raise SystemExit(f'Not an Android ARM64 shared library: {source}')
        shutil.copy2(source, libs / source.name)
    assets = dest / 'assets'
    support = assets / 'support/tools/release'
    support.mkdir(parents=True)
    shutil.copytree(ROOT / 'tools/release/kbm_chips', support / 'kbm_chips')
    shutil.copy2(ROOT / 'tools/release/prewarm.keys', support / 'prewarm.keys')
    licenses = assets / 'licenses'
    licenses.mkdir()
    license_files = {
        'PROJECT.txt': ROOT / 'LICENSE',
        'THIRD_PARTY.md': ROOT / 'THIRD_PARTY.md',
        'SDL.txt': args.deps / 'sdl/LICENSE.txt',
        'FFmpeg-LGPL-2.1.txt': args.deps / 'ffmpeg/COPYING.LGPLv2.1',
        'FFmpeg-build-config.txt': args.deps / 'ffmpeg-build/config.h',
        'ADRENOTOOLS.txt': args.deps / 'adrenotools/LICENSE',
        'VOLK.txt': args.deps / 'volk/LICENSE.md',
        'DXC.txt': args.deps / 'dxc/LICENSE.TXT',
        'XenonRecomp.txt': args.deps / 'xenon/LICENSE.md',
        'XenosRecomp.txt': args.deps / 'xenos/LICENSE.md',
    }
    for name, source in license_files.items():
        shutil.copy2(source, licenses / name)
    notice = (ROOT / 'THIRD_PARTY.md').read_text()
    notice += '\n\nAndroid: launcher is a clean implementation. UnleashedRecomp-Android was a UX reference only; no GPL code/art is copied.\n'
    notice += '\n'.join(f'{name}: see bundled license, locked source in dependencies.json' for name in license_files)
    (licenses / 'NOTICE.txt').write_text(notice)
    shutil.copy2(ROOT / 'tools/android/dependencies.json', licenses / 'dependencies.json')
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    info = {'mode': args.mode, 'revision': revision, 'abi': 'arm64-v8a', 'minSdk': 29,
            'guest_code': 'recompiled' if args.mode == 'game' else 'stub', 'device_tested': False,
            'description': 'Stub PPC, not the game' if args.mode == 'diagnostic' else 'Real PPC input; device testing still required'}
    (assets / 'build-info.json').write_text(json.dumps(info, indent=2) + '\n')
    print(f'Prepared {args.mode} APK inputs in {dest}')


if __name__ == '__main__':
    main()
