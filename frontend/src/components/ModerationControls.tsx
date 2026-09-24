import { useId, useState } from 'react';
import { mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from './ActionNotice';

/** Moderator actions for a comment or chat message ({@code path} is "/comments/1" or "/chat/1"): hide with a reason, or restore. */
export function ModerationControls({ path, hidden, author, onChanged }: { path: string; hidden: boolean; author: string; onChanged: () => void }) {
    const id = useId();
    const [asking, setAsking] = useState(false);
    const [reason, setReason] = useState('');
    const action = useAction();
    if (hidden) return <>
        <button type="button" className="plain-button" disabled={action.busy} aria-label={'Повернути повідомлення ' + author}
            onClick={() => void action.run(async () => { await mutate(path + '/unhide', {}); onChanged(); })}>Повернути</button>
        <ActionNotice {...action} />
    </>;
    if (!asking) return <button type="button" className="plain-button" aria-label={'Приховати повідомлення ' + author} onClick={() => setAsking(true)}>Приховати</button>;
    return <form className="moderation-form" onSubmit={event => {
        event.preventDefault();
        void action.run(async () => { await mutate(path + '/hide', { reason }); setAsking(false); setReason(''); onChanged(); });
    }}>
        <label htmlFor={id}>Причина (побачать усі)</label>
        <input id={id} autoFocus maxLength={300} value={reason} placeholder="Наприклад, спойлер" onChange={event => setReason(event.target.value)} />
        <button className="button" disabled={action.busy}>Приховати</button>
        <button type="button" onClick={() => setAsking(false)}>Скасувати</button>
        <ActionNotice {...action} />
    </form>;
}
