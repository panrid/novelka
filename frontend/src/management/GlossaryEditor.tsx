import { useEffect, useState } from 'react';
import { mutate } from '../api/client';
import { ActionNotice } from '../components/ActionNotice';
import { useAction } from '../hooks/useAction';
import { SelectField } from '../components/SelectField';
import { ListEmpty, ListFilter, ListPages, ListSearch, listParams, useListState, type PageData } from '../components/ListTools';
import { useResource } from '../hooks/useResource';

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

export interface GlossaryProposal {
    id: number;
    proposal: unknown;
    status: 'pending' | 'dismissed' | 'in_dictionary';
    occurrences: number;
    canonicalKey?: string;
    kind?: 'new' | 'update' | 'possible_duplicate';
    differences?: string[];
    candidates?: Entry[];
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

export function GlossaryEditor({ novel, glossary, refresh }: {
    novel: string;
    glossary: Glossary;
    refresh: () => void;
}) {
    const list = useListState('gloss_', 'japanese', ['kind'], 'asc');
    const entriesResource = useResource<PageData<Entry>>('/manage/' + encodeURIComponent(novel) + '/glossary/entries?' + listParams(list.state), true);
    const proposalList = useListState('proposal_', 'created', []);
    const proposalResource = useResource<PageData<GlossaryProposal>>('/manage/' + encodeURIComponent(novel) + '/proposals?' + listParams(proposalList.state), true);
    const proposals = proposalResource.data?.items ?? [];
    const pending = proposals.filter(item => item.status === 'pending');
    const [entry, setEntry] = useState<Entry>({ ...empty });
    const saveAction = useAction();
    const reviewAction = useAction();
    const [probe, setProbe] = useState({ japanese: '', ukrainian: '', kind: '' });
    useEffect(() => {
        const timer = window.setTimeout(() => setProbe({ japanese: entry.japanese, ukrainian: entry.ukrainian, kind: entry.kind }), 300);
        return () => window.clearTimeout(timer);
    }, [entry.japanese, entry.ukrainian, entry.kind]);
    const similarResource = useResource<Entry[]>('/manage/' + encodeURIComponent(novel) + '/glossary/similar?'
        + new URLSearchParams(probe).toString(), true);
    const matches = entriesResource.data?.items ?? [];
    const refreshAll = () => { entriesResource.retry(); similarResource.retry(); proposalResource.retry(); refresh(); };
    const isCharacter = entry.kind === 'character';
    const save = () => saveAction.run(async () => {
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
        refreshAll();
    }, 'Запис збережено. Пов’язані переклади позначено для перевірки.');
    const possibleDuplicates = (similarResource.data ?? []).filter(item => item.key !== entry.key);

    return <div className="glossary-panel"><div className="panel-heading"><div><h2>Словник</h2><p className="muted">Імена, терміни й факти, які ШІ використовує під час перекладу.</p></div><span className="badge">Версія {glossary.revision}</span></div>
        <div className="glossary-layout"><div className="glossary-browser"><ListSearch label="Знайти запис" value={list.state.q} onChange={q => list.update({ q, page: 1 })} />
            <div className="list-toolbar"><ListFilter label="Тип" value={list.state.filters.kind} onChange={value => list.setFilter('kind', value)} options={[
                { value: '', label: 'Усі типи' }, { value: 'character', label: 'Персонажі' }, { value: 'term', label: 'Терміни' },
                { value: 'place', label: 'Місця' }, { value: 'other', label: 'Інше' }]} />
                {list.state.filters.kind && <button type="button" onClick={list.clearFilters}>Очистити фільтр</button>}</div>
            {entriesResource.loading && <p role="status">Оновлюємо словник…</p>}
            {entriesResource.error && <p role="alert">{entriesResource.error} <button type="button" onClick={entriesResource.retry}>Повторити</button></p>}
            <div className="glossary-list" aria-label="Записи словника">{matches.map(item => <button type="button" key={item.key} className={entry.key === item.key ? 'selected' : ''} onClick={() => setEntry({ ...item })}>
                <strong>{item.japanese}</strong> <span>→ {item.ukrainian || 'ще не перекладено'}</span><small>{kindLabel(item.kind)}{item.kind === 'character' && ' · ' + genderLabel(item.gender)}</small>
            </button>)}</div>
            {entriesResource.data && !matches.length && <ListEmpty filtered={!!(list.state.q || list.state.filters.kind)} noun="Записів словника" />}
            {entriesResource.data && <ListPages data={entriesResource.data} onPage={list.setPage} />}
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
                {!!possibleDuplicates.length && <div className="glossary-duplicates"><strong>Схожі записи у словнику</strong>
                    <p>Перевірте, чи це той самий персонаж або термін. Під час об’єднання поточний запис залишиться основним; написання та факти іншого збережуться.</p>
                    {possibleDuplicates.map(candidate => <div key={candidate.key}><span>{candidate.japanese} → {candidate.ukrainian} · {candidate.key}</span>
                        {similarResource.data?.some(existing => existing.key === entry.key) ? <button type="button" disabled={saveAction.busy} onClick={() => { void saveAction.run(async () => {
                            await mutate('/manage/' + encodeURIComponent(novel) + '/glossary/merge', {
                                revision: glossary.revision, keepKey: entry.key, removeKey: candidate.key,
                            }); refreshAll();
                        }, 'Записи об’єднано. Перевірте збережені поля.'); }}>Об’єднати з цим записом</button>
                            : <button type="button" onClick={() => setEntry({ ...candidate })}>Редагувати наявний запис</button>}</div>)}</div>}
                <button className="button" disabled={saveAction.busy}>Зберегти запис</button><ActionNotice {...saveAction} />
            </form>
        </div>
        <details className="suggestion-list"><summary>Пропозиції ШІ: {proposalResource.data?.total ?? '…'} груп</summary>
            <p>Однакові пропозиції згруповано. На цій сторінці потребують перевірки: {pending.length}. Історія пропозицій зберігається після рішення.</p>
            <p>Коректні нові імена й терміни додаються автоматично. Для вже відомого персонажа або терміна пропозиція не перезаписує наявні дані. За потреби відкрийте її у формі, перевірте й збережіть вручну. Історія після цього залишається.</p>
            <div className="list-toolbar"><ListSearch label="Знайти ім’я або термін у пропозиціях" value={proposalList.state.q} onChange={q => proposalList.update({ q, page: 1 })} />
                <ListFilter label="Порядок" value={proposalList.state.sort} onChange={sort => proposalList.update({ sort, page: 1 })} options={[
                    { value: 'created', label: 'За часом' }, { value: 'occurrences', label: 'За кількістю повторів' }]} />
                <button type="button" onClick={() => proposalList.update({ page: 1, direction: proposalList.state.direction === 'asc' ? 'desc' : 'asc' })}
                    aria-label="Змінити напрямок сортування" title="Змінити напрямок сортування">{proposalList.state.direction === 'asc' ? '↑' : '↓'}</button></div>
            {proposalResource.loading && <p role="status">Оновлюємо пропозиції…</p>}
            {proposalResource.error && <p role="alert">{proposalResource.error} <button type="button" onClick={proposalResource.retry}>Повторити</button></p>}
            {proposals.map(item => {
                const suggested = suggestedEntry(item.proposal);
                return <article className="suggestion-card" key={item.id}>
                    <span className="badge">{item.status === 'pending' && item.kind === 'update' ? 'Уточнення наявного запису' : item.status === 'pending' && item.kind === 'possible_duplicate' ? 'Можливий дубль' : { pending: 'На перевірку', dismissed: 'Відхилено', in_dictionary: 'Уже у словнику' }[item.status]}{item.occurrences > 1 && ` · ${item.occurrences} повтори`}</span>
                    {suggested ? <><strong>{suggested.japanese} → {suggested.ukrainian || 'без перекладу'}</strong><span>{kindLabel(suggested.kind)} · глава {suggested.sourceChapter}</span>
                        {!!item.candidates?.length && <span>У словнику: {item.candidates.map(candidate => <button key={candidate.key} type="button" onClick={() => setEntry({ ...candidate })}>{candidate.japanese} → {candidate.ukrainian} · редагувати</button>)}</span>}
                        {!!item.differences?.length && <span>Пропонує змінити: {item.differences.join(', ')}</span>}
                        {item.status === 'pending' && <button type="button" onClick={() => setEntry({ ...suggested, key: item.canonicalKey ?? suggested.key })}>Відкрити у формі</button>}</> : <><strong>Пропозиція з помилкою</strong><p className="muted">ШІ повернув дані, які не можна безпечно додати до словника.</p><details><summary>Технічні дані</summary><pre>{JSON.stringify(item.proposal, null, 2)}</pre></details></>}
                    {item.status === 'pending' && <button type="button" disabled={reviewAction.busy} onClick={() => { void reviewAction.run(async () => {
                        await mutate('/manage/' + encodeURIComponent(novel) + '/proposals/' + item.id + '/dismiss'); refreshAll();
                    }, 'Пропозицію та її однакові повтори відхилено. Словник не змінено.'); }}>Відхилити</button>}
                </article>;
            })}
            {proposalResource.data && !proposals.length && <ListEmpty filtered={!!proposalList.state.q} noun="Пропозицій" />}
            {proposalResource.data && <ListPages data={proposalResource.data} onPage={proposalList.setPage} />}
            <ActionNotice {...reviewAction} />
        </details>
    </div>;
}

function kindLabel(kind: string) {
    return ({ character: 'персонаж', term: 'термін', place: 'місце', other: 'інше' } as Record<string, string>)[kind] || kind;
}

function genderLabel(gender: string) {
    return ({ unknown: 'стать невідома', male: 'чоловіча стать', female: 'жіноча стать', other: 'інша стать' } as Record<string, string>)[gender] || gender;
}
