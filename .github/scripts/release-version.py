#!/usr/bin/env python3
"""Prepare matching ArchVow IDE package versions in an ephemeral CI checkout.

The version is deterministic for the history reachable from a stable or beta
commit since ArchVow 0.19.0. Distinct odd (stable) and even (beta) patch
versions prevent channel collisions. No tags or GitHub write credentials are needed.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE_COMMIT = 'f87b75a522f019d58db4bed21270b88b079184f5'
BASE_MAJOR_MINOR = '0.19'


def release_version(channel: str = 'stable', root: Path = ROOT) -> str:
    if channel not in ('stable', 'beta'):
        raise SystemExit(f'Unsupported Marketplace channel: {channel!r}')
    try:
        subprocess.run(['git', 'merge-base', '--is-ancestor', BASE_COMMIT, 'HEAD'],
                       cwd=root, check=True, capture_output=True, text=True)
        # Count all commits reachable from the release ref, not only first-parent.
        # This lets a stable merge commit include all beta-branch commits so its
        # version is greater than the beta versions that it promotes.
        count = int(subprocess.check_output(
            ['git', 'rev-list', '--count', f'{BASE_COMMIT}..HEAD'],
            cwd=root, text=True).strip())
    except (subprocess.CalledProcessError, ValueError) as exc:
        raise SystemExit(
            'Cannot determine the release version: full git history including '
            f'{BASE_COMMIT} is required (checkout fetch-depth: 0).') from exc
    if count < 1:
        raise SystemExit('No release commits after ArchVow 0.19.0; refusing to republish 0.19.0.')
    # Existing published 0.19.1 on main remains unchanged. For any given commit
    # count, stable and beta must not reuse the same Marketplace version.
    patch = 2 * count - (1 if channel == 'stable' else 0)
    return f'{BASE_MAJOR_MINOR}.{patch}'


def rewrite_once(path: Path, pattern: str, replacement: str) -> None:
    original = path.read_text(encoding='utf-8')
    updated, count = re.subn(pattern, lambda _: replacement, original, count=1, flags=re.MULTILINE)
    if count != 1:
        raise SystemExit(f'Expected exactly one version marker in {path}: {pattern}')
    if updated != original:
        path.write_text(updated, encoding='utf-8')


def apply_version(version: str, root: Path = ROOT, channel: str = "stable") -> None:
    if channel not in ("stable", "beta"):
        raise SystemExit(f"Unsupported Marketplace channel: {channel!r}")
    if not re.fullmatch(r'\d+\.\d+\.\d+', version):
        raise SystemExit(f'Invalid SemVer version: {version!r}')
    package = root / 'vscode/package.json'
    lockfile = root / 'vscode/package-lock.json'
    gradle = root / 'intellij/gradle.properties'
    readme = root / 'README.md'

    rewrite_once(package, r'^  "version": "\d+\.\d+\.\d+",$', f'  "version": "{version}",')
    rewrite_once(lockfile, r'^  "version": "\d+\.\d+\.\d+",$', f'  "version": "{version}",')
    rewrite_once(lockfile, r'^      "version": "\d+\.\d+\.\d+",$', f'      "version": "{version}",')
    rewrite_once(gradle, r'^pluginVersion=\d+\.\d+\.\d+$', f'pluginVersion={version}')
    rewrite_once(readme, r'Both plugins have the same version \(\d+\.\d+\.\d+\)',
                 f'Both plugins have the same version ({version})')
    rewrite_once(readme,
                 r'dist/archvow-intellij-\d+\.\d+\.\d+\.zip, dist/archvow-\d+\.\d+\.\d+\.vsix',
                 f'dist/archvow-intellij-{version}.zip, dist/archvow-{version}.vsix')

    # The existing release checks require the newest entry of each changelog
    # to match package.json AND to identify the supported ArchVow CLI.
    cli = (root / 'contract/cli/version.txt').read_text(encoding='utf-8').strip().split()[-1]
    cli_major_minor = '.'.join(cli.split('.')[:2])
    date = dt.date.today().isoformat()
    for ide in ('vscode', 'intellij'):
        path = root / ide / 'CHANGELOG.md'
        original = path.read_text(encoding='utf-8')
        first = re.search(r'(?m)^## (\d+\.\d+\.\d+)\b.*$', original)
        if not first:
            raise SystemExit(f'No version entry found in {path}')
        if first.group(1) == version:
            continue
        entry = (f'## {version} - {date}\n\n'
                 f'Built on archvow {cli_major_minor} (unchanged CLI contract).\n\n'
                 f'- Automated Marketplace {channel} release from the validated {channel} branch.\n\n')
        updated = original[:first.start()] + entry + original[first.start():]
        path.write_text(updated, encoding='utf-8')

    assert json.loads(package.read_text(encoding='utf-8'))['version'] == version
    assert json.loads(lockfile.read_text(encoding='utf-8'))['packages']['']['version'] == version


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--compute', action='store_true', help='print deterministic release version from git')
    group.add_argument('--apply', metavar='VERSION', help='prepare all manifests and changelogs')
    parser.add_argument('--channel', choices=('stable', 'beta'), default='stable',
                        help='stable (main) or beta (beta branch)')
    args = parser.parse_args()
    if args.compute:
        print(release_version(channel=args.channel))
    else:
        apply_version(args.apply, channel=args.channel)
        print(f'Prepared ArchVow IDE plugins {args.apply}')


if __name__ == '__main__':
    main()
