import { useState } from 'react';
import { mutate } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { shelves } from '../lib/library';
import { SelectField } from './SelectField';

/** Moves a novel between the reader's private shelves; "not in library" removes it. Hidden for guests. */
export function LibraryControl({ novel, initial, onChange, label = 'Моя бібліотека', hideLabel = false }: {
    novel: string; initial: string | null; onChange?: (status: string | null) => void; label?: string; hideLabel?: boolean;
}) {
    const { user } = useAuth();
    const [status, setStatus] = useState(initial ?? '');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    if (!user) return null;
    const change = (next: string) => {
        if (busy || next === status) return;
        const previous = status;
        setStatus(next); setBusy(true); setError('');
        void mutate<{ status: string | null }>('/library/' + encodeURIComponent(novel), { status: next })
            .then(result => { setStatus(result.status ?? ''); onChange?.(result.status); })
            .catch(failure => { setStatus(previous); setError(failure instanceof Error ? failure.message : 'Не вдалося змінити бібліотеку.'); })
            .finally(() => setBusy(false));
    };
    return <div className={'library-control' + (status ? ' in-library' : '')}>
        <SelectField label={label} hideLabel={hideLabel} value={status} onChange={change}
            options={[{ value: '', label: 'Не в бібліотеці' }, ...shelves]} />
        {error && <span role="alert" className="vote-error">{error}</span>}
    </div>;
}
