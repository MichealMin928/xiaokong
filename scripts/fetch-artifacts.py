#!/usr/bin/env python3
"""Fetch exact upstream artifacts; never execute downloads or extract arbitrary paths."""
import argparse
import hashlib
import json
import shutil
import tarfile
import tempfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def destination(root, name):
    path = (root / name).resolve()
    if not path.is_relative_to(root.resolve()):
        raise ValueError('Artifact path escapes project')
    return path


def verified(path, expected):
    return path.is_file() and digest(path) == expected


def install(artifact, archive, root):
    # Only manifest-listed regular files are copied; tar paths/symlinks are never extracted.
    bundle = tarfile.open(archive, 'r:bz2') if artifact.get('format') == 'tar.bz2' else None
    try:
        for item in artifact['files']:
            target = destination(root, item['path'])
            target.parent.mkdir(parents=True, exist_ok=True)
            source = None
            if bundle:
                member = bundle.getmember(item['member'])
                if not member.isfile() or member.size != item['bytes']:
                    raise ValueError('Unexpected archive member: ' + item['member'])
                source = bundle.extractfile(member)
            else:
                source = archive.open('rb')
            with source, tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as out:
                temporary = Path(out.name)
                try:
                    shutil.copyfileobj(source, out)
                    out.close()
                    if temporary.stat().st_size != item['bytes'] or digest(temporary) != item['sha256']:
                        raise ValueError('Extracted file checksum mismatch: ' + item['path'])
                    temporary.replace(target)
                finally:
                    temporary.unlink(missing_ok=True)
    finally:
        if bundle:
            bundle.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Verify existing files without downloading')
    parser.add_argument('--accept-model-licenses', action='store_true', help='Read THIRD_PARTY_NOTICES.md first; acknowledge upstream model terms')
    args = parser.parse_args()
    manifest = json.loads((ROOT / 'config/artifacts.json').read_text())
    if not args.check and not args.accept_model_licenses:
        parser.error('Read THIRD_PARTY_NOTICES.md and pass --accept-model-licenses to download.')
    missing = []
    for artifact in manifest['artifacts']:
        valid = all(verified(destination(ROOT, f['path']), f['sha256']) for f in artifact['files'])
        if valid:
            print('Verified:', artifact['id'], flush=True)
            continue
        if args.check:
            missing.append(artifact['id'])
            continue
        if not artifact['url'].startswith('https://'):
            raise ValueError('HTTPS is required')
        cache = ROOT / '.cache/artifacts' / artifact['sha256']
        cache.parent.mkdir(parents=True, exist_ok=True)
        if not verified(cache, artifact['sha256']):
            print('Downloading:', artifact['id'], flush=True)
            with tempfile.NamedTemporaryFile(dir=cache.parent, delete=False) as out:
                temporary = Path(out.name)
                try:
                    request = urllib.request.Request(artifact['url'], headers={'User-Agent': 'xiaokong-artifact-fetcher/1'})
                    with urllib.request.urlopen(request, timeout=120) as response:
                        if not response.url.startswith('https://'):
                            raise ValueError('Insecure redirect')
                        shutil.copyfileobj(response, out)
                    out.close()
                    if digest(temporary) != artifact['sha256']:
                        raise ValueError('Download checksum mismatch: ' + artifact['id'])
                    temporary.replace(cache)
                finally:
                    temporary.unlink(missing_ok=True)
        install(artifact, cache, ROOT)
        print('Installed and verified:', artifact['id'], flush=True)
    if missing:
        parser.exit(1, 'Missing or modified artifacts: ' + ', '.join(missing) + '\nRun scripts/fetch-artifacts.py --accept-model-licenses\n')


if __name__ == '__main__':
    main()
