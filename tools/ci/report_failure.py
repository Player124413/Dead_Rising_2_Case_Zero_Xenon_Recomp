#!/usr/bin/env python3
"""Expose bounded build-error context as check annotations (also visible via gh API).

Hosted logs/artifacts use a separate download host which some sandboxes cannot
reach. This supplements, never replaces or suppresses, the actual failing step.
Do not pass credential/signing logs to this helper.
"""
import re
import sys
from pathlib import Path

for arg in sys.argv[1:]:
    path = Path(arg)
    if not path.is_file():
        continue
    with path.open('rb') as f:
        f.seek(max(0, path.stat().st_size - 65536))
        text = f.read().decode('utf-8', errors='replace')
    text = re.sub(r'\x1b\[[0-9;]*m', '', text)
    lines = text.splitlines()
    selected = set()
    for i, line in enumerate(lines):
        if re.search(r'(?i)(\berror\b|\bfailed\b|\bfailure\b|\bFAIL\b|shellcheck|SC\d{4}|\.yml:\d)', line):
            selected.update(range(max(0, i - 1), min(len(lines), i + 5)))
    excerpt = '\n'.join(lines[i] for i in sorted(selected)) if selected else '\n'.join(lines[-30:])
    excerpt = excerpt[-10000:]
    escaped = excerpt.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::error title=Build log {path.name}::{escaped}')
