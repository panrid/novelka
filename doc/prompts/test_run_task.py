#!/usr/bin/env python3
"""Offline launcher checks. Never invoke a provider or mutate the source checkout."""
import configparser
import hashlib
import json
import multiprocessing
import os
from pathlib import Path
import subprocess
import sys
import tempfile

import run_task as launcher
from task_policy import LEVELS, route

SCRIPTS = Path(__file__).resolve().parent


def snapshot():
    return [subprocess.check_output(args) for args in (
        ['git', 'status', '--porcelain=v1', '--untracked-files=all'],
        ['git', 'diff', '--binary'], ['git', 'diff', '--cached', '--binary'],
        ['git', 'rev-parse', 'HEAD'], ['git', 'worktree', 'list', '--porcelain'])]


def hold(state, number, pipe):
    with launcher.locks(state, number):
        pipe.send('locked')
        pipe.recv()


def rejected(call):
    try:
        call()
    except RuntimeError:
        return
    raise AssertionError('Expected controlled rejection')


def main():
    before = snapshot()
    with tempfile.TemporaryDirectory(prefix='novelka-launcher-test-') as tmp:
        base = Path(tmp)
        state = base / 'state'
        state.mkdir(mode=0o700)
        fixture = base / 'issue.json'
        config_path = base / 'engines.conf'
        sentinel = 'PRIVATE_TEST_VALUE_982761'
        env = dict(os.environ, NOVELKA_TASK_STATE=str(state), NOVELKA_TEST_SECRET=sentinel)
        # If dry-run accidentally invokes any provider, fail and record the call.
        binaries = base / 'bin'
        binaries.mkdir()
        called = base / 'provider-called'
        for binary in ('claude', 'codex', 'agy'):
            path = binaries / binary
            path.write_text(f'#!/bin/sh\ntouch "{called}"\nexit 99\n')
            path.chmod(0o700)
        env['PATH'] = str(binaries) + os.pathsep + env['PATH']

        def config_for(engine):
            config = configparser.ConfigParser()
            config.read(SCRIPTS / 'engines.conf')
            config['launcher']['engine'] = 'auto'
            config['launcher']['enabled'] = engine
            # Removed engines have no configuration and need no installed binary.
            for section in list(config.sections()):
                if any(section == other or section.startswith(other + '.') or section == 'models.' + other
                       for other in ('claude', 'codex', 'antigravity') if other != engine):
                    config.remove_section(section)
            return config

        config = config_for('claude')

        def invoke(level='локальна', phase='plan', extra=(), body='Лише dry-run.', labels=(), number=999999):
            fixture.write_text(json.dumps(dict(title='Безпечна фіктивна задача', body=body,
                labels=[dict(name='складність: ' + level)] + [dict(name=x) for x in labels]), ensure_ascii=False))
            with config_path.open('w') as stream:
                config.write(stream)
            return subprocess.run(['bash', str(SCRIPTS / 'run-task.sh'), str(number), phase,
                                   '--dry-run', '--issue-file', str(fixture), '--config', str(config_path), *extra],
                                  env=env, capture_output=True, text=True)

        def digest(body='Лише dry-run.'):
            return hashlib.sha256(('Безпечна фіктивна задача\n\n' + body).encode()).hexdigest()

        expected = {
            'claude': [('haiku', 'low'), ('sonnet', 'medium'), ('sonnet', 'high'),
                       ('opus', 'medium'), ('opus', 'high'), ('opus', 'high')],
            'codex': [('gpt-6-luna', 'low'), ('gpt-6.1-sol', 'medium'), ('gpt-6.1-sol', 'high'),
                      ('gpt-6-astra', 'medium'), ('gpt-6-astra', 'high'), ('gpt-6-astra', 'high')],
            'antigravity': [('gemini-3.8-flash-low', 'low'), ('gemini-3.8-flash-medium', 'medium'),
                            ('gemini-4-argon-medium', 'medium'), ('gemini-4-argon-high', 'high'),
                            ('claude-opus-5-5-high', 'high'), ('claude-opus-5-5-high', 'high')],
        }
        for engine, profiles in expected.items():
            config = config_for(engine)
            for index, (level, (model, effort)) in enumerate(zip(LEVELS, profiles)):
                for phase in ('plan', 'execute'):
                    extra = []
                    if index >= 3:
                        extra += ['--reviewed-hash', digest()]
                    if phase == 'execute' and index >= 2:
                        (state / 'plan-999999.json').write_text(json.dumps(dict(issue_hash=digest(), plan='Тестовий план.')))
                        extra += ['--approved-hash', digest()]
                    result = invoke(level, phase, extra)
                    assert result.returncode == 0, result.stderr
                    assert model in result.stdout and effort in result.stdout
                    assert 'DRY-RUN' in result.stdout
                    assert sentinel not in result.stdout + result.stderr
                    if engine == 'antigravity':
                        assert '--sandbox' in result.stdout and '--input-format stream-json' in result.stdout
                    if engine == 'codex':
                        sandbox = 'read-only' if phase == 'plan' else 'workspace-write'
                        assert f'--sandbox {sandbox}' in result.stdout and '--json' in result.stdout
            assert not called.exists(), 'Dry-run invoked a provider'
        print('OK: 36 dry-runs, три engine × шість рівнів × дві фази; один engine без інших конфігів/CLI')

        config = config_for('claude')
        assert invoke('функціональна', 'execute').returncode != 0
        (state / 'plan-999999.json').write_text(json.dumps(dict(issue_hash='old', plan='Застарілий план.')))
        assert invoke('функціональна', 'execute', ['--approved-hash', digest()]).returncode != 0
        for level in LEVELS[3:]:
            assert invoke(level).returncode != 0  # Gate even before planning.
        assert invoke(labels=['витрати: високі']).returncode != 0
        assert invoke(body='x' * 12001).returncode != 0
        assert invoke(body='x' * 24001, extra=['--reviewed-hash', digest('x' * 24001)]).returncode != 0
        for effort in ('xhigh', 'max', 'ultra'):
            config['claude.локальна']['effort'] = effort
            assert invoke().returncode != 0
        config = config_for('claude')
        config['models.claude']['standard'] = 'model-max'
        assert invoke().returncode != 0
        config = config_for('claude')
        config['launcher']['engine'] = 'codex'
        assert invoke().returncode != 0  # Explicitly disabled engine.
        config = config_for('claude')
        cost_path = state / f'cost-999999-{digest()}.json'
        cost_path.write_text(json.dumps(dict(tokens=100, review=True)))
        assert invoke().returncode != 0
        assert invoke(extra=['--reviewed-hash', digest()]).returncode == 0
        cost_path.write_text(json.dumps(dict(tokens=50001, review=True)))
        assert invoke(extra=['--reviewed-hash', digest()]).returncode != 0
        cost_path.unlink()
        print('OK: gates плану/хешу/витрат, великий контекст, заборона xhigh/max/ultra, вимкнений engine')

        secret_result = invoke(body='api_key=' + sentinel)
        assert secret_result.returncode != 0
        assert sentinel not in secret_result.stdout + secret_result.stderr
        assert not list(state.rglob('worktree'))
        assert all(sentinel not in p.read_text() for p in state.rglob('*') if p.is_file())
        assert all(json.loads(p.read_text())['status'] in ('dry-run-ok', 'stopped')
                   for p in state.rglob('run.json'))
        print('OK: журнали без секрету, worktree/публікація/моделі не запускались')

        codex = '\n'.join(json.dumps(event) for event in [
            dict(type='item.completed', item=dict(type='agent_message', text='План.')),
            dict(type='turn.completed', usage=dict(input_tokens=100, cached_input_tokens=20, output_tokens=5))])
        assert launcher.decode_response('codex', codex) == ('План.', 105)
        assert launcher.decode_response('claude', json.dumps(dict(result='План.', usage=dict(
            input_tokens=10, output_tokens=5, cache_read_input_tokens=20)))) == ('План.', 35)
        agy = json.dumps(dict(event='result', result=dict(status='SUCCESS', response='План.', usage=dict(total_tokens=42))))
        assert launcher.decode_response('antigravity', agy) == ('План.', 42)
        rejected(lambda: launcher.decode_response('antigravity', json.dumps(dict(event='result', result=dict(status='WAITING')))))
        rejected(lambda: launcher.decode_response('codex', json.dumps(dict(type='turn.failed'))))
        print('OK: provider envelopes, числові витрати, помилки/незавершені відповіді')

        from unittest.mock import patch
        from contextlib import redirect_stdout
        import io
        config = config_for('claude')
        with config_path.open('w') as stream:
            config.write(stream)
        fake_issue = dict(title='Бюджетна перевірка', body='Без мережі.',
                          labels=[dict(name='складність: локальна')])
        for number, usage in [(900001, {'input_tokens': 50001}), (900002, {})]:
            calls = []
            def fake_command(args, cwd=None, data=None):
                calls.append(args)
                if args[:2] == ['git', 'rev-parse']:
                    return str(base / 'checkout')
                if args[:3] == ['gh', 'issue', 'view']:
                    return json.dumps(fake_issue)
                if args[-1] == '--help':
                    return '--effort --model --permission-mode --no-session-persistence --strict-mcp-config'
                if args[:2] == ['git', 'fetch'] or args[:3] == ['git', 'worktree', 'add']:
                    return ''
                raise AssertionError(f'Unexpected publication command: {args}')
            raw = json.dumps(dict(result='Not published', usage=usage))
            argv = ['run-task', str(number), 'plan', '--config', str(config_path)]
            with patch.object(sys, 'argv', argv), patch.dict(os.environ, NOVELKA_TASK_STATE=str(state)), \
                 patch.object(launcher, 'command', side_effect=fake_command), \
                 patch.object(launcher, 'engine_executable', return_value='claude'), \
                 patch.object(launcher, 'provider_call', return_value=raw) as provider, redirect_stdout(io.StringIO()):
                rejected(launcher.main)
                assert provider.call_count == 1
            assert not (state / f'plan-{number}.json').exists()
            reviews = list(state.glob(f'{number}-*/review.txt'))
            assert len(reviews) == 1 and reviews[0].read_text() == 'Not published'
            ledger = list(state.glob(f'cost-{number}-*.json'))
            assert len(ledger) == 1 and json.loads(ledger[0].read_text())['review']
            assert not any(args[:3] in (['gh','issue','comment'], ['gh','pr','create']) for args in calls)
        print('OK: високі/невідомі витрати блокують публікацію, без повтору provider call')

        processes = []
        try:
            for number in (101, 102):
                parent, child = multiprocessing.Pipe()
                process = multiprocessing.Process(target=hold, args=(state, number, child))
                process.start()
                assert parent.poll(5) and parent.recv() == 'locked'
                processes.append((process, parent))
            def acquire(number):
                with launcher.locks(state, number):
                    pass
            rejected(lambda: acquire(101))
            rejected(lambda: acquire(103))
            processes[0][0].terminate()
            processes[0][0].join(5)
            acquire(103)
        finally:
            for process, pipe in processes:
                if process.is_alive():
                    process.terminate()
                process.join(5)
                pipe.close()
        print('OK: два слоти, lock issue, відмова третьому, звільнення після переривання')

        # A surviving CLI must keep the slot if the launcher dies unexpectedly.
        with launcher.locks(state, 104) as descriptors:
            child = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'],
                                     pass_fds=descriptors)
        try:
            def acquire_inherited():
                with launcher.locks(state, 104):
                    pass
            rejected(acquire_inherited)
        finally:
            child.terminate()
            child.wait(timeout=5)
        with launcher.locks(state, 104):
            pass
        print('OK: дочірній CLI утримує lock після втрати батьківського процесу')

        # A fake local process, never a provider, verifies timeout group cancellation.
        import time
        marker = base / 'timeout-child-survived'
        child_code = f'import time; from pathlib import Path; time.sleep(0.6); Path({str(marker)!r}).touch()'
        parent_code = f'import subprocess,sys,time; subprocess.Popen([sys.executable,"-c",{child_code!r}]); time.sleep(30)'
        rejected(lambda: launcher.provider_call([sys.executable, '-c', parent_code], base, '', 0.1))
        time.sleep(0.8)
        assert not marker.exists()
        print('OK: тайм-аут завершує групу процесу без повторів')
    assert before == snapshot(), 'Основний checkout змінився'
    print('OK: файли, індекс, HEAD і worktrees основного checkout незмінні')


if __name__ == '__main__':
    main()
