import { useState } from 'react';
import { mutate } from '../api/client';
import { ActionNotice } from '../components/ActionNotice';
import { ErrorState, Loading } from '../components/Status';
import { useAction } from '../hooks/useAction';
import { useResource } from '../hooks/useResource';
import { GlossaryEditor, type Glossary } from './GlossaryEditor';
import { JobTable } from './JobTable';

interface Detail {
    novel: {
        id: string;
        title: string;
        titleUk: string | null;
        author: string;
        authorUk: string | null;
        descriptionUk: string | null;
        chapterCount: number;
    };
    aliases: { alias: string }[];
    importedChapters: number;
    glossary: Glossary;
}

export function NovelManager({ novel, initialTab = 'info' }: { novel: string; initialTab?: 'info' | 'glossary' }) {
    const path = '/manage/' + encodeURIComponent(novel);
    const resource = useResource<Detail>(path, true);
    const action = useAction();
    const [tab, setTab] = useState<'info' | 'glossary' | 'materials'>(initialTab);
    const [alias, setAlias] = useState('');
    const [chapter, setChapter] = useState(1);
    const [text, setText] = useState('');
    if (resource.error) return <ErrorState message={resource.error} retry={resource.retry} />;
    if (!resource.data) return <Loading />;
    const data = resource.data;
    const saveMetadata = (form: HTMLFormElement) => action.run(async () => {
        const values = new FormData(form);
        await mutate(path + '/metadata', {
            titleUk: values.get('titleUk'), authorUk: values.get('authorUk'), descriptionUk: values.get('descriptionUk'),
        });
        resource.retry();
    }, 'Українські дані новели збережено.');

    return <><section className="panel novel-management"><div className="panel-heading"><div><p className="eyebrow">{data.novel.id}</p><h2>{data.novel.titleUk || data.novel.title}</h2><p className="muted">Оригінал: {data.novel.title} · {data.novel.author} · {data.novel.chapterCount} глав</p></div></div>
        <nav className="tab-bar compact-tabs" aria-label="Керування новелою"><button aria-pressed={tab === 'info'} onClick={() => setTab('info')}>Дані новели</button><button aria-pressed={tab === 'glossary'} onClick={() => setTab('glossary')}>Словник</button><button aria-pressed={tab === 'materials'} onClick={() => setTab('materials')}>Глави й експорт</button></nav>
        {tab === 'info' && <><p>Ці поля бачать читачі в каталозі й на сторінці новели. Порожнє поле прибирає локалізований варіант.</p>
            <form className="stack-form" onSubmit={event => { event.preventDefault(); void saveMetadata(event.currentTarget); }}>
                <label>Українська назва<input name="titleUk" maxLength={500} defaultValue={data.novel.titleUk || ''} placeholder={data.novel.title} /></label>
                <label>Автор українською<input name="authorUk" maxLength={500} defaultValue={data.novel.authorUk || ''} placeholder={data.novel.author} /></label>
                <label>Опис українською<textarea name="descriptionUk" rows={6} maxLength={10000} defaultValue={data.novel.descriptionUk || ''} placeholder="Коротко опишіть зав’язку, жанр або світ новели." /></label>
                <button className="button" disabled={action.busy}>Зберегти дані</button><ActionNotice {...action} />
            </form>
            <details><summary>Аліаси й технічні дії</summary><p className="muted">Аліас дозволяє запускати CLI-команди коротким іменем. Читачі його також можуть використати в пошуку.</p>
                <div className="button-row">{data.aliases.map(item => <span className="badge" key={item.alias}>{item.alias} <button aria-label={'Видалити аліас ' + item.alias} disabled={action.busy} onClick={() => { void action.run(async () => { await mutate(path + '/aliases/' + encodeURIComponent(item.alias), undefined, 'DELETE'); resource.retry(); }); }}>×</button></span>)}</div>
                <form className="inline-form" onSubmit={event => { event.preventDefault(); void action.run(async () => { await mutate(path + '/aliases', { alias }); setAlias(''); resource.retry(); }); }}><label>Новий аліас<input required value={alias} onChange={event => setAlias(event.target.value)} /></label><button disabled={action.busy}>Додати аліас</button></form>
            </details>
        </>}
        {tab === 'glossary' && <GlossaryEditor novel={novel} glossary={data.glossary} refresh={resource.retry} />}
        {tab === 'materials' && <><p>Імпортовано оригіналів: {data.importedChapters}. Кожен переклад має окрему ревізію.</p>
            <div className="button-row"><a className="button secondary" href={'/api' + path + '/export?format=epub'}>Завантажити EPUB</a><a className="button secondary" href={'/api' + path + '/export?format=html'}>Завантажити HTML</a></div>
            <JobTable novel={novel} />
            <details><summary>Вставити оригінальний текст вручну</summary><p>Зміна оригіналу приховає застарілий переклад, доки ви не створите нову ревізію.</p>
                <form className="stack-form" onSubmit={event => { event.preventDefault(); void action.run(async () => { await mutate(path + '/text', { chapter, text }); setText(''); resource.retry(); }); }}>
                    <label>Глава<input type="number" min="1" max={data.novel.chapterCount} required value={chapter} onChange={event => setChapter(Number(event.target.value))} /></label>
                    <label>Текст японською<textarea rows={10} required maxLength={300000} value={text} onChange={event => setText(event.target.value)} /></label><button disabled={action.busy}>Зберегти оригінал</button>
                </form>
            </details>
        </>}
    </section></>;
}
