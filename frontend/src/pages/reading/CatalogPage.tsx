import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { SlidersHorizontal, X } from '../../ui/icons';
import { useEffect, useState } from 'react';
import { novelsWord, readingApi, SOURCE_STATUS_LABELS, STATUS_LABELS, type TagCount } from '../../reading/api';
import { SearchBox } from '../../reading/SearchBox';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
import { showError } from '../../ui/toast';
import { CardRow, teamSearch } from './HomePage';
import styles from './reading.module.css';

/**
 * @param status the translation's state; {@code source} the original's
 * @param from   at least this many chapters; {@code to} at most
 * @param rated  readers rate it 4 or more
 * @param fresh  new chapters within the «week» or «month»
 */
export type CatalogSearch = {
    q?: string; tags?: string[]; kind?: string; sort?: string;
    status?: string; source?: string; from?: number; to?: number; rated?: boolean; fresh?: string;
};

/** A change of the filters; undefined clears one. */
type Patch = { [K in keyof CatalogSearch]?: CatalogSearch[K] | undefined };

/** Filters beyond words, tags and kind: the ones a collection sets and the chips above the results show. */
const EXTRA = ['status', 'source', 'from', 'to', 'rated', 'fresh'] as const;

/** Ready-made sets of filters, one press away. */
const COLLECTIONS: { label: string; search: CatalogSearch }[] = [
    { label: '📚 Читати без очікування', search: { status: 'completed' } },
    { label: '🌙 На вечір', search: { to: 30 } },
    { label: '🏔 Довге читання', search: { from: 300 } },
    { label: '✨ Свіже цього тижня', search: { fresh: 'week', sort: 'updated' } },
    { label: '⭐ Найкраще оцінені', search: { rated: true, sort: 'rating' } },
];

const RANGES: { label: string; from?: number; to?: number }[] = [
    { label: 'до 50', to: 50 },
    { label: '50–200', from: 50, to: 200 },
    { label: '200–500', from: 200, to: 500 },
    { label: '500+', from: 500 },
];

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

    const update = (patch: Patch) =>
        void navigate({ to: '/catalog', search: (previous: CatalogSearch) => clean({ ...previous, ...patch } as CatalogSearch), replace: true });

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
        queryKey: ['catalog', search.q ?? '', tags, kind, sort, pick(search)],
        queryFn: ({ pageParam }) => readingApi.catalog({ q: search.q ?? '', tags, kind, ...pick(search), sort, page: pageParam }),
        initialPageParam: 1,
        getNextPageParam: (last) => (last.hasMore ? last.page + 1 : undefined),
    });

    const items = results.data?.pages.flatMap((page) => page.items) ?? [];
    const total = results.data?.pages[0]?.total;
    const extras = EXTRA.filter((key) => search[key] !== undefined);
    const narrowed = Boolean(search.q) || tags.length > 0 || kind !== 'all' || extras.length > 0;
    const chosenFilters = tags.length + (kind !== 'all' ? 1 : 0) + extras.length;
    const filters = { q: search.q ?? '', tags, kind, ...pick(search) };
    const collectionOn = (collection: CatalogSearch) =>
        Object.entries(collection).every(([key, value]) => search[key as keyof CatalogSearch] === value)
        && extras.every((key) => key in collection);
    const toggleCollection = (collection: CatalogSearch) => {
        const cleared: Patch = { sort: undefined };
        EXTRA.forEach((key) => { cleared[key] = undefined; });
        update(collectionOn(collection) ? cleared : { ...cleared, ...collection });
    };
    const [rolling, setRolling] = useState(false);
    const surprise = async () => {
        setRolling(true);
        try {
            const card = await readingApi.randomNovel(filters);
            void navigate({ to: '/n/$slug', params: { slug: card.novelSlug }, search: teamSearch(card) });
        } catch (error) {
            showError(error instanceof Error ? error.message : 'Не вдалося вибрати новелу.');
        } finally {
            setRolling(false);
        }
    };
    const range = (from?: number, to?: number) => update({ from, to });
    const labels: Record<(typeof EXTRA)[number], string | null> = {
        status: search.status ? `Переклад: ${STATUS_LABELS[search.status as keyof typeof STATUS_LABELS] ?? search.status}` : null,
        source: search.source ? `Оригінал: ${SOURCE_STATUS_LABELS[search.source as keyof typeof SOURCE_STATUS_LABELS] ?? search.source}` : null,
        from: search.from !== undefined ? `від ${search.from} глав` : null,
        to: search.to !== undefined ? `до ${search.to} глав` : null,
        rated: search.rated ? 'оцінка 4+' : null,
        fresh: search.fresh === 'week' ? 'оновлено за тиждень' : search.fresh === 'month' ? 'оновлено за місяць' : null,
    };

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
                <SlidersHorizontal size={16} aria-hidden /> Фільтри{chosenFilters > 0 ? ` · ${chosenFilters}` : ''}
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
                        <option value="rating">за оцінкою</option>
                    </select>
                </label>
                <label className={styles.small}>
                    Переклад{' '}
                    <select className={styles.select} value={search.status ?? ''} onChange={(event) => update({ status: event.target.value || undefined })}>
                        <option value="">будь-який</option>
                        {Object.entries(STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                    </select>
                </label>
                <label className={styles.small}>
                    Оригінал{' '}
                    <select className={styles.select} value={search.source ?? ''} onChange={(event) => update({ source: event.target.value || undefined })}>
                        <option value="">будь-який</option>
                        {Object.entries(SOURCE_STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                    </select>
                </label>
                <fieldset className={styles.filterGroup}>
                    <legend className={styles.filterName}>Кількість глав</legend>
                    <div className={styles.range}>
                        <input type="number" min={0} inputMode="numeric" aria-label="Глав від" placeholder="від" value={search.from ?? ''}
                            onChange={(event) => update({ from: count(event.target.value) })} />
                        <span className={styles.small}>–</span>
                        <input type="number" min={0} inputMode="numeric" aria-label="Глав до" placeholder="до" value={search.to ?? ''}
                            onChange={(event) => update({ to: count(event.target.value) })} />
                    </div>
                    <div className={styles.chips} style={{ marginTop: 8 }}>
                        {RANGES.map((option) => {
                            const on = search.from === option.from && search.to === option.to;
                            return (
                                <button key={option.label} type="button" className={`${styles.chip} ${on ? styles.chipOn : ''}`} aria-pressed={on}
                                    onClick={() => (on ? range(undefined, undefined) : range(option.from, option.to))}>{option.label}</button>
                            );
                        })}
                    </div>
                </fieldset>
                <label className={styles.check}>
                    <input type="checkbox" checked={Boolean(search.rated)} onChange={() => update({ rated: search.rated ? undefined : true })} />
                    <span className={styles.grow}>Оцінка читачів 4+ ★</span>
                </label>
                <label className={styles.small}>
                    Оновлено{' '}
                    <select className={styles.select} value={search.fresh ?? ''} onChange={(event) => update({ fresh: event.target.value || undefined })}>
                        <option value="">будь-коли</option>
                        <option value="week">за тиждень</option>
                        <option value="month">за місяць</option>
                    </select>
                </label>
                {groups.data?.map((group) => (
                    <FilterGroup key={group.name} name={group.name} tags={group.tags} chosen={tags} onToggle={toggleTag} />
                ))}
            </div>

            <div className={styles.results}>
            <div className={styles.collections} role="group" aria-label="Добірки">
                <button type="button" className={styles.collection} onClick={() => void surprise()} disabled={rolling}>
                    🎲 Випадкова новела
                </button>
                {COLLECTIONS.map((collection) => {
                    const on = collectionOn(collection.search);
                    return (
                        <button key={collection.label} type="button" aria-pressed={on} className={`${styles.collection} ${on ? styles.collectionOn : ''}`}
                            onClick={() => toggleCollection(collection.search)}>{collection.label}</button>
                    );
                })}
            </div>
            {(tags.length > 0 || extras.length > 0) && (
                <div className={styles.chips} aria-label="Обрані фільтри" role="group" style={{ marginBottom: 8 }}>
                    {extras.map((key) => labels[key] && (
                        <button key={key} type="button" className={`${styles.chip} ${styles.chipOn}`} onClick={() => update({ [key]: undefined })}
                            aria-label={`Прибрати: ${labels[key]}`}>
                            {labels[key]} <X size={12} aria-hidden />
                        </button>
                    ))}
                    {tags.map((slug) => (
                        <button key={slug} type="button" className={`${styles.chip} ${styles.chipOn}`} onClick={() => toggleTag(slug)}
                            aria-label={`Прибрати тег ${nameOf(slug)}`}>
                            {nameOf(slug)} <X size={12} aria-hidden />
                        </button>
                    ))}
                    {tags.length + extras.length > 1 && (
                        <button type="button" className={`${styles.chip} ${styles.chipGhost}`}
                            onClick={() => update({ tags: [], status: undefined, source: undefined, from: undefined, to: undefined, rated: undefined, fresh: undefined })}>
                            Скинути всі
                        </button>
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

/** A chapter count typed in a field: empty clears it. */
function count(text: string): number | undefined {
    const value = Number.parseInt(text, 10);
    return Number.isFinite(value) && value >= 0 ? value : undefined;
}

/** The filters beyond words, tags and kind, without the empty ones (they are part of the query key). */
function pick(search: CatalogSearch) {
    const out: Pick<CatalogSearch, (typeof EXTRA)[number]> = {};
    if (search.status) out.status = search.status;
    if (search.source) out.source = search.source;
    if (search.from !== undefined) out.from = search.from;
    if (search.to !== undefined) out.to = search.to;
    if (search.rated) out.rated = true;
    if (search.fresh) out.fresh = search.fresh;
    return out;
}

function clean(search: CatalogSearch): CatalogSearch {
    const out: CatalogSearch = {};
    if (search.q) out.q = search.q;
    if (search.tags && search.tags.length > 0) out.tags = search.tags;
    if (search.kind && search.kind !== 'all') out.kind = search.kind;
    if (search.sort && search.sort !== 'popular') out.sort = search.sort;
    return { ...out, ...pick(search) };
}

export function validateCatalogSearch(search: Record<string, unknown>): CatalogSearch {
    const tags = Array.isArray(search.tags) ? search.tags.filter((tag): tag is string => typeof tag === 'string') : [];
    const text = (key: string) => (typeof search[key] === 'string' ? { [key]: search[key] as string } : {});
    const number = (key: string) => {
        const value = typeof search[key] === 'number' ? search[key] : typeof search[key] === 'string' ? Number(search[key]) : NaN;
        return Number.isInteger(value) && (value as number) >= 0 ? { [key]: value as number } : {};
    };
    return clean({
        ...text('q'), tags, ...text('kind'), ...text('sort'), ...text('status'), ...text('source'), ...text('fresh'),
        ...number('from'), ...number('to'),
        ...(search.rated === true || search.rated === 'true' || search.rated === 1 ? { rated: true } : {}),
    });
}
