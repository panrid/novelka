import { useEffect, useState } from 'react';
import { getJson, mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';

interface Task { id: string; operation: string; novel_id: string; state: string; message: string | null; current_job_id: string | null; spent_usd: number; cancel_requested: boolean; username: string }
const states: Record<string, string> = { queued: 'У черзі', running: 'Виконується', complete: 'Готово', failed: 'Помилка', interrupted: 'Перервано', cancelled: 'Зупинено' };
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
    return <section className="panel"><h2>Черга завдань</h2><p className="muted">Оновлюється кожні 5 секунд. Зупинка спрацьовує між запитами до ШІ.</p>
        {error && <p role="alert">{error}</p>}<ActionNotice {...action} />
        {!tasks.length && <p>Завдань поки немає.</p>}
        {tasks.map(task => <article className="task-card" key={task.id}><div className="button-row"><strong>{task.novel_id} · {task.operation}</strong><span className="badge">{states[task.state] || task.state}</span></div>
            <p>Облік цього запуску: ${Number(task.spent_usd).toFixed(6)} · {task.username}</p>
            <p className="muted">Включає резерв для викликів без підтвердженої ціни.</p>
            {task.current_job_id && <p>ID перекладу: <code>{task.current_job_id}</code></p>}{task.message && <p>{task.message}</p>}
            {['queued', 'running'].includes(task.state) && <button disabled={action.busy || task.cancel_requested} onClick={() => { void action.run(() => mutate('/tasks/' + task.id + '/cancel'), 'Запит на зупинку надіслано.'); }}>{task.cancel_requested ? 'Зупиняється…' : 'Зупинити'}</button>}
        </article>)}
        <div className="button-row"><button disabled={offset === 0} onClick={() => setOffset(value => Math.max(0, value - 50))}>Новіші</button><button disabled={tasks.length < 50} onClick={() => setOffset(value => value + 50)}>Старіші</button></div>
    </section>;
}
