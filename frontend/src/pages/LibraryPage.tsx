import { useEffect } from 'react';
import { useResource } from '../hooks/useResource';
import { novelPath } from '../lib/routes';
import { shelves, shelfLabel } from '../lib/library';
import { ErrorState, Loading } from '../components/Status';
import { ListPages, ListSearch, listParams, useListState, type PageData } from '../components/ListTools';
import { SelectField } from '../components/SelectField';
import { NovelCover } from '../components/NovelCover';
import { LibraryControl } from '../components/LibraryControl';

interface LibraryItem { id: string; title: string; author: string; chapterCount: number; readyChapters: number; status: string; updatedAt: string }

const emptyText: Record<string, string> = {
    reading: 'Додайте новелу до «Читаю» на її сторінці — вона з’явиться тут.',
    planned: 'Збережіть сюди історії, до яких хочете повернутися.',
    completed: 'Тут будуть прочитані новели.',
    on_hold: 'Тут будуть новели, які ви відклали на потім.',
    dropped: 'Тут будуть новели, які ви вирішили не дочитувати.',
};

/** The reader's private shelves: tabs with counts, search and order; a novel can move between shelves right from its card. */
export function LibraryPage() {
    const list = useListState('', 'updated', ['status']);
    const status = list.state.filters.status || 'reading';
    const counts = useResource<Record<string, number>>('/library/counts', true);
    const { data, error, retry, loading } = useResource<PageData<LibraryItem>>('/library?' + listParams(list.state, { status }), true);
    useEffect(() => { document.title = 'Моя бібліотека — Новелка'; }, []);
    const refresh = () => { retry(); counts.retry(); };
    return <div className="page workspace library-page">
        <p className="eyebrow">Лише для вас</p><h1>Моя бібліотека</h1>
        <div className="tab-bar" role="group" aria-label="Списки бібліотеки">{shelves.map(shelf =>
            <button key={shelf.value} aria-pressed={status === shelf.value} onClick={() => list.update({ page: 1, filters: { status: shelf.value } })}>
                {shelf.label}{counts.data && <span className="tab-count"> {counts.data[shelf.value] ?? 0}</span>}</button>)}</div>
        <div className="catalog-tools">
            <ListSearch label="Назва або автор" value={list.state.q} onChange={q => list.update({ q, page: 1 })} />
            <SelectField label="Порядок" value={list.state.sort} onChange={sort => list.update({ sort, page: 1 })}
                options={[{ value: 'updated', label: 'Нещодавно змінені' }, { value: 'title', label: 'Назва' }, { value: 'ready', label: 'Готові глави' }]} />
            <button aria-label="Змінити напрямок сортування" title="Змінити напрямок сортування"
                onClick={() => list.update({ direction: list.state.direction === 'asc' ? 'desc' : 'asc', page: 1 })}>{list.state.direction === 'asc' ? '↑' : '↓'}</button>
        </div>
        {error ? <ErrorState message={error} retry={retry} /> : !data ? <Loading /> : <>
            {loading && <p role="status">Оновлюємо бібліотеку…</p>}
            {data.items.length === 0 ? <div className="empty-state">
                <span className="empty-symbol" aria-hidden="true">棚</span>
                <h3>{list.state.q ? 'Нічого не знайдено' : `У списку «${shelfLabel(status)}» поки порожньо`}</h3>
                <p>{list.state.q ? 'Спробуйте іншу назву або автора.' : emptyText[status]}</p>
                {!list.state.q && <a className="text-link" href="#/">До каталогу <span aria-hidden="true">→</span></a>}
            </div> : <div className="novel-grid">{data.items.map(novel => <div className="library-card" key={novel.id}>
                <a className="novel-card" href={'#' + novelPath(novel.id)}>
                    <NovelCover id={novel.id} title={novel.title} />
                    <div className="card-body"><h3>{novel.title}</h3><p className="card-author">{novel.author}</p>
                        <div className="card-bottom"><span>{novel.readyChapters} / {novel.chapterCount} глав</span><span aria-hidden="true">↗</span></div></div>
                </a>
                <LibraryControl key={novel.id + novel.status} novel={novel.id} initial={novel.status} label={'Список для «' + novel.title + '»'} hideLabel onChange={refresh} />
            </div>)}</div>}
            <ListPages data={data} onPage={list.setPage} /></>}
    </div>;
}
