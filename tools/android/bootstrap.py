#!/usr/bin/env python3
"""Fetch locked build inputs; never fetch or generate game data implicitly."""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOCK = json.loads((Path(__file__).with_name('dependencies.json')).read_text())


def run(*args, cwd=None):
    subprocess.run(args, cwd=cwd, check=True)


def sync(name, spec, root):
    dest = root / name
    # The marker also binds the patch. Never apply a changed patch on top of old
    # patched sources, or reset a developer's local checkout silently.
    patch = ROOT / spec['patch'] if 'patch' in spec else None
    stamp = spec['commit'] + (hashlib.sha256(patch.read_bytes()).hexdigest() if patch else '')
    marker = dest / '.cz-dependency'
    if marker.exists():
        if marker.read_text() != stamp:
            raise RuntimeError(f'{dest} has a different lock/patch; remove this dependency directory and retry')
        head = subprocess.check_output(['git', '-C', str(dest), 'rev-parse', 'HEAD'], text=True).strip()
        if head != spec['commit']:
            raise RuntimeError(f'{dest} is not at its locked commit')
        return
    if dest.exists():
        raise RuntimeError(f'{dest} is incomplete or not managed by this script; move it aside and retry')
    dest.mkdir(parents=True)
    run('git', 'init', '-q', str(dest))
    run('git', 'remote', 'add', 'origin', spec['url'], cwd=dest)
    run('git', 'fetch', '--depth=1', 'origin', spec['commit'], cwd=dest)
    run('git', 'checkout', '--detach', 'FETCH_HEAD', cwd=dest)
    run('git', 'submodule', 'update', '--init', '--recursive', '--depth=1',
        *spec.get('submodules', []), cwd=dest)
    if patch:
        run('git', 'apply', '--check', str(patch), cwd=dest)
        run('git', 'apply', str(patch), cwd=dest)
    marker.write_text(stamp)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT / 'thirdparty/android')
    parser.add_argument('--dxc', action='store_true', help='also fetch the on-device shader compiler source')
    args = parser.parse_args()
    for name, spec in LOCK.items():
        if isinstance(spec, dict) and (name != 'dxc' or args.dxc):
            sync(name, spec, args.root.resolve())


if __name__ == '__main__':
    main()
