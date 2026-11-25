"""Check nightly build routing and publication without GitHub or signing credentials."""

import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import textwrap
import unittest

from test_release_workflows import ROOT, step_script

WORKFLOW = ROOT / '.github/workflows/nightly.yml'


class NightlyWorkflowTest(unittest.TestCase):
    def test_version_adds_commit_build_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for declared in ('14.0.0', '14.1.0-rc1', '14.0.0+old'):
                (root / 'gradle.properties').write_text(f'org.gradle.caching=true\nappVersion={declared}\n')
                output = root / 'output'
                output.write_text('')
                result = self.run_step('Add the commit as build metadata', root, GITHUB_OUTPUT=str(output))
                self.assertEqual(0, result.returncode, result.stderr)
                value = output.read_text().strip().removeprefix('value=')
                self.assertEqual(declared.split('+')[0] + '+abcdef0', value)

    def run_step(self, step, directory, **env):
        return subprocess.run(
            ['bash', '-c', step_script('nightly.yml', step)], cwd=directory,
            env={**os.environ, 'GITHUB_SHA': 'abcdef0123456789', 'GITHUB_RUN_NUMBER': '42',
                 'GITHUB_REPOSITORY': 'test/repo', 'GH_REPO': 'test/repo',
                 'RUNNER_TEMP': str(directory), 'APP_VERSION': '14.0.0+abcdef0', **env},
            capture_output=True, text=True,
        )

    def git(self, directory, *args):
        return subprocess.check_output(['git', *args], cwd=directory, text=True).strip()

    def fake_bin(self, root, previous, release, completion):
        """Fake gh (nightly body and latest release) and curl (OpenRouter reply, or failure when None)."""
        bin_dir = root / 'bin'
        bin_dir.mkdir(exist_ok=True)
        (root / 'requests.txt').write_text('')
        gh = bin_dir / 'gh'
        gh.write_text(textwrap.dedent(f'''\
            #!/usr/bin/env bash
            if [[ "$3" == nightly ]]; then
              [[ -n "{previous}" ]] || exit 1
              printf '%s\\n' "{previous}"
            else
              [[ -n "{release}" ]] || exit 1
              printf '%s\\n' "{release}"
            fi
            '''))
        curl = bin_dir / 'curl'
        reply = 'exit 22' if completion is None else f"printf '%s' {json.dumps(json.dumps({'choices': [{'message': {'content': completion}}]}))}"
        curl.write_text(f'#!/usr/bin/env bash\ncat >> {root}/requests.txt\necho >> {root}/requests.txt\n{reply}\n')
        for tool in (gh, curl):
            tool.chmod(0o755)
        return {'PATH': f'{bin_dir}:{os.environ["PATH"]}'}

    def test_publication_replaces_old_assets_and_never_marks_latest(self):
        for existing in (False, True):
            with self.subTest(existing=existing), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.git(root, 'init', '-q')
                self.git(root, 'config', 'user.email', 'nightly@example.invalid')
                self.git(root, 'config', 'user.name', 'Nightly test')
                self.git(root, 'commit', '-q', '--allow-empty', '-m', 'source')
                assets = ['Mochy.apk',
                          'Metrolist-Desktop-linux-x64.AppImage',
                          'Metrolist-Desktop-windows.zip']
                (root / 'nightly-assets').mkdir()
                for name in assets:
                    (root / 'nightly-assets' / name).write_bytes(b'package')
                (root / 'artifacts/notes').mkdir(parents=True)
                (root / 'artifacts/notes/nightly-notes.md').write_text('Built from source\n')
                (root / 'state.json').write_text(json.dumps({
                    'exists': existing, 'assets': ['legacy.apk', 'obsolete.zip'] if existing else [],
                    'calls': [], 'draft': False,
                }))
                gh = root / 'gh'
                gh.write_text(textwrap.dedent('''\
                    #!/usr/bin/env python3
                    import json
                    from pathlib import Path
                    import sys

                    args = sys.argv[1:]
                    state = json.loads(Path('state.json').read_text())
                    state['calls'].append(args)
                    status = 0
                    if args[:2] == ['release', 'view']:
                        if not state['exists']:
                            status = 1
                        else:
                            print('\\n'.join(state['assets']))
                    elif args[:2] in (['release', 'create'], ['release', 'edit']):
                        assert '--prerelease' in args and '--latest=false' in args
                        state['exists'] = True
                        state['draft'] = '--draft=false' not in args
                        if not state['draft']:
                            assert len(state['assets']) == 3
                            assert '--notes-file' in args
                    elif args[:2] == ['release', 'delete-asset']:
                        assert state['draft']
                        state['assets'].remove(args[3])
                    elif args[:2] == ['release', 'upload']:
                        assert state['draft']
                        state['assets'] = [Path(p).name for p in args[3:] if not p.startswith('--')]
                    else:
                        raise AssertionError(args)
                    Path('state.json').write_text(json.dumps(state))
                    sys.exit(status)
                    '''))
                gh.chmod(0o755)
                result = self.run_step('Publish nightly prerelease', root, PATH=f'{root}:{os.environ["PATH"]}')
                self.assertEqual(0, result.returncode, result.stderr)
                state = json.loads((root / 'state.json').read_text())
                self.assertFalse(state['draft'])
                self.assertEqual(set(assets), set(state['assets']))
                calls = state['calls']
                if existing:
                    self.assertEqual(['legacy.apk', 'obsolete.zip'],
                                     [call[3] for call in calls if call[:2] == ['release', 'delete-asset']])
                else:
                    create = next(call for call in calls if call[:2] == ['release', 'create'])
                    self.assertIn('abcdef0123456789', create)
                self.assertIn('--target', calls[-1])
                self.assertIn('abcdef0123456789', calls[-1])
                self.assertIn('artifacts/notes/nightly-notes.md', calls[-1])
                self.assertIn('--draft=false', calls[-1])


if __name__ == '__main__':
    unittest.main()
