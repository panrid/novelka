import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams, useSearch } from '@tanstack/react-router';
import { ArrowDownUp, BookmarkPlus, Check } from 'lucide-react';
import { useLayoutEffect, useRef, useState } from 'react';
import { Button as AriaButton, Menu, MenuItem, MenuTrigger, Popover } from 'react-aria-components';
import { ApiError } from '../../api/client';
import { useMe } from '../../auth/me';
import { Blocks } from '../../reading/Blocks';
import { Cover } from '../../reading/Cover';
import { LIST_LABELS, STATUS_LABELS, chaptersWord, readingApi, type ListName, type NovelPage as Novel } from '../../reading/api';
import { relayApi } from '../../studio/api';
import { localProgress } from '../../reading/progress';
import { novelQuery } from '../../reading/queries';
import { Button } from '../../ui/Button';
import { LinkButton } from '../../ui/LinkButton';
import { Notice } from '../../ui/Notice';
import styles from './novel.module.css';

export function NovelPage() {
    const { slug } = useParams({ strict: false }) as { slug: string };
    const { t }: { t?: string } = useSearch({ strict: false });
    const novel = useQuery(novelQuery(slug, t));

    if (novel.isPending) {
        return <p className={styles.muted} style={{ paddingTop: 24 }}>Завантажуємо…</p>;
    }
    if (novel.isError) {
        const adult = novel.error instanceof ApiError && novel.error.reason === 'adult';
        return (
            <section className={styles.page}>
                <Notice tone={adult ? 'info' : 'error'}>{novel.error.message}</Notice>
                {adult && (
                    <div style={{ marginTop: 12 }}>
                        <LinkButton to="/me/settings/privacy" variant="secondary">До налаштувань приватності</LinkButton>
                    </div>
                )}
            </section>
        );
    }
    return <NovelView novel={novel.data} team={t} />;
}

function NovelView({ novel, team }: { novel: Novel; team: string | undefined }) {
    const [expanded, setExpanded] = useState(false);
    const [overflows, setOverflows] = useState(false);
    const description = useRef<HTMLDivElement>(null);
    // «читати повністю» only when three lines really hide something.
    useLayoutEffect(() => {
        const element = description.current;
        if (element && !expanded) {
            setOverflows(element.scrollHeight > element.clientHeight + 2);
        }
    }, [expanded, novel.description]);
    const edition = novel.edition;
    const local = localProgress(novel.slug, edition.teamHandle);
    const resume = novel.viewer?.chapterNumber ?? local?.number ?? null;
    const machine = edition.kind === 'machine' || edition.kind === 'mixed';

    return (
        <section className={styles.page}>
            <div className={styles.head}>
                <Cover url={edition.coverUrl} title={novel.title} seed={novel.slug} width={112} />
                <div className={styles.headText}>
                    <h1 className={styles.title}>{novel.title}</h1>
                    <p className={styles.muted}>{novel.origin === 'original' ? 'оригінальний твір' : 'переклад з японської'}</p>
                    <div className={styles.chips}>
                        {novel.author && <span className={styles.chip}>✎ {novel.author}</span>}
                        <span className={`${styles.chip} ${styles.team}`} title={edition.teamName}>${edition.teamHandle}</span>
                        {machine && <span className={`${styles.chip} ${styles.ghost}`} title="Машинний переклад">ШІ</span>}
                        {novel.adult && <span className={`${styles.chip} ${styles.ghost}`}>18+</span>}
                    </div>
                    <p className={styles.muted}>
                        {edition.chapterCount} {chaptersWord(edition.chapterCount)} · {STATUS_LABELS[edition.status]}
                    </p>
                </div>
            </div>

            <div className={styles.actions}>
                <LinkButton to="/n/$slug/$number" params={{ slug: novel.slug, number: String(resume ?? 1) }}
                    search={team ? { t: team } : {}} wide>
                    {resume ? `Продовжити · гл. ${resume}` : 'Почати читати'}
                </LinkButton>
                <LibraryButton novel={novel} />
            </div>

            {novel.editions.length > 1 && (
                <div className={styles.editions} role="group" aria-label="Переклади">
                    {novel.editions.map((other) => (
                        <Link key={other.editionId} to="/n/$slug" params={{ slug: novel.slug }} search={{ t: other.teamHandle }}
                            className={`${styles.chip} ${other.editionId === edition.editionId ? styles.on : styles.ghost}`}
                            aria-current={other.editionId === edition.editionId ? 'true' : undefined}>
                            {other.teamName} · {other.chapterCount}
                        </Link>
                    ))}
                </div>
            )}

            {novel.tags.length > 0 && (
                <div className={styles.chips} style={{ margin: '14px 0 10px' }}>
                    {novel.tags.map((tag) => (
                        <Link key={tag} to="/catalog" search={{ tags: [tag.toLowerCase()] }} className={`${styles.chip} ${styles.ghost}`}>
                            {tag}
                        </Link>
                    ))}
                </div>
            )}

            {novel.description.length > 0 && (
                <div className={styles.description}>
                    <div ref={description} className={expanded ? undefined : styles.clamped}>
                        <Blocks blocks={novel.description} />
                    </div>
                    {(overflows || expanded) && (
                        <button type="button" className={styles.more} onClick={() => setExpanded(!expanded)} aria-expanded={expanded}>
                            {expanded ? 'згорнути' : 'читати опис повністю'}
                        </button>
                    )}
                </div>
            )}

            {novel.viewer?.teamRole && (
                <div className={styles.actions}>
                    <LinkButton to="/studio/$editionId" params={{ editionId: String(edition.editionId) }} variant="secondary" wide>
                        Керувати
                    </LinkButton>
                </div>
            )}

            <ChapterList slug={novel.slug} team={team} current={resume} />

            {novel.relay.continuations.map((next) => (
                <Link key={next.teamHandle} to="/n/$slug/$number" params={{ slug: novel.slug, number: String(next.firstNumber) }}
                    search={{ t: next.teamHandle }} className={styles.continuation}>
                    Продовження від ${next.teamHandle} — з глави {next.firstNumber} →
                </Link>
            ))}
            {novel.origin === 'translation' && !novel.viewer?.teamRole && <RelayOffer novel={novel} />}
        </section>
    );
}

const RELAY_REASONS = {
    abandoned: 'Команда позначила переклад покинутим.',
    inactive: 'Власник перекладу давно не заходив на сайт.',
    unanswered: 'Нових глав давно немає, а на запит продовжити ніхто не відповів.',
};

/** «Естафета» for readers: continue a free translation, or ask the team for permission. */
function RelayOffer({ novel }: { novel: Novel }) {
    const me = useMe();
    const navigate = useNavigate();
    const [asked, setAsked] = useState(false);
    const start = useMutation({
        mutationFn: () => relayApi.start(novel.edition.editionId, '', 'human'),
        onSuccess: ({ editionId }) => void navigate({ to: '/studio/$editionId', params: { editionId: String(editionId) } }),
    });
    const ask = useMutation({
        mutationFn: (message: string) => relayApi.ask(novel.edition.editionId, '', message),
        onSuccess: () => setAsked(true),
    });
    if (!me) return null;
    if (novel.relay.free) {
        return (
            <div className={styles.relay}>
                <b>Переклад вільний для продовження</b>
                <p className={styles.muted}>{novel.relay.reason ? RELAY_REASONS[novel.relay.reason] : ''} Ваш переклад почнеться з глави {novel.relay.lastNumber + 1}.</p>
                {start.isError && <Notice tone="error">{start.error.message}</Notice>}
                <Button variant="secondary" onPress={() => start.mutate()} pending={start.isPending}>Продовжити переклад</Button>
            </div>
        );
    }
    if (novel.edition.status === 'completed') return null;
    return (
        <div className={styles.relay}>
            {asked ? <p className={styles.muted}>Запит надіслано. Власник отримав лист.</p> : (
                <>
                    {ask.isError && <Notice tone="error">{ask.error.message}</Notice>}
                    <button type="button" className={styles.more} onClick={() => {
                        const message = window.prompt('Кілька слів власнику перекладу (необовʼязково):', '');
                        if (message !== null) ask.mutate(message);
                    }}>Хочу продовжити цей переклад</button>
                </>
            )}
        </div>
    );
}

function LibraryButton({ novel }: { novel: Novel }) {
    const me = useMe();
    const navigate = useNavigate();
    const client = useQueryClient();
    const current = novel.viewer?.list ?? null;
    const set = useMutation({
        mutationFn: (list: ListName | null) => readingApi.setList(novel.edition.editionId, list),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['novel', novel.slug] }),
    });

    if (!me) {
        return (
            <Button variant="secondary" aria-label="Додати в бібліотеку"
                onPress={() => void navigate({ to: '/login', search: { next: window.location.pathname + window.location.search } })}>
                <BookmarkPlus size={20} aria-hidden />
            </Button>
        );
    }
    return (
        <MenuTrigger>
            <AriaButton className={styles.libraryButton} aria-label={current ? `У бібліотеці: ${LIST_LABELS[current]}` : 'Додати в бібліотеку'}>
                {current ? <><Check size={18} aria-hidden /> {LIST_LABELS[current]}</> : <BookmarkPlus size={20} aria-hidden />}
            </AriaButton>
            <Popover className={styles.popover} placement="bottom end">
                <Menu className={styles.menu} onAction={(key) => set.mutate(key === 'remove' ? null : (key as ListName))}>
                    {(Object.keys(LIST_LABELS) as ListName[]).map((list) => (
                        <MenuItem key={list} id={list} className={styles.menuItem}>
                            {LIST_LABELS[list]} {current === list && <Check size={16} aria-hidden />}
                        </MenuItem>
                    ))}
                    {current && <MenuItem id="remove" className={`${styles.menuItem} ${styles.remove}`}>Прибрати з бібліотеки</MenuItem>}
                </Menu>
            </Popover>
        </MenuTrigger>
    );
}

function ChapterList({ slug, team, current }: { slug: string; team: string | undefined; current: number | null }) {
    const [order, setOrder] = useState<'asc' | 'desc'>('asc');
    const chapters = useInfiniteQuery({
        queryKey: ['chapters', slug, team ?? '', order],
        queryFn: ({ pageParam }) => readingApi.chapters(slug, team, order, pageParam),
        initialPageParam: 1,
        getNextPageParam: (last) => (last.hasMore ? last.page + 1 : undefined),
    });
    const rows = chapters.data?.pages.flatMap((page) => page.items) ?? [];

    return (
        <div className={styles.chapters} id="chapters">
            <div className={styles.chaptersHead}>
                <h2 className={styles.sectionTitle}>Глави</h2>
                <button type="button" className={styles.order} onClick={() => setOrder(order === 'asc' ? 'desc' : 'asc')}>
                    <ArrowDownUp size={14} aria-hidden /> {order === 'asc' ? 'від першої' : 'від останньої'}
                </button>
            </div>
            {chapters.isError && <Notice tone="error">{chapters.error.message}</Notice>}
            <ol className={styles.list}>
                {rows.map((row) => (
                    <li key={row.number}>
                        <Link to="/n/$slug/$number" params={{ slug, number: String(row.number) }} search={team ? { t: team } : {}}
                            className={`${styles.chapter} ${row.number === current ? styles.here : ''}`}>
                            <span>{row.number}. {row.title}</span>
                            {row.number === current && <span className={styles.muted}>тут зупинились</span>}
                        </Link>
                    </li>
                ))}
            </ol>
            {chapters.hasNextPage && (
                <Button variant="secondary" wide onPress={() => void chapters.fetchNextPage()} pending={chapters.isFetchingNextPage} pendingLabel="Завантажуємо…">
                    Показати ще
                </Button>
            )}
        </div>
    );
}
