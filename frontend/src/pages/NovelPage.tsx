import { useEffect } from 'react';
import type { NovelDetail } from '../api/types';
import { useResource } from '../hooks/useResource';
import { chapterPath, novelPath } from '../lib/routes';
import { readPreference } from '../lib/preferences';
import { ErrorState, Loading } from '../components/Status';

export function NovelPage({ id }: { id: string }) {
    const { data, error, retry } = useResource<NovelDetail>(novelPath(id));
    useEffect(() => { document.title = data ? data.title + ' — Новелка' : 'Новела — Новелка'; }, [data]);
    if (error) return <ErrorState message={error} retry={retry} />;
    if (!data) return <Loading />;
    const last = Number(readPreference('chapter:' + data.id));
    const resume = data.chapters.find(chapter => chapter.number === last);
    const first = resume || data.chapters[0];
    return <div className="page novel-page">
        <a className="back-link" href="#/">← До каталогу</a>
        <section className="novel-intro"><div className="eyebrow">Японська новела · {data.id}</div><h1>{data.title}</h1><p className="novel-author">{data.author}</p>{data.description && <p className="novel-description">{data.description}</p>}
            <p className="muted">{data.chapters.length} готових глав із {data.chapterCount} в оригіналі</p>
            {first && <a className="button" href={'#' + chapterPath(data.id, first.number)}>{resume ? 'Продовжити читання' : 'Почати читання'} <span aria-hidden="true">→</span></a>}
        </section>
        <section className="contents-section"><div className="section-heading"><h2>Зміст</h2><span className="count-label">Український переклад</span></div>
            {data.chapters.length === 0 ? <div className="empty-state"><h3>Переклад ще готується</h3><p>Готові глави з’являться тут після завершення перекладу через CLI.</p></div>
                : <ol className="chapter-list">{data.chapters.map(chapter => <li key={chapter.number}><a href={'#' + chapterPath(data.id, chapter.number)}><span className="chapter-number">{String(chapter.number).padStart(2, '0')}</span><span>{chapter.title}</span><span className="chapter-arrow" aria-hidden="true">→</span></a></li>)}</ol>}
        </section>
    </div>;
}
