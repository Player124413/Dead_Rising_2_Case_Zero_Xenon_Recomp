import hashlib
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/android'))
from package_sources import archive


class SourceArchiveTest(unittest.TestCase):
    def fixture(self, directory):
        repo = Path(directory) / 'source'
        repo.mkdir()
        # Isolated test repo, no changes to the session repository or its branch.
        subprocess.run(['git', 'init', '-q', '--initial-branch=arena/01a07720-dead-rising-2-case-zero-xenon', str(repo)], check=True)
        (repo / 'file.c').write_text('int library_example(void) { return 7; }\n')
        subprocess.run(['git', '-C', str(repo), 'add', 'file.c'], check=True)
        subprocess.run(['git', '-C', str(repo), '-c', 'user.name=Source test', '-c', 'user.email=source-test@example.invalid',
                        'commit', '-qm', 'Synthetic source fixture'], check=True)
        return repo

    def test_exact_sources_without_git_or_local_metadata(self):
        with tempfile.TemporaryDirectory() as d:
            repo = self.fixture(d)
            revision = subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip()
            (repo / '.cz-dependency').write_text(revision)
            output = Path(d) / 'source.tar.gz'
            self.assertEqual(archive(repo, 'source', output, revision), revision)
            with tarfile.open(output) as tar:
                self.assertEqual(tar.getnames(), ['source', 'source/file.c'])
                self.assertEqual(tar.extractfile('source/file.c').read(), (repo / 'file.c').read_bytes())
            first = hashlib.sha256(output.read_bytes()).digest()
            archive(repo, 'source', output, revision)
            self.assertEqual(first, hashlib.sha256(output.read_bytes()).digest())

    def test_refuse_wrong_revision_and_omitted_changes(self):
        with tempfile.TemporaryDirectory() as d:
            repo = self.fixture(d)
            output = Path(d) / 'source.tar.gz'
            with self.assertRaises(ValueError): archive(repo, 'source', output, '0' * 40)
            (repo / 'untracked.txt').write_text('Do not leak this or omit modified library input')
            with self.assertRaises(ValueError): archive(repo, 'source', output)
            (repo / 'untracked.txt').unlink()
            (repo / 'file.c').write_text('int library_example(void) { return 8; }\n')
            with self.assertRaises(ValueError): archive(repo, 'source', output)


if __name__ == '__main__':
    unittest.main()
