import { useState } from 'react';
import { useResource } from '../hooks/useResource';
import { ErrorState, Loading } from '../components/Status';

type Cost = Record<string, string | number | null>;
const money = (value: string | number | null | undefined) => value == null ? 'Невідомо' : '$' + Number(value).toFixed(6);
export function CostReport({ novel }: { novel: string }) {
    const [details, setDetails] = useState(false);
    const resource = useResource<Cost[]>('/manage/costs?details=' + details + (novel ? '&novel=' + encodeURIComponent(novel) : ''));
    return <section className="panel"><h2>Історія витрат</h2><p className="muted">{novel ? 'Для вибраної новели.' : 'Для всіх новел.'} Невідома фактична ціна не вважається нулем. Оцінка — резерв на момент запиту.</p>
        <div className="button-row"><label className="check-label"><input type="checkbox" checked={details} onChange={event => setDetails(event.target.checked)} />Кожен запит окремо</label><button onClick={resource.retry}>Оновити витрати</button></div>
        {resource.error ? <ErrorState message={resource.error} retry={resource.retry} /> : !resource.data ? <Loading /> : <>
            {!resource.data.length && <p>Платних запитів ще не було.</p>}
            <div className="table-scroll"><table><thead><tr><th>Новела / глава</th><th>Етап / модель</th><th>Оцінка</th><th>Факт</th><th>{details ? 'Стан' : 'Невідомих / усього'}</th><th>Токени вхід / вихід</th></tr></thead>
                <tbody>{resource.data.map((row, index) => <tr key={String(row.id ?? index)}><td>{row.novel_id} / {row.chapter}{details && <small className="block">Job: {row.job_id}<br />Запит: {row.id}</small>}</td>
                    <td>{row.stage}<small className="block">{row.model}</small></td><td>{money(row.estimated_usd)}</td><td>{money(details ? row.actual_usd : row.known_actual_usd)}</td><td>{details ? row.state : `${row.unknown_cost_calls} / ${row.calls}`}</td><td>{row.input_tokens ?? '—'} / {row.output_tokens ?? '—'}</td></tr>)}</tbody></table></div>
        </>}
    </section>;
}
