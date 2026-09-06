#!/usr/bin/env python3
"""Validate completed dependency outputs by inputs + content, not SDK mtimes.

A new hosted runner reinstalls the NDK: header timestamps can invalidate every
cached Ninja object despite identical source and toolchain contents. Only a
successful build can write this marker; missing/changed outputs force a rebuild.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
from verify_apk import verify_elf

ROOT = Path(__file__).resolve().parents[2]
LIBRARIES = ['ffmpeg-install/lib/libavcodec.so', 'ffmpeg-install/lib/libavutil.so',
             'dxc-build/lib/libdxcompiler.so']


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def input_key(deps, ndk):
    inputs = {name: digest(ROOT / 'tools/android' / name)
              for name in ['native_cache.py', 'build_ffmpeg.sh', 'build_dxc.sh', 'dependencies.json']}
    inputs['ndk'] = digest(ndk / 'source.properties')
    for tool in ('clang', 'cmake'):
        inputs[tool] = subprocess.check_output([tool, '--version'], text=True).splitlines()[0]
    for key in ('CFLAGS', 'CXXFLAGS', 'CPPFLAGS', 'LDFLAGS'):
        inputs[key] = os.environ.get(key, '')
    for name in ('dxc', 'ffmpeg'):
        repo = deps / name
        dirty = subprocess.check_output(['git', '-C', str(repo), 'status', '--porcelain', '--untracked-files=all'], text=True)
        if any(line != '?? .cz-dependency' for line in dirty.splitlines()):
            return None # permit local library work, but never reuse it as a clean pinned build
        inputs[name] = subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip()
        inputs[name + '-submodules'] = subprocess.check_output(['git', '-C', str(repo), 'submodule', 'status', '--recursive'], text=True)
    return hashlib.sha256(json.dumps(inputs, sort_keys=True).encode()).hexdigest()


def outputs(deps):
    paths = [deps / p for p in LIBRARIES]
    paths += [deps / 'ffmpeg-build/config.h', deps / 'ffmpeg-build/ffbuild/config.mak']
    headers = sorted((deps / 'ffmpeg-install/include').rglob('*.h'))
    if not headers:
        raise ValueError('Missing installed FFmpeg headers')
    return {p.relative_to(deps).as_posix(): digest(p) for p in paths + headers}


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('operation', choices=['check', 'record', 'invalidate'])
    ap.add_argument('--deps', type=Path, required=True)
    ap.add_argument('--ndk', type=Path, required=True)
    args = ap.parse_args()
    state = args.deps / '.native-deps-complete.json'
    if args.operation == 'invalidate':
        state.unlink(missing_ok=True)
        return 0
    if args.operation == 'check':
        try:
            old = json.loads(state.read_text())
            key = input_key(args.deps, args.ndk)
            if key and old == {'key': key, 'outputs': outputs(args.deps)}:
                print('Using verified completed FFmpeg/DXC outputs from the locked cache')
                return 0
        except (OSError, ValueError, subprocess.SubprocessError):
            pass # cache miss, not a build/test failure; the real build runs next
        return 1
    key = input_key(args.deps, args.ndk)
    if key is None:
        state.unlink(missing_ok=True)
        print('Dependency sources have local modifications; not marking them as a clean cached build')
        return 0
    for relative in LIBRARIES:
        verify_elf((args.deps / relative).read_bytes(), relative)
    value = {'key': key, 'outputs': outputs(args.deps)}
    tmp = state.with_suffix('.tmp')
    tmp.write_text(json.dumps(value, sort_keys=True) + '\n')
    tmp.replace(state)
    print('Recorded complete Android ARM64 FFmpeg/DXC outputs and their hashes')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
