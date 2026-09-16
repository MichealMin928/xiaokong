#!/usr/bin/env python3
"""Check Git's public file set, local Markdown links and common accidental secrets."""
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]


def main():
    result = subprocess.run(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'],
                            cwd=ROOT, check=True, capture_output=True)
    names = set(result.stdout.decode().split('\0')) - {''}
    failures = []
    forbidden_roots = {'evidence', 'releases', 'checkpoints', '.tools', '.gradle', '.cache', '.idea'}
    forbidden_suffixes = {'.apk', '.aab', '.aar', '.onnx', '.task', '.wav', '.jks', '.keystore', '.log'}
    patterns = {
        'private key': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
        'GitHub credential': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,})\b'),
        'API credential': re.compile(r'\bsk-(?:proj-)?[A-Za-z0-9_-]{30,}\b'),
        'machine home path': re.compile(r'/(?:Users|home)/[A-Za-z0-9_.-]+/'),
    }
    for name in sorted(names):
        path = ROOT / name
        parts = Path(name).parts
        if (parts[0] in forbidden_roots or path.suffix.lower() in forbidden_suffixes
                or name == 'local.properties' or Path(name).name.startswith('.env')
                or 'privateTest' in parts or name.startswith('app/src/test/resources/')
                or name.startswith('app/src/debug/assets/')
                or (name.startswith('docs/') and not name.startswith('docs/public/'))):
            failures.append(name + ': private or generated path')
        if path.is_symlink():
            failures.append(name + ': symlink is not part of the public source format')
            continue
        if not path.is_file():
            failures.append(name + ': tracked file missing')
            continue
        if path.stat().st_size > 5 * 1024 * 1024:
            failures.append(name + ': file larger than public-source limit (5 MiB)')
            continue
        try:
            text = path.read_text(encoding='utf-8')
        except UnicodeDecodeError:
            if path.suffix not in {'.png', '.jar'}:
                failures.append(name + ': unexpected binary file')
            continue
        for label, pattern in patterns.items():
            if pattern.search(text):
                failures.append(name + ': ' + label)
        if path.suffix == '.md':
            for link in re.findall(r'\]\(([^)]+)\)', text):
                link = link.split('#')[0].split('?')[0]
                if not link or re.match(r'^[a-zA-Z]+:', link):
                    continue
                target = (path.parent / unquote(link)).resolve()
                if not target.is_relative_to(ROOT) or not target.exists():
                    failures.append(name + ': missing local link ' + link)
                elif target.is_file() and str(target.relative_to(ROOT)) not in names:
                    failures.append(name + ': link points to unpublished file ' + link)
    if failures:
        print('\n'.join(failures), file=sys.stderr)
        return 1
    print(f'Public source check passed: {len(names)} files; no blocked paths or detected secrets; local links resolve.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
