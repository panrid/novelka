import { useState } from 'react';
import { mutate, getJson } from '../api/client';
import { ActionNotice } from '../components/ActionNotice';
import { ErrorState, Loading } from '../components/Status';
import { useAction } from '../hooks/useAction';
import { useResource } from '../hooks/useResource';

interface Overview {
    drafts: { chapter: number; title: string; updated_at: string; updated_by: string }[];
    published: { chapter: number; title: string; revision: number }[];
}
interface ChapterState {
    draft: { title: string; text: string } | null;
    published: { title: string; text: string; revision: number } | null;
}

/**
 * Chapters of a manual novel: a private draft is edited and saved, then published as the next revision.
 * Published chapters open as a new draft, so editing never changes what readers see until publication.
 */
export function ManualChapters({ path }: { path: string }) {
    const overview = useResource<Overview>(path + '/manual', true);
    const action = useAction();
    const [chapter, setChapter] = useState(0);
    const [title, setTitle] = useState('');
    const [text, setText] = useState('');
    const [state, setState] = useState<ChapterState>();
    if (overview.error) return <ErrorState message={overview.error} retry={overview.retry} />;
    if (!overview.data) return <Loading />;
    const data = overview.data;
    const next = Math.max(0, ...data.published.map(item => item.chapter), ...data.drafts.map(item => item.chapter)) + 1;
    const open = (number: number) => void action.run(async () => {
        const loaded = await getJson<ChapterState>(path + '/manual/' + number);
        const source = loaded.draft ?? loaded.published;
        setChapter(number); setState(loaded); setTitle(source?.title ?? ''); setText(source?.text ?? '');
    });
    const save = async () => { await mutate(path + '/manual/' + chapter, { title, text }); overview.retry(); };
    return <section className="manual-chapters" aria-labelledby="manual-chapters-title">
        <h3 id="manual-chapters-title">Глави вручну</h3>
        <p className="muted">Чернетку бачать лише адміністратори. Публікація створює нову ревізію глави; попередні ревізії зберігаються.</p>
        <div className="manual-chapter-lists">
            <div><h4>Опубліковані</h4>{data.published.length ? <ul>{data.published.map(item => <li key={item.chapter}>
                <button type="button" className="link-button" onClick={() => open(item.chapter)}>{item.chapter}. {item.title}</button>
                <span className="muted"> · ревізія {item.revision}</span></li>)}</ul> : <p className="muted">Ще немає.</p>}</div>
            <div><h4>Чернетки</h4>{data.drafts.length ? <ul>{data.drafts.map(item => <li key={item.chapter}>
                <button type="button" className="link-button" onClick={() => open(item.chapter)}>{item.chapter}. {item.title}</button>
                <span className="muted"> · {item.updated_by}, {new Date(item.updated_at).toLocaleString('uk-UA')}</span></li>)}</ul> : <p className="muted">Немає.</p>}</div>
        </div>
        {!chapter ? <button type="button" className="button" onClick={() => open(next)}>Нова глава {next}</button>
            : <form className="stack-form" onSubmit={event => { event.preventDefault(); void action.run(save, 'Чернетку збережено.'); }}>
                <p className="manual-chapter-status" role="status">Глава {chapter}: {state?.draft ? 'редагується чернетка' : state?.published
                    ? `опублікована ревізія ${state.published.revision}; зміни стануть новою ревізією` : 'нова глава'}.</p>
                <label>Назва глави<input required maxLength={300} value={title} onChange={event => setTitle(event.target.value)} /></label>
                <label>Текст перекладу<textarea required rows={14} maxLength={300000} value={text} onChange={event => setText(event.target.value)} /></label>
                <small className="muted">Кожен непорожній рядок стає абзацом. Рядок із «***» або «◇◇◇» стає розділювачем сцени.</small>
                <div className="button-row">
                    <button disabled={action.busy}>Зберегти чернетку</button>
                    <button type="button" className="button" disabled={action.busy || !title.trim() || !text.trim()} onClick={() => void action.run(async () => {
                        await save();
                        const result = await mutate<{ message: string }>(path + '/manual/' + chapter + '/publish');
                        setState(await getJson<ChapterState>(path + '/manual/' + chapter));
                        overview.retry();
                        return result;
                    }, 'Главу опубліковано. Читачі вже бачать нову ревізію.')}>Опублікувати</button>
                    {state?.draft && <button type="button" disabled={action.busy} onClick={() => {
                        if (!window.confirm('Видалити чернетку глави ' + chapter + '? Опублікований текст не зміниться.')) return;
                        void action.run(async () => { await mutate(path + '/manual/' + chapter, undefined, 'DELETE'); overview.retry(); setChapter(0); }, 'Чернетку видалено.');
                    }}>Видалити чернетку</button>}
                    <button type="button" className="plain-button" onClick={() => setChapter(0)}>Закрити</button>
                </div>
            </form>}
        <ActionNotice {...action} />
    </section>;
}
