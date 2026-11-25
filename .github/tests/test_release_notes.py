"""Check stable notes extraction, preflight failures and draft publication offline."""

import os
from pathlib import Path
import re
import subprocess
import tempfile
import textwrap
import unittest

from test_release_workflows import step_script

ROOT = Path(__file__).resolve().parents[2]
PARSER = ROOT / '.github/scripts/parse_changelog.sh'


class ChangelogTest(unittest.TestCase):
    def test_extracts_real_14_entry_with_default_path_and_optional_v(self):
        for version in ('14.0.0', 'v14.0.0'):
            with self.subTest(version=version):
                result = subprocess.run(['bash', str(PARSER), version], cwd=ROOT, capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertTrue(result.stdout.startswith('# First public multiplatform release\n'))
                self.assertIn('## Known limitations\n', result.stdout)
                self.assertIn('Full changelog: https://github.com/MetrolistGroup/Metrolist/commits/v14.0.0\n', result.stdout)
                self.assertNotIn('---v', result.stdout)
                self.assertNotIn('KMP status update', result.stdout)

    def test_exact_version_and_entry_boundaries(self):
        with tempfile.TemporaryDirectory() as directory:
            changelog = Path(directory) / 'changelog.md'
            changelog.write_text('---v14x0x0\nWrong version\n---v14.0.0\nMastodon\n\n---v13.7.0\nTODO\n')
            result = subprocess.run(['bash', str(PARSER), '14.0.0', str(changelog)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual('Mastodon\n', result.stdout)
            changelog.write_text('---v14x0x0\nWrong version\n')
            result = subprocess.run(['bash', str(PARSER), '14.0.0', str(changelog)], capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('not found', result.stderr)
            self.assertEqual('', result.stdout)

    def test_rejects_empty_entries_and_todo_markers_without_output(self):
        for entry, error in (
            ('\n \t\n', 'empty changelog entry'),
            ('TODO: finish\n', 'TODO marker'),
            ('# Notes\n<!-- todo -->\n', 'TODO marker'),
            ('- Todo_fix\n', 'TODO marker'),
        ):
            with self.subTest(entry=entry), tempfile.TemporaryDirectory() as directory:
                changelog = Path(directory) / 'changelog.md'
                changelog.write_text(f'---v14.0.0\n{entry}---v13.7.0\nOld notes\n')
                result = subprocess.run(['bash', str(PARSER), '14.0.0', str(changelog)], capture_output=True, text=True)
                self.assertNotEqual(0, result.returncode)
                self.assertIn(error, result.stderr)
                self.assertEqual('', result.stdout)

    def test_missing_arguments_and_file(self):
        for arguments, error in (([], 'version argument required'), (['14.0.0', 'missing.md'], 'file not found')):
            with self.subTest(arguments=arguments), tempfile.TemporaryDirectory() as directory:
                result = subprocess.run(['bash', str(PARSER), *arguments], cwd=directory, capture_output=True, text=True)
                self.assertNotEqual(0, result.returncode)
                self.assertIn(error, result.stderr)


class StableReleaseNotesTest(unittest.TestCase):
    def fixture(self, root, notes='Finished notes\n', version_code='987'):
        (root / '.github').mkdir()
        (root / '.github/scripts').symlink_to(PARSER.parent, target_is_directory=True)
        (root / 'docs').mkdir()
        (root / 'docs/changelog.md').write_text(f'---v14.0.0\n{notes}\n---v13.7.0\nOld notes\n')
        (root / 'androidApp').mkdir()
        (root / 'androidApp/build.gradle.kts').write_text(f'    versionCode = {version_code}\n')
        fastlane = root / f'fastlane/metadata/android/en-US/changelogs/{version_code}.txt'
        fastlane.parent.mkdir(parents=True)
        fastlane.write_text('Store notes\n')
        return fastlane

    def test_real_fastlane_note_matches_android_code_and_store_limit(self):
        build = (ROOT / 'androidApp/build.gradle.kts').read_text()
        code = re.search(r'^\s*versionCode\s*=\s*(\d+)\s*$', build, re.MULTILINE)[1]
        notes = (ROOT / f'fastlane/metadata/android/en-US/changelogs/{code}.txt').read_text()
        self.assertTrue(notes.strip())
        self.assertLessEqual(len(notes), 500)

if __name__ == '__main__':
    unittest.main()
