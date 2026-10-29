"""Routing and cost gates shared by all dispatcher engines; no provider calls."""
import re

LEVELS = ('механічна', 'локальна', 'функціональна', 'складна', 'архітектурна', 'критична')
LEGACY = {'мала': 'локальна', 'середня': 'функціональна'}
ENGINES = ('claude', 'codex', 'antigravity')
AUTOMATIC_EFFORTS = ('low', 'medium', 'high')


def issue_level(issue):
    labels = [label['name'].removeprefix('складність: ') for label in issue['labels']
              if label['name'].startswith('складність: ')]
    if len(labels) != 1:
        raise RuntimeError('Потрібна рівно одна позначка складності; її визначає диспетчер.')
    level = LEGACY.get(labels[0], labels[0])
    if level not in LEVELS:
        raise RuntimeError('Невідома складність задачі.')
    return level


def route(config, level):
    enabled = [engine.strip() for engine in config['launcher']['enabled'].split(',') if engine.strip()]
    if not enabled or len(set(enabled)) != len(enabled) or any(e not in ENGINES for e in enabled):
        raise RuntimeError('Потрібен непорожній унікальний список enabled engines.')
    engine = config['launcher']['engine']
    if engine == 'auto':
        engine = enabled[0]  # Configuration priority, never probe/fallback between providers.
    if engine not in enabled:
        raise RuntimeError('Вибраний engine вимкнений; змініть конфігурацію.')
    profile = config[f'{engine}.{level}']
    alias, effort = profile['model'], profile['effort']
    if effort not in AUTOMATIC_EFFORTS:
        raise RuntimeError('xhigh/max/ultra та інші дорогі effort заборонені в launcher; лише ручна сесія.')
    model = config[f'models.{engine}'][alias]
    if not re.fullmatch(r'[a-zA-Z0-9_.-]+', model):
        raise RuntimeError('Некоректний псевдонім моделі.')
    # A model slug may itself pin reasoning effort (Antigravity catalog).
    if re.search(r'(?:^|[-_])(xhigh|max|ultra)(?:$|[-_])', model, re.I):
        raise RuntimeError('Модель з дорогим вбудованим effort дозволена лише вручну.')
    if engine == 'antigravity':
        pinned_effort = model.rsplit('-', 1)[-1]
        if pinned_effort in AUTOMATIC_EFFORTS and pinned_effort != effort:
            raise RuntimeError('Effort Antigravity не відповідає effort у slug моделі.')
    return engine, model, effort


def limits(config):
    result = {key: config['limits'].getint(key) for key in
              ('timeout_seconds', 'review_input_bytes', 'max_prompt_bytes', 'review_tokens')}
    if (not 1 <= result['timeout_seconds'] <= 900
            or not 1 <= result['review_input_bytes'] <= result['max_prompt_bytes'] <= 100000
            or not 1 <= result['review_tokens'] <= 1000000):
        raise RuntimeError('Некоректні ліміти запуску.')
    return result


def needs_review(issue, level, text, budget):
    return (LEVELS.index(level) >= 3
            or any(label['name'] == 'витрати: високі' for label in issue['labels'])
            or len(text.encode('utf-8')) > budget['review_input_bytes'])
