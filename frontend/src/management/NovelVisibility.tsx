import { useId, useState } from 'react';
import { mutate } from '../api/client';
import { permits, useAuth } from '../auth/AuthContext';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';

/**
 * Whether the novel is in the catalog. The translator sees why it was hidden; only administrators hide or restore it.
 * A hidden novel stays readable for its translator and administrators and is 404 for everyone else.
 */
export function NovelVisibility({ path, hidden, reason, onChanged }: { path: string; hidden: boolean; reason: string; onChanged: () => void }) {
    const { user } = useAuth();
    const id = useId();
    const [text, setText] = useState('');
    const action = useAction();
    const admin = permits(user, 'ADMIN');
    if (!hidden && !admin) return null;
    return <section className={'novel-visibility' + (hidden ? ' hidden-novel' : '')} aria-labelledby={id + '-title'}>
        <h3 id={id + '-title'}>{hidden ? 'Новелу приховано' : 'Видимість новели'}</h3>
        {hidden ? <p>Її не видно в каталозі, а сторінки відкривають лише перекладач і адміністратори.{reason && <> Причина: <strong>{reason}</strong>.</>}</p>
            : <p className="muted">Новела в каталозі. Приховування прибирає її з каталогу й бібліотек читачів, не видаляючи переклад.</p>}
        {admin && (hidden
            ? <button type="button" className="button" disabled={action.busy}
                onClick={() => void action.run(async () => { await mutate(path + '/unhide', {}); onChanged(); }, 'Новела знову в каталозі.')}>Повернути в каталог</button>
            : <form className="inline-form" onSubmit={event => {
                event.preventDefault();
                void action.run(async () => { await mutate(path + '/hide', { reason: text }); setText(''); onChanged(); }, 'Новелу приховано.');
            }}>
                <label htmlFor={id}>Причина для перекладача</label>
                <input id={id} maxLength={300} value={text} onChange={event => setText(event.target.value)} />
                <button disabled={action.busy}>Приховати новелу</button>
            </form>)}
        <ActionNotice {...action} />
    </section>;
}
