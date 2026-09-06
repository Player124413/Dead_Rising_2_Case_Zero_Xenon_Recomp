#!/usr/bin/env python3
"""Archive exact LGPL sources/build recipes next to the APK, never game inputs."""
import argparse
import hashlib
import json
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def archive(repo, prefix, destination, expected=None):
    # git archive includes tracked source at HEAD only: not object files, tokens,
    # .git configuration, local game data or unrelated files in the build tree.
    revision = subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip()
    if expected is not None and revision != expected:
        raise ValueError(f'Wrong source revision in {repo}')
    dirty = subprocess.check_output(['git', '-C', str(repo), 'status', '--porcelain', '--untracked-files=all'], text=True)
    if any(line != '?? .cz-dependency' for line in dirty.splitlines()):
        raise ValueError(f'{repo} has local changes: supply those modified sources, not an unmodified HEAD archive')
    with destination.open('wb') as f:
        subprocess.run(['git', '-C', str(repo), 'archive', '--format=tar.gz',
                        '--prefix=' + prefix + '/', revision], stdout=f, check=True)
    return revision


def package(deps, out):
    out.mkdir(parents=True, exist_ok=True)
    inputs = []
    versions = {}
    lock = json.loads((ROOT / 'tools/android/dependencies.json').read_text())
    xenon = deps / 'xenon'
    xenon_head = subprocess.check_output(['git', '-C', str(xenon), 'rev-parse', 'HEAD'], text=True).strip()
    if xenon_head != lock['xenon']['commit']:
        raise ValueError('Xenon source is not at the locked revision')
    link = subprocess.check_output(['git', '-C', str(xenon), 'ls-tree', 'HEAD', 'thirdparty/libmspack'], text=True).split()
    if len(link) != 4 or link[:2] != ['160000', 'commit']:
        raise ValueError('Missing locked libmspack submodule')
    expected = {'ffmpeg': lock['ffmpeg']['commit'], 'libmspack': link[2]}
    for name, relative in [('ffmpeg', 'ffmpeg'), ('libmspack', 'xenon/thirdparty/libmspack')]:
        target = out / (name + '-source.tar.gz')
        versions[name] = archive(deps / relative, 'thirdparty/android/' + relative, target, expected[name])
        inputs.append((target, target.name))
    for relative in ['tools/android/build_ffmpeg.sh', 'tools/android/build_lzx.sh',
                     'tools/android/dependencies.json', 'runtime/android/CMakeLists.txt',
                     'docs/android-lgpl.md', 'THIRD_PARTY.md']:
        inputs.append((ROOT / relative, relative))
    for name in ('config.h', 'ffbuild/config.mak'):
        inputs.append((deps / 'ffmpeg-build' / name, 'ffmpeg-build-configuration/' + name))
    versions['project'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    manifest = out / 'source-revisions.json'
    manifest.write_text(json.dumps(versions, indent=2) + '\n')
    inputs.append((manifest, manifest.name))
    sums = out / 'SHA256SUMS'
    sums.write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest() + '  ' + name + '\n' for path, name in inputs))
    inputs.append((sums, sums.name))
    result = out / 'CaseZero-Android-LGPL-sources.zip'
    with zipfile.ZipFile(result, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        for source, name in inputs:
            z.write(source, name)
    print(result)
    return result


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--deps', type=Path, default=ROOT / 'thirdparty/android')
    ap.add_argument('--out', type=Path, default=ROOT / 'build/android-open-source')
    args = ap.parse_args()
    package(args.deps, args.out)
