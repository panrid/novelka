import { ErrorState, Loading } from '../components/Status';
import { ListEmpty, ListFilter, ListPages, ListSearch, TableHeader, listParams, useListState, type PageData } from '../components/ListTools';
import { useResource } from '../hooks/useResource';

interface Job { id: string; chapter: number; revision: number; state: string; updated_at: string }

export function JobTable({ novel }: { novel: string }) {
    const list = useListState('job_', 'updated', ['state']);
    const resource = useResource<PageData<Job>>('/manage/' + encodeURIComponent(novel) + '/jobs?' + listParams(list.state), true);
    const data = resource.data;
    return <section><h3>Ревізії перекладу</h3>
        <div className="list-toolbar"><ListSearch label="Номер глави або ID перекладу" value={list.state.q} onChange={q => list.update({ q, page: 1 })} />
            <ListFilter label="Стан" value={list.state.filters.state} onChange={value => list.setFilter('state', value)} options={[
                { value: '', label: 'Усі стани' }, { value: 'complete', label: 'Готово' }, { value: 'needs-review', label: 'Потребує перевірки' },
                { value: 'pending', label: 'Очікує' }, { value: 'running', label: 'Виконується' }, { value: 'failed', label: 'Помилка' }]} />
            {list.state.filters.state && <button onClick={list.clearFilters}>Очистити фільтр</button>}</div>
        {resource.error ? <ErrorState message={resource.error} retry={resource.retry} /> : !data ? <Loading /> : <>
            {resource.loading && <p role="status">Оновлюємо ревізії…</p>}
            {data.items.length ? <div className="table-scroll"><table><thead><tr>
                <TableHeader label="Глава" help="Номер глави в оригіналі." sortKey="chapter" state={list.state} onSort={list.setSort} />
                <TableHeader label="Ревізія" help="Порядковий номер варіанта перекладу цієї глави." sortKey="revision" state={list.state} onSort={list.setSort} />
                <TableHeader label="Стан" help="Поточний стан цієї ревізії." sortKey="state" state={list.state} onSort={list.setSort} />
                <TableHeader label="Оновлено" help="Час останньої зміни ревізії." sortKey="updated" state={list.state} onSort={list.setSort} />
                <TableHeader label="ID для відновлення" help="Скопіюйте ID, якщо треба відновити конкретний переклад." />
            </tr></thead><tbody>{data.items.map(job => <tr key={job.id}><td>{job.chapter}</td><td>{job.revision}</td><td>{job.state}</td>
                <td>{new Date(job.updated_at).toLocaleString('uk-UA')}</td><td><code title={job.id}>{job.id}</code></td></tr>)}</tbody></table></div>
                : <ListEmpty filtered={!!(list.state.q || list.state.filters.state)} noun="Ревізій" />}
            <ListPages data={data} onPage={list.setPage} /></>}
    </section>;
}
