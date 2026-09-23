import { useEffect, useState } from 'react';
import { SelectField } from './SelectField';

export interface PageData<T> { items: T[]; page: number; size: number; total: number; totalPages: number }
export interface ListState { page: number; q: string; sort: string; direction: 'asc' | 'desc'; filters: Record<string, string> }

export function useListState(prefix: string, defaultSort: string, filterNames: string[], defaultDirection: 'asc' | 'desc' = 'desc') {
    const read = (): ListState => {
        const params = new URLSearchParams(window.location.hash.split('?')[1] || '');
        return { page: Math.max(1, Number(params.get(prefix + 'page')) || 1), q: params.get(prefix + 'q') || '',
            sort: params.get(prefix + 'sort') || defaultSort,
            direction: params.get(prefix + 'direction') === 'asc' ? 'asc' : params.get(prefix + 'direction') === 'desc' ? 'desc' : defaultDirection,
            filters: Object.fromEntries(filterNames.map(name => [name, params.get(prefix + name) || ''])) };
    };
    const [state, setState] = useState(read);
    useEffect(() => {
        const sync = () => setState(read());
        window.addEventListener('hashchange', sync);
        window.addEventListener('popstate', sync);
        return () => { window.removeEventListener('hashchange', sync); window.removeEventListener('popstate', sync); };
    }, [prefix, defaultSort, defaultDirection, filterNames.join('|')]);
    const update = (next: Partial<ListState>) => setState(previous => {
        const value = { ...previous, ...next };
        const [path, search] = window.location.hash.slice(1).split('?');
        const params = new URLSearchParams(search || '');
        const fields = { page: String(value.page), q: value.q, sort: value.sort, direction: value.direction, ...value.filters };
        Object.entries(fields).forEach(([key, field]) => field ? params.set(prefix + key, field) : params.delete(prefix + key));
        window.history.replaceState(null, '', '#' + path + (params.size ? '?' + params : ''));
        return value;
    });
    return { state, update, setPage: (page: number) => update({ page }),
        setSort: (sort: string) => update({ page: 1, sort, direction: state.sort === sort && state.direction === 'asc' ? 'desc' : 'asc' }),
        setFilter: (name: string, value: string) => update({ page: 1, filters: { ...state.filters, [name]: value } }),
        clearFilters: () => update({ page: 1, filters: Object.fromEntries(filterNames.map(name => [name, ''])) }) };
}

export function useDebouncedQuery(value: string, onChange: (value: string) => void) {
    const [draft, setDraft] = useState(value);
    useEffect(() => { setDraft(value); }, [value]);
    useEffect(() => {
        if (draft === value) return;
        const timer = window.setTimeout(() => onChange(draft.trim()), 300);
        return () => window.clearTimeout(timer);
    }, [draft, value, onChange]);
    return { draft, setDraft };
}

export function listParams(state: ListState, extras: Record<string, string | boolean> = {}) {
    const params = new URLSearchParams({ page: String(state.page), size: '25', q: state.q, sort: state.sort,
        direction: state.direction, ...Object.fromEntries(Object.entries(state.filters).filter(([, value]) => value)),
        ...Object.fromEntries(Object.entries(extras).map(([key, value]) => [key, String(value)])) });
    return params.toString();
}

export function ListSearch({ value, onChange, label }: { value: string; onChange: (value: string) => void; label: string }) {
    const { draft, setDraft } = useDebouncedQuery(value, onChange);
    return <label className="list-search">{label}<input type="search" value={draft} onChange={event => setDraft(event.target.value)} /></label>;
}

export function ListFilter({ label, value, onChange, options }: { label: string; value: string; onChange: (value: string) => void; options: { value: string; label: string }[] }) {
    return <SelectField label={label} value={value} onChange={onChange} options={options} />;
}

export function ListPages({ data, onPage }: { data: PageData<unknown>; onPage: (page: number) => void }) {
    useEffect(() => {
        if (data.page > Math.max(1, data.totalPages)) onPage(Math.max(1, data.totalPages));
    }, [data.page, data.totalPages, onPage]);
    if (!data.totalPages) return null;
    return <nav className="list-pages" aria-label="Сторінки списку"><button disabled={data.page <= 1} onClick={() => onPage(data.page - 1)}>← Назад</button>
        <span>Сторінка {data.page} з {data.totalPages} · {data.total} записів</span>
        <button disabled={data.page >= data.totalPages} onClick={() => onPage(data.page + 1)}>Далі →</button></nav>;
}

export function ListEmpty({ filtered, noun }: { filtered: boolean; noun: string }) {
    return <div className="empty-state"><p>{filtered ? 'За заданими параметрами нічого не знайдено.' : `${noun} поки немає.`}</p></div>;
}

export function TableHeader({ label, help, sortKey, state, onSort }: {
    label: string; help: string; sortKey?: string; state?: ListState; onSort?: (key: string) => void;
}) {
    const selected = sortKey && state?.sort === sortKey;
    return <th scope="col" aria-sort={sortKey ? selected ? state?.direction === 'asc' ? 'ascending' : 'descending' : 'none' : undefined}>
        <span className="table-heading">{sortKey && onSort ? <button type="button" className="sort-button" onClick={() => onSort(sortKey)}>
            {label}<span aria-hidden="true">{selected ? state?.direction === 'asc' ? '↑' : '↓' : '↕'}</span></button> : <span>{label}</span>}
            <details className="column-help"><summary aria-label={'Пояснення: ' + label}>?</summary><span>{help}</span></details></span></th>;
}
