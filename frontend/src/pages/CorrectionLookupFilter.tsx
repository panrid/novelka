import { useState } from 'react';
import { useResource } from '../hooks/useResource';
import { useDebouncedQuery } from '../components/ListTools';

interface Choice { id: string; title?: string; username?: string }

export function CorrectionLookupFilter({ label, endpoint, value, selected, onChange }: {
    label: string; endpoint: string; value: string; selected?: string; onChange: (value: string) => void;
}) {
    const [query, setQuery] = useState('');
    const [picked, setPicked] = useState<Choice | null>(null);
    const selectedLabel = picked?.id === value ? picked.title || picked.username || value : selected || value;
    const { draft, setDraft } = useDebouncedQuery(query, setQuery);
    const resource = useResource<{ items: Choice[] }>(endpoint + '?q=' + encodeURIComponent(query) + '&size=20');
    return <div className="correction-lookup">
        <label>{label}{value ? <span className="correction-selected">
            <span>{selectedLabel}</span><button type="button" aria-label={'Очистити: ' + label} title={'Очистити: ' + label} onClick={() => { onChange(''); setDraft(''); setQuery(''); }}>×</button>
        </span> : <input type="search" value={draft} onChange={event => setDraft(event.target.value)} placeholder="Почніть вводити назву або ім’я" />}</label>
        {!value && draft.trim() && <div className="correction-lookup-results">
            {resource.error ? <p role="alert">Не вдалося знайти варіанти. {resource.error}</p>
                : resource.loading || query !== draft.trim() ? <p role="status">Шукаємо…</p>
                    : resource.data?.items.length ? <ul>{resource.data.items.map(item => <li key={item.id}><button type="button" onClick={() => {
                        setPicked(item); onChange(item.id); setDraft(''); setQuery('');
                    }}>{item.title || item.username || item.id}<small>{item.id}</small></button></li>)}</ul>
                        : <p>Нічого не знайдено.</p>}
        </div>}
    </div>;
}
