import { useEffect } from 'react';
import type { NovelDetail, ChapterSummary } from '../api/types';
import { useResource } from '../hooks/useResource';
import { chapterPath, novelPath } from '../lib/routes';
import { readPreference } from '../lib/preferences';
import { ErrorState, Loading } from '../components/Status';
import { ListEmpty, ListPages, ListSearch, listParams, useListState, type PageData } from '../components/ListTools';

export function NovelPage({ id }: { id: string }) {
    const last = Number(readPreference('chapter:' + id));
    const { data, error, retry } = useResource<NovelDetail>(novelPath(id) + (last > 0 ? '?resume=' + last : ''));
    const list = useListState('chapter_', 'number', [], 'asc');
    const contents = useResource<PageData<ChapterSummary>>(novelPath(id) + '/contents?' + listParams(list.state), true);
    useEffect(() => { document.title = data ? data.title + ' — Новелка' : 'Новела — Новелка'; }, [data]);
    if (error) return <ErrorState message={error} retry={retry} />;
    if (!data) return <Loading />;
    const first = data.resumeChapter ?? data.firstChapter;
    return <div className="page novel-page">
        <a className="back-link" href="#/">← До каталогу</a>
        <section className="novel-intro"><div className="eyebrow">Японська новела · {data.id}</div><h1>{data.title}</h1><p className="novel-author">{data.author}</p>{data.tags.length > 0 && <p className="novel-tags" aria-label="Теги">{data.tags.map(tag =>
            <a className="tag-chip" key={tag.slug} href={'#/?tags=' + encodeURIComponent(tag.slug)}>{tag.name}</a>)}</p>}{data.description && <p className="novel-description">{data.description}</p>}
            <p className="muted">{data.readyChapters} готових глав із {data.chapterCount} в оригіналі</p>
            {first && <a className="button" href={'#' + chapterPath(data.id, first)}>{data.resumeChapter ? 'Продовжити читання' : 'Почати читання'} <span aria-hidden="true">→</span></a>}
        </section>
        <section className="contents-section"><div className="section-heading"><h2>Зміст</h2><span className="count-label">Український переклад</span></div>
            {data.readyChapters > 0 && <div className="list-toolbar"><ListSearch label="Знайти главу за назвою або номером" value={list.state.q} onChange={q => list.update({ q, page: 1 })} />
                <button onClick={() => list.update({ page: 1, direction: list.state.direction === 'asc' ? 'desc' : 'asc' })}>Номер {list.state.direction === 'asc' ? '↑' : '↓'}</button></div>}
            {contents.error ? <ErrorState message={contents.error} retry={contents.retry} /> : !contents.data ? <Loading /> : <>
                {contents.loading && <p role="status">Оновлюємо зміст…</p>}
                {contents.data.items.length ? <ol className="chapter-list">{contents.data.items.map(chapter => <li key={chapter.number}><a href={'#' + chapterPath(data.id, chapter.number)}><span className="chapter-number">{String(chapter.number).padStart(2, '0')}</span><span>{chapter.title}</span><span className="chapter-arrow" aria-hidden="true">→</span></a></li>)}</ol>
                    : data.readyChapters ? <ListEmpty filtered={!!list.state.q} noun="Глав" /> : <div className="empty-state"><h3>Переклад ще готується</h3><p>Готові глави з’являться тут після завершення перекладу.</p></div>}
                <ListPages data={contents.data} onPage={list.setPage} /></>}
        </section>
    </div>;
}
