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
import shutil
import signal
import subprocess
import sys
import time

REPO = 'panrid/novelka'
HERE = Path(__file__).resolve().parent
sys.dont_write_bytecode = True
sys.path.insert(0, str(HERE))
from task_policy import LEVELS, issue_level, route, limits, needs_review


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
        yield tuple(handle.fileno() for handle in handles)
    finally:
        for handle in handles:
            handle.close()


def engine_executable(engine):
    if engine not in ('claude', 'codex', 'antigravity'):
        raise RuntimeError('Невідомий engine у конфігурації.')
    binary = 'agy' if engine == 'antigravity' else engine
    found = shutil.which(binary)
    if found:
        return found
    npm_binary = Path.home() / '.npm-global/bin' / binary
    if npm_binary.is_file() and os.access(npm_binary, os.X_OK):
        return str(npm_binary)
    raise RuntimeError(f'CLI {engine} не встановлений або недоступний.')


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
        args = ['codex', '--ask-for-approval', 'never', '--no-daemon', 'exec', '--ephemeral', '--json',
                '-c', f'model_reasoning_effort="{effort}"', '--sandbox',
                'read-only' if phase == 'plan' else 'workspace-write', '--cd', str(cwd)]
        if model != 'default':
            args += ['--model', model]
        return args + ['-']
    if engine == 'antigravity':
        return ['agy', '--input-format', 'stream-json', '--output-format', 'stream-json',
                '--model', model, '--effort', effort, '--mode',
                'plan' if phase == 'plan' else 'accept-edits', '--sandbox',
                '--disable-slash-commands', '--log-file', '/dev/null']
    raise RuntimeError('Невідомий engine у конфігурації.')


def provider_call(cli, cwd, prompt, timeout, lock_fds=()):
    # One process per invocation. Kill its process group on timeout/interruption.
    data = prompt
    if Path(cli[0]).name == 'agy':
        data = json.dumps({'event': 'user', 'message': {'content': prompt}}) + '\n'
    process = subprocess.Popen(cli, cwd=cwd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, text=True, start_new_session=True, pass_fds=lock_fds)
    try:
        stdout, _ = process.communicate(data, timeout=timeout)
    except BaseException as error:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.communicate()
        if isinstance(error, subprocess.TimeoutExpired):
            raise RuntimeError('Ліміт часу вичерпано; потрібен ручний перегляд витрат, повторів не буде.') from None
        raise
    if process.returncode:
        raise RuntimeError('Помилка engine; запуск зупинено без повтору чи зміни моделі.')
    return stdout


def decode_response(engine, raw):
    if engine == 'claude':
        envelope = json.loads(raw)
        if envelope.get('is_error'):
            raise RuntimeError('Claude повідомив помилку; публікацію зупинено.')
        response = envelope['result']
        usage = envelope.get('usage', {})
        keys = ('input_tokens', 'output_tokens', 'cache_creation_input_tokens', 'cache_read_input_tokens')
        tokens = sum(usage.get(key, 0) for key in keys) if 'input_tokens' in usage else None
    elif engine == 'antigravity':
        events = [json.loads(line) for line in raw.splitlines() if line.strip()]
        results = [event['result'] for event in events if event.get('event') == 'result']
        if len(results) != 1 or results[0].get('status') != 'SUCCESS':
            raise RuntimeError('Antigravity не завершив одну задачу успішно; публікацію зупинено.')
        response = results[0]['response']
        tokens = results[0].get('usage', {}).get('total_tokens')
    else:
        events = [json.loads(line) for line in raw.splitlines() if line.strip()]
        if any(event.get('type') in ('turn.failed', 'error') for event in events):
            raise RuntimeError('Codex повідомив помилку; публікацію зупинено.')
        messages = [event['item']['text'] for event in events
                    if event.get('type') == 'item.completed' and event['item'].get('type') == 'agent_message']
        completed = [event['usage'] for event in events if event.get('type') == 'turn.completed']
        if not messages or not completed:
            raise RuntimeError('Codex не повернув завершеної відповіді.')
        response = messages[-1]
        tokens = sum(usage['input_tokens'] + usage['output_tokens'] for usage in completed)
    if tokens is not None and (type(tokens) is not int or tokens < 0):
        raise RuntimeError('Некоректна статистика токенів engine.')
    return safe_text(response), tokens


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('number', type=int)
    parser.add_argument('phase', choices=['plan', 'execute'])
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--config', type=Path, default=HERE / 'engines.conf',
                        help='INI engine configuration')
    parser.add_argument('--issue-file', type=Path, help='JSON fixture; only with --dry-run')
    parser.add_argument('--reviewed-hash', help='Після ручного перегляду ризику й витрат власником')
    parser.add_argument('--approved-hash', help='Диспетчер передає лише після «так» власника')
    parser.add_argument('--push-main', action='store_true')
    args = parser.parse_args()
    if args.number < 1 or (args.issue_file and not args.dry_run):
        parser.error('Потрібен додатний номер; --issue-file дозволений лише з --dry-run.')
    root = Path(command(['git', 'rev-parse', '--show-toplevel']).strip()).resolve()
    config = configparser.ConfigParser(interpolation=None)
    if not config.read(args.config, encoding='utf-8'):
        raise RuntimeError('Не вдалося прочитати engines.conf.')
    issue = json.loads(args.issue_file.read_text() if args.issue_file else command(
        ['gh', 'issue', 'view', str(args.number), '-R', REPO, '--json', 'title,body,labels']))
    text = safe_text(issue['title'] + '\n\n' + (issue.get('body') or ''))
    digest = hashlib.sha256(text.encode()).hexdigest()
    level = issue_level(issue)
    engine, model, effort = route(config, level)
    safe_text(model)
    budget = limits(config)
    review_required = needs_review(issue, level, text, budget)
    if review_required and args.reviewed_hash != digest:
        raise RuntimeError(f'Потрібен ручний перегляд ризику/витрат до запуску. Хеш issue: {digest}; після дозволу --reviewed-hash HASH.')
    state = Path(os.environ.get('NOVELKA_TASK_STATE',
                 str(Path.home() / '.local/state/novelka-tasks'))).resolve()
    if state == root or root in state.parents:
        raise RuntimeError('State directory має бути поза основним checkout.')
    os.umask(0o077)
    state.mkdir(parents=True, exist_ok=True)
    with locks(state, args.number) as lock_fds:
        run = state / f'{args.number}-{time.time_ns()}'
        run.mkdir()
        log = run / 'run.json'
        metadata = dict(issue=args.number, level=level, engine=engine, model=model,
                        effort=effort, phase=args.phase, issue_hash=digest,
                        dry_run=args.dry_run, status='prepared')
        log.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
        provider_started = False
        try:
            print(f'#{args.number} · {level} · {engine} · {model} · {effort}\nЖурнал: {log}\nХеш issue: {digest}', flush=True)
            plan_path = state / f'plan-{args.number}.json'
            approved_plan = ''
            if args.phase == 'execute' and (LEVELS.index(level) >= 2 or review_required):
                if args.approved_hash != digest or not plan_path.exists():
                    raise RuntimeError('Спершу plan, показ плану власнику та «так»; передайте --approved-hash HASH.')
                saved = json.loads(plan_path.read_text())
                if saved['issue_hash'] != digest:
                    raise RuntimeError('Issue змінилось: потрібні новий план і нове «так».')
                approved_plan = safe_text(saved['plan'])
            worktree = run / 'worktree'
            cli = engine_command(engine, model, effort, args.phase, worktree)
            template = (HERE / 'dispatcher.md').read_text().split('<!-- EXECUTOR_TEMPLATE_START -->')[1].split('<!-- EXECUTOR_TEMPLATE_END -->')[0]
            prompt = template.replace('{{ISSUE_NUMBER}}', str(args.number)).replace('{{LEVEL}}', level)
            prompt = prompt.replace('{{ISSUE_TEXT}}', text).replace('{{PLAN}}', approved_plan)
            if args.phase == 'plan':
                prompt += '\nЛише план українською. Не виконуй задачу, нічого не редагуй. Вкажи перевірки й невизначеність.'
            else:
                prompt += '\nПоверни лише JSON: {"status":"ready|uncertain|tests_failed","test_failures":0,"summary":"..."}. Без markdown.'
            safe_text(prompt)
            if len(prompt.encode('utf-8')) > budget['max_prompt_bytes']:
                raise RuntimeError('Промпт завеликий: розбийте задачу й вручну перевірте витрати.')
            cost_path = state / f'cost-{args.number}-{digest}.json'
            cost = json.loads(cost_path.read_text()) if cost_path.exists() else {'tokens': 0, 'review': False}
            if cost['tokens'] >= budget['review_tokens']:
                raise RuntimeError('Бюджет звітних токенів вичерпано; не повторюйте виклик, потрібне ручне рішення про обсяг/ліміт.')
            if cost['review'] and args.reviewed_hash != digest:
                raise RuntimeError('Попередня спроба дорога/незавершена: потрібен ручний перегляд витрат.')
            if args.dry_run:
                metadata['status'] = 'dry-run-ok'
                log.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
                print('DRY-RUN: модель, worktree, коментар, commit, push і PR не запускались.')
                print('CLI: ' + ' '.join(cli))
                return
            if config[engine]['status'] not in ('CLI_VERIFIED_RUNTIME_UNTESTED', 'VERIFIED'):
                raise RuntimeError('Адаптер UNTESTED: спершу перевірте його CLI.')
            cli[0] = engine_executable(engine)
            help_text = command([cli[0], '--help'])
            flags = ('--effort', '--model', '--permission-mode', '--no-session-persistence', '--strict-mcp-config')
            if engine == 'codex':
                help_text += command([cli[0], 'exec', '--help'])
                flags = ('--model', '--sandbox', '--cd', '--config', '--ask-for-approval',
                         '--ephemeral', '--no-daemon', '--json')
            elif engine == 'antigravity':
                flags = ('--input-format', '--output-format', '--model', '--effort', '--mode',
                         '--sandbox', '--disable-slash-commands', '--log-file')
            for flag in flags:
                if flag not in help_text:
                    raise RuntimeError('Версія CLI не підтримує потрібні параметри.')
            command(['git', 'fetch', 'origin', 'main'], root)
            branch = f'task/{args.number}-{time.time_ns()}'
            command(['git', 'worktree', 'add', '-b', branch, str(worktree), 'origin/main'], root)
            # Persist a conservative marker before spending; failures/timeouts need review.
            cost['review'] = True
            cost_path.write_text(json.dumps(cost))
            provider_started = True
            raw = provider_call(cli, worktree, prompt, budget['timeout_seconds'], lock_fds)
            response, tokens = decode_response(engine, raw)
            metadata['reported_tokens'] = tokens
            cost['tokens'] += tokens or 0
            cost['review'] = tokens is None or cost['tokens'] >= budget['review_tokens']
            cost_path.write_text(json.dumps(cost))
            if cost['review']:
                review_path = run / 'review.txt'
                review_path.write_text(response)
                raise RuntimeError(f'Витрати високі або невідомі: результат не публікується; ручний перегляд: {review_path}')
            latest = json.loads(command(['gh', 'issue', 'view', str(args.number), '-R', REPO,
                                         '--json', 'title,body']))
            latest_text = latest['title'] + '\n\n' + (latest.get('body') or '')
            if hashlib.sha256(latest_text.encode()).hexdigest() != digest:
                raise RuntimeError('Issue змінилось під час роботи: потрібне нове погодження; публікацію зупинено.')
            if args.phase == 'plan':
                if command(['git', 'status', '--porcelain', '--untracked-files=all'], worktree).strip():
                    raise RuntimeError('Планувальник змінив worktree; результат не публікується.')
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
                if LEVELS.index(level) <= 1 and any(
                    p.startswith(('.github/', '.claude/', '.codex/', 'infra/'))
                    or p.endswith('.sql') or re.search(r'(auth|security|permission|migration)', p, re.I)
                    for p in staged
                ):
                    raise RuntimeError('Чутливі зміни у простій задачі: зупинка й переоцінка складності.')
                numstat = command(['git', 'diff', '--cached', '--numstat'], worktree)
                changed_lines = sum(int(n) for row in numstat.splitlines()
                                    for n in row.split('\t')[:2] if n.isdigit())
                if (len(staged) > 10 or changed_lines > 500) and args.reviewed_hash != digest:
                    raise RuntimeError('Великий diff: потрібен ручний перегляд обсягу/витрат перед публікацією.')
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
            if provider_started:
                cost['review'] = True
                cost_path.write_text(json.dumps(cost))
            metadata["status"] = "stopped"
            log.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
            raise


def interrupted(signum, _frame):
    raise RuntimeError('Запуск перервано; перед повтором потрібен ручний перегляд.')


if __name__ == '__main__':
    for signum in (signal.SIGTERM, signal.SIGINT, signal.SIGHUP):
        signal.signal(signum, interrupted)
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, IndexError, TypeError, AttributeError, configparser.Error) as error:
        # Print only controlled errors, never malformed model output.
        print(str(error) if isinstance(error, RuntimeError) else 'Помилка даних/оточення; запуск зупинено.', file=sys.stderr)
        sys.exit(1)
