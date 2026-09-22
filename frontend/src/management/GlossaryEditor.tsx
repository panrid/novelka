import { useState } from 'react';
import { mutate } from '../api/client';
import { ActionNotice } from '../components/ActionNotice';
import { useAction } from '../hooks/useAction';
import { SelectField } from '../components/SelectField';

export interface Entry {
    key: string;
    kind: string;
    japanese: string;
    reading: string;
    ukrainian: string;
    aliases: string[];
    gender: string;
    facts: string;
    certainty: string;
    sourceChapter: number;
    manual: boolean;
}

export interface Glossary {
    revision: number;
    entries: Entry[];
}

const empty: Entry = {
    key: '', kind: 'character', japanese: '', reading: '', ukrainian: '', aliases: [], gender: 'unknown',
    facts: '', certainty: 'unknown', sourceChapter: 1, manual: true,
};

function isEntry(value: unknown): value is Entry {
    return typeof value === 'object' && value !== null && 'japanese' in value && 'ukrainian' in value && 'kind' in value;
}

function suggestedEntry(value: unknown) {
    return isEntry(value) ? {
        ...empty,
        ...value,
        aliases: Array.isArray(value.aliases) ? value.aliases : [],
        key: value.key || value.japanese,
        manual: true,
    } : null;
}

export function GlossaryEditor({ novel, glossary, proposals, refresh }: {
    novel: string;
    glossary: Glossary;
    proposals: { id: number; proposal: unknown }[];
    refresh: () => void;
}) {
    const [query, setQuery] = useState('');
    const [entry, setEntry] = useState<Entry>({ ...empty });
    const action = useAction();
    const normalizedQuery = query.trim().toLocaleLowerCase('uk');
    const matches = glossary.entries.filter(item => [item.japanese, item.ukrainian, item.reading, ...item.aliases]
        .join(' ').toLocaleLowerCase('uk').includes(normalizedQuery));
    const isCharacter = entry.kind === 'character';
    const save = () => action.run(async () => {
        const updated = {
            ...entry,
            key: entry.key.trim() || entry.japanese.trim(),
            japanese: entry.japanese.trim(),
            ukrainian: entry.ukrainian.trim(),
            reading: entry.reading.trim(),
            aliases: entry.aliases.map(value => value.trim()).filter(Boolean),
            facts: entry.facts.trim(),
            manual: true,
        };
        await mutate('/manage/' + encodeURIComponent(novel) + '/glossary', { revision: glossary.revision, entries: [updated] });
        setEntry(updated);
        refresh();
    }, 'Запис збережено. Пов’язані переклади позначено для перевірки.');

    return <div className="glossary-panel"><div className="panel-heading"><div><h2>Словник</h2><p className="muted">Імена, терміни й факти, які ШІ використовує під час перекладу.</p></div><span className="badge">Версія {glossary.revision}</span></div>
        <div className="glossary-layout"><div className="glossary-browser"><label>Знайти запис<input placeholder="Ім’я, термін або переклад" value={query} onChange={event => setQuery(event.target.value)} /></label>
            <div className="glossary-list" aria-label="Записи словника">{matches.map(item => <button type="button" key={item.key} className={entry.key === item.key ? 'selected' : ''} onClick={() => setEntry({ ...item })}>
                <strong>{item.japanese}</strong> <span>→ {item.ukrainian || 'ще не перекладено'}</span><small>{kindLabel(item.kind)}{item.kind === 'character' && ' · ' + genderLabel(item.gender)}</small>
            </button>)}</div>
            {!matches.length && <p className="muted">Записів не знайдено.</p>}
            <button type="button" onClick={() => setEntry({ ...empty })}>+ Новий запис</button>
        </div>
            <form className="stack-form glossary-form" onSubmit={event => { event.preventDefault(); void save(); }}><h3>{entry.key ? 'Редагувати запис' : 'Новий запис'}</h3>
                <div className="form-grid"><label>Японською<input required maxLength={1000} placeholder="涼" value={entry.japanese} onChange={event => setEntry({ ...entry, japanese: event.target.value })} /></label>
                    <label>Українською<input maxLength={1000} placeholder="Рьо" value={entry.ukrainian} onChange={event => setEntry({ ...entry, ukrainian: event.target.value })} /></label>
                    <SelectField label="Це" value={entry.kind} onChange={kind => setEntry({ ...entry, kind })}
                        options={[{ value: 'character', label: 'Персонаж' }, { value: 'term', label: 'Термін' }, { value: 'place', label: 'Місце' }, { value: 'other', label: 'Інше' }]} />
                    {isCharacter && <SelectField label="Стать" value={entry.gender} onChange={gender => setEntry({ ...entry, gender })}
                        options={[{ value: 'unknown', label: 'Невідомо' }, { value: 'male', label: 'Чоловіча' }, { value: 'female', label: 'Жіноча' }, { value: 'other', label: 'Інша' }]} />}
                </div>
                <label>Коротке пояснення для перекладу<textarea rows={3} maxLength={10000} placeholder="Роль, стосунки, важливі деталі сюжету" value={entry.facts} onChange={event => setEntry({ ...entry, facts: event.target.value })} /></label>
                <details><summary>Додаткові дані</summary><div className="form-grid"><label>Читання<input maxLength={1000} placeholder="りょう" value={entry.reading} onChange={event => setEntry({ ...entry, reading: event.target.value })} /></label>
                    <label>Інші написання<input maxLength={4000} placeholder="через кому" value={entry.aliases.join(', ')} onChange={event => setEntry({ ...entry, aliases: event.target.value.split(',') })} /></label>
                    <SelectField label="Впевненість" value={entry.certainty} onChange={certainty => setEntry({ ...entry, certainty })}
                        options={[{ value: 'unknown', label: 'Невідомо' }, { value: 'assumed', label: 'Припущення' }, { value: 'confirmed', label: 'Підтверджено' }]} />
                    <label>Глава-джерело<input type="number" min="1" required value={entry.sourceChapter} onChange={event => setEntry({ ...entry, sourceChapter: Number(event.target.value) })} /></label>
                    <label>Технічний ключ<input maxLength={1000} placeholder="За замовчуванням — японське написання" value={entry.key} onChange={event => setEntry({ ...entry, key: event.target.value })} /></label>
                </div><p className="muted">Ключ потрібен лише для стабільного оновлення наявного запису. Для нового запису його можна не заповнювати.</p></details>
                <button className="button" disabled={action.busy}>Зберегти запис</button><ActionNotice {...action} />
            </form>
        </div>
        <details className="suggestion-list"><summary>Пропозиції ШІ ({proposals.length})</summary><p>Пропозиції не змінюють словник автоматично. Відкрийте потрібну у формі, перевірте дані та збережіть її вручну.</p>
            {proposals.map(item => {
                const suggested = suggestedEntry(item.proposal);
                return <article className="suggestion-card" key={item.id}>{suggested ? <><strong>{suggested.japanese} → {suggested.ukrainian || 'без перекладу'}</strong><span>{kindLabel(suggested.kind)}</span><button type="button" onClick={() => setEntry(suggested)}>Відкрити у формі</button></> : <><strong>Пропозиція з помилкою</strong><p className="muted">ШІ повернув дані, які не можна безпечно додати до словника.</p><details><summary>Технічні дані</summary><pre>{JSON.stringify(item.proposal, null, 2)}</pre></details></>}</article>;
            })}
            {!proposals.length && <p className="muted">Поки немає нових пропозицій.</p>}
        </details>
    </div>;
}

function kindLabel(kind: string) {
    return ({ character: 'персонаж', term: 'термін', place: 'місце', other: 'інше' } as Record<string, string>)[kind] || kind;
}

function genderLabel(gender: string) {
    return ({ unknown: 'стать невідома', male: 'чоловіча стать', female: 'жіноча стать', other: 'інша стать' } as Record<string, string>)[gender] || gender;
}
