#!/usr/bin/env python3
"""Inspect a release APK; does not claim recognition or human acceptance."""
import argparse
import hashlib
import json
import os
import re
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--aapt', type=Path)
    args = parser.parse_args()
    aapt = args.aapt
    if aapt is None:
        sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
        if sdk:
            aapt = Path(sdk) / 'build-tools/35.0.0/aapt'
    if not aapt or not aapt.is_file():
        parser.error('Set ANDROID_HOME or pass --aapt pointing to SDK build-tools/aapt')
    permissions = subprocess.check_output([str(aapt), 'dump', 'permissions', str(args.apk)], text=True)
    manifest = subprocess.check_output([str(aapt), 'dump', 'xmltree', str(args.apk), 'AndroidManifest.xml'], text=True)
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(args.apk)], text=True)
    for permission in ('android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE'):
        if permission in permissions:
            raise ValueError('Unexpected network permission: ' + permission)
    if 'android:debuggable' in manifest and re.search(r'android:debuggable[^\n]*(?:0xffffffff|true)', manifest):
        raise ValueError('Release must not be debuggable')
    for name in ('VoiceRepeatTestActivity', 'ActionTestActivity', 'SelectionTestActivity', 'ProofChildActivity'):
        if name in manifest:
            raise ValueError('Debug-only activity is present: ' + name)
    required = [f for a in json.loads((ROOT/'config/artifacts.json').read_text())['artifacts']
                for f in a['files'] if f['path'].startswith('app/src/main/assets/')]
    with zipfile.ZipFile(args.apk) as apk:
        if any('voice-fixtures/' in name or name.endswith(('.wav', '.tsv')) and 'oppo-' in name for name in apk.namelist()):
            raise ValueError('Private fixture packaged')
        for item in required:
            name = item['path'].replace('app/src/main/', '', 1)
            if hashlib.sha256(apk.read(name)).hexdigest() != item['sha256']:
                raise ValueError('Packaged model checksum mismatch: ' + name)
        for file in (ROOT/'app/src/main/assets/licenses').glob('*.txt'):
            if apk.read('assets/licenses/'+file.name) != file.read_bytes():
                raise ValueError('Bundled notice differs: ' + file.name)
    print(json.dumps({'apk':args.apk.name,'model_files_verified':len(required),'network_permissions':False,
                      'debug_entries':False,'package':badging.splitlines()[0]}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
