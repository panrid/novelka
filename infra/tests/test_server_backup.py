"""Exercise the production shell entry point with injected pg_dump/tar failures."""
import datetime as dt
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).parents[1] / 'backup.sh'


class ServerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.backups = self.root / 'backups'
        self.media = self.root / 'media'
        self.bin = self.root / 'bin'
        for path in (self.backups, self.media, self.bin):
            path.mkdir()
        (self.media / 'image.txt').write_text('media fixture')
        self.env = dict(os.environ, BACKUP_DIR=str(self.backups), MEDIA_DIR=str(self.media),
                        PATH=str(self.bin) + ':' + os.environ['PATH'])
        self.day = str(dt.datetime.now(dt.timezone.utc).date())
        self.stub('pg_dump', '''while [ "$1" != -f ]; do shift; done
shift
case "${FAIL_DUMP:-}" in
 error) echo partial > "$1"; exit 1 ;;
 empty) : > "$1" ;;
 *) echo PGDMP-fixture > "$1" ;;
esac''')
        self.stub('pg_restore', '[ "${FAIL_RESTORE:-}" != yes ]')
        real_tar = shutil.which('tar')
        self.stub('tar', f'''case "${{FAIL_TAR:-}}" in
 error) exit 1 ;;
 empty) : > "$2" ;;
 *) exec {real_tar} "$@" ;;
esac''')
        if not shutil.which('flock'):
            # macOS lacks util-linux flock. Shell locking is tested on Linux CI;
            # the offsite Python tests exercise the real OS lock on both systems.
            self.stub('flock', 'exit 0')

    def stub(self, name, body):
        path = self.bin / name
        path.write_text('#!/bin/sh\n' + body + '\n')
        path.chmod(0o755)

    def run_script(self, **extra):
        return subprocess.run(['bash', str(SCRIPT), 'once'], env=dict(self.env, **extra),
                              capture_output=True, text=True)

    def test_success_and_repeat(self):
        first = self.run_script()
        self.assertEqual(first.returncode, 0, first.stderr)
        manifest = self.backups / f'novelka-{self.day}.sha256'
        self.assertTrue(manifest.is_file())
        success = (self.backups / 'status/last-success').read_bytes()
        second = self.run_script(FAIL_DUMP='error', FAIL_TAR='error')
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertEqual(success, (self.backups / 'status/last-success').read_bytes())

    def test_failures_are_explicit_and_keep_previous_files(self):
        for failure in ({'FAIL_DUMP': 'error'}, {'FAIL_DUMP': 'empty'},
                        {'FAIL_RESTORE': 'yes'}, {'FAIL_TAR': 'error'}, {'FAIL_TAR': 'empty'}):
            with self.subTest(failure=failure):
                old = self.backups / 'novelka-2000-01-01.dump'
                old.write_bytes(b'old successful backup')
                os.utime(old, (1, 1))
                result = self.run_script(**failure)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('BACKUP FAILED', result.stderr)
                self.assertNotIn('BACKUP OK', result.stdout)
                self.assertTrue(old.exists())
                self.assertFalse((self.backups / f'novelka-{self.day}.sha256').exists())
                self.assertIn('last_attempt_ok 0', (self.backups / 'status/server.prom').read_text())
                self.assertFalse(list(self.backups.glob('.pair-*')))

    def test_bad_existing_pair_is_not_claimed_as_success(self):
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        (self.backups / f'media-{self.day}.tgz').write_bytes(b'corrupt')
        result = self.run_script()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('BACKUP FAILED', result.stderr)

    @unittest.skipUnless(shutil.which('flock'), 'util-linux flock is checked on Linux CI')
    def test_real_server_lock_blocks_second_process(self):
        import fcntl
        with (self.backups / '.backup.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((self.backups / f'novelka-{self.day}.sha256').exists())


if __name__ == '__main__':
    unittest.main()
