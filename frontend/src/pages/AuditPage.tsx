import { useResource } from '../hooks/useResource';
import { ErrorState, Loading } from '../components/Status';
import { ListEmpty, ListPages, ListSearch, TableHeader, listParams, useListState, type PageData } from '../components/ListTools';

interface Event { id: number; username: string; action: string; target: string; details: unknown; created_at: string }
export function AuditPage() {
    const list = useListState('', 'created', []);
    const resource = useResource<PageData<Event>>('/accounts/audit?' + listParams(list.state), true);
    const data = resource.data;
    return <div className="page workspace"><h1>Журнал дій</h1>
        <div className="list-toolbar"><ListSearch label="Хто або об’єкт" value={list.state.q} onChange={q => list.update({ q, page: 1 })} /></div>
        {resource.error ? <ErrorState message={resource.error} retry={resource.retry} /> : !data ? <Loading /> : <>
            {resource.loading && <p role="status">Оновлюємо список…</p>}
            {data.items.length ? <div className="table-scroll"><table><thead><tr>
                <TableHeader label="Час" help="Коли виконано дію." sortKey="created" state={list.state} onSort={list.setSort} />
                <TableHeader label="Хто" help="Обліковий запис виконавця." sortKey="actor" state={list.state} onSort={list.setSort} />
                <TableHeader label="Дія" help="Технічна назва виконаної дії." sortKey="action" state={list.state} onSort={list.setSort} />
                <TableHeader label="Об’єкт" help="До чого застосовано дію." />
                <TableHeader label="Деталі" help="Додаткові дані; розгорніть для перегляду." />
            </tr></thead><tbody>{data.items.map(event => <tr key={event.id}><td>{new Date(event.created_at).toLocaleString('uk')}</td><td>{event.username}</td><td>{event.action}</td><td>{event.target}</td><td><details className="table-details"><summary>Переглянути</summary><pre>{JSON.stringify(event.details, null, 2)}</pre></details></td></tr>)}</tbody></table></div>
                : <ListEmpty filtered={!!list.state.q} noun="Подій" />}
            <ListPages data={data} onPage={list.setPage} /></>}
    </div>;
}
