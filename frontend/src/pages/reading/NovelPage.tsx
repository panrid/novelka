import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, Navigate, useNavigate, useParams, useRouterState, useSearch } from '@tanstack/react-router';
import { ArrowDownUp, Bell, BellRing, BookmarkPlus, Check, MessageCircle } from '../../ui/icons';
import { Fragment, useLayoutEffect, useRef, useState } from 'react';
import { Button as AriaButton, Menu, MenuItem, MenuTrigger, Popover, ToggleButton } from 'react-aria-components';
import { ApiError } from '../../api/client';
import { useMe } from '../../auth/me';
import { Discussion, useCommentCount } from '../../community/Discussion';
import discussionStyles from '../../community/discussion.module.css';
import { commentApi } from '../../community/api';
import { adminApi } from '../../admin/api';
import { Sheet } from '../../ui/Sheet';
import { Blocks } from '../../reading/Blocks';
import { Cover } from '../../reading/Cover';
import { LIST_LABELS, SOURCE_STATUS_LABELS, chapterHeading, chaptersWord, otherNames, readingApi, translationStatus, volumeTitle, type ChapterRow as ChapterRowType, type ListName, type NovelPage as Novel } from '../../reading/api';
import { relayApi, teamApi } from '../../studio/api';
import { Segmented } from '../../ui/Segmented';
import { localProgress } from '../../reading/progress';
import { novelQuery } from '../../reading/queries';
import { Button } from '../../ui/Button';
import { Pager } from '../../ui/Pager';
import { LinkButton } from '../../ui/LinkButton';
import { Notice } from '../../ui/Notice';
import { showInfo } from '../../ui/toast';
import { ReportEdition } from '../../reading/ReportEdition';
import styles from './novel.module.css';
import { askConfirm, askText } from '../../ui/ask';

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
    if (novel.data.slug !== slug) {
        // An address the novel had before: show it under the one it has now.
        return <Navigate to="/n/$slug" params={{ slug: novel.data.slug }} search={t ? { t } : {}} hash={true} replace />;
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
    // The place kept in this browser is a guest's: a signed-in reader (viewer is theirs) has their own on the
    // server, and a new account must not take up where someone else stopped in the same browser.
    const local = novel.viewer ? null : localProgress(novel.slug, edition.teamHandle);
    const resume = novel.viewer?.chapterNumber ?? local?.number ?? null;
    // The chapter's own number (0, 31.1); a place remembered only in this browser has just its position.
    const shown = novel.viewer?.chapterNumber
        ? (novel.viewer.chapterLabel ?? String(novel.viewer.chapterNumber))
        : local ? (local.label ?? String(local.number)) : null;
    const resumeLabel = shown ? `Продовжити · гл. ${shown}` : 'Продовжити';
    const machine = edition.kind === 'machine' || edition.kind === 'mixed';

    return (
        <section className={`${styles.page} ${styles.novel}`}>
            <div className={styles.side}>
                <div className={styles.coverBox}>
                    <Cover url={edition.coverUrl} title={novel.title} seed={novel.slug} width={112} fluid />
                </div>
                <div className={styles.actions}>
                    <LinkButton to="/n/$slug/$number" params={{ slug: novel.slug, number: String(resume ?? 1) }}
                        search={team ? { t: team } : {}} wide>
                        {resume ? resumeLabel : 'Почати читати'}
                    </LinkButton>
                    <LibraryButton novel={novel} />
                    <BellButton novel={novel} />
                </div>

                {novel.editions.length > 1 && (
                    <div className={styles.editions} role="group" aria-label="Переклади">
                        <span className={styles.muted}>Переклади:</span>
                        {novel.editions.map((other) => (
                            <Link key={other.editionId} to="/n/$slug" params={{ slug: novel.slug }} search={{ t: other.teamHandle }}
                                className={`${styles.chip} ${other.editionId === edition.editionId ? styles.on : styles.ghost}`}
                                aria-current={other.editionId === edition.editionId ? 'true' : undefined}>
                                {other.teamName} · {other.chapterCount}
                            </Link>
                        ))}
                    </div>
                )}

                {novel.viewer?.teamRole && (
                    <div className={`${styles.actions} ${styles.manage}`}>
                        <LinkButton to="/studio/$editionId" params={{ editionId: String(edition.editionId) }} variant="secondary" wide>
                            Керувати
                        </LinkButton>
                    </div>
                )}
            </div>
            <div className={styles.content}>
                <div className={styles.headText}>
                    <h1 className={styles.title}>{novel.title}</h1>
                    {otherNames(novel.title, novel.facts).length > 0 && (
                        <p className={styles.names}>{otherNames(novel.title, novel.facts).join(' · ')}</p>
                    )}
                    <p className={styles.muted}>
                        {novel.origin === 'original' ? 'оригінальний твір' : translatedFrom(novel.language)}
                        {novel.originalUrl && <> · <a href={novel.originalUrl} target="_blank" rel="noopener noreferrer nofollow">оригінал ↗</a></>}
                    </p>
                    <div className={styles.chips}>
                        {novel.author && <span className={styles.chip}>✎ {novel.author}</span>}
                        <span className={`${styles.chip} ${styles.team}`} title={edition.teamName}>${edition.teamHandle}</span>
                        {machine && <span className={`${styles.chip} ${styles.ghost}`} title="Машинний переклад">ШІ</span>}
                        {novel.adult && <span className={`${styles.chip} ${styles.ghost}`}>18+</span>}
                    </div>
                    <p className={styles.muted}>
                        {edition.chapterCount} {chaptersWord(edition.chapterCount)} · {translationStatus(edition.status, edition.pausedUntil)}
                    </p>
                    {novel.origin !== 'original' && <OriginalProgress novel={novel} />}
                    <Stars novel={novel} />
                </div>
                {novel.tags.length > 0 && (
                    <div className={`${styles.chips} ${styles.tags}`} style={{ marginTop: 14 }}>
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

                {novel.viewer && <Skipped novel={novel} />}
                <ChapterList slug={novel.slug} team={team} current={resume} total={edition.chapterCount}
                    editionId={novel.viewer ? edition.editionId : null} />

                {novel.relay.continuations.map((next) => (
                    <Link key={next.teamHandle} to="/n/$slug/$number" params={{ slug: novel.slug, number: String(next.firstNumber) }}
                        search={{ t: next.teamHandle }} className={styles.continuation}>
                        Продовження від ${next.teamHandle} — з глави {next.firstNumber} →
                    </Link>
                ))}
                {novel.origin === 'translation' && !novel.viewer?.teamRole && <RelayOffer novel={novel} />}
            {novel.origin === 'translation' && <OwnTranslation novel={novel} />}
                {!novel.viewer?.teamRole && <ReportEdition editionId={edition.editionId} />}
                <HideEdition editionId={edition.editionId} />
                <DiscussionButton editionId={edition.editionId} />
            </div>
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
            {asked || novel.viewer?.relayAsked ? <p className={styles.muted}>Запит надіслано, чекаємо відповіді власника.</p> : (
                <>
                    {ask.isError && <Notice tone="error">{ask.error.message}</Notice>}
                    <button type="button" className={styles.more} onClick={() => void askText({
                        title: 'Продовжити переклад', label: 'Кілька слів власнику', hint: 'Необовʼязково.', optional: true, multiline: true,
                        confirmLabel: 'Надіслати запит',
                    }).then((message) => { if (message !== null) ask.mutate(message); })}>Хочу продовжити цей переклад</button>
                </>
            )}
        </div>
    );
}

/**
 * «Перекласти самому» (етап 17): anyone with an account starts their own translation of this
 * novel — from the first chapter or going on after this one — without asking anybody.
 */
function OwnTranslation({ novel }: { novel: Novel }) {
    const me = useMe();
    const navigate = useNavigate();
    const [open, setOpen] = useState(false);
    const [from, setFrom] = useState<'start' | 'after'>('after');
    const teams = useQuery({ queryKey: ['my-teams'], queryFn: teamApi.mine, enabled: open });
    const mine = (teams.data ?? []).filter((team) => team.role !== 'editor');
    const [team, setTeam] = useState('');
    const chosen = team || mine[0]?.handle || '';
    const start = useMutation({
        mutationFn: () => relayApi.own(novel.slug, chosen, from === 'after' ? novel.edition.editionId : null),
        onSuccess: ({ editionId }) => void navigate({ to: '/studio/$editionId', params: { editionId: String(editionId) } }),
    });
    if (!me) return null;
    return (
        <>
            <button type="button" className={styles.more} style={{ marginTop: 12 }} onClick={() => setOpen(true)}>Перекласти самому</button>
            {open && (
                <Sheet open onClose={() => setOpen(false)} title="Свій переклад">
                    <div style={{ display: 'grid', gap: 14 }}>
                        <p className={styles.muted}>
                            Ваш переклад стане поруч із наявними, читачі оберуть, чий читати. Дозвіл не потрібен.
                        </p>
                        <Segmented label="З якої глави" value={from} onChange={setFrom} options={[
                            { value: 'after', label: `Після ${'$'}${novel.edition.teamHandle} — з глави ${novel.relay.lastNumber + 1}` },
                            { value: 'start', label: 'З першої глави' },
                        ]} />
                        {mine.length > 1 && (
                            <label style={{ display: 'grid', gap: 6 }}>
                                <span className={styles.muted}>Команда</span>
                                <select className={styles.select} value={chosen} onChange={(event) => setTeam(event.target.value)}>
                                    {mine.map((option) => <option key={option.handle} value={option.handle}>{option.name}</option>)}
                                </select>
                            </label>
                        )}
                        {start.isError && <Notice tone="error">{start.error.message}</Notice>}
                        <Button onPress={() => start.mutate()} pending={start.isPending} pendingLabel="Створюємо…" isDisabled={teams.isPending}>
                            Почати переклад
                        </Button>
                    </div>
                </Sheet>
            )}
        </>
    );
}

function LibraryButton({ novel }: { novel: Novel }) {
    const me = useMe();
    const navigate = useNavigate();
    const client = useQueryClient();
    const current = novel.viewer?.list ?? null;
    const set = useMutation({ meta: { errorToast: true },
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

/** Subscribe to the translation's new chapters: only those who rang it hear about them. */
function BellButton({ novel }: { novel: Novel }) {
    const me = useMe();
    const navigate = useNavigate();
    const client = useQueryClient();
    const on = novel.viewer?.subscribed ?? false;
    const ring = useMutation({ meta: { errorToast: true },
        mutationFn: (next: boolean) => readingApi.subscribe(novel.edition.editionId, next),
        onSuccess: (_, next) => {
            showInfo(next ? 'Сповіщатимемо про нові глави.' : 'Більше не сповіщатимемо про нові глави.');
            void client.invalidateQueries({ queryKey: ['novel', novel.slug] });
        },
    });
    const label = on ? 'Ви отримуєте сповіщення про нові глави. Відписатися' : 'Підписатися на нові глави';
    const icon = on ? <BellRing size={20} aria-hidden fill="currentColor" /> : <Bell size={20} aria-hidden />;
    if (!me) {
        return (
            <AriaButton className={styles.libraryButton} aria-label={label}
                onPress={() => void navigate({ to: '/login', search: { next: window.location.pathname + window.location.search } })}>
                {icon}
            </AriaButton>
        );
    }
    return (
        <ToggleButton className={on ? `${styles.libraryButton} ${styles.bellOn}` : styles.libraryButton} aria-label={label}
            isSelected={on} isDisabled={ring.isPending} onChange={(next) => ring.mutate(next)}>
            {icon}
        </ToggleButton>
    );
}

const CHAPTERS_PER_PAGE = 20;

/** The chapters 20 to a page (етап 17), opened at the page with the chapter the reader stopped at. */
/** Marks change the novel page (skipped chapters, the place) and every page of the list. */
function useReadMarks(slug: string, editionId: number | null) {
    const client = useQueryClient();
    const refresh = () => {
        for (const key of [['novel', slug], ['chapters', slug], ['home'], ['library']]) void client.invalidateQueries({ queryKey: key });
    };
    const mark = useMutation({ meta: { errorToast: true },
        mutationFn: ({ from, to, read }: { from: number; to: number; read: boolean }) => readingApi.markRead(editionId!, from, to, read),
        onSuccess: refresh,
    });
    const reset = useMutation({ meta: { errorToast: true }, mutationFn: () => readingApi.resetProgress(editionId!), onSuccess: refresh });
    return { mark, reset };
}

/** «Пропущено 3 глави»: chapters before the place the reader never finished. */
function Skipped({ novel }: { novel: Novel }) {
    const viewer = novel.viewer!;
    const { mark } = useReadMarks(novel.slug, novel.edition.editionId);
    const team = novel.editions.length > 1 ? novel.edition.teamHandle : undefined;
    if (!viewer.skipped || !viewer.firstUnread || !viewer.chapterNumber) return null;
    return (
        <div className={styles.skipped} role="status">
            <div>
                Пропущено {viewer.skipped} {chaptersWord(viewer.skipped)} перед главою {viewer.chapterLabel ?? viewer.chapterNumber}
                {' '}— перша з них {viewer.firstUnreadLabel ?? viewer.firstUnread}.
            </div>
            <div className={styles.skippedActions}>
                <LinkButton to="/n/$slug/$number" params={{ slug: novel.slug, number: String(viewer.firstUnread) }}
                    search={team ? { t: team } : {}} variant="secondary">Читати з першої непрочитаної</LinkButton>
                <Button variant="quiet" pending={mark.isPending}
                    onPress={() => mark.mutate({ from: viewer.firstUnread!, to: viewer.chapterNumber! - 1, read: true })}>
                    Позначити прочитаними
                </Button>
            </div>
        </div>
    );
}

function ChapterList({ slug, team, current, total, editionId }: {
    slug: string; team: string | undefined; current: number | null; total: number;
    /** The reader's own translation for marks; null for a guest. */
    editionId: number | null;
}) {
    const { mark, reset } = useReadMarks(slug, editionId);
    const [marking, setMarking] = useState<ChapterRowType | null>(null);
    const [order, setOrder] = useState<'asc' | 'desc'>('asc');
    const pages = Math.max(1, Math.ceil(total / CHAPTERS_PER_PAGE));
    const [page, setPage] = useState(() => (current ? Math.min(pages, Math.max(1, Math.ceil(current / CHAPTERS_PER_PAGE))) : 1));
    const chapters = useQuery({
        queryKey: ['chapters', slug, team ?? '', order, page],
        queryFn: () => readingApi.chapters(slug, team, order, page),
        placeholderData: (previous) => previous,
    });
    const rows = chapters.data?.items ?? [];
    const top = useRef<HTMLDivElement>(null);
    const turn = (next: number) => {
        setPage(next);
        top.current?.scrollIntoView?.({ block: 'start' });
    };

    return (
        <div className={styles.chapters} id="chapters" ref={top}>
            <div className={styles.chaptersHead}>
                <h2 className={styles.sectionTitle}>Глави</h2>
                <button type="button" className={styles.order} onClick={() => { setOrder(order === 'asc' ? 'desc' : 'asc'); setPage(1); }}>
                    <ArrowDownUp size={14} aria-hidden /> {order === 'asc' ? 'від першої' : 'від останньої'}
                </button>
            </div>
            {chapters.isError && <Notice tone="error">{chapters.error.message}</Notice>}
            <ol className={styles.list}>
                {rows.map((row, index) => (
                    <Fragment key={row.number}>
                    {row.volume && row.volume.firstNumber !== rows[index - 1]?.volume?.firstNumber && (
                        <li className={styles.volumeHead}>{volumeTitle(row.volume)}</li>
                    )}
                    <li className={editionId ? styles.markedRow : undefined}>
                        <Link to="/n/$slug/$number" params={{ slug, number: String(row.number) }} search={team ? { t: team } : {}}
                            className={`${styles.chapter} ${row.number === current ? styles.here : ''} ${row.read ? styles.read : ''}`}>
                            <span>{chapterHeading(row)}</span>
                            {row.number === current && <span className={styles.muted}>тут зупинились</span>}
                        </Link>
                        {editionId && (
                            <button type="button" className={`${styles.mark} ${row.read ? styles.markOn : ''}`} onClick={() => setMarking(row)}
                                aria-label={`${chapterHeading(row)}: ${row.read ? 'прочитано' : 'не прочитано'}`}>
                                {row.read && <Check size={14} aria-hidden />}
                            </button>
                        )}
                    </li>
                    </Fragment>
                ))}
            </ol>
            <Pager page={page} total={total} size={CHAPTERS_PER_PAGE} onPage={turn} />
            {editionId && current && (
                <button type="button" className={styles.resetProgress} disabled={reset.isPending}
                    onClick={() => void askConfirm({
                        title: 'Скинути прогрес?', text: 'Місце читання й позначки «прочитано» зникнуть. Новела лишиться в бібліотеці.',
                        confirmLabel: 'Скинути', danger: true,
                    }).then((yes) => { if (yes) reset.mutate(); })}>
                    Скинути прогрес читання
                </button>
            )}
            {marking && (
                <Sheet open onClose={() => setMarking(null)} title={chapterHeading(marking)}>
                    <div className={styles.markSheet}>
                        <Button wide variant={marking.read ? 'secondary' : 'primary'}
                            onPress={() => { mark.mutate({ from: marking.number, to: marking.number, read: !marking.read }); setMarking(null); }}>
                            {marking.read ? 'Позначити непрочитаною' : 'Позначити прочитаною'}
                        </Button>
                        {marking.number > 1 && (
                            <Button wide variant="secondary"
                                onPress={() => { mark.mutate({ from: 1, to: marking.number, read: true }); setMarking(null); }}>
                                Усі до цієї включно — прочитані
                            </Button>
                        )}
                    </div>
                </Sheet>
            )}
        </div>
    );
}

/** Talk about the translation as a whole; a link to a comment (#c40) opens it at once. */
function DiscussionButton({ editionId }: { editionId: number }) {
    const hash = useRouterState({ select: (state) => state.location.hash });
    const focus = /^c(\d+)$/.exec(hash)?.[1];
    const [open, setOpen] = useState(Boolean(focus));
    const count = useCommentCount(editionId);
    return (
        <>
            <button type="button" className={discussionStyles.fab} onClick={() => setOpen(true)}
                aria-label={`Обговорення перекладу, коментарів: ${count}`}>
                <MessageCircle size={18} aria-hidden />{count > 0 ? count : 'Обговорення'}
            </button>
            <Sheet open={open} onClose={() => setOpen(false)} title="Обговорення перекладу" tall>
                <Discussion editionId={editionId} focus={focus ? Number(focus) : undefined} />
            </Sheet>
        </>
    );
}

/** Average stars; a signed-in reader gives their own (tap the same star again to take it back). */
function Stars({ novel }: { novel: Novel }) {
    const me = useMe();
    const client = useQueryClient();
    const edition = novel.edition;
    const mine = novel.viewer?.myRating ?? null;
    const rate = useMutation({ meta: { errorToast: true },
        mutationFn: (score: number | null) => commentApi.rate(edition.editionId, score),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['novel', novel.slug] }),
    });
    return (
        <div className={styles.stars}>
            {me ? (
                <span role="radiogroup" aria-label="Ваша оцінка перекладу">
                    {[1, 2, 3, 4, 5].map((score) => (
                        <button key={score} type="button" role="radio" aria-checked={mine === score} aria-label={`${score} з 5`}
                            className={(mine ?? 0) >= score ? styles.starOn : styles.star}
                            onClick={() => rate.mutate(mine === score ? null : score)}>★</button>
                    ))}
                </span>
            ) : edition.rating != null && <span className={styles.starOn} aria-hidden>★</span>}
            <span className={styles.muted}>
                {edition.rating != null ? `${edition.rating.toFixed(1).replace('.', ',')} · оцінок: ${edition.ratings ?? 0}` : 'ще без оцінок'}
            </span>
        </div>
    );
}

/** Administrators hide a translation that breaks the rules; it comes back from «Приховане». */
function HideEdition({ editionId }: { editionId: number }) {
    const me = useMe();
    const navigate = useNavigate();
    const hide = useMutation({
        mutationFn: (reason: string) => adminApi.hide('edition', editionId, reason),
        onSuccess: () => void navigate({ to: '/admin/moderation' }),
    });
    if (me?.role !== 'admin' && me?.role !== 'owner') return null;
    return (
        <div style={{ marginTop: 24 }}>
            <Button variant="danger" onPress={() => void askText({
                title: 'Приховати переклад', label: 'Причина', hint: 'Її побачать інші модератори.', confirmLabel: 'Приховати', danger: true,
            }).then((reason) => { if (reason) hide.mutate(reason); })}>Приховати переклад</Button>
            {hide.isError && <Notice tone="error">{hide.error.message}</Notice>}
        </div>
    );
}

const FROM: Record<string, string> = {
    ja: 'японської', en: 'англійської', ko: 'корейської', zh: 'китайської', fr: 'французької', de: 'німецької', es: 'іспанської', pl: 'польської',
};

/** «переклад з японської», or just «переклад» when the site does not know the original's language. */
function translatedFrom(language: string | null | undefined): string {
    return language && FROM[language] ? `переклад з ${FROM[language]}` : 'переклад';
}

/** «Оригінал: виходить · 822 глави» and how much of it this translation covers. */
function OriginalProgress({ novel }: { novel: Novel }) {
    const facts = novel.facts;
    if (!facts?.sourceStatus && !facts?.sourceChapterCount) return null;
    const total = facts.sourceChapterCount;
    const done = novel.edition.chapterCount;
    return (
        <div className={styles.original}>
            <div className={styles.muted}>
                Оригінал: {[facts.sourceStatus && SOURCE_STATUS_LABELS[facts.sourceStatus], total && `${total} ${chaptersWord(total)}`]
                    .filter(Boolean).join(' · ')}
            </div>
            {total ? (
                <>
                    <div className={styles.progressBar} role="progressbar" aria-label="Перекладено" aria-valuemin={0}
                        aria-valuemax={total} aria-valuenow={Math.min(done, total)}>
                        <span style={{ width: `${Math.min(100, (done / total) * 100)}%` }} />
                    </div>
                    <div className={styles.muted}>Перекладено {done} з {total}</div>
                </>
            ) : null}
        </div>
    );
}
