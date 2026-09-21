import { useState } from 'react';
import { useResource } from '../hooks/useResource';
import { ErrorState, Loading } from '../components/Status';

interface Event { id: number; username: string; action: string; target: string; details: unknown; created_at: string }
export function AuditPage() {
    const [offset, setOffset] = useState(0);
    const resource = useResource<Event[]>('/accounts/audit?offset=' + offset);
    return <div className="page workspace"><h1>Журнал дій</h1>
        {resource.error ? <ErrorState message={resource.error} retry={resource.retry} /> : !resource.data ? <Loading /> :
            <div className="table-scroll"><table><thead><tr><th>Час</th><th>Хто</th><th>Дія</th><th>Об’єкт</th><th>Деталі</th></tr></thead><tbody>{resource.data.map(event => <tr key={event.id}><td>{new Date(event.created_at).toLocaleString('uk')}</td><td>{event.username}</td><td>{event.action}</td><td>{event.target}</td><td><pre>{JSON.stringify(event.details, null, 2)}</pre></td></tr>)}</tbody></table></div>}
        <div className="button-row"><button disabled={!offset} onClick={() => setOffset(value => value - 50)}>Назад</button><button disabled={(resource.data?.length ?? 0) < 50} onClick={() => setOffset(value => value + 50)}>Далі</button></div>
    </div>;
}
