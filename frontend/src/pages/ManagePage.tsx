import { useState } from 'react';
import type { NovelCard } from '../api/types';
import { useResource } from '../hooks/useResource';
import { TaskForm } from '../management/TaskForm';
import { TaskQueue } from '../management/TaskQueue';
import { NovelManager } from '../management/NovelManager';
import { CostReport } from '../management/CostReport';
import { ErrorState } from '../components/Status';
import { SelectField } from '../components/SelectField';
import { ListSearch, type PageData } from '../components/ListTools';
import type { TaskPreset } from '../management/TaskPreset';

export function ManagePage({ search = '' }: { search?: string }) {
    const params = new URLSearchParams(search);
    const [catalogQuery, setCatalogQuery] = useState('');
    const catalog = useResource<PageData<NovelCard>>('/novels/search?size=25&q=' + encodeURIComponent(catalogQuery) + '&sort=title&direction=asc', true);
    const [novel, setNovel] = useState(params.get('novel') ?? '');
    const [selectedTitle, setSelectedTitle] = useState('');
    const [tab, setTab] = useState(params.get('tab') === 'glossary' ? 'novel' : 'tasks');
    const [preset, setPreset] = useState<TaskPreset>();
    const [formVersion, setFormVersion] = useState(0);
    const [version, setVersion] = useState(0);
    const prepare = (next: TaskPreset) => { setNovel(next.novelId); setPreset(next); setFormVersion(value => value + 1); setTab('tasks'); };
    const taskId = params.get('task') ?? undefined;
    return <div className="page workspace"><p className="eyebrow">Від оригіналу до публікації</p><h1>Майстерня перекладу</h1>
        <div className="workspace-toolbar"><ListSearch label="Знайти новелу" value={catalogQuery} onChange={setCatalogQuery} />
            <SelectField label="Новела" value={novel} onChange={value => { setNovel(value); setSelectedTitle(catalog.data?.items.find(item => item.id === value)?.title || value); setPreset(undefined); setFormVersion(value => value + 1); }}
            options={[{ value: '', label: 'Оберіть новелу' }, ...(novel ? [{ value: novel, label: `${selectedTitle || catalog.data?.items.find(item => item.id === novel)?.title || novel} · ${novel}` }] : []),
                ...(catalog.data?.items ?? []).filter(item => item.id !== novel).map(item => ({ value: item.id, label: `${item.title} · ${item.id}` }))]} />
            <button onClick={catalog.retry}>Оновити каталог</button></div>
        {catalog.error && <ErrorState message={catalog.error} retry={catalog.retry} />}
        <nav className="tab-bar" aria-label="Керування перекладами">{Object.entries({ tasks: 'Переклад', novel: 'Дані новели', costs: 'Витрати' }).map(([key, label]) => <button key={key} aria-pressed={tab === key} onClick={() => setTab(key)}>{label}</button>)}</nav>
        {taskId && <p><a className="text-link" href={'#/manage?novel=' + encodeURIComponent(novel) + '&task=' + encodeURIComponent(taskId)}>До завдання</a> · <a className="text-link" href="#/manage">Усі завдання</a></p>}
        {tab === 'tasks' && <><TaskForm key={formVersion} novel={novel} preset={preset} onCreated={id => {
            setVersion(value => value + 1);
            if (taskId) window.location.hash = '/manage?novel=' + encodeURIComponent(novel) + '&task=' + encodeURIComponent(id);
        }} />
            <TaskQueue version={version} onPrepare={prepare} taskId={taskId} /></>}
        {tab === 'novel' && (novel ? <NovelManager key={novel} novel={novel} initialTab={params.get('tab') === 'glossary' ? 'glossary' : 'info'} /> : <p>Оберіть новелу. Нову можна додати через імпорт у вкладці запуску.</p>)}
        {tab === 'costs' && <CostReport novel={novel} />}
    </div>;
}
