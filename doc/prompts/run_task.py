#!/usr/bin/env python3
"""Dispatcher launcher. State and worktrees live outside the source checkout."""
import argparse
import configparser
import contextlib
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

REPO = 'panrid/novelka'
HERE = Path(__file__).resolve().parent


def command(args, cwd=None, data=None):
    # Never echo subprocess output/errors: they can include credentials.
    result = subprocess.run(args, cwd=cwd, input=data, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f'Команда {args[0]} завершилась помилкою; сирий вивід не журналюється.')
    return result.stdout


def safe_text(text):
    # Reject common secret formats rather than persisting a partly redacted prompt.
    patterns = [r'-----BEGIN .*PRIVATE KEY', r'\b(?:gh[pousr]_|github_pat_|sk-)[A-Za-z0-9_\-]{12,}',
                r'(?im)\b(?:password|passwd|token|api[_-]?key|secret)\s*[:=]\s*\S+',
                r'://[^\s/:]+:[^\s/@]+@']
    if any(re.search(pattern, text) for pattern in patterns):
        raise RuntimeError('Виявлено можливий секрет; текст не збережено й не передано моделі.')
    return text


@contextlib.contextmanager
def locks(state, number):
    handles = []
    try:
        task = open(state / f'issue-{number}.lock', 'a')
        handles.append(task)
        try:
            fcntl.flock(task, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError('Ця задача вже запущена.') from None
        for slot in range(2):
            handle = open(state / f'slot-{slot}.lock', 'a')
            try:
                fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
                handles.append(handle)
                break
            except BlockingIOError:
                handle.close()
        else:
            raise RuntimeError('Уже працюють два виконавці; повторіть пізніше.')
        yield
    finally:
        for handle in handles:
            handle.close()


def engine_command(engine, model, effort, phase, cwd):
    if engine == 'claude':
        args = ['claude', '--print', '--model', model, '--effort', effort,
                '--permission-mode', 'dontAsk', '--output-format', 'json',
                '--strict-mcp-config', '--mcp-config', '{"mcpServers":{}}',
                '--disable-slash-commands', '--no-session-persistence']
        tools = ['Read', 'Glob', 'Grep']
        allowed = tools.copy()
        if phase == 'execute':
            tools += ['Edit', 'Write', 'Bash']
            allowed += ['Edit', 'Write', 'Bash(./gradlew :backend:test)',
                        'Bash(npm --prefix frontend run check)', 'Bash(git diff *)',
                        'Bash(bash -n *)', 'Bash(bash doc/prompts/test-run-task.sh)']
        return args + ['--tools', ','.join(tools), '--allowedTools', ','.join(allowed)]
    if engine == 'codex':
        return ['codex', '--ask-for-approval', 'never', 'exec', '--model', model,
                '-c', f'model_reasoning_effort="{effort}"', '--sandbox',
                'read-only' if phase == 'plan' else 'workspace-write', '--cd', str(cwd), '-']
    raise RuntimeError('Невідомий engine у конфігурації.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('number', type=int)
    parser.add_argument('phase', choices=['plan', 'execute'])
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--issue-file', type=Path, help='JSON fixture; only with --dry-run')
    parser.add_argument('--approved-hash', help='Диспетчер передає лише після «так» власника')
    parser.add_argument('--push-main', action='store_true')
    args = parser.parse_args()
    if args.number < 1 or (args.issue_file and not args.dry_run):
        parser.error('Потрібен додатний номер; --issue-file дозволений лише з --dry-run.')
    root = Path(command(['git', 'rev-parse', '--show-toplevel']).strip()).resolve()
    config = configparser.ConfigParser(interpolation=None)
    config.read(HERE / 'engines.conf', encoding='utf-8')
    issue = json.loads(args.issue_file.read_text() if args.issue_file else command(
        ['gh', 'issue', 'view', str(args.number), '-R', REPO, '--json', 'title,body,labels']))
    text = safe_text(issue['title'] + '\n\n' + (issue.get('body') or ''))
    digest = hashlib.sha256(text.encode()).hexdigest()
    levels = [x['name'].removeprefix('складність: ') for x in issue['labels']
              if x['name'].startswith('складність: ')]
    if len(levels) != 1 or levels[0] not in ('мала', 'середня', 'архітектурна'):
        raise RuntimeError('Потрібна рівно одна позначка складності.')
    level = levels[0]
    if level == 'архітектурна':
        raise RuntimeError('Архітектурну задачу роблять вручну в окремій сесії; launcher її не запускає.')
    profile = config[level]
    engine, model, effort = (profile[key] for key in ('engine', 'model', 'effort'))
    if not re.fullmatch(r'[a-zA-Z0-9_.-]+', model) or effort not in ('medium', 'high'):
        raise RuntimeError('Некоректний model alias або effort.')
    state = Path(os.environ.get('NOVELKA_TASK_STATE',
                 str(Path.home() / '.local/state/novelka-tasks'))).resolve()
    if state == root or root in state.parents:
        raise RuntimeError('State directory має бути поза основним checkout.')
    os.umask(0o077)
    state.mkdir(parents=True, exist_ok=True)
    with locks(state, args.number):
        run = state / f'{args.number}-{time.time_ns()}'
        run.mkdir()
        log = run / 'run.json'
        metadata = dict(issue=args.number, level=level, engine=engine, model=model,
                        effort=effort, phase=args.phase, issue_hash=digest,
                        dry_run=args.dry_run, status='prepared')
        log.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
        try:
            print(f'#{args.number} · {level} · {engine} · {model} · {effort}\nЖурнал: {log}\nХеш issue: {digest}', flush=True)
            plan_path = state / f'plan-{args.number}.json'
            approved_plan = ''
            if args.phase == 'execute' and level == 'середня':
                if args.approved_hash != digest or not plan_path.exists():
                    raise RuntimeError('Спершу plan, показ плану власнику та «так»; передайте --approved-hash HASH.')
                saved = json.loads(plan_path.read_text())
                if saved['issue_hash'] != digest:
                    raise RuntimeError('Issue змінилось: потрібні новий план і нове «так».')
                approved_plan = safe_text(saved['plan'])
            worktree = run / 'worktree'
            cli = engine_command(engine, model, effort, args.phase, worktree)
            if args.dry_run:
                metadata['status'] = 'dry-run-ok'
                log.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
                print('DRY-RUN: модель, worktree, коментар, commit, push і PR не запускались.')
                print('CLI: ' + ' '.join(cli))
                return
            if engine == 'codex' and config[engine]['status'] != 'VERIFIED':
                raise RuntimeError('Codex adapter UNTESTED: перевірте CLI і адаптер перед увімкненням.')
            help_text = command([engine, '--help'])
            flags = ('--effort', '--model', '--permission-mode', '--no-session-persistence', '--strict-mcp-config')
            if engine == 'codex':
                help_text += command(['codex', 'exec', '--help'])
                flags = ('--model', '--sandbox', '--cd', '--config', '--ask-for-approval')
            for flag in flags:
                if flag not in help_text:
                    raise RuntimeError('Версія CLI не підтримує потрібні параметри.')
            command(['git', 'fetch', 'origin', 'main'], root)
            branch = f'task/{args.number}-{time.time_ns()}'
            command(['git', 'worktree', 'add', '-b', branch, str(worktree), 'origin/main'], root)
            template = (HERE / 'dispatcher.md').read_text().split('<!-- EXECUTOR_TEMPLATE_START -->')[1].split('<!-- EXECUTOR_TEMPLATE_END -->')[0]
            prompt = template.replace('{{ISSUE_NUMBER}}', str(args.number)).replace('{{LEVEL}}', level)
            prompt = prompt.replace('{{ISSUE_TEXT}}', text).replace('{{PLAN}}', approved_plan)
            if args.phase == 'plan':
                prompt += '\nЛише план українською. Не виконуй задачу, нічого не редагуй. Вкажи перевірки й невизначеність.'
            else:
                prompt += '\nПоверни лише JSON: {"status":"ready|uncertain|tests_failed","test_failures":0,"summary":"..."}. Без markdown.'
            response = command(cli, worktree, safe_text(prompt))
            if engine == 'claude':
                envelope = json.loads(response)
                if envelope.get('is_error'):
                    raise RuntimeError('Claude повідомив помилку; публікацію зупинено.')
                response = envelope['result']
            response = safe_text(response)
            if args.phase == 'plan':
                plan_path.write_text(json.dumps(dict(issue_hash=digest, plan=response), ensure_ascii=False))
                comment = run / 'plan.md'
                comment.write_text(f'План · SHA-256 issue: `{digest}`\n\n{response}')
                command(['gh', 'issue', 'comment', str(args.number), '-R', REPO, '--body-file', str(comment)])
            else:
                result = json.loads(response)
                if (result['status'] != 'ready' or type(result['test_failures']) is not int
                        or not 0 <= result['test_failures'] < 2):
                    raise RuntimeError('Виконавець зупинився: перевірте невизначеність/тести й підвищте складність за потреби.')
                command(['git', 'diff', '--check'], worktree)
                command(['git', 'add', '--all'], worktree)
                staged = command(['git', 'diff', '--cached', '--name-only'], worktree).splitlines()
                if not staged:
                    raise RuntimeError('Немає змін для PR.')
                if any(Path(p).name.startswith('.env') or Path(p).suffix in ('.pem', '.key') for p in staged):
                    raise RuntimeError('Заборонений файл у staged changes.')
                safe_text(command(['git', 'diff', '--cached'], worktree))
                # Publication has its own checks; do not rely only on model claims.
                if any(p.startswith(('backend/', 'frontend/')) for p in staged):
                    command(['./gradlew', ':backend:test'], worktree)
                    command(['npm', '--prefix', 'frontend', 'run', 'check'], worktree)
                for path in staged:
                    target = worktree / path
                    if target.is_file() and path.endswith('.sh'):
                        command(['bash', '-n', path], worktree)
                command(['git', 'diff', '--cached', '--check'], worktree)
                command(['git', 'commit', '-m', f'{issue["title"]}\n\nCloses #{args.number}'], worktree)
                if args.push_main:
                    if command(['gh', 'api', 'user', '--jq', '.login']).strip() != 'panrid':
                        raise RuntimeError('--push-main дозволено лише власнику.')
                    command(['git', 'push', 'origin', 'HEAD:main'], worktree)
                else:
                    command(['git', 'push', '-u', 'origin', branch], worktree)
                    body = run / 'pr.md'
                    body.write_text(safe_text(result['summary']) + f'\n\nCloses #{args.number}\n')
                    url = command(['gh', 'pr', 'create', '-R', REPO, '--base', 'main', '--head', branch,
                                   '--title', f'Виконано задачу #{args.number}', '--body-file', str(body)])
                    print(url.strip())
            metadata['status'] = 'completed'
            log.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
            print(f'Завершено. Worktree збережено: {worktree}')
        except BaseException:
            metadata["status"] = "stopped"
            log.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
            raise


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, IndexError, TypeError) as error:
        # Print only controlled errors, never malformed model output.
        print(str(error) if isinstance(error, RuntimeError) else 'Помилка даних/оточення; запуск зупинено.', file=sys.stderr)
        sys.exit(1)
