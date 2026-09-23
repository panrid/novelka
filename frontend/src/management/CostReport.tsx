import { useResource } from '../hooks/useResource';
import { ErrorState, Loading } from '../components/Status';
import { ListEmpty, ListFilter, ListPages, ListSearch, TableHeader, listParams, useListState, type PageData } from '../components/ListTools';

type Cost = Record<string, string | number | null>;
interface Column { key: string; label: string; help: string; type: 'text' | 'number' | 'money' | 'date' }
const money = (value: string | number | null | undefined) => value == null ? 'Невідомо' : '$' + Number(value).toFixed(6);

export function CostReport({ novel }: { novel: string }) {
    const list = useListState('cost_', '', ['details', 'stage']);
    const details = list.state.filters.details === 'true';
    const resource = useResource<PageData<Cost>>('/manage/costs?' + listParams(list.state, { details, novel }), true);
    const columns: Column[] = [
        { key: 'novel_id', label: 'Новела', help: 'ID новели, для якої зроблено запит.', type: 'text' },
        { key: 'chapter', label: 'Глава', help: 'Номер глави в оригіналі.', type: 'number' },
        { key: 'stage', label: 'Етап', help: 'Аналіз, переклад або вичитка.', type: 'text' },
        { key: 'model', label: 'Модель', help: 'Модель ШІ, що обробила текст.', type: 'text' },
        { key: 'estimated_usd', label: 'Оцінка', help: 'Резервована сума перед запитом у доларах США.', type: 'money' },
        { key: details ? 'actual_usd' : 'known_actual_usd', label: 'Факт', help: 'Підтверджена провайдером ціна; невідома ціна не дорівнює нулю.', type: 'money' },
        ...(details ? [{ key: 'state', label: 'Стан', help: 'Результат окремого запиту.', type: 'text' } as Column]
            : [{ key: 'unknown_cost_calls', label: 'Невідомих', help: 'Запити без підтвердженої ціни.', type: 'number' },
                { key: 'calls', label: 'Запитів', help: 'Кількість запитів у цій групі.', type: 'number' }] as Column[]),
        { key: 'input_tokens', label: 'Токени вхід', help: 'Вхідні токени, повідомлені провайдером.', type: 'number' },
        { key: 'output_tokens', label: 'Токени вихід', help: 'Вихідні токени, повідомлені провайдером.', type: 'number' },
        ...(details ? [{ key: 'created_at', label: 'Час', help: 'Час початку запиту.', type: 'date' } as Column] : []),
    ];
    const data = resource.data;
    return <section className="panel"><h2>Історія витрат</h2><p className="muted">{novel ? 'Для вибраної новели.' : 'Для всіх новел.'} Невідома фактична ціна не вважається нулем.</p>
        <div className="list-toolbar"><ListSearch label="Новела або модель" value={list.state.q} onChange={q => list.update({ q, page: 1 })} />
            <ListFilter label="Етап" value={list.state.filters.stage} onChange={value => list.setFilter('stage', value)} options={[
                { value: '', label: 'Усі етапи' }, { value: 'analyze', label: 'Аналіз' }, { value: 'translate', label: 'Переклад' }, { value: 'proofread', label: 'Вичитка' }]} />
            <label className="check-label"><input type="checkbox" checked={details} onChange={event => list.update({ page: 1, sort: '', filters: { ...list.state.filters, details: event.target.checked ? 'true' : '' } })} />Кожен запит окремо</label>
            {list.state.filters.stage && <button onClick={() => list.setFilter('stage', '')}>Очистити фільтр</button>}
            <button onClick={resource.retry}>Оновити витрати</button></div>
        {resource.error ? <ErrorState message={resource.error} retry={resource.retry} /> : !data ? <Loading /> : <>
            {resource.loading && <p role="status">Оновлюємо витрати…</p>}
            {data.items.length ? <div className="table-scroll"><table className="cost-table"><caption className="sr-only">Історія витрат</caption><thead><tr>{columns.map(column =>
                <TableHeader key={column.key} label={column.label} help={column.help} sortKey={column.key === 'created_at' ? 'created' : column.key} state={list.state} onSort={list.setSort} />)}</tr></thead>
                <tbody>{data.items.map((row, index) => <tr key={String(row.id ?? index)}>{columns.map(column => <td key={column.key}>
                    {column.type === 'money' ? money(row[column.key]) : column.type === 'date' && row[column.key] != null ? new Date(row[column.key]!).toLocaleString('uk-UA') : row[column.key] ?? '—'}
                    {column.key === 'novel_id' && details && <details className="cost-identifiers"><summary>ID запиту</summary><small className="block">Job: {row.job_id}<br />Запит: {row.id}</small></details>}
                </td>)}</tr>)}</tbody></table></div> : <ListEmpty filtered={!!(list.state.q || list.state.filters.stage || novel)} noun="Платних запитів" />}
            <ListPages data={data} onPage={list.setPage} /></>}
    </section>;
}
