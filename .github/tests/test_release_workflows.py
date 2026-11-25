"""Exercise release asset validation and retry source checks without network access."""

import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]


def step_script(workflow, step):
    text = (ROOT / '.github/workflows' / workflow).read_text()
    body = text.split(f'- name: {step}\n', 1)[1].split('        run: |\n', 1)[1]
    lines = []
    for line in body.splitlines():
        if line and not line.startswith('          '):
            break
        lines.append(line[10:])
    return '\n'.join(lines)


class ReleaseWorkflowsTest(unittest.TestCase):
    def test_nuget_cache_tracks_sdk_and_dependencies_without_writing_on_pull_requests(self):
        text = (ROOT / '.github/workflows/reusable-desktop-windows.yml').read_text()
        writable = text.split('      - name: Cache NuGet packages\n', 1)[1].split('      - name:', 1)[0]
        read_only = text.split('      - name: Restore NuGet packages (read-only)\n', 1)[1].split('      - name:', 1)[0]
        self.assertIn('if: ${{ !inputs.cache-read-only }}', writable)
        self.assertIn('uses: actions/cache@', writable)
        self.assertIn('if: inputs.cache-read-only', read_only)
        self.assertIn('uses: actions/cache/restore@', read_only)
        self.assertEqual(writable.split('key: ')[1].strip(), read_only.split('key: ')[1].strip())
        for cache in (writable, read_only):
            self.assertIn('path: ~/.nuget/packages', cache)
            self.assertIn('runner.os', cache)
            self.assertIn('runner.arch', cache)
            self.assertIn("hashFiles('metro-wv2-helper/global.json', 'metro-wv2-helper/**/*.csproj')", cache)
            self.assertNotIn('restore-keys:', cache)
        self.assertIn('dotnet run --project tests/LoginCompletion.Tests.csproj', text)

if __name__ == '__main__':
    unittest.main()
