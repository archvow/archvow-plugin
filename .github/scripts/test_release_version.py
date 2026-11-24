"""The release a tag names (1.1.0): its version and channel, refused unless every release file says the same version."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / 'release-version.py'
spec = importlib.util.spec_from_file_location('archvow_release_version', SCRIPT)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTagTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.set_version('1.1.0')

    def set_version(self, version, changelog=None):
        for folder in ('vscode', 'intellij'):
            (self.root / folder).mkdir(exist_ok=True)
            (self.root / folder / 'CHANGELOG.md').write_text(f'# Changelog\n\n## {changelog or version} - 2026-11-24\n\n- what changed\n')
        (self.root / 'vscode/package.json').write_text(json.dumps({'name': 'archvow', 'version': version}, indent=2))
        (self.root / 'intellij/gradle.properties').write_text(f'# the plugin\'s own version\npluginVersion={version}\n')

    def test_a_stable_tag_and_a_beta_tag(self):
        self.assertEqual(release.check_tag('v1.1.0', self.root), ('1.1.0', 'stable'))
        self.set_version('1.2.0')
        self.assertEqual(release.check_tag('v1.2.0-beta', self.root), ('1.2.0', 'beta'))

    def test_a_tag_that_is_not_a_release_is_refused(self):
        for tag in ('1.1.0', 'v1.1', 'v1.1.0-rc1', 'release-1.1.0', 'v1.1.0-beta.2'):
            with self.assertRaises(SystemExit, msg=tag):
                release.check_tag(tag, self.root)

    def test_a_tag_and_files_that_disagree_publish_nothing(self):
        with self.assertRaises(SystemExit) as refused:
            release.check_tag('v1.1.1', self.root)
        self.assertIn('vscode/package.json says 1.1.0', str(refused.exception))
        self.set_version('1.1.1', changelog='1.1.0')  # the manifests moved, the changelogs did not
        with self.assertRaises(SystemExit) as refused:
            release.check_tag('v1.1.1', self.root)
        self.assertIn('vscode/CHANGELOG.md says 1.1.0', str(refused.exception))

    def test_this_repository_says_one_version(self):
        said = release.versions_said()
        self.assertEqual(len(set(said.values())), 1, said)


if __name__ == '__main__':
    unittest.main()
