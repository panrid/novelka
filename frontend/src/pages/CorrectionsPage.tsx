import { useState } from 'react';
import { permits, useAuth } from '../auth/AuthContext';
import { useResource } from '../hooks/useResource';
import { mutate } from '../api/client';
import { ErrorState, Loading } from '../components/Status';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';
import { chapterPath } from '../lib/routes';

interface Correction {
    id: string; author_id: string; author: string; novel_id: string; chapter: number; original: string;
    replacement: string; reason: string; state: string; review_note: string | null;
}
const stateLabels: Record<string, string> = { pending: 'Очікує перевірки', approved: 'Погоджено', rejected: 'Відхилено' };

function ReviewCard({ correction, queue, refresh }: { correction: Correction; queue: boolean; refresh: () => void }) {
    const { user } = useAuth();
    const action = useAction();
    const [note, setNote] = useState('');
    const review = (approve: boolean) => { void action.run(async () => {
        await mutate('/corrections/' + correction.id + '/review', { approve, note }); refresh();
    }); };
    return <article className="review-card">
        <div className="section-heading"><a href={'#' + chapterPath(correction.novel_id, correction.chapter)}>{correction.novel_id} · глава {correction.chapter}</a><span className="badge">{stateLabels[correction.state]}</span></div>
        <p className="muted">Пропонує {correction.author}</p>
        <div className="review-diff"><div><small>Було</small><p>{correction.original}</p></div><div><small>Пропозиція</small><p>{correction.replacement}</p></div></div>
        {correction.reason && <p>Пояснення: {correction.reason}</p>}
        {correction.review_note && <p>Рішення редактора: {correction.review_note}</p>}
        {queue && correction.state === 'pending' && correction.author_id !== user?.id && <div className="stack-form">
            <label>Коментар рішення<input maxLength={2000} value={note} onChange={event => setNote(event.target.value)} /></label>
            <div className="button-row"><button className="button" disabled={action.busy} onClick={() => review(true)}>Погодити й опублікувати</button><button disabled={action.busy} onClick={() => review(false)}>Відхилити</button></div>
        </div>}
        {queue && correction.state === 'pending' && correction.author_id === user?.id && <p className="muted">Вашу правку має перевірити інший редактор.</p>}
        <ActionNotice {...action} />
    </article>;
}

export function CorrectionsPage() {
    const { user } = useAuth();
    const [queue, setQueue] = useState(false);
    const [offset, setOffset] = useState(0);
    const resource = useResource<Correction[]>('/corrections?queue=' + queue + '&offset=' + offset);
    return <div className="page workspace">
        <p className="eyebrow">Спільна робота над текстом</p><h1>Редакторські правки</h1>
        {permits(user, 'EDITOR') && <div className="tab-bar"><button aria-pressed={!queue} onClick={() => { setQueue(false); setOffset(0); }}>Мої правки</button><button aria-pressed={queue} onClick={() => { setQueue(true); setOffset(0); }}>Черга редактора</button></div>}
        {resource.error ? <ErrorState message={resource.error} retry={resource.retry} /> : !resource.data ? <Loading />
            : resource.data.length ? resource.data.map(correction => <ReviewCard key={correction.id} correction={correction} queue={queue} refresh={resource.retry} />)
                : <div className="empty-state"><h2>Поки немає правок</h2><p>Пропозицію можна залишити біля абзацу в читалці.</p></div>}
        <div className="button-row"><button disabled={!offset} onClick={() => setOffset(value => value - 50)}>Назад</button><button disabled={(resource.data?.length ?? 0) < 50} onClick={() => setOffset(value => value + 50)}>Далі</button></div>
    </div>;
}
