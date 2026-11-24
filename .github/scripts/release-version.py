#!/usr/bin/env python3
"""The release a tag names (1.1.0): the plugins are published from a tag, never from a push to a branch.

`v1.1.0` publishes the stable release 1.1.0; `v1.2.0-beta` the pre-release 1.2.0 (VS Code's Marketplace takes no
`-beta` suffix: a pre-release uses numbers no stable release uses). The tag must name the version people set -
vscode/package.json, intellij/gradle.properties and the newest entry of both changelogs - or CI publishes nothing.
Nothing is computed, nothing is rewritten: the release commit already says its version, and the release test
checks that every file says the same one.

    python3 .github/scripts/release-version.py --check-tag v1.1.0     # prints: 1.1.0 stable
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TAG = re.compile(r'^v(\d+\.\d+\.\d+)(-beta)?$')


def read_tag(tag: str) -> tuple[str, str]:
    """`v1.1.0` -> ("1.1.0", "stable"), `v1.2.0-beta` -> ("1.2.0", "beta"); anything else is refused."""
    m = TAG.match(tag.strip())
    if not m:
        raise SystemExit(f'Not a release tag: {tag!r} - a release is tagged vMAJOR.MINOR.PATCH, or vMAJOR.MINOR.PATCH-beta')
    return m.group(1), 'beta' if m.group(2) else 'stable'


def versions_said(root: Path = ROOT) -> dict[str, str]:
    """The version each release file says."""
    said = {'vscode/package.json': json.loads((root / 'vscode/package.json').read_text(encoding='utf-8'))['version']}
    gradle = re.search(r'(?m)^pluginVersion=(\S+)$', (root / 'intellij/gradle.properties').read_text(encoding='utf-8'))
    said['intellij/gradle.properties'] = gradle.group(1) if gradle else ''
    for ide in ('vscode', 'intellij'):
        newest = re.search(r'(?m)^## (\S+)', (root / ide / 'CHANGELOG.md').read_text(encoding='utf-8'))
        said[f'{ide}/CHANGELOG.md'] = newest.group(1) if newest else ''
    return said


def check_tag(tag: str, root: Path = ROOT) -> tuple[str, str]:
    """The tag's version and channel, once every release file says that version."""
    version, channel = read_tag(tag)
    wrong = {f: v for f, v in versions_said(root).items() if v != version}
    if wrong:
        raise SystemExit(f'The tag {tag} names {version}, but ' + ', '.join(f'{f} says {v or "nothing"}' for f, v in wrong.items())
                         + ': set the version in the release commit, then tag it.')
    return version, channel


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check-tag', metavar='TAG', required=True, help='the pushed tag, e.g. v1.1.0 or v1.2.0-beta')
    args = parser.parse_args()
    version, channel = check_tag(args.check_tag)
    print(f'{version} {channel}')


if __name__ == '__main__':
    main()
