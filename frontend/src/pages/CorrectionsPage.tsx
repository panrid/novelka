import { useState } from 'react';
import { permits, useAuth } from '../auth/AuthContext';
import { useResource } from '../hooks/useResource';
import { mutate } from '../api/client';
import { ErrorState, Loading } from '../components/Status';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';
import { chapterPath } from '../lib/routes';
import { ListEmpty, ListFilter, ListPages, ListSearch, listParams, useListState, type PageData } from '../components/ListTools';

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
    const list = useListState('', 'created', ['queue', 'state']);
    const queue = list.state.filters.queue === 'true';
    const resource = useResource<PageData<Correction>>('/corrections?' + listParams(list.state, { queue }), true);
    const data = resource.data;
    return <div className="page workspace">
        <p className="eyebrow">Спільна робота над текстом</p><h1>Редакторські правки</h1>
        {permits(user, 'EDITOR') && <div className="tab-bar"><button aria-pressed={!queue} onClick={() => list.setFilter('queue', '')}>Мої правки</button><button aria-pressed={queue} onClick={() => list.setFilter('queue', 'true')}>Черга редактора</button></div>}
        <div className="list-toolbar"><ListSearch label="Текст правки" value={list.state.q} onChange={q => list.update({ q, page: 1 })} />
            <ListFilter label="Стан" value={list.state.filters.state} onChange={value => list.setFilter('state', value)} options={[
                { value: '', label: 'Усі стани' }, ...Object.entries(stateLabels).map(([value, label]) => ({ value, label }))]} />
            {list.state.filters.state && <button onClick={() => list.setFilter('state', '')}>Очистити фільтр</button>}</div>
        {resource.error ? <ErrorState message={resource.error} retry={resource.retry} /> : !data ? <Loading /> : <>
            {resource.loading && <p role="status">Оновлюємо список…</p>}
            {data.items.length ? data.items.map(correction => <ReviewCard key={correction.id} correction={correction} queue={queue} refresh={resource.retry} />)
                : <ListEmpty filtered={!!(list.state.q || list.state.filters.state)} noun="Правок" />}
            <ListPages data={data} onPage={list.setPage} /></>}
    </div>;
}
