import { useEffect, useRef, useState } from 'react';
import { mutate } from '../api/client';
import { ActionNotice } from '../components/ActionNotice';
import { useAction } from '../hooks/useAction';
import { SelectField } from '../components/SelectField';
import type { TaskPreset } from './TaskPreset';
import { HelpField } from '../components/HelpField';

export function TaskForm({ novel, onCreated, preset }: { novel: string; onCreated: (id: string) => void; preset?: TaskPreset }) {
    const [operation, setOperation] = useState<string>(preset?.operation ?? 'translate');
    const [url, setUrl] = useState('');
    const [first, setFirst] = useState(preset?.chapter ?? 1);
    const [last, setLast] = useState(preset?.chapter ?? 1);
    const [jobId, setJobId] = useState(preset?.jobId ?? '');
    const [budget, setBudget] = useState('');
    const [force, setForce] = useState(preset?.force ?? false);
    const [retry, setRetry] = useState(false);
    const [confirmed, setConfirmed] = useState(false);
    const [dictionarySearchLimit, setDictionarySearchLimit] = useState(preset?.dictionarySearchLimit ?? 6);
    const request = useRef({ signature: '', key: '' });
    const action = useAction();
    const panel = useRef<HTMLElement>(null);
    useEffect(() => {
        if (preset) { panel.current?.scrollIntoView({ block: 'start' }); panel.current?.focus({ preventScroll: true }); }
    }, [preset]);
    const paid = operation !== 'import';
    const choose = (value: string) => { setOperation(value); setConfirmed(false); };
    const operationName = ({ import: 'імпорт', translate: 'переклад', proofread: 'вичитка', resume: 'відновлення' } as Record<string, string>)[operation];
    return <section className="panel" ref={panel} tabIndex={-1}><h2>Запустити переклад</h2><p className="muted">Оберіть новелу вище, підготуйте оригінал і запустіть потрібний крок. Черга збереже прогрес, якщо вкладку закрити.</p>
        {preset && operation === preset.operation && <p className="quick-task-notice" role="status">Підготовлено: {operationName}, глава {first}, {novel}. {force ? 'Буде створено новий переклад глави.' : 'Використаємо збережений переклад.'} Вкажіть бюджет і підтвердьте запуск нижче.</p>}
        <form className="stack-form" onSubmit={event => {
            event.preventDefault();
            void action.run(async () => {
                const body = { operation, novelId: novel, url, first, last: operation === 'proofread' ? first : last, jobId,
                    force: operation === 'translate' && force, retryUncertain: operation === 'resume' && retry, budgetUsd: paid ? Number(budget) : 0, dictionarySearchLimit };
                const signature = JSON.stringify(body);
                if (request.current.signature !== signature) request.current = { signature, key: crypto.randomUUID() };
                const created = await mutate<{ id: string }>('/tasks', { ...body, requestKey: request.current.key });
                request.current = { signature: '', key: '' };
                setConfirmed(false);
                onCreated(created.id);
            }, 'Завдання додано в чергу. Вкладку можна закрити.');
        }}>
            <div className="task-choices" aria-label="Основна дія"><button type="button" aria-pressed={operation === 'import'} onClick={() => choose('import')}><strong>1. Імпортувати</strong><span>Завантажити новелу або глави з Syosetu.</span></button>
                <button type="button" aria-pressed={operation === 'translate'} onClick={() => choose('translate')}><strong>2. Перекласти</strong><span>Перекласти та автоматично вичитати готові глави.</span></button></div>
            <details className="advanced-operation" open={['proofread', 'resume'].includes(operation) || undefined}><summary>Додаткові операції</summary>
                <SelectField label="Операція" value={['proofread', 'resume'].includes(operation) ? operation : ''} onChange={value => { if (value) choose(value); }}
                    options={[{ value: '', label: 'Оберіть за потреби' }, { value: 'proofread', label: 'Повторно вичитати одну главу' }, { value: 'resume', label: 'Відновити переклад за ID job' }]} />
                <p className="muted">Використовуйте їх після збою або коли потрібна нова вичитка вже перекладеної глави.</p></details>
            <p className="task-step">Поточний крок: <strong>{operationName}</strong>.</p>
            {operation === 'import' && <label>Посилання на новелу<input type="url" required placeholder="https://ncode.syosetu.com/n0022gd/" value={url} onChange={event => setUrl(event.target.value)} /></label>}
            {operation === 'resume' ? <label>ID перекладу (job)<input required value={jobId} onChange={event => setJobId(event.target.value)} /></label>
                : <div className="form-grid"><label>Перша глава<input type="number" min={operation === 'import' ? 0 : 1} required value={first} onChange={event => setFirst(Number(event.target.value))} /></label>
                    {operation !== 'proofread' && <label>Остання глава<input type="number" min={first} required value={last} onChange={event => setLast(Number(event.target.value))} /></label>}</div>}
            {operation === 'import' && <p className="muted">0–0 імпортує тільки метадані. За один запуск можна імпортувати до 100 глав.</p>}
            {operation === 'translate' && <details><summary>Перекласти готові глави повторно</summary><label className="check-label"><input type="checkbox" checked={force} onChange={event => setForce(event.target.checked)} />Створити нові ревізії навіть для готових глав. Це створить нові витрати.</label></details>}
            {operation === 'resume' && <label className="check-label"><input type="checkbox" checked={retry} onChange={event => setRetry(event.target.checked)} />Я перевірив витрати й дозволяю повтор uncertain-запиту, який міг уже бути оплачений.</label>}
            {paid && <details><summary>Додаткові опції словника</summary>
                <HelpField label="Ліміт звернень до словника" help="Від 0 до 30 на кожен етап сегмента, типово 6. Одне звернення містить до 20 слів одразу. Після ліміту ШІ завершує відповідь із наявним контекстом. 0 вимикає додаткові пошуки; початковий добір словника залишається. Більше звернень може збільшити витрати в межах бюджету.">
                    {id => <input id={id} type="number" min="0" max="30" step="1" required value={dictionarySearchLimit}
                        onChange={event => { setDictionarySearchLimit(Number(event.target.value)); setConfirmed(false); }} />}</HelpField>
            </details>}
            {paid && <fieldset><legend>Ліміт витрат</legend><label>Додатковий бюджет для всього запуску, $<input type="number" required min="0.01" step="0.01" value={budget} onChange={event => { setBudget(event.target.value); setConfirmed(false); }} /></label>
                <p className="muted">Це верхня межа нових запитів у цьому запуску. Оцінені й фактичні витрати залишаються в історії.</p>
                <label className="check-label"><input required type="checkbox" checked={confirmed} onChange={event => setConfirmed(event.target.checked)} />Дозволяю платні запити OpenRouter у межах цього бюджету.</label></fieldset>}
            <button className="button" disabled={action.busy || (paid && !confirmed) || (['translate', 'proofread'].includes(operation) && !novel)}>Додати в чергу</button>
            <ActionNotice {...action} />
        </form>
    </section>;
}
