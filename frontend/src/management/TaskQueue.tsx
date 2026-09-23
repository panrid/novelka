import { useEffect, useState } from 'react';
import { getJson, mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';
import { describeTaskFailure } from './taskFailure';
import type { TaskPreset } from './TaskPreset';
import { ListEmpty, ListFilter, ListPages, ListSearch, listParams, useListState, type PageData } from '../components/ListTools';

interface Task {
    id: string; operation: string; novel_id: string; state: string; message: string | null;
    current_job_id: string | null; current_chapter?: number | null; spent_usd: number; cancel_requested: boolean; username: string;
    created_at?: string; request?: { first: number; last: number; dictionarySearchLimit?: number };
    can_resume?: boolean; can_proofread?: boolean; latest_job_state?: string;
}
const states: Record<string, string> = { queued: 'У черзі', running: 'Виконується', complete: 'Готово', failed: 'Помилка', interrupted: 'Перервано', cancelled: 'Зупинено' };
const operations: Record<string, string> = { import: 'Імпорт', translate: 'Переклад', proofread: 'Вичитка', resume: 'Відновлення' };

function chapterRange(task: Task) {
    if (task.operation === 'resume') return task.current_chapter ? `Глава ${task.current_chapter}` : null;
    const first = task.request?.first;
    const last = task.request?.last;
    if (first === 0 && last === 0) return 'Метадані';
    if (!first || !last) return null;
    return first === last ? `Глава ${first}` : `Глави ${first}–${last}`;
}

export function TaskQueue({ version, onPrepare, taskId }: { version: number; onPrepare: (preset: TaskPreset) => void; taskId?: string }) {
    const [tasks, setTasks] = useState<Task[]>([]);
    const [pageData, setPageData] = useState<PageData<Task>>();
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);
    const [retry, setRetry] = useState(0);
    const list = useListState('task_', 'created', ['state', 'operation']);
    const action = useAction();
    useEffect(() => {
        const controller = new AbortController();
        const refresh = async () => {
            try {
                const data = taskId ? null : await getJson<PageData<Task>>('/tasks?' + listParams(list.state), controller.signal);
                const items = data ? data.items : [await getJson<Task>('/tasks/' + encodeURIComponent(taskId!), controller.signal)];
                if (!controller.signal.aborted) { setTasks(items); setPageData(data ?? undefined); setError(''); setLoading(false); }
            }
            catch (failure) { if (!controller.signal.aborted) { setError(failure instanceof Error ? failure.message : 'Помилка черги'); setLoading(false); } }
        };
        setLoading(true);
        void refresh(); const timer = window.setInterval(() => { void refresh(); }, 5000);
        return () => { controller.abort(); window.clearInterval(timer); };
    }, [version, list.state, action.message, taskId, retry]);
    return <section className="panel task-queue"><div className="task-queue-heading"><div><h2>Черга завдань</h2>
        <p className="muted">Оновлюється кожні 5 секунд</p></div></div>
        {!taskId && <div className="list-toolbar"><ListSearch label="Новела або ID завдання" value={list.state.q} onChange={q => list.update({ q, page: 1 })} />
            <ListFilter label="Стан" value={list.state.filters.state} onChange={value => list.setFilter('state', value)} options={[
                { value: '', label: 'Усі стани' }, ...Object.entries(states).map(([value, label]) => ({ value, label }))]} />
            <ListFilter label="Операція" value={list.state.filters.operation} onChange={value => list.setFilter('operation', value)} options={[
                { value: '', label: 'Усі операції' }, ...Object.entries(operations).map(([value, label]) => ({ value, label }))]} />
            {(list.state.filters.state || list.state.filters.operation) && <button onClick={list.clearFilters}>Очистити фільтри</button>}</div>}
        {error && <div role="alert">{error} <button onClick={() => setRetry(value => value + 1)}>Повторити</button></div>}<ActionNotice {...action} />
        {loading && <p role="status">Оновлюємо завдання…</p>}
        {!loading && !error && !tasks.length && <ListEmpty filtered={!!(list.state.q || list.state.filters.state || list.state.filters.operation)} noun="Завдань" />}
        {!error && <div className="task-list">{tasks.map(task => {
            const failure = task.message && ['failed', 'interrupted'].includes(task.state) ? describeTaskFailure(task.message) : null;
            const range = chapterRange(task);
            const stopped = ['failed', 'interrupted', 'cancelled'].includes(task.state);
            const needsReview = task.latest_job_state === 'needs-review' || /Dictionary changed|Словник змінився/.test(task.message ?? '');
            const glossary = needsReview || /dictionary|словник|tool round/i.test(task.message ?? '');
            const chapter = task.current_chapter ?? (task.request?.first === task.request?.last ? task.request?.first : undefined);
            const dictionarySearchLimit = task.request?.dictionarySearchLimit ?? 6;
            return <article className="task-card" key={task.id} aria-label={`${operations[task.operation] || task.operation} ${task.novel_id}`}>
                <div className="task-card-main"><div className="task-card-title"><strong>{operations[task.operation] || task.operation}</strong>
                    <span>{task.novel_id}</span>{range && <span>{range}</span>}</div>
                    <div className="task-card-status"><span className={`badge task-state task-state-${task.state}`}>{states[task.state] || task.state}</span>
                        <span title="Включно з резервом для запитів без підтвердженої ціни">Витрати: ${Number(task.spent_usd).toFixed(6)}</span></div></div>
                {task.current_chapter && task.request?.first !== task.request?.last && task.operation !== 'resume' && ['running', 'failed', 'interrupted'].includes(task.state)
                    && <p className="muted task-current-chapter">Поточна глава: {task.current_chapter}</p>}
                {failure && <p className="task-failure-title" role={task.state === 'failed' ? 'alert' : undefined}>{failure.title}</p>}
                {task.cancel_requested && task.state === 'running' && <p className="muted task-cancel-note">Зупиниться після поточного запиту до ШІ.</p>}
                <div className="task-card-actions">
                    {(stopped || needsReview) && <div className="task-quick-actions">
                        {glossary && <a className="quick-action" href={'#/manage?novel=' + encodeURIComponent(task.novel_id) + '&tab=glossary&task=' + encodeURIComponent(task.id)}>Відкрити словник</a>}
                        {stopped && task.can_resume && task.current_job_id && chapter && <button type="button" onClick={() => onPrepare({ operation: 'resume', novelId: task.novel_id, chapter, jobId: task.current_job_id!, dictionarySearchLimit })}>Відновити</button>}
                        {needsReview && task.can_proofread && chapter && <button type="button" onClick={() => onPrepare({ operation: 'proofread', novelId: task.novel_id, chapter, dictionarySearchLimit })}>Повторно вичитати</button>}
                        {chapter && chapter > 0 && task.operation !== 'import' && <button type="button" onClick={() => onPrepare({ operation: 'translate', novelId: task.novel_id, chapter, force: true, dictionarySearchLimit })}>Перекласти главу заново</button>}
                    </div>}
                    <details><summary>Подробиці</summary><div className="task-card-details">
                        {failure && <p>{failure.detail}</p>}
                        {task.message && !failure && <p>{task.message}</p>}
                        {task.current_job_id && <p>ID перекладу для відновлення: <code>{task.current_job_id}</code></p>}
                        {task.operation !== 'import' && <p>Ліміт пакетних звернень до словника: {dictionarySearchLimit} на етап сегмента.</p>}
                        <p>Автор: {task.username}{task.created_at && ` · Створено: ${new Date(task.created_at).toLocaleString('uk-UA')}`}</p>
                        <p className="muted">Витрати включають резерв для запитів без підтвердженої ціни.</p>
                    </div></details>
                    {['queued', 'running'].includes(task.state) && <button type="button" disabled={action.busy || task.cancel_requested}
                        onClick={() => { void action.run(() => mutate('/tasks/' + task.id + '/cancel'), 'Запит на зупинку надіслано.'); }}>
                        {task.cancel_requested ? 'Зупиняється…' : 'Зупинити'}</button>}
                </div>
            </article>;
        })}</div>}
        {!taskId && pageData && <ListPages data={pageData} onPage={list.setPage} />}
    </section>;
}
