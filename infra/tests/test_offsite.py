"""Failure injection: no Google credentials, real backups, or paid services used."""
import datetime as dt
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('offsite', Path(__file__).parents[1] / 'offsite/backup.py')
m = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(m)


class FakeDrive(m.DriveBackup):
    def __init__(self, cloud):
        super().__init__()
        self.cloud = cloud
        self.fail_upload = False
        self.corrupt_download = False
        self.fail_after_rename = False
        self.uploads = 0
        self.encryption_fails = False

    def command(self, args):
        assert args[0] == 'age'
        if self.encryption_fails:
            raise m.BackupError('age failed')
        # Explicit stand-in, NOT an age cryptographic test.
        Path(args[args.index('--output') + 1]).write_bytes(b'fake-ciphertext:' + Path(args[-1]).read_bytes())
        return b''

    def rc(self, *args):
        verb = args[0]
        def remote(path):
            assert path.startswith(self.remote + '/')
            return self.cloud / path[len(self.remote) + 1:]
        if verb == 'mkdir':
            return b''
        if verb == 'lsjson':
            return json.dumps([{'Name': p.name, 'Size': p.stat().st_size,
                                'Hashes': {'MD5': m.digest(p, 'md5')}}
                               for p in self.cloud.iterdir()]).encode()
        if verb == 'copyto':
            if args[1].startswith(self.remote):
                shutil.copyfile(remote(args[1]), args[2])
                if self.corrupt_download:
                    Path(args[2]).write_bytes(b'corrupted')
            else:
                self.uploads += 1
                if self.fail_upload:
                    remote(args[2]).write_bytes(b'partial')
                    raise m.BackupError('network interrupted')
                shutil.copyfile(args[1], remote(args[2]))
            return b''
        if verb == 'moveto':
            remote(args[1]).rename(remote(args[2]))
            if self.fail_after_rename:
                raise m.BackupError('connection lost after rename')
            return b''
        if verb == 'deletefile':
            remote(args[1]).unlink()
            return b''
        raise AssertionError(args)


class OffsiteTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name in ('source', 'cloud'):
            (self.root / name).mkdir()
        recipient = self.root / 'recipient'
        recipient.write_text('age1' + 'q' * 58)
        env = {'BACKUP_DIR': str(self.root / 'source'), 'STATE_DIR': str(self.root / 'state'),
               'WORK_DIR': str(self.root / 'work'), 'AGE_RECIPIENT_FILE': str(recipient)}
        self.env = patch.dict(os.environ, env)
        self.env.start()
        self.addCleanup(self.env.stop)
        self.worker = FakeDrive(self.root / 'cloud')
        self.today = dt.datetime.now(m.UTC).date()
        self.pair(self.today)

    def pair(self, date):
        day = date.isoformat()
        names = [f'novelka-{day}.dump', f'media-{day}.tgz']
        lines = []
        for name in names:
            path = self.worker.source / name
            path.write_bytes(('fixture-' + name).encode())
            lines.append(f'{m.digest(path)}  {name}\n')
        (self.worker.source / f'novelka-{day}.sha256').write_text(''.join(lines))

    def test_success_repeat_does_not_upload_or_refresh_success(self):
        self.worker.run()
        before = (self.worker.state / 'last-success.json').read_bytes()
        self.worker.run()
        self.assertEqual(self.worker.uploads, 1)
        self.assertEqual(before, (self.worker.state / 'last-success.json').read_bytes())
        self.assertEqual(len(list(self.worker.cloud.iterdir())), 1)
        self.assertFalse((self.worker.work / 'plaintext').exists())

    def test_interrupted_upload_recovers_without_final_partial(self):
        self.worker.fail_upload = True
        with self.assertRaises(m.BackupError):
            self.worker.run()
        self.assertTrue(self.worker.pending.exists())
        self.assertTrue(all(p.name.endswith('.upload') for p in self.worker.cloud.iterdir()))
        self.assertFalse((self.worker.state / 'last-success.json').exists())
        self.worker.fail_upload = False
        self.worker.run()
        self.assertFalse(self.worker.pending.exists())
        self.assertEqual(len(list(self.worker.cloud.iterdir())), 1)

    def test_crash_after_remote_rename_recovers_without_second_upload(self):
        self.worker.fail_after_rename = True
        with self.assertRaises(m.BackupError):
            self.worker.run()
        self.worker.fail_after_rename = False
        self.worker.run()
        self.assertEqual(self.worker.uploads, 1)

    def test_corrupt_download_is_not_success(self):
        self.worker.corrupt_download = True
        with self.assertRaises(m.BackupError):
            self.worker.run()
        self.assertFalse((self.worker.state / 'last-success.json').exists())
        self.assertTrue(all(p.name.endswith('.upload') for p in self.worker.cloud.iterdir()))

    def test_missing_media_does_not_fall_back_to_yesterday(self):
        self.pair(self.today - dt.timedelta(days=1))
        (self.worker.source / f'media-{self.today}.tgz').unlink()
        with self.assertRaises(m.BackupError):
            self.worker.run()
        self.assertEqual(self.worker.uploads, 0)

    def test_corrupt_source_and_empty_source_fail(self):
        media = self.worker.source / f'media-{self.today}.tgz'
        for content in (b'corrupted', b''):
            media.write_bytes(content)
            with self.assertRaises(m.BackupError):
                self.worker.run()
        self.assertEqual(self.worker.uploads, 0)

    def test_no_space_does_not_delete_old_backup(self):
        old = self.worker.cloud / 'old-unowned.age'
        old.write_bytes(b'keep')
        with patch.object(m.shutil, 'disk_usage', return_value=shutil._ntuple_diskusage(100, 100, 0)):
            with self.assertRaises(m.BackupError):
                self.worker.run()
        self.assertEqual(old.read_bytes(), b'keep')
        self.assertEqual(self.worker.uploads, 0)

    def test_encryption_failure_leaves_no_plaintext_or_remote_data(self):
        self.worker.encryption_fails = True
        with self.assertRaises(m.BackupError):
            self.worker.run()
        self.assertFalse((self.worker.work / 'plaintext').exists())
        self.assertFalse(list(self.worker.cloud.iterdir()))

    def test_concurrent_run_is_locked(self):
        with (self.worker.state / 'lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            self.worker.run()
        self.assertEqual(self.worker.uploads, 0)
        self.worker.run()
        self.assertEqual(self.worker.uploads, 1)

    def test_catches_up_after_missed_runs_and_rejects_stale_pair(self):
        self.worker.run()  # no scheduled callback is needed after restarting the worker
        shutil.rmtree(self.worker.source)
        self.worker.source.mkdir()
        self.pair(self.today - dt.timedelta(days=4))
        with self.assertRaises(m.BackupError):
            self.worker.run()
        self.assertEqual(self.worker.uploads, 1)

    def test_retention_only_owned_verified_files_after_success(self):
        day = self.today - dt.timedelta(days=31)
        name = f'novelka-drive-v1-{day}.tar.age'
        old = self.worker.cloud / name
        old.write_bytes(b'old')
        receipt = {'name': name, 'day': str(day), 'sha256': m.digest(old),
                   'source_sha256': 'a' * 64, 'md5': m.digest(old, 'md5'), 'size': 3}
        m.atomic_json(self.worker.state / 'receipts' / (name + '.json'), receipt)
        unowned = self.worker.cloud / 'novelka-drive-v1-2000-01-01.tar.age'
        unowned.write_bytes(b'not ours')
        self.worker.fail_upload = True
        with self.assertRaises(m.BackupError):
            self.worker.run()
        self.assertTrue(old.exists())
        self.worker.fail_upload = False
        self.worker.run()
        self.assertFalse(old.exists())
        self.assertTrue(unowned.exists())

    def test_missing_or_changed_confirmed_archive_is_not_marked_success(self):
        self.worker.run()
        next(self.worker.cloud.iterdir()).unlink()
        with self.assertRaises(m.BackupError):
            self.worker.run()
        self.assertIn('last_attempt_ok 0', (self.worker.state / 'drive.prom').read_text())

    def test_plaintext_left_by_crash_removed_before_retry(self):
        plain = self.worker.work / 'plaintext'
        plain.mkdir()
        (plain / 'private.dump').write_bytes(b'fixture')
        self.worker.run()
        self.assertFalse(plain.exists())


if __name__ == '__main__':
    unittest.main()
