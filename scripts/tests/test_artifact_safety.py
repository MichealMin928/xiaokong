import hashlib
import importlib.util
import io
import tarfile
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('fetch_artifacts', Path(__file__).parents[1] / 'fetch-artifacts.py')
fetch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fetch)


class ArtifactSafetyTest(unittest.TestCase):
    def test_destination_rejects_outside_project(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                fetch.destination(Path(directory), '../outside')

    def test_corrupt_file_does_not_replace_existing_destination(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root/'download'; archive.write_bytes(b'corrupt')
            target = root/'model'; target.write_bytes(b'previous')
            artifact = {'files':[{'path':'model','bytes':7,'sha256':hashlib.sha256(b'correct').hexdigest()}]}
            with self.assertRaises(ValueError):
                fetch.install(artifact, archive, root)
            self.assertEqual(b'previous', target.read_bytes())

    def test_archive_only_copies_named_regular_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); archive = root/'bundle.tar.bz2'; data = b'model'
            with tarfile.open(archive, 'w:bz2') as bundle:
                for name in ('model.onnx', '../escaped'):
                    item = tarfile.TarInfo(name); item.size = len(data)
                    bundle.addfile(item, io.BytesIO(data))
            artifact = {'format':'tar.bz2','files':[{'member':'model.onnx','path':'assets/model','bytes':len(data),
                       'sha256':hashlib.sha256(data).hexdigest()}]}
            fetch.install(artifact, archive, root)
            self.assertEqual(data, (root/'assets/model').read_bytes())
            self.assertEqual({'assets','bundle.tar.bz2'}, {p.name for p in root.iterdir()})

    def test_archive_symlink_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); archive = root/'bundle.tar.bz2'
            with tarfile.open(archive, 'w:bz2') as bundle:
                item = tarfile.TarInfo('model'); item.type = tarfile.SYMTYPE; item.linkname = '/etc/passwd'
                bundle.addfile(item)
            artifact = {'format':'tar.bz2','files':[{'member':'model','path':'assets/model','bytes':0,'sha256':''}]}
            with self.assertRaises(ValueError):
                fetch.install(artifact, archive, root)


if __name__ == '__main__':
    unittest.main()
