import { useEffect, useState } from 'react';
import { getJson, mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';
import { describeTaskFailure } from './taskFailure';

interface Task {
    id: string; operation: string; novel_id: string; state: string; message: string | null;
    current_job_id: string | null; current_chapter?: number | null; spent_usd: number; cancel_requested: boolean; username: string;
    created_at?: string; request?: { first: number; last: number };
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

export function TaskQueue({ version }: { version: number }) {
    const [tasks, setTasks] = useState<Task[]>([]);
    const [error, setError] = useState('');
    const [offset, setOffset] = useState(0);
    const action = useAction();
    useEffect(() => {
        const controller = new AbortController();
        const refresh = async () => {
            try { const data = await getJson<Task[]>('/tasks?offset=' + offset, controller.signal); if (!controller.signal.aborted) { setTasks(data); setError(''); } }
            catch (failure) { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : 'Помилка черги'); }
        };
        void refresh(); const timer = window.setInterval(() => { void refresh(); }, 5000);
        return () => { controller.abort(); window.clearInterval(timer); };
    }, [version, offset, action.message]);
    return <section className="panel task-queue"><div className="task-queue-heading"><div><h2>Черга завдань</h2>
        <p className="muted">Оновлюється кожні 5 секунд</p></div></div>
        {error && <p role="alert">{error}</p>}<ActionNotice {...action} />
        {!tasks.length && <p>Завдань поки немає.</p>}
        <div className="task-list">{tasks.map(task => {
            const failure = task.message && ['failed', 'interrupted'].includes(task.state) ? describeTaskFailure(task.message) : null;
            const range = chapterRange(task);
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
                    <details><summary>Подробиці</summary><div className="task-card-details">
                        {failure && <p>{failure.detail}</p>}
                        {task.message && !failure && <p>{task.message}</p>}
                        {task.current_job_id && <p>ID перекладу для відновлення: <code>{task.current_job_id}</code></p>}
                        <p>Автор: {task.username}{task.created_at && ` · Створено: ${new Date(task.created_at).toLocaleString('uk-UA')}`}</p>
                        <p className="muted">Витрати включають резерв для запитів без підтвердженої ціни.</p>
                    </div></details>
                    {['queued', 'running'].includes(task.state) && <button type="button" disabled={action.busy || task.cancel_requested}
                        onClick={() => { void action.run(() => mutate('/tasks/' + task.id + '/cancel'), 'Запит на зупинку надіслано.'); }}>
                        {task.cancel_requested ? 'Зупиняється…' : 'Зупинити'}</button>}
                </div>
            </article>;
        })}</div>
        {(offset > 0 || tasks.length === 50) && <div className="task-pages"><button disabled={offset === 0} onClick={() => setOffset(value => Math.max(0, value - 50))}>Новіші</button>
            <button disabled={tasks.length < 50} onClick={() => setOffset(value => value + 50)}>Старіші</button></div>}
    </section>;
}
