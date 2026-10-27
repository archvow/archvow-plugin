"""Check the *actual Gradle buildPlugin distribution*, not merely source plugin.xml.

Usage:
    python tools/verify_built_plugin.py
    python tools/verify_built_plugin.py build/distributions/Archvow-0.3.1.zip

The IntelliJ Platform Gradle Plugin rewrites META-INF/plugin.xml during build;
checking source plugin.xml cannot detect an unexpected default until-build.
"""
from __future__ import annotations

import io
from pathlib import Path
import sys
from zipfile import BadZipFile, ZipFile
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_ID = "org.archvow.intellij"
EXPECTED_SINCE = 241
EXPECTED_UNTIL_BRANCH = 262


def descriptor_from_distribution(distribution: Path) -> tuple[str, str, str, str]:
    """Return (id, version, since, until) from plugin.xml inside bundled JAR."""
    with ZipFile(distribution) as artifact:
        jars = sorted(n for n in artifact.namelist() if n.endswith('.jar') and '/lib/' in n)
        for jar_name in jars:
            with ZipFile(io.BytesIO(artifact.read(jar_name))) as jar:
                if 'META-INF/plugin.xml' not in jar.namelist():
                    continue
                plugin = ET.fromstring(jar.read('META-INF/plugin.xml'))
                if plugin.findtext('id') != EXPECTED_ID:
                    continue
                idea = plugin.find('idea-version')
                if idea is None:
                    raise ValueError(f'No idea-version in {jar_name}!')
                return (plugin.findtext('id') or '', plugin.findtext('version') or '',
                        idea.get('since-build') or '', idea.get('until-build') or '')
    raise ValueError(f'No Archvow plugin.xml found in {distribution}')


def supports(branch: int, since: str, until: str) -> bool:
    """Validate a *branch* against the simple branch-star version range."""
    first = int(since.split('.')[0])
    if not until.endswith('.*'):
        raise ValueError(f'Unexpected until-build format {until!r}')
    last = int(until[:-2])
    return first <= branch <= last


def verify(distribution: Path) -> str:
    plugin_id, version, since, until = descriptor_from_distribution(distribution)
    if since != str(EXPECTED_SINCE) or until != f'{EXPECTED_UNTIL_BRANCH}.*':
        raise ValueError(f'Incorrect IDE range in compiled ZIP: {since} → {until}; expected 241 → 262.*')
    assert supports(241, since, until)
    assert supports(252, since, until)
    assert supports(262, since, until)
    assert not supports(263, since, until)
    return f'OK: {plugin_id} {version}; since-build={since}, until-build={until} (IDE branches 241, 252, 262)'


def verify_embedded_icons(distribution: Path) -> str:
    """Check that Plugin Manager logos are actually in the main plugin JAR."""
    required = {'META-INF/pluginIcon.svg', 'META-INF/pluginIcon_dark.svg'}
    with ZipFile(distribution) as archive:
        for name in archive.namelist():
            if not name.endswith('.jar') or '/lib/' not in name:
                continue
            with ZipFile(io.BytesIO(archive.read(name))) as jar:
                names = set(jar.namelist())
                if 'META-INF/plugin.xml' not in names:
                    continue
                descriptor = ET.fromstring(jar.read('META-INF/plugin.xml'))
                if descriptor.findtext('id') != EXPECTED_ID:
                    continue
                missing = required - names
                if missing:
                    raise ValueError('Missing Plugin Manager icons in main JAR: ' + ', '.join(sorted(missing)))
                return 'Plugin Manager icons packaged: ' + ', '.join(sorted(required))
    raise ValueError('Archvow plugin main JAR not found')


def main(argv: list[str]) -> int:
    if argv:
        dist = Path(argv[0])
    else:
        built = list((ROOT / 'build' / 'distributions').glob('*.zip'))
        if not built:
            print('No build/distributions/*.zip found. Run ./gradlew clean buildPlugin first.', file=sys.stderr)
            return 2
        dist = max(built, key=lambda p: p.stat().st_mtime)
    try:
        print(verify(dist))
        print(verify_embedded_icons(dist))
        return 0
    except (OSError, ValueError, BadZipFile, ET.ParseError, AssertionError) as exc:
        print(f'FAILED: {dist}: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main(sys.argv[1:]))
