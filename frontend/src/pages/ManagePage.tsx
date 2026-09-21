import { useState } from 'react';
import type { NovelCard } from '../api/types';
import { useResource } from '../hooks/useResource';
import { TaskForm } from '../management/TaskForm';
import { TaskQueue } from '../management/TaskQueue';
import { NovelManager } from '../management/NovelManager';
import { CostReport } from '../management/CostReport';
import { ErrorState } from '../components/Status';

export function ManagePage() {
    const catalog = useResource<NovelCard[]>('/novels');
    const [novel, setNovel] = useState('');
    const [tab, setTab] = useState('tasks');
    const [version, setVersion] = useState(0);
    return <div className="page workspace"><p className="eyebrow">Від оригіналу до публікації</p><h1>Майстерня перекладу</h1>
        <div className="workspace-toolbar"><label>Новела<select value={novel} onChange={event => setNovel(event.target.value)}>
            <option value="">Оберіть новелу</option>{catalog.data?.map(item => <option value={item.id} key={item.id}>{item.title} · {item.id}</option>)}
        </select></label><button onClick={catalog.retry}>Оновити каталог</button></div>
        {catalog.error && <ErrorState message={catalog.error} retry={catalog.retry} />}
        <nav className="tab-bar" aria-label="Керування перекладами">{Object.entries({ tasks: 'Запуск і черга', novel: 'Новела й словник', costs: 'Витрати' }).map(([key, label]) => <button key={key} aria-pressed={tab === key} onClick={() => setTab(key)}>{label}</button>)}</nav>
        {tab === 'tasks' && <><TaskForm novel={novel} onCreated={() => setVersion(value => value + 1)} /><TaskQueue version={version} /></>}
        {tab === 'novel' && (novel ? <NovelManager key={novel} novel={novel} /> : <p>Оберіть новелу. Нову можна додати через імпорт у вкладці запуску.</p>)}
        {tab === 'costs' && <CostReport novel={novel} />}
    </div>;
}
