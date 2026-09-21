import { useRef, useState } from 'react';
import { mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';

export function TaskForm({ novel, onCreated }: { novel: string; onCreated: () => void }) {
    const [operation, setOperation] = useState('translate');
    const [url, setUrl] = useState('');
    const [first, setFirst] = useState(1);
    const [last, setLast] = useState(1);
    const [jobId, setJobId] = useState('');
    const [budget, setBudget] = useState('');
    const [force, setForce] = useState(false);
    const [retry, setRetry] = useState(false);
    const [confirmed, setConfirmed] = useState(false);
    const request = useRef({ signature: '', key: '' });
    const action = useAction();
    const paid = operation !== 'import';
    return <section className="panel"><h2>Нове завдання</h2><form className="stack-form" onSubmit={event => {
        event.preventDefault();
        void action.run(async () => {
            const body = { operation, novelId: novel, url, first, last: operation === 'proofread' ? first : last, jobId,
                force: operation === 'translate' && force, retryUncertain: operation === 'resume' && retry, budgetUsd: paid ? Number(budget) : 0 };
            const signature = JSON.stringify(body);
            if (request.current.signature !== signature) request.current = { signature, key: crypto.randomUUID() };
            await mutate('/tasks', { ...body, requestKey: request.current.key });
            request.current = { signature: '', key: '' }; setConfirmed(false); onCreated();
        }, 'Завдання додано в чергу. Вкладку можна закрити.');
    }}>
        <label>Операція<select value={operation} onChange={event => { setOperation(event.target.value); setConfirmed(false); }}>
            <option value="translate">Переклад і автоматична вичитка</option><option value="import">Імпорт із Syosetu</option>
            <option value="proofread">Повторна вичитка глави</option><option value="resume">Відновити переклад за ID</option>
        </select></label>
        {operation === 'import' && <label>Посилання на новелу<input type="url" required placeholder="https://ncode.syosetu.com/n0022gd/" value={url} onChange={event => setUrl(event.target.value)} /></label>}
        {operation === 'resume' ? <label>ID перекладу (job)<input required value={jobId} onChange={event => setJobId(event.target.value)} /></label>
            : <div className="form-grid"><label>Перша глава<input type="number" min={operation === 'import' ? 0 : 1} required value={first} onChange={event => setFirst(Number(event.target.value))} /></label>
                {operation !== 'proofread' && <label>Остання глава<input type="number" min={first} required value={last} onChange={event => setLast(Number(event.target.value))} /></label>}</div>}
        {operation === 'import' && <small>До 100 глав за запуск. Діапазон 0–0 імпортує лише опис новели.</small>}
        {operation === 'translate' && <label className="check-label"><input type="checkbox" checked={force} onChange={event => setForce(event.target.checked)} />Створити нові ревізії навіть для готових глав (повторні витрати)</label>}
        {operation === 'resume' && <label className="check-label"><input type="checkbox" checked={retry} onChange={event => setRetry(event.target.checked)} />Я перевірив витрати й дозволяю повтор uncertain-запиту, який міг уже бути оплачений</label>}
        {paid && <><label>Додатковий бюджет усього запуску, $<input type="number" required min="0.01" step="0.01" value={budget} onChange={event => { setBudget(event.target.value); setConfirmed(false); }} /></label>
            <p className="muted">Резерв розраховується за тарифами власника. Фактична ціна провайдера може відрізнятися. Історія оцінених і фактичних витрат зберігається.</p>
            <label className="check-label"><input required type="checkbox" checked={confirmed} onChange={event => setConfirmed(event.target.checked)} />Дозволяю платні запити OpenRouter в межах вказаного бюджету</label></>}
        <button className="button" disabled={action.busy || (paid && !confirmed) || (['translate', 'proofread'].includes(operation) && !novel)}>Додати в чергу</button>
        <ActionNotice {...action} />
    </form></section>;
}
