"""Regression checks for beta/stable Marketplace version allocation (offline)."""
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / 'release-version.py'
spec = importlib.util.spec_from_file_location('archvow_release_version', SCRIPT)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseVersionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        def git(*args):
            return subprocess.check_output(['git', *args], cwd=self.root, text=True).strip()
        self.git = git
        git('init', '-q', '-b', 'main')
        git('config', 'user.email', 'ci@example.invalid')
        git('config', 'user.name', 'ArchVow CI')
        (self.root / 'anchor').write_text('archvow 0.19.0\n')
        git('add', 'anchor')
        git('commit', '-qm', 'version anchor')
        release.BASE_COMMIT = git('rev-parse', 'HEAD')
        git('commit', '--allow-empty', '-qm', 'release 0.19.1')

    def test_distinct_versions(self):
        self.assertEqual(release.release_version('stable', self.root), '0.19.1')
        self.assertEqual(release.release_version('beta', self.root), '0.19.2')
        with self.assertRaises(SystemExit):
            release.release_version('candidate', self.root)

    def test_beta_merge_advances_stable(self):
        self.git('switch', '-q', '-c', 'beta')
        self.git('commit', '--allow-empty', '-qm', 'candidate A')
        v1 = release.release_version('beta', self.root)
        self.git('commit', '--allow-empty', '-qm', 'candidate B')
        v2 = release.release_version('beta', self.root)
        self.assertEqual((v1, v2), ('0.19.4', '0.19.6'))
        self.git('switch', '-q', 'main')
        self.git('merge', '--no-ff', '-qm', 'promote beta', 'beta')
        stable = release.release_version('stable', self.root)
        self.assertEqual(stable, '0.19.7')
        self.assertGreater(tuple(map(int, stable.split('.'))),
                           tuple(map(int, v2.split('.'))))

    def test_apply_versions_for_beta_without_suffix(self):
        for d in ['vscode', 'intellij', 'contract/cli']:
            (self.root / d).mkdir(parents=True)
        (self.root / 'vscode/package-lock.json').write_text(
            '{\n  "version": "0.19.1",\n  "packages": {\n    "": {\n      "version": "0.19.1",\n      "dummy": true\n    }\n  }\n}\n')
        (self.root / 'vscode/package.json').write_text(
            '{\n  "version": "0.19.1",\n  "name": "archvow"\n}\n')
        (self.root / 'intellij/gradle.properties').write_text('pluginVersion=0.19.1\n')
        (self.root / 'README.md').write_text(
            'Both plugins have the same version (0.19.1)\n'
            'dist/archvow-intellij-0.19.1.zip, dist/archvow-0.19.1.vsix\n')
        (self.root / 'contract/cli/version.txt').write_text('archvow 1.22.0\n')
        for d in ['vscode', 'intellij']:
            (self.root / d / 'CHANGELOG.md').write_text(
                '## 0.19.1 - 2026-11-09\n\nBuilt on archvow 1.22.\n')
        release.apply_version('0.19.4', self.root, 'beta')
        self.assertEqual(json.loads((self.root / 'vscode/package.json').read_text())['version'], '0.19.4')
        self.assertIn('pluginVersion=0.19.4', (self.root / 'intellij/gradle.properties').read_text())
        for d in ['vscode', 'intellij']:
            changes = (self.root / d / 'CHANGELOG.md').read_text()
            self.assertTrue(changes.startswith('## 0.19.4'))
            self.assertIn('Marketplace beta release', changes)
        with self.assertRaises(SystemExit):
            release.apply_version('0.19.4-beta', self.root, 'beta')


if __name__ == '__main__':
    unittest.main()
