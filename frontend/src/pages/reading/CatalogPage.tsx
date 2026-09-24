import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { useNavigate, useSearch } from '@tanstack/react-router';
import { useEffect, useState } from 'react';
import { readingApi } from '../../reading/api';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
import { TextInput } from '../../ui/TextInput';
import { CardRow } from './HomePage';
import styles from './reading.module.css';

export type CatalogSearch = { q?: string; tags?: string[]; kind?: string; sort?: string };

const KIND_OPTIONS = [
    { value: 'all', label: 'Усе' },
    { value: 'translation', label: 'Переклади' },
    { value: 'original', label: 'Твори' },
] as const;

/** Filters live in the address, so a search can be shared and survives «назад». */
export function CatalogPage() {
    const search: CatalogSearch = useSearch({ strict: false });
    const navigate = useNavigate();
    const [text, setText] = useState(search.q ?? '');
    const [showAllTags, setShowAllTags] = useState(false);
    const tags = search.tags ?? [];
    const kind = search.kind ?? 'all';
    const sort = search.sort ?? 'popular';

    const update = (patch: Partial<CatalogSearch>) =>
        void navigate({ to: '/catalog', search: (previous: CatalogSearch) => clean({ ...previous, ...patch }), replace: true });

    // Search as you type, but not on every key press.
    useEffect(() => {
        const timer = setTimeout(() => {
            if ((search.q ?? '') !== text.trim()) {
                update({ q: text.trim() });
            }
        }, 300);
        return () => clearTimeout(timer);
    });

    const allTags = useQuery({ queryKey: ['tags'], queryFn: readingApi.tags, staleTime: 5 * 60_000 });
    const results = useInfiniteQuery({
        queryKey: ['catalog', search.q ?? '', tags, kind, sort],
        queryFn: ({ pageParam }) => readingApi.catalog({ q: search.q ?? '', tags, kind, sort, page: pageParam }),
        initialPageParam: 1,
        getNextPageParam: (last) => (last.hasMore ? last.page + 1 : undefined),
    });

    const visibleTags = (allTags.data ?? []).slice(0, showAllTags ? 60 : 12);
    const items = results.data?.pages.flatMap((page) => page.items) ?? [];

    return (
        <section className={styles.page}>
            <h1 className="visually-hidden">Пошук</h1>
            <div className={styles.search}>
                <TextInput label="Назва або автор" type="search" value={text} onChange={setText} placeholder="Наприклад, маг води" />
            </div>
            <div className={styles.filters}>
                <Segmented label="Що шукаємо" value={kind} options={KIND_OPTIONS} onChange={(value) => update({ kind: value })} />
                {visibleTags.length > 0 && (
                    <div className={styles.chips} role="group" aria-label="Теги">
                        {visibleTags.map((tag) => {
                            const on = tags.includes(tag.slug);
                            return (
                                <button key={tag.slug} type="button" aria-pressed={on}
                                    className={`${styles.chip} ${on ? styles.chipOn : styles.chipGhost}`}
                                    onClick={() => update({ tags: on ? tags.filter((t) => t !== tag.slug) : [...tags, tag.slug] })}>
                                    {tag.name}
                                </button>
                            );
                        })}
                        {(allTags.data?.length ?? 0) > 12 && (
                            <button type="button" className={`${styles.chip} ${styles.chipGhost}`} onClick={() => setShowAllTags(!showAllTags)}>
                                {showAllTags ? 'менше' : 'усі теги'}
                            </button>
                        )}
                    </div>
                )}
                <label className={styles.small}>
                    Порядок{' '}
                    <select className={styles.select} value={sort} onChange={(event) => update({ sort: event.target.value })}>
                        <option value="popular">популярні</option>
                        <option value="updated">нові глави</option>
                        <option value="new">нові на сайті</option>
                        <option value="title">за назвою</option>
                    </select>
                </label>
            </div>

            {results.isError && <Notice tone="error">{results.error.message}</Notice>}
            {results.isPending && <p className={styles.empty}>Шукаємо…</p>}
            {results.isSuccess && items.length === 0 && (
                <p className={styles.empty}>Нічого не знайшли. Спробуйте іншу назву або приберіть теги.</p>
            )}
            {items.map((card) => <CardRow key={card.editionId} card={card} />)}
            {results.hasNextPage && (
                <div className={styles.more}>
                    <Button variant="secondary" onPress={() => void results.fetchNextPage()} pending={results.isFetchingNextPage} pendingLabel="Завантажуємо…">
                        Показати ще
                    </Button>
                </div>
            )}
        </section>
    );
}

function clean(search: CatalogSearch): CatalogSearch {
    const out: CatalogSearch = {};
    if (search.q) out.q = search.q;
    if (search.tags && search.tags.length > 0) out.tags = search.tags;
    if (search.kind && search.kind !== 'all') out.kind = search.kind;
    if (search.sort && search.sort !== 'popular') out.sort = search.sort;
    return out;
}

export function validateCatalogSearch(search: Record<string, unknown>): CatalogSearch {
    const tags = Array.isArray(search.tags) ? search.tags.filter((tag): tag is string => typeof tag === 'string') : [];
    return clean({
        ...(typeof search.q === 'string' ? { q: search.q } : {}),
        tags,
        ...(typeof search.kind === 'string' ? { kind: search.kind } : {}),
        ...(typeof search.sort === 'string' ? { sort: search.sort } : {}),
    });
}
