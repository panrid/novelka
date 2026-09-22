import { useState } from 'react';
import { useResource } from '../hooks/useResource';
import { ErrorState, Loading } from '../components/Status';

type Cost = Record<string, string | number | null>;
interface Column { key: string; label: string; type: 'text' | 'number' | 'money' | 'date' }
const money = (value: string | number | null | undefined) => value == null ? 'Невідомо' : '$' + Number(value).toFixed(6);
export function CostReport({ novel }: { novel: string }) {
    const [details, setDetails] = useState(false);
    const [sort, setSort] = useState<{ key: string; descending: boolean } | null>(null);
    const resource = useResource<Cost[]>('/manage/costs?details=' + details + (novel ? '&novel=' + encodeURIComponent(novel) : ''));
    const columns: Column[] = [
        { key: 'novel_id', label: 'Новела', type: 'text' }, { key: 'chapter', label: 'Глава', type: 'number' },
        { key: 'stage', label: 'Етап', type: 'text' }, { key: 'model', label: 'Модель', type: 'text' },
        { key: 'estimated_usd', label: 'Оцінка', type: 'money' },
        { key: details ? 'actual_usd' : 'known_actual_usd', label: 'Факт', type: 'money' },
        ...(details ? [{ key: 'state', label: 'Стан', type: 'text' } as Column]
            : [{ key: 'unknown_cost_calls', label: 'Невідомих', type: 'number' }, { key: 'calls', label: 'Запитів', type: 'number' }] as Column[]),
        { key: 'input_tokens', label: 'Токени вхід', type: 'number' }, { key: 'output_tokens', label: 'Токени вихід', type: 'number' },
        ...(details ? [{ key: 'created_at', label: 'Час', type: 'date' } as Column] : []),
    ];
    const column = columns.find(item => item.key === sort?.key);
    const rows = [...(resource.data ?? [])];
    if (sort && column) rows.sort((a, b) => {
        const left = a[sort.key], right = b[sort.key];
        // Unknown costs must remain unknown and sort last in either direction.
        if (left == null || right == null) return left == null ? right == null ? 0 : 1 : -1;
        const comparison = column.type === 'text' ? String(left).localeCompare(String(right), 'uk', { numeric: true })
            : column.type === 'date' ? new Date(left).getTime() - new Date(right).getTime() : Number(left) - Number(right);
        return sort.descending ? -comparison : comparison;
    });
    return <section className="panel"><h2>Історія витрат</h2><p className="muted">{novel ? 'Для вибраної новели.' : 'Для всіх новел.'} Невідома фактична ціна не вважається нулем. Оцінка — резерв на момент запиту.</p>
        <div className="button-row"><label className="check-label"><input type="checkbox" checked={details} onChange={event => { setDetails(event.target.checked); setSort(null); }} />Кожен запит окремо</label><button onClick={resource.retry}>Оновити витрати</button></div>
        {resource.error ? <ErrorState message={resource.error} retry={resource.retry} /> : !resource.data ? <Loading /> : <>
            {!resource.data.length && <p>Платних запитів ще не було.</p>}
            <p className="muted table-hint">Натисніть назву стовпця для сортування. Повторне натискання змінює напрямок.</p>
            <div className="table-scroll"><table className="cost-table"><caption className="sr-only">Історія витрат</caption><thead><tr>{columns.map(item =>
                <th key={item.key} scope="col" aria-sort={sort?.key === item.key ? sort.descending ? 'descending' : 'ascending' : 'none'}>
                    <button type="button" className="sort-button" onClick={() => setSort({ key: item.key, descending: sort?.key === item.key ? !sort.descending : false })}>
                        {item.label}<span aria-hidden="true">{sort?.key === item.key ? sort.descending ? '↓' : '↑' : '↕'}</span>
                    </button></th>)}</tr></thead>
                <tbody>{rows.map((row, index) => <tr key={String(row.id ?? index)}>{columns.map(item => <td key={item.key}>
                    {item.type === 'money' ? money(row[item.key]) : item.type === 'date' && row[item.key] != null ? new Date(row[item.key]!).toLocaleString('uk-UA') : row[item.key] ?? '—'}
                    {item.key === 'novel_id' && details && <details className="cost-identifiers"><summary>ID запиту</summary><small className="block">Job: {row.job_id}<br />Запит: {row.id}</small></details>}
                </td>)}</tr>)}</tbody></table></div>
        </>}
    </section>;
}
