import { useState } from 'react';
import { mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';

export interface Entry { key: string; kind: string; japanese: string; reading: string; ukrainian: string; aliases: string[]; gender: string; facts: string; certainty: string; sourceChapter: number; manual: boolean }
export interface Glossary { revision: number; entries: Entry[] }
const empty: Entry = { key: '', kind: 'character', japanese: '', reading: '', ukrainian: '', aliases: [], gender: 'unknown', facts: '', certainty: 'unknown', sourceChapter: 1, manual: true };
export function GlossaryEditor({ novel, glossary, proposals, refresh }: { novel: string; glossary: Glossary; proposals: { id: number; proposal: unknown }[]; refresh: () => void }) {
    const [query, setQuery] = useState('');
    const [entry, setEntry] = useState<Entry>({ ...empty });
    const action = useAction();
    const matches = glossary.entries.filter(item => JSON.stringify(item).toLowerCase().includes(query.toLowerCase()));
    return <section className="panel"><h2>Словник · версія {glossary.revision}</h2>
        <label>Пошук імен, термінів і фактів<input value={query} onChange={event => setQuery(event.target.value)} /></label>
        <div className="glossary-list">{matches.map(item => <button key={item.key} onClick={() => setEntry({ ...item })}>{item.japanese} → {item.ukrainian || '—'} <small>{item.key} · {item.gender}</small></button>)}</div>
        {!matches.length && <p>Записів не знайдено.</p>}
        <button onClick={() => setEntry({ ...empty })}>Новий запис</button>
        <form className="stack-form" onSubmit={event => { event.preventDefault(); void action.run(async () => {
            await mutate('/manage/' + encodeURIComponent(novel) + '/glossary', { revision: glossary.revision, entries: [entry] }); refresh();
        }, 'Запис збережено. Залежні переклади позначено на перевірку.'); }}>
            <div className="form-grid">{Object.entries({ key: 'Стабільний ключ', kind: 'Тип (character / term / place)', japanese: 'Японською', reading: 'Читання', ukrainian: 'Українською' }).map(([key, label]) => <label key={key}>{label}<input required={['key', 'japanese', 'kind'].includes(key)} maxLength={1000} value={entry[key as keyof Entry] as string} onChange={event => setEntry({ ...entry, [key]: event.target.value })} /></label>)}</div>
            <p className="muted">Ключ визначає запис: той самий ключ оновлює його, інший створює новий. Ручні записи мають пріоритет перед пропозиціями ШІ.</p>
            <label>Інші написання, через кому<input value={entry.aliases.join(', ')} onChange={event => setEntry({ ...entry, aliases: event.target.value.split(',').map(value => value.trim()) })} /></label>
            <div className="form-grid"><label>Стать<select value={entry.gender} onChange={event => setEntry({ ...entry, gender: event.target.value })}>{Object.entries({ unknown: 'Невідомо', male: 'Чоловіча', female: 'Жіноча', other: 'Інша' }).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
                <label>Впевненість<select value={entry.certainty} onChange={event => setEntry({ ...entry, certainty: event.target.value })}>{Object.entries({ unknown: 'Невідомо', assumed: 'Припущення', confirmed: 'Підтверджено' }).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
                <label>Глава-джерело<input type="number" min="1" required value={entry.sourceChapter} onChange={event => setEntry({ ...entry, sourceChapter: Number(event.target.value) })} /></label></div>
            <label>Факти й контекст<textarea rows={4} maxLength={10000} value={entry.facts} onChange={event => setEntry({ ...entry, facts: event.target.value })} /></label>
            <button className="button" disabled={action.busy}>Зберегти запис</button><ActionNotice {...action} />
        </form>
        <details><summary>Пропозиції ШІ ({proposals.length})</summary><p>Перевірте пропозицію та перенесіть підтверджені дані у форму вище.</p>{proposals.map(proposal => <pre key={proposal.id}>{JSON.stringify(proposal.proposal, null, 2)}</pre>)}</details>
    </section>;
}
