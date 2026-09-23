import { useId, useState } from 'react';
import { useResource } from '../hooks/useResource';
import { useDebouncedQuery } from './ListTools';
import { tagName } from '../lib/tags';

interface TagSuggestion { name: string; slug: string; novels: number }

/** Text field with suggestions from existing tags; Enter or the button adds the typed tag. */
export function TagInput({ label, onAdd, placeholder, action = 'Додати' }: {
    label: string; onAdd: (name: string) => void; placeholder?: string; action?: string;
}) {
    const id = useId();
    const [query, setQuery] = useState('');
    const { draft, setDraft } = useDebouncedQuery(query, setQuery);
    const suggestions = useResource<{ items: TagSuggestion[] }>('/tags?q=' + encodeURIComponent(query));
    const add = () => {
        const name = tagName(draft);
        if (!name || name.length > 40 || name.includes('|')) return;
        onAdd(name); setDraft(''); setQuery('');
    };
    return <div className="tag-input">
        <label htmlFor={id}>{label}</label>
        <div className="tag-input-row">
            <input id={id} list={id + '-tags'} value={draft} maxLength={40} placeholder={placeholder} autoComplete="off"
                onChange={event => setDraft(event.target.value)}
                onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); add(); } }} />
            <button type="button" onClick={add} disabled={!tagName(draft)}>{action}</button>
        </div>
        <datalist id={id + '-tags'}>{suggestions.data?.items.map(item =>
            <option key={item.slug} value={item.name}>{item.novels} нов.</option>)}</datalist>
    </div>;
}
