#!/usr/bin/env python3
"""Daily age-encrypted PostgreSQL + media pairs, verified in Google Drive.

No private key is needed. A durable pending receipt recovers uploads/renames after
crashes. Only remote files with a local success receipt are eligible for retention.
"""
import datetime as dt
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import time

UTC = dt.timezone.utc
DAY = r"\d{4}-\d{2}-\d{2}"
MANIFEST = re.compile(rf"novelka-({DAY})\.sha256")
ARCHIVE = re.compile(rf"novelka-drive-v1-({DAY})\.tar\.age")


class BackupError(Exception):
    pass


def atomic_json(path, value):
    temp = path.with_suffix(path.suffix + '.tmp')
    with temp.open('w') as stream:
        json.dump(value, stream, sort_keys=True)
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temp, path)
    # Make journal updates survive a host crash before the next upload step.
    descriptor = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def digest(path, algorithm='sha256'):
    result = hashlib.new(algorithm)
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def log(message):
    print(f'{dt.datetime.now(UTC).isoformat()} {message}', flush=True)


class DriveBackup:
    def __init__(self):
        self.source = Path(os.environ.get('BACKUP_DIR', '/backups'))
        self.state = Path(os.environ.get('STATE_DIR', '/state'))
        self.work = Path(os.environ.get('WORK_DIR', '/work'))
        self.remote = os.environ.get('DRIVE_ROOT', 'gdrive:Novelka/копії').rstrip('/')
        self.recipient = Path(os.environ.get('AGE_RECIPIENT_FILE', '/config/recipient.txt'))
        self.keep_days = 30
        for folder in (self.state, self.work, self.state / 'receipts'):
            folder.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.pending = self.state / 'pending.json'

    @staticmethod
    def command(args):
        try:
            result = subprocess.run(args, capture_output=True, timeout=3600, check=False)
        except (OSError, subprocess.TimeoutExpired) as error:
            raise BackupError(f'{args[0]}: команда недоступна або перевищила час очікування') from error
        if result.returncode:
            # Do not leak rclone configuration, OAuth response bodies, or file contents.
            raise BackupError(f'{args[0]} {args[1]}: код {result.returncode}; перевірте мережу, доступ і вільне місце')
        return result.stdout

    def rc(self, *args):
        return self.command(['rclone', *args, '--contimeout', '30s', '--timeout', '5m',
                             '--retries', '3', '--low-level-retries', '3'])

    def objects(self):
        value = json.loads(self.rc('lsjson', self.remote, '--files-only', '--hash'))
        names = {}
        for item in value:
            name = item['Name']
            if name in names:
                raise BackupError('Google Drive має дублікати імен; потрібна ручна перевірка')
            names[name] = item
        return names

    @staticmethod
    def validate_receipt(value):
        name = value.get('name', '')
        if not ARCHIVE.fullmatch(name):
            raise BackupError('Некоректне ім’я в локальному журналі')
        for key in ('sha256', 'source_sha256'):
            if not re.fullmatch('[0-9a-f]{64}', value.get(key, '')):
                raise BackupError('Некоректна контрольна сума в локальному журналі')
        if not re.fullmatch('[0-9a-f]{32}', value.get('md5', '')) or value.get('size', 0) <= 0:
            raise BackupError('Некоректний розмір або MD5 в локальному журналі')
        return value

    @staticmethod
    def matching(item, receipt):
        return (item is not None and item.get('Size') == receipt['size']
                and {key.lower(): val for key, val in item.get('Hashes', {}).items()}.get('md5', '').lower() == receipt['md5'])

    def newest(self):
        candidates = sorted(p for p in self.source.iterdir() if MANIFEST.fullmatch(p.name))
        if not candidates:
            raise BackupError('Немає завершеної пари з SHA-256 manifest')
        manifest = candidates[-1]
        day = MANIFEST.fullmatch(manifest.name)[1]
        date = dt.date.fromisoformat(day)
        age = dt.datetime.now(UTC) - dt.datetime.combine(date, dt.time(), UTC)
        if age.total_seconds() < 0 or age > dt.timedelta(hours=48):
            raise BackupError(f'Найновіша пара {day} прострочена або має дату в майбутньому')
        return day, manifest

    def prepare(self, day, manifest):
        raw = manifest.read_bytes()
        expected_names = [f'novelka-{day}.dump', f'media-{day}.tgz']
        lines = raw.decode('ascii').splitlines()
        if len(lines) != 2:
            raise BackupError('Manifest має містити рівно базу та картинки')
        entries = []
        for line, name in zip(lines, expected_names):
            match = re.fullmatch(r'([0-9a-f]{64})  (.+)', line)
            if not match or match[2] != name:
                raise BackupError('Manifest не відповідає парі за одну дату')
            path = self.source / name
            if path.is_symlink() or not path.is_file() or path.stat().st_size == 0:
                raise BackupError(f'Відсутній або порожній файл {name}')
            entries.append((path, match[1]))
        required = sum(path.stat().st_size for path, _ in entries) * 4 + 64 * 1024 * 1024
        if shutil.disk_usage(self.work).free < required:
            raise BackupError('Недостатньо місця для пакування, шифрування та перевірки')
        recipient = self.recipient.read_text().strip()
        if not re.fullmatch(r'age1[0-9a-z]{58}', recipient):
            raise BackupError('Потрібен один публічний age-ключ; приватний ключ серверу не передавати')
        plain = self.work / 'plaintext'
        plain.mkdir(mode=0o700)
        name = f'novelka-drive-v1-{day}.tar.age'
        encrypted = self.work / name
        try:
            for source, expected in entries:
                target = plain / source.name
                shutil.copyfile(source, target)
                if digest(target) != expected:
                    raise BackupError(f'SHA-256 не збігається: {source.name}')
            if manifest.read_bytes() != raw:
                raise BackupError('Пара змінилася під час копіювання')
            (plain / 'SHA256SUMS').write_bytes(raw)
            with tarfile.open(plain / 'pair.tar', 'w') as archive:
                for filename in [*expected_names, 'SHA256SUMS']:
                    archive.add(plain / filename, arcname=filename, recursive=False)
            # rclone never sees this directory; only ciphertext is uploaded.
            part = self.work / 'ciphertext.part'
            self.command(['age', '--encrypt', '--recipient', recipient, '--output', str(part), str(plain / 'pair.tar')])
            if not part.is_file() or part.stat().st_size == 0:
                raise BackupError('age не створив непорожній архів')
            with part.open('rb') as stream:
                os.fsync(stream.fileno())
            os.replace(part, encrypted)
            receipt = {'name': name, 'day': day, 'size': encrypted.stat().st_size,
                       'sha256': digest(encrypted), 'md5': digest(encrypted, 'md5'),
                       'source_sha256': hashlib.sha256(raw).hexdigest()}
            atomic_json(self.pending, receipt)
            return receipt
        finally:
            shutil.rmtree(plain)

    def download_check(self, name, receipt):
        target = self.work / 'verification.age'
        try:
            self.rc('copyto', f'{self.remote}/{name}', str(target), '--ignore-times')
            if target.stat().st_size != receipt['size'] or digest(target) != receipt['sha256']:
                raise BackupError('Завантажений із Google Drive архів не пройшов SHA-256')
        finally:
            target.unlink(missing_ok=True)

    def complete(self, receipt):
        receipt = self.validate_receipt(receipt)
        name = receipt['name']
        encrypted = self.work / name
        items = self.objects()
        if name not in items:
            if (not encrypted.is_file() or encrypted.stat().st_size != receipt['size']
                    or digest(encrypted) != receipt['sha256']):
                raise BackupError('Немає справного зашифрованого файла для продовження; pending збережено')
            staging = name + '.upload'
            if not self.matching(items.get(staging), receipt):
                self.rc('copyto', str(encrypted), f'{self.remote}/{staging}', '--ignore-times')
            items = self.objects()
            if not self.matching(items.get(staging), receipt):
                raise BackupError('Розмір або MD5 завантаження не збігаються')
            self.download_check(staging, receipt)
            self.rc('moveto', f'{self.remote}/{staging}', f'{self.remote}/{name}')
            items = self.objects()
        if not self.matching(items.get(name), receipt):
            raise BackupError('Наявний архів Google Drive не відповідає локальному журналу; не перезаписую')
        # Also covers a crash after remote rename and before local receipt persistence.
        self.download_check(name, receipt)
        receipt.setdefault('verified_at', int(time.time()))
        atomic_json(self.pending, receipt)
        atomic_json(self.state / 'receipts' / (name + '.json'), receipt)
        atomic_json(self.state / 'last-success.json', receipt)
        self.pending.unlink(missing_ok=True)
        encrypted.unlink(missing_ok=True)
        log(f'GOOGLE DRIVE OK: {name}; повторне завантаження й SHA-256 перевірено')

    def prune(self):
        cutoff = dt.datetime.now(UTC).date() - dt.timedelta(days=self.keep_days)
        items = self.objects()
        for path in sorted((self.state / 'receipts').glob('*.json')):
            receipt = self.validate_receipt(json.loads(path.read_text()))
            name = receipt['name']
            date = dt.date.fromisoformat(ARCHIVE.fullmatch(name)[1])
            if date >= cutoff:
                continue
            if name in items:
                if not self.matching(items[name], receipt):
                    raise BackupError(f'Старий файл {name} змінено; очищення зупинено')
                # Exact owned file only. Do not empty the user's Drive trash.
                self.rc('deletefile', f'{self.remote}/{name}', '--drive-use-trash=false')
            path.unlink()
            log(f'Видалено власну копію старше 30 днів: {name}')

    def metrics(self, ok):
        last = self.state / 'last-success.json'
        value = json.loads(last.read_text()) if last.exists() else {}
        timestamp = value.get('verified_at', 0)
        source_time = int(dt.datetime.combine(dt.date.fromisoformat(value['day']), dt.time(), UTC).timestamp()) if value else 0
        text = (f'novelka_backup_drive_last_attempt_ok {int(ok)}\n'
                f'novelka_backup_drive_last_attempt_timestamp_seconds {int(time.time())}\n'
                f'novelka_backup_drive_last_verified_timestamp_seconds {timestamp}\n'
                f'novelka_backup_drive_source_timestamp_seconds {source_time}\n')
        temporary = self.state / 'drive.prom.tmp'
        temporary.write_text(text)
        os.replace(temporary, self.state / 'drive.prom')

    def run(self):
        with (self.state / 'lock').open('a') as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                log('Інший запуск працює; пропускаю')
                return
            try:
                # Owned staging only; recover plaintext left by SIGKILL before any network work.
                shutil.rmtree(self.work / 'plaintext', ignore_errors=True)
                (self.work / 'ciphertext.part').unlink(missing_ok=True)
                (self.work / 'verification.age').unlink(missing_ok=True)
                if self.pending.exists():
                    self.complete(json.loads(self.pending.read_text()))
                day, manifest = self.newest()
                name = f'novelka-drive-v1-{day}.tar.age'
                saved = self.state / 'receipts' / (name + '.json')
                self.rc('mkdir', self.remote)
                if saved.exists():
                    receipt = self.validate_receipt(json.loads(saved.read_text()))
                    if receipt['source_sha256'] != digest(manifest):
                        raise BackupError('Склад уже збереженої пари змінено; потрібна ручна перевірка')
                    if not self.matching(self.objects().get(name), receipt):
                        raise BackupError('Підтверджену копію в Google Drive видалено або змінено')
                    log(f'Вже збережено: {name}; повторного завантаження немає')
                else:
                    if name in self.objects():
                        raise BackupError('Архів уже є, але локального журналу немає; потрібне відновлення журналу')
                    self.complete(self.prepare(day, manifest))
                self.prune()
                self.metrics(True)
            except Exception:
                self.metrics(False)
                raise


def main():
    os.umask(0o077)
    mode = sys.argv[1] if len(sys.argv) == 2 else ''
    if mode not in ('once', 'loop'):
        raise SystemExit('Використання: backup.py once|loop')
    while True:
        try:
            DriveBackup().run()
        except Exception as error:
            log(f'GOOGLE DRIVE FAILED: {type(error).__name__}: {error}')
            if mode == 'once':
                return 1
        if mode == 'once':
            return 0
        # A reboot triggers an immediate check; per-day receipts prevent duplicate uploads.
        time.sleep(3600)


if __name__ == '__main__':
    sys.exit(main())
