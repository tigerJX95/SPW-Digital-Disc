#!/usr/bin/env python3
"""Verify an approved SPW archive and its cover entry call."""
import hashlib
import json
import struct
import sys
import zipfile
from pathlib import Path

from trace import parse


def verify(host, profiles):
    digest = hashlib.sha256()
    with host.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
        host_sha = digest.hexdigest()
        profile = next((item for item in profiles if item['host_sha256'] == host_sha), None)
        if profile is None:
            raise ValueError('Unsupported host build; do not bypass verification')

        stream.seek(0)
        with zipfile.ZipFile(stream) as archive:
            surface = archive.read(profile['surface'] + '.class')
            screen = archive.read(profile['screen'] + '.class')

    for key, data in (('surface', surface), ('screen', screen)):
        if hashlib.sha256(data).hexdigest() != profile[key + '_sha256']:
            raise ValueError(f'{key} class fingerprint mismatch')

    owner, callers, _ = parse(surface)
    target, callees, _ = parse(screen)
    if owner != profile['surface'] or target != profile['screen']:
        raise ValueError('Host class name differs from verified profile')
    caller = [method for method in callers if method['descriptor'] == profile['caller_descriptor']]
    callee = [method for method in callees if method['descriptor'] == profile['callee_descriptor']]
    if len(caller) != 1 or len(callee) != 1:
        raise ValueError(f'Expected unique caller and callee signatures, found {len(caller)} and {len(callee)}')

    sites = [
        call for call in caller[0]['calls']
        if call['owner'] == target
        and call['method'] == callee[0]['method']
        and call['descriptor'] == callee[0]['descriptor']
    ]
    if len(sites) != 1 or sites[0]['opcode'] != '0xb8':
        raise ValueError('Expected exactly one INVOKESTATIC cover call in the verified caller')
    return {
        'PASS': True,
        'profile': profile['version'],
        'host_sha256': host_sha,
        'invoke': 'INVOKESTATIC',
        'offset': sites[0]['offset'],
        'matching_sites': len(sites),
    }


def main():
    if len(sys.argv) != 2:
        raise SystemExit('Usage: verify_host.py <SPW host archive>')
    profiles = json.loads(Path(__file__).with_name('host_builds.json').read_text(encoding='utf-8'))
    try:
        result = verify(Path(sys.argv[1]), profiles)
    except (OSError, KeyError, ValueError, IndexError, struct.error, zipfile.BadZipFile) as error:
        raise SystemExit(f'Host verification failed: {error}') from None
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
