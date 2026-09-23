import { useState } from 'react';
import { permits, useAuth } from '../auth/AuthContext';
import { useResource } from '../hooks/useResource';
import { mutate } from '../api/client';
import { ErrorState, Loading } from '../components/Status';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';
import { TextDiff } from '../components/TextDiff';
import { chapterPath, novelPath } from '../lib/routes';
import { ListEmpty, ListFilter, ListPages, ListSearch, TableHeader, listParams, useListState, type PageData } from '../components/ListTools';
import { CorrectionLookupFilter } from './CorrectionLookupFilter';

interface Correction {
    id: string; author_id: string; author: string; novel_id: string; novel_title: string;
    chapter: number; chapter_title: string; state: string; created_at: string; reviewed_at: string | null;
}
interface CorrectionDetail {
    id: string; author_id: string; original: string; replacement: string; reason: string;
    review_note: string | null; base_revision: number; published_revision?: number | null;
}
const stateLabels: Record<string, string> = { pending: 'Очікує перевірки', approved: 'Погоджено', rejected: 'Відхилено' };
const sortLabels: Record<string, string> = { created: 'Дата подання', novel: 'Новела', chapter: 'Глава', author: 'Автор', state: 'Стан' };
const dateTime = new Intl.DateTimeFormat('uk-UA', { dateStyle: 'medium', timeStyle: 'short' });

function ReviewDetail({ correction, queue, refresh }: { correction: Correction; queue: boolean; refresh: () => void }) {
    const { user } = useAuth();
    const { data, error, loading, retry } = useResource<CorrectionDetail>('/corrections/' + correction.id);
    const action = useAction();
    const [note, setNote] = useState('');
    const review = (approve: boolean) => { void action.run(async () => {
        await mutate('/corrections/' + correction.id + '/review', { approve, note });
        refresh(); retry();
    }); };
    if (error) return <ErrorState message={error} retry={retry} />;
    if (!data || loading) return <Loading />;
    return <div className="correction-detail">
        <TextDiff before={data.original} after={data.replacement} />
        {data.reason && <p><strong>Пояснення автора:</strong> {data.reason}</p>}
        {data.review_note && <p><strong>Рішення редактора:</strong> {data.review_note}</p>}
        <p className="muted correction-origin">Порівняно з абзацом ревізії {data.base_revision}, на яку спиралася правка.
            {data.published_revision != null && <> Опубліковано як ревізію {data.published_revision}.</>}</p>
        {queue && correction.state === 'pending' && correction.author_id !== user?.id && <div className="correction-review">
            <label>Коментар рішення<input maxLength={2000} value={note} onChange={event => setNote(event.target.value)} /></label>
            <div className="button-row"><button className="button" disabled={action.busy} onClick={() => review(true)}>Погодити й опублікувати</button>
                <button disabled={action.busy} onClick={() => review(false)}>Відхилити</button></div>
        </div>}
        {queue && correction.state === 'pending' && correction.author_id === user?.id && <p className="muted">Вашу правку має перевірити інший редактор.</p>}
        <ActionNotice {...action} />
    </div>;
}

export function CorrectionsPage() {
    const { user } = useAuth();
    const list = useListState('', 'created', ['queue', 'state', 'novel', 'chapter', 'authorId', 'dateFrom', 'dateTo']);
    const queue = list.state.filters.queue === 'true' && permits(user, 'EDITOR');
    const [openId, setOpenId] = useState<string | null>(null);
    const resource = useResource<PageData<Correction>>('/corrections?' + listParams(list.state, { queue }), true);
    const data = resource.data;
    const filters = list.state.filters;
    const filtered = !!(list.state.q || filters.state || filters.novel || filters.chapter || filters.authorId || filters.dateFrom || filters.dateTo);
    const clear = () => list.update({ page: 1, q: '', filters: { queue: queue ? 'true' : '', state: '', novel: '', chapter: '', authorId: '', dateFrom: '', dateTo: '' } });
    return <div className="page workspace corrections-page">
        <p className="eyebrow">Спільна робота над текстом</p><h1>Редакторські правки</h1>
        {permits(user, 'EDITOR') && <div className="tab-bar"><button aria-pressed={!queue} onClick={() => list.setFilter('queue', '')}>Мої правки</button>
            <button aria-pressed={queue} onClick={() => list.setFilter('queue', 'true')}>Черга редактора</button></div>}
        <div className="correction-filters">
            <ListSearch label="Назва, глава, автор або текст правки" value={list.state.q} onChange={q => list.update({ q, page: 1 })} />
            <CorrectionLookupFilter label="Новела" endpoint="/novels/search" value={filters.novel}
                selected={data?.items.find(item => item.novel_id === filters.novel)?.novel_title}
                onChange={novel => list.update({ page: 1, filters: { ...filters, novel, chapter: '' } })} />
            <label>Глава<input type="number" min="1" value={filters.chapter} onChange={event => list.setFilter('chapter', event.target.value)} /></label>
            {queue && <CorrectionLookupFilter label="Автор" endpoint="/corrections/authors" value={filters.authorId}
                selected={data?.items.find(item => item.author_id === filters.authorId)?.author}
                onChange={authorId => list.setFilter('authorId', authorId)} />}
            <ListFilter label="Стан" value={filters.state} onChange={value => list.setFilter('state', value)} options={[
                { value: '', label: 'Усі стани' }, ...Object.entries(stateLabels).map(([value, label]) => ({ value, label }))]} />
            <label>Від дати<input type="date" value={filters.dateFrom} max={filters.dateTo || undefined}
                onChange={event => list.setFilter('dateFrom', event.target.value)} /></label>
            <label>До дати<input type="date" value={filters.dateTo} min={filters.dateFrom || undefined}
                onChange={event => list.setFilter('dateTo', event.target.value)} /></label>
            <div className="correction-sort"><label>Порядок<select value={list.state.sort} onChange={event => list.update({ sort: event.target.value, page: 1 })}>
                {Object.entries(sortLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
                <button type="button" aria-label="Змінити напрямок сортування" onClick={() => list.update({ direction: list.state.direction === 'asc' ? 'desc' : 'asc', page: 1 })}>
                    {list.state.direction === 'asc' ? '↑' : '↓'}</button></div>
            {filtered && <button type="button" className="correction-clear" onClick={clear}>Очистити пошук і фільтри</button>}
        </div>
        {resource.error ? <ErrorState message={resource.error} retry={resource.retry} /> : !data ? <Loading /> : <>
            {resource.loading && <p role="status">Оновлюємо правки…</p>}
            {data.items.length ? <div className="table-scroll correction-table-scroll" tabIndex={0} role="region" aria-label="Список правок; на вузькому екрані прокручується горизонтально">
                <table className="correction-table"><thead><tr>
                    <TableHeader label="Новела" help="Українська назва твору; відкриває сторінку новели." sortKey="novel" state={list.state} onSort={list.setSort} />
                    <TableHeader label="Глава" help="Номер і назва глави, до якої належить правка." sortKey="chapter" state={list.state} onSort={list.setSort} />
                    <TableHeader label="Автор" help="Поточне ім’я автора правки. Зв’язок зберігається за ID акаунта." sortKey="author" state={list.state} onSort={list.setSort} />
                    <TableHeader label="Стан" help="Очікує перевірки, погоджено або відхилено." sortKey="state" state={list.state} onSort={list.setSort} />
                    <TableHeader label="Подано" help="Дата й час створення правки." sortKey="created" state={list.state} onSort={list.setSort} />
                    <TableHeader label="Перегляд" help="Відкриває порівняння текстів і, за наявності прав, дії редактора." />
                </tr></thead>{data.items.map(correction => <tbody key={correction.id}>
                    <tr><td data-label="Новела"><a href={'#' + novelPath(correction.novel_id)} title={correction.novel_title}>{correction.novel_title || correction.novel_id}</a></td>
                        <td data-label="Глава"><a href={'#' + chapterPath(correction.novel_id, correction.chapter)} title={correction.chapter_title}>{correction.chapter}. {correction.chapter_title || 'Глава ' + correction.chapter}</a></td>
                        <td data-label="Автор">{correction.author}</td><td data-label="Стан"><span className={'badge correction-state ' + correction.state}>{stateLabels[correction.state] || correction.state}</span></td>
                        <td data-label="Подано"><time dateTime={correction.created_at}>{dateTime.format(new Date(correction.created_at))}</time></td>
                        <td data-label="Перегляд"><button type="button" aria-expanded={openId === correction.id} aria-controls={'correction-' + correction.id}
                            onClick={() => setOpenId(openId === correction.id ? null : correction.id)}>
                            {openId === correction.id ? 'Сховати diff' : 'Показати diff'}</button></td></tr>
                    {openId === correction.id && <tr className="correction-expanded"><td colSpan={6} id={'correction-' + correction.id}>
                        <ReviewDetail correction={correction} queue={queue} refresh={resource.retry} />
                    </td></tr>}
                </tbody>)}</table></div>
                : <div><ListEmpty filtered={filtered} noun="Правок" />{filtered && <button type="button" onClick={clear}>Очистити пошук і фільтри</button>}</div>}
            <ListPages data={data} onPage={list.setPage} /></>}
    </div>;
}
