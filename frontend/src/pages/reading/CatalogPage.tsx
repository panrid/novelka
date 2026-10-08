import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { SlidersHorizontal, X } from '../../ui/icons';
import { useEffect, useState } from 'react';
import { novelsWord, readingApi, type TagCount } from '../../reading/api';
import { SearchBox } from '../../reading/SearchBox';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
import { CardRow } from './HomePage';
import styles from './reading.module.css';

export type CatalogSearch = { q?: string; tags?: string[]; kind?: string; sort?: string };

const KIND_OPTIONS = [
    { value: 'all', label: 'Усе' },
    { value: 'translation', label: 'Переклади' },
    { value: 'original', label: 'Твори' },
] as const;

/**
 * The catalog: every novel of the site in one place, narrowed by words, tags and kind. Filters
 * live in the address, so a search can be shared and survives «назад».
 */
export function CatalogPage() {
    const search: CatalogSearch = useSearch({ strict: false });
    const navigate = useNavigate();
    const [text, setText] = useState(search.q ?? '');
    // Words arriving from outside (the header's search box) replace what the field holds.
    const [seen, setSeen] = useState(search.q ?? '');
    if ((search.q ?? '') !== seen) {
        setSeen(search.q ?? '');
        if ((search.q ?? '') !== text.trim()) setText(search.q ?? '');
    }
    // On a phone the filters fold away behind a button; on a wide screen they are always beside the results.
    const [filtersOpen, setFiltersOpen] = useState(false);
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

    const groups = useQuery({ queryKey: ['tag-groups'], queryFn: readingApi.tagGroups, staleTime: 5 * 60_000 });
    const nameOf = (slug: string) => groups.data?.flatMap((group) => group.tags).find((tag) => tag.slug === slug)?.name ?? slug;
    const toggleTag = (slug: string) => update({ tags: tags.includes(slug) ? tags.filter((t) => t !== slug) : [...tags, slug] });
    const results = useInfiniteQuery({
        queryKey: ['catalog', search.q ?? '', tags, kind, sort],
        queryFn: ({ pageParam }) => readingApi.catalog({ q: search.q ?? '', tags, kind, sort, page: pageParam }),
        initialPageParam: 1,
        getNextPageParam: (last) => (last.hasMore ? last.page + 1 : undefined),
    });

    const items = results.data?.pages.flatMap((page) => page.items) ?? [];
    const total = results.data?.pages[0]?.total;
    const narrowed = Boolean(search.q) || tags.length > 0 || kind !== 'all';

    return (
        <section className={`${styles.page} ${styles.catalog}`}>
            <div className={styles.search}>
                <h1 className={styles.catalogTitle}>
                    Каталог
                    {total !== undefined && (
                        <span className={styles.small}>
                            {' '}{narrowed ? `знайдено ${total} ${novelsWord(total)}` : `${total} ${novelsWord(total)}`}
                        </span>
                    )}
                </h1>
                <SearchBox label="Пошук новел" placeholder="Назва, автор або тег" value={text} onChange={setText}
                    onSubmit={(words) => update({ q: words })} />
            </div>
            <button type="button" className={styles.filtersToggle} aria-expanded={filtersOpen} onClick={() => setFiltersOpen(!filtersOpen)}>
                <SlidersHorizontal size={16} aria-hidden /> Фільтри{tags.length + (kind !== 'all' ? 1 : 0) > 0 ? ` · ${tags.length + (kind !== 'all' ? 1 : 0)}` : ''}
            </button>
            <div className={`${styles.filters} ${filtersOpen ? styles.filtersOpen : ''}`}>
                <Segmented label="Що шукаємо" value={kind} options={KIND_OPTIONS} onChange={(value) => update({ kind: value })} />
                <label className={styles.small}>
                    Порядок{' '}
                    <select className={styles.select} value={sort} onChange={(event) => update({ sort: event.target.value })}>
                        <option value="popular">популярні</option>
                        <option value="updated">нові глави</option>
                        <option value="new">нові на сайті</option>
                        <option value="title">за назвою</option>
                    </select>
                </label>
                {groups.data?.map((group) => (
                    <FilterGroup key={group.name} name={group.name} tags={group.tags} chosen={tags} onToggle={toggleTag} />
                ))}
            </div>

            <div className={styles.results}>
            {tags.length > 0 && (
                <div className={styles.chips} aria-label="Обрані теги" role="group">
                    {tags.map((slug) => (
                        <button key={slug} type="button" className={`${styles.chip} ${styles.chipOn}`} onClick={() => toggleTag(slug)}
                            aria-label={`Прибрати тег ${nameOf(slug)}`}>
                            {nameOf(slug)} <X size={12} aria-hidden />
                        </button>
                    ))}
                    {tags.length > 1 && (
                        <button type="button" className={`${styles.chip} ${styles.chipGhost}`} onClick={() => update({ tags: [] })}>Скинути всі</button>
                    )}
                </div>
            )}
            {results.isError && <Notice tone="error">{results.error.message}</Notice>}
            {results.isPending && <p className={styles.empty}>Шукаємо…</p>}
            {results.isSuccess && items.length === 0 && (
                <p className={styles.empty}>
                    Нічого не знайшли. Спробуйте іншу назву або приберіть теги — чи <Link to="/proposals">запропонуйте новелу перекласти</Link>.
                    {narrowed && (
                        <>
                            {' '}<Link to="/catalog">Показати всі новели</Link>.
                        </>
                    )}
                </p>
            )}
            <div className={styles.cards}>{items.map((card) => <CardRow key={card.editionId} card={card} />)}</div>
            {results.hasNextPage && (
                <div className={styles.more}>
                    <Button variant="secondary" onPress={() => void results.fetchNextPage()} pending={results.isFetchingNextPage} pendingLabel="Завантажуємо…">
                        Показати ще
                    </Button>
                </div>
            )}
            </div>
        </section>
    );
}

const FOLDED = 6;

/**
 * One group of tags as a shop's filter: a list of checkboxes with how many novels each would
 * show. Tags no novel has yet stay out unless chosen; a long group folds after six.
 */
function FilterGroup({ name, tags, chosen, onToggle }: { name: string; tags: TagCount[]; chosen: string[]; onToggle: (slug: string) => void }) {
    const [open, setOpen] = useState(false);
    const present = tags.filter((tag) => tag.novels > 0 || chosen.includes(tag.slug));
    if (present.length === 0) return null;
    const shown = open ? present : present.filter((tag, index) => index < FOLDED || chosen.includes(tag.slug));
    return (
        <fieldset className={styles.filterGroup}>
            <legend className={styles.filterName}>{name}</legend>
            {shown.map((tag) => (
                <label key={tag.slug} className={styles.check}>
                    <input type="checkbox" checked={chosen.includes(tag.slug)} onChange={() => onToggle(tag.slug)} />
                    <span className={styles.grow}>{tag.name}</span>
                    <span className={styles.chipCount}>{tag.novels}</span>
                </label>
            ))}
            {present.length > shown.length || open ? (
                present.length > FOLDED && (
                    <button type="button" className={styles.moreLink} onClick={() => setOpen(!open)}>
                        {open ? 'згорнути' : `ще ${present.length - shown.length}`}
                    </button>
                )
            ) : null}
        </fieldset>
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
