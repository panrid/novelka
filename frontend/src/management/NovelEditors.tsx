import { useEffect, useId, useState } from 'react';
import { getJson, mutate } from '../api/client';
import { useResource } from '../hooks/useResource';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';
import { Autocomplete } from '../components/Autocomplete';
import { ErrorState, Loading } from '../components/Status';

interface Person { id: string; username: string }
interface Editors { owner: Person | null; openReview: boolean; editors: (Person & { granted_at: string })[] }

const date = new Intl.DateTimeFormat('uk-UA', { dateStyle: 'medium' });

/** The translator (or an administrator) picks who decides on corrections of this novel, or opens review to everyone. */
export function NovelEditors({ novel }: { novel: string }) {
    const path = '/manage/' + encodeURIComponent(novel);
    const id = useId();
    const resource = useResource<Editors>(path + '/editors');
    const [data, setData] = useState<Editors | undefined>();
    const [query, setQuery] = useState('');
    const [suggestions, setSuggestions] = useState<Person[]>([]);
    const action = useAction();
    useEffect(() => { if (resource.data) setData(resource.data); }, [resource.data]);
    useEffect(() => {
        const q = query.trim().replace(/^@/, '');
        if (!q) { setSuggestions([]); return; }
        const controller = new AbortController();
        const timer = window.setTimeout(() => {
            getJson<{ items: Person[] }>('/users/search?q=' + encodeURIComponent(q), controller.signal)
                .then(result => setSuggestions(result.items)).catch(() => {});
        }, 200);
        return () => { window.clearTimeout(timer); controller.abort(); };
    }, [query]);
    if (resource.error) return <ErrorState message={resource.error} retry={resource.retry} />;
    if (!data) return <Loading />;
    const change = (request: () => Promise<Editors>, message: string) => void action.run(async () => { setData(await request()); }, message);
    const add = (person: Person) => {
        setQuery('');
        change(() => mutate<Editors>(path + '/editors', { accountId: person.id }), `${person.username} тепер вирішує щодо правок.`);
    };
    const taken = new Set([data.owner?.id, ...data.editors.map(editor => editor.id)]);
    return <section className="novel-editors" aria-labelledby={id + '-title'}>
        <h3 id={id + '-title'}>Хто вирішує щодо правок</h3>
        <p className="muted">{data.owner ? <>Перекладач <strong>{data.owner.username}</strong> завжди погоджує правки, зокрема власні.</>
            : 'У новели немає перекладача: нею керують адміністратори.'} Редактори не погоджують власних правок.</p>
        <label className="check-label"><input type="checkbox" checked={data.openReview} disabled={action.busy}
            onChange={event => change(() => mutate<Editors>(path + '/review-access', { open: event.target.checked }),
                event.target.checked ? 'Тепер правки можуть перевіряти всі користувачі.' : 'Правки перевіряють лише вибрані редактори.')} />
            Усі користувачі, включно з майбутніми</label>
        {!data.openReview && <>
            <div className="model-picker"><label htmlFor={id}>Додати редактора</label>
                <Autocomplete id={id} value={query} onChange={setQuery} placeholder="Нік користувача" maxLength={41}
                    onPick={suggestion => { const person = suggestions.find(item => item.id === suggestion.value); if (person) add(person); }}
                    suggestions={suggestions.filter(person => !taken.has(person.id)).map(person => ({ value: person.id, label: person.username }))} /></div>
            {data.editors.length ? <ul className="editor-list">{data.editors.map(editor => <li key={editor.id}>
                <span><strong>{editor.username}</strong> <span className="muted">з {date.format(new Date(editor.granted_at))}</span></span>
                <button type="button" className="plain-button" disabled={action.busy} aria-label={'Прибрати редактора ' + editor.username}
                    onClick={() => change(() => mutate<Editors>(path + '/editors/' + encodeURIComponent(editor.id), undefined, 'DELETE'),
                        `${editor.username} більше не перевіряє правки.`)}>Прибрати</button>
            </li>)}</ul> : <p className="muted">Редакторів ще немає.</p>}
        </>}
        <ActionNotice {...action} />
    </section>;
}
