import { useEffect } from 'react';
import type { NovelCard } from '../api/types';
import { useResource } from '../hooks/useResource';
import { novelPath } from '../lib/routes';
import { ErrorState, Loading } from '../components/Status';
import { ListPages, ListSearch, listParams, useListState, type PageData } from '../components/ListTools';

export function CatalogPage() {
    const list = useListState('', 'title', ['readyOnly'], 'asc');
    const readyOnly = list.state.filters.readyOnly === 'true';
    const { data, error, retry, loading } = useResource<PageData<NovelCard>>('/novels/search?' + listParams(list.state, { readyOnly }), true);
    useEffect(() => { document.title = 'Каталог — Новелка'; }, []);
    const novels = data?.items;
    return <div className="page catalog-page">
        <section className="hero">
            <div className="hero-copy">
                <p className="eyebrow">Японські новели · Український переклад</p>
                <h1>Ще одна глава.<br /><em>Ще один світ.</em></h1>
                <p className="hero-description">Знайомі слова. Незнайомі світи. Оберіть історію<br className="desktop-break" /> й дозвольте собі трохи загубитися між рядками.</p>
                <a className="text-link" href="#catalog" onClick={event => { event.preventDefault(); document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth' }); }}>Знайти свою історію <span aria-hidden="true">↘</span></a>
            </div>
            <div className="hero-art" aria-hidden="true">
                <div className="art-sun" /><div className="art-orbit" />
                <div className="art-book art-book-back" /><div className="art-book art-book-front"><span>物語</span><small>ІСТОРІЇ<br />МІЖ СВІТАМИ</small></div>
                <span className="art-caption">Відкрийте наступну сторінку</span>
            </div>
        </section>
        <section id="catalog" className="catalog-section" aria-labelledby="catalog-title">
            <div className="section-heading"><div><p className="eyebrow">Ваша наступна історія</p><h2 id="catalog-title">Каталог новел</h2></div>{data && <span className="count-label">{data.total} у каталозі</span>}</div>
            <div className="catalog-tools">
                <ListSearch label="Назва, автор або аліас" value={list.state.q} onChange={q => list.update({ q, page: 1 })} />
                <button className={'filter-button' + (readyOnly ? ' selected' : '')} aria-pressed={readyOnly} onClick={() => list.setFilter('readyOnly', readyOnly ? '' : 'true')}>Є готові глави <span aria-hidden="true">✓</span></button>
                <label>Порядок<select value={list.state.sort} onChange={event => list.update({ sort: event.target.value, page: 1 })}><option value="title">Назва</option><option value="author">Автор</option><option value="ready">Готові глави</option></select></label>
                <button aria-label="Змінити напрямок сортування" onClick={() => list.update({ direction: list.state.direction === 'asc' ? 'desc' : 'asc', page: 1 })}>{list.state.direction === 'asc' ? '↑' : '↓'}</button>
            </div>
            {error ? <ErrorState message={error} retry={retry} /> : !novels ? <Loading /> : <>{loading && <p role="status">Оновлюємо каталог…</p>}{novels.length === 0 ? <div className="empty-state">
                <span className="empty-symbol" aria-hidden="true">書</span>
                <h3>{list.state.q || readyOnly ? 'Історію не знайдено' : 'Перша історія ще попереду'}</h3>
                <p>{list.state.q || readyOnly ? 'Спробуйте іншу назву, автора або вимкніть фільтр готових глав.' : 'Імпортуйте новелу в майстерні — вона з’явиться тут.'}</p>
            </div> : <div className="novel-grid">{novels.map((novel, index) => <a className="novel-card" key={novel.id} href={'#' + novelPath(novel.id)}>
                <div className={'book-cover cover-' + index % 4} aria-hidden="true"><div className="cover-circle" /><span className="cover-id">{novel.id}</span><span className="cover-letter">{novel.title.slice(0, 1)}</span><span className="cover-imprint">NOVELKA / STORIES</span></div>
                <div className="card-body"><span className={'availability' + (novel.readyChapters ? ' available' : '')}>{novel.readyChapters ? 'Готово до читання' : 'Очікує перекладу'}</span>
                    <h3>{novel.title}</h3><p className="card-author">{novel.author}</p>{novel.description && <p className="card-description">{novel.description}</p>}
                    {novel.aliases.length > 0 && <p className="card-alias">{novel.aliases.join(' · ')}</p>}
                    <div className="card-bottom"><span>{novel.readyChapters} / {novel.chapterCount} глав</span><span aria-hidden="true">↗</span></div>
                </div>
            </a>)}</div>}<ListPages data={data} onPage={list.setPage} /></>}
        </section>
    </div>;
}
