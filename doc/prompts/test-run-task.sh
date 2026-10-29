#!/usr/bin/env bash
set -euo pipefail
export PYTHONDONTWRITEBYTECODE=1
python3 - "$(dirname -- "${BASH_SOURCE[0]}")" <<'PY'
import importlib.util
import json
import multiprocessing
import os
from pathlib import Path
import subprocess
import sys
import tempfile

scripts = Path(sys.argv[1]).resolve()
spec = importlib.util.spec_from_file_location('launcher', scripts / 'run_task.py')
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)


def snapshot():
    return [subprocess.check_output(args) for args in (
        ['git', 'status', '--porcelain=v1', '--untracked-files=all'],
        ['git', 'diff', '--binary'], ['git', 'diff', '--cached', '--binary'],
        ['git', 'rev-parse', 'HEAD'], ['git', 'worktree', 'list', '--porcelain'])]


def hold(state, number, pipe):
    with launcher.locks(state, number):
        pipe.send('locked')
        pipe.recv()


before = snapshot()
with tempfile.TemporaryDirectory(prefix='novelka-launcher-test-') as tmp:
    base = Path(tmp)
    state = base / 'state'
    state.mkdir(mode=0o700)
    fixture = base / 'issue.json'
    fixture.write_text(json.dumps(dict(title='Безпечна фіктивна задача', body='Лише dry-run.',
                                      labels=[dict(name='складність: мала')]), ensure_ascii=False))
    sentinel = 'PRIVATE_TEST_VALUE_982761'
    env = dict(os.environ, NOVELKA_TASK_STATE=str(state), NOVELKA_TEST_SECRET=sentinel)
    result = subprocess.run(['bash', str(scripts / 'run-task.sh'), '999999', 'execute',
                             '--dry-run', '--issue-file', str(fixture)],
                            env=env, capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    assert 'DRY-RUN' in result.stdout and 'claude' in result.stdout
    logs = list(state.rglob('run.json'))
    assert len(logs) == 1
    metadata = json.loads(logs[0].read_text())
    assert metadata['model'] == 'sonnet' and metadata['effort'] == 'medium'
    assert metadata['status'] == 'dry-run-ok'
    assert not list(state.rglob('worktree'))
    assert sentinel not in result.stdout + result.stderr
    assert all(sentinel not in p.read_text() for p in state.rglob('*') if p.is_file())
    # Secret-looking issue text is rejected before logs/prompt creation.
    fixture.write_text(json.dumps(dict(title='Фіктивна задача', body='api_key=PRIVATE_TEST_VALUE_982761',
                                      labels=[dict(name='складність: мала')])))
    rejected = subprocess.run(['bash', str(scripts / 'run-task.sh'), '999998', 'plan',
                               '--dry-run', '--issue-file', str(fixture)],
                              env=env, capture_output=True, text=True)
    assert rejected.returncode != 0
    assert sentinel not in rejected.stdout + rejected.stderr
    assert all(sentinel not in p.read_text() for p in state.rglob('*') if p.is_file())
    print('OK: dry-run і журнали без тестового секрету')
    processes = []
    try:
        for number in (101, 102):
            parent, child = multiprocessing.Pipe()
            process = multiprocessing.Process(target=hold, args=(state, number, child))
            process.start()
            assert parent.poll(5), 'lock process timeout'
            assert parent.recv() == 'locked'
            processes.append((process, parent))
        for number in (101, 103):
            try:
                with launcher.locks(state, number):
                    raise AssertionError('Lock unexpectedly acquired')
            except RuntimeError:
                pass
        processes[0][0].terminate()
        processes[0][0].join(5)
        with launcher.locks(state, 103):
            pass
        print('OK: два слоти, lock issue, відмова третьому, звільнення після переривання')
    finally:
        for process, pipe in processes:
            if process.is_alive():
                process.terminate()
            process.join(5)
            pipe.close()
assert before == snapshot(), 'Основний checkout змінився'
print('OK: файли, індекс, HEAD і worktrees основного checkout незмінні')
PY
