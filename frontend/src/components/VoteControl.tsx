import { useState } from 'react';
import { mutate } from '../api/client';
import { useAuth } from '../auth/AuthContext';

export interface VoteSummary { score: number; mine: number }

/**
 * Shared ↑ score ↓ control for novels and comments. Pressing the active arrow removes the vote,
 * the other arrow changes it. The server keeps one vote per account; the UI shows its answer.
 */
export function VoteControl({ type, target, initial, label }: { type: 'novel' | 'comment'; target: string; initial: VoteSummary; label: string }) {
    const { user } = useAuth();
    const [summary, setSummary] = useState(initial);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const vote = (value: number) => {
        setBusy(true); setError('');
        void mutate<VoteSummary>('/votes/' + type + '/' + encodeURIComponent(target), { value: summary.mine === value ? 0 : value })
            .then(setSummary).catch(failure => setError(failure instanceof Error ? failure.message : 'Не вдалося проголосувати.'))
            .finally(() => setBusy(false));
    };
    const disabled = busy || !user;
    const hint = user ? undefined : 'Увійдіть, щоб голосувати';
    return <div className="vote-control" role="group" aria-label={label}>
        <button type="button" className="vote-up" aria-pressed={summary.mine === 1} disabled={disabled} title={hint ?? 'Подобається'}
            aria-label={summary.mine === 1 ? 'Прибрати голос «подобається»' : 'Подобається'} onClick={() => vote(1)}>↑</button>
        <span className="vote-score" aria-live="polite" aria-label={'Рейтинг ' + summary.score}>{summary.score > 0 ? '+' + summary.score : summary.score}</span>
        <button type="button" className="vote-down" aria-pressed={summary.mine === -1} disabled={disabled} title={hint ?? 'Не подобається'}
            aria-label={summary.mine === -1 ? 'Прибрати голос «не подобається»' : 'Не подобається'} onClick={() => vote(-1)}>↓</button>
        {error && <span role="alert" className="vote-error">{error}</span>}
    </div>;
}
