import { useState } from 'react';
import { mutate } from '../api/client';
import { useResource } from '../hooks/useResource';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';
import { ErrorState, Loading } from '../components/Status';
import { GlossaryEditor, type Glossary } from './GlossaryEditor';

interface Detail {
    novel: { id: string; title: string; titleUk: string | null; chapterCount: number };
    aliases: { alias: string }[]; chapters: { number: number; title: string }[];
    jobs: { id: string; chapter: number; revision: number; state: string }[];
    glossary: Glossary; proposals: { id: number; proposal: unknown }[];
}
export function NovelManager({ novel }: { novel: string }) {
    const path = '/manage/' + encodeURIComponent(novel);
    const resource = useResource<Detail>(path);
    const action = useAction();
    const [title, setTitle] = useState('');
    const [alias, setAlias] = useState('');
    const [chapter, setChapter] = useState(1);
    const [text, setText] = useState('');
    if (resource.error) return <ErrorState message={resource.error} retry={resource.retry} />;
    if (!resource.data) return <Loading />;
    const data = resource.data;
    return <><section className="panel"><h2>{data.novel.titleUk || data.novel.title}</h2><p className="muted">{data.novel.title} · {data.novel.chapterCount} глав</p>
        <form className="stack-form" onSubmit={event => { event.preventDefault(); void action.run(async () => { await mutate(path + '/title', { titleUk: title }); resource.retry(); }); }}>
            <label>Нова українська назва<input required maxLength={500} placeholder={data.novel.titleUk || 'Українська назва'} value={title} onChange={event => setTitle(event.target.value)} /></label><button disabled={action.busy}>Зберегти назву</button>
        </form>
        <h3>Аліаси</h3><div className="button-row">{data.aliases.map(item => <span key={item.alias}>{item.alias} <button aria-label={'Видалити аліас ' + item.alias} disabled={action.busy} onClick={() => { void action.run(async () => { await mutate(path + '/aliases/' + encodeURIComponent(item.alias), undefined, 'DELETE'); resource.retry(); }); }}>×</button></span>)}</div>
        <form className="inline-form" onSubmit={event => { event.preventDefault(); void action.run(async () => { await mutate(path + '/aliases', { alias }); setAlias(''); resource.retry(); }); }}><label>Новий аліас<input required value={alias} onChange={event => setAlias(event.target.value)} /></label><button disabled={action.busy}>Додати аліас</button></form>
        <ActionNotice {...action} /><div className="button-row"><a className="button secondary" href={'/api' + path + '/export?format=epub'}>Завантажити EPUB</a><a className="button secondary" href={'/api' + path + '/export?format=html'}>Завантажити HTML</a></div>
    </section>
        <GlossaryEditor novel={novel} glossary={data.glossary} proposals={data.proposals} refresh={resource.retry} />
        <section className="panel"><h2>Оригінали й переклади</h2><p>Імпортовано глав: {data.chapters.length}. Усі ревізії зберігаються.</p>
            <div className="table-scroll"><table><thead><tr><th>Глава</th><th>Ревізія</th><th>Стан</th><th>ID для відновлення</th></tr></thead><tbody>{data.jobs.map(job => <tr key={job.id}><td>{job.chapter}</td><td>{job.revision}</td><td>{job.state}</td><td><code>{job.id}</code></td></tr>)}</tbody></table></div>
            <details><summary>Вставити оригінальний текст вручну</summary><p>Зміна оригіналу може приховати застарілий переклад до повторного перекладу.</p>
                <form className="stack-form" onSubmit={event => { event.preventDefault(); void action.run(async () => { await mutate(path + '/text', { chapter, text }); setText(''); resource.retry(); }); }}>
                    <label>Глава<input type="number" min="1" max={data.novel.chapterCount} required value={chapter} onChange={event => setChapter(Number(event.target.value))} /></label>
                    <label>Текст японською<textarea rows={10} required maxLength={300000} value={text} onChange={event => setText(event.target.value)} /></label><button disabled={action.busy}>Зберегти оригінал</button>
                </form></details>
        </section>
    </>;
}
