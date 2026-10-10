import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, Navigate, useNavigate, useParams, useRouterState, useSearch } from '@tanstack/react-router';
import { ArrowDownUp, Bell, BellRing, BookmarkPlus, Check, Ellipsis } from '../../ui/icons';
import { Fragment, useRef, useState } from 'react';
import { Button as AriaButton, Menu, MenuItem, MenuTrigger, Popover, Tab, TabList, TabPanel, Tabs, ToggleButton } from 'react-aria-components';
import { ApiError, download, saveFile } from '../../api/client';
import { useMe } from '../../auth/me';
import { Discussion, useCommentCount } from '../../community/Discussion';
import { commentApi } from '../../community/api';
import { adminApi } from '../../admin/api';
import { Sheet } from '../../ui/Sheet';
import { Blocks } from '../../reading/Blocks';
import { Cover } from '../../reading/Cover';
import { LIST_LABELS, SOURCE_STATUS_LABELS, chapterHeading, chaptersWord, otherNames, readingApi, translationStatus, volumeTitle, type ChapterRow as ChapterRowType, type ContentsVolume, type ListName, type NovelPage as Novel } from '../../reading/api';
import { relayApi, teamApi } from '../../studio/api';
import { Segmented } from '../../ui/Segmented';
import { localProgress } from '../../reading/progress';
import { novelQuery } from '../../reading/queries';
import { Button } from '../../ui/Button';
import { Pager } from '../../ui/Pager';
import { LinkButton } from '../../ui/LinkButton';
import { Notice } from '../../ui/Notice';
import { showInfo } from '../../ui/toast';
import { useReportEdition } from '../../reading/ReportEdition';
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

type NovelTab = 'about' | 'chapters' | 'talk';

function NovelView({ novel, team }: { novel: Novel; team: string | undefined }) {
    const edition = novel.edition;
    // A link to a comment (#c40 from the inbox) opens the discussion at it; «Зміст» from the reader, the chapters.
    const hash = useRouterState({ select: (state) => state.location.hash });
    const focus = /^c(\d+)$/.exec(hash)?.[1];
    const [tab, setTab] = useState<NovelTab>(focus ? 'talk' : 'chapters');
    const [shownFocus, setShownFocus] = useState(focus);
    if (focus !== shownFocus) {
        setShownFocus(focus);
        if (focus) setTab('talk');
    }
    const comments = useCommentCount(edition.editionId);
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
    const meta = [
        novel.author,
        novel.origin === 'original' ? 'оригінальний твір' : translatedFrom(novel.language).replace(/^переклад /, ''),
        `$${edition.teamHandle}`,
        machine && 'ШІ',
        novel.adult && '18+',
    ].filter(Boolean).join(' · ');

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
                    <MoreButton novel={novel} />
                </div>
            </div>
            <div className={styles.content}>
                <div className={styles.headText}>
                    <h1 className={styles.title}>{novel.title}</h1>
                    <p className={styles.meta} title={edition.teamName}>{meta}</p>
                    <p className={styles.muted}>
                        {edition.chapterCount} {chaptersWord(edition.chapterCount)} · {translationStatus(edition.status, edition.pausedUntil)}
                    </p>
                    {novel.origin !== 'original' && <OriginalProgress novel={novel} />}
                    <p className={styles.muted}>
                        {edition.rating != null
                            ? <><span className={styles.starOn} aria-hidden>★</span> {edition.rating.toFixed(1).replace('.', ',')} · оцінок: {edition.ratings ?? 0}</>
                            : 'ще без оцінок'}
                    </p>
                </div>
                <Tabs selectedKey={tab} onSelectionChange={(key) => setTab(key as NovelTab)} className={styles.tabsBox}>
                    <TabList aria-label="Розділи новели" className={styles.tabs}>
                        <Tab id="about" className={styles.tab}>Про новелу</Tab>
                        <Tab id="chapters" className={styles.tab}>Глави · {edition.chapterCount}</Tab>
                        <Tab id="talk" className={styles.tab}>Обговорення{comments > 0 ? ` · ${comments}` : ''}</Tab>
                    </TabList>
                    <TabPanel id="about" className={styles.panel}>
                        <About novel={novel} />
                    </TabPanel>
                    <TabPanel id="chapters" className={styles.panel}>
                        {novel.viewer && <Skipped novel={novel} />}
                        <ChapterList slug={novel.slug} team={team} current={resume} total={edition.chapterCount}
                            editionId={novel.viewer ? edition.editionId : null} />
                        {novel.relay.continuations.map((next) => (
                            <Link key={next.teamHandle} to="/n/$slug/$number" params={{ slug: novel.slug, number: String(next.firstNumber) }}
                                search={{ t: next.teamHandle }} className={styles.continuation}>
                                Продовження від ${next.teamHandle} — з глави {next.firstNumber} →
                            </Link>
                        ))}
                    </TabPanel>
                    <TabPanel id="talk" className={styles.panel}>
                        <Discussion editionId={edition.editionId} focus={focus ? Number(focus) : undefined} />
                    </TabPanel>
                </Tabs>
            </div>
        </section>
    );
}

/** «Про новелу»: tags, the description, the reader's own stars, other names and the original. */
function About({ novel }: { novel: Novel }) {
    const names = otherNames(novel.title, novel.facts);
    const facts = novel.facts;
    const original = [facts?.sourceStatus && SOURCE_STATUS_LABELS[facts.sourceStatus],
        facts?.sourceChapterCount && `${facts.sourceChapterCount} ${chaptersWord(facts.sourceChapterCount)}`].filter(Boolean).join(' · ');
    return (
        <div className={styles.about}>
            {novel.tags.length > 0 && (
                <div className={styles.chips}>
                    {novel.tags.map((tag) => (
                        <Link key={tag} to="/catalog" search={{ tags: [tag.toLowerCase()] }} className={`${styles.chip} ${styles.ghost}`}>
                            {tag}
                        </Link>
                    ))}
                </div>
            )}
            {novel.description.length > 0 && (
                <div className={styles.description}><Blocks blocks={novel.description} /></div>
            )}
            <MyRating novel={novel} />
            {names.length > 0 && (
                <div>
                    <h2 className={styles.label}>Інші назви</h2>
                    <p className={styles.names}>{names.join(' · ')}</p>
                </div>
            )}
            {novel.origin !== 'original' && (original || novel.originalUrl) && (
                <div>
                    <h2 className={styles.label}>Оригінал</h2>
                    <p className={styles.muted}>
                        {original}
                        {novel.originalUrl && <>{original && ' · '}<a href={novel.originalUrl} target="_blank" rel="noopener noreferrer nofollow">відкрити ↗</a></>}
                    </p>
                </div>
            )}
        </div>
    );
}

/**
 * «⋯»: what is needed now and then — other translations, translating it oneself, the reading
 * progress, a report; for the team, managing it; for administrators, hiding it.
 */
function MoreButton({ novel }: { novel: Novel }) {
    const me = useMe();
    const navigate = useNavigate();
    const [open, setOpen] = useState(false);
    const [own, setOwn] = useState(false);
    const [book, setBook] = useState(false);
    const edition = novel.edition;
    const { reset } = useReadMarks(novel.slug, novel.viewer ? edition.editionId : null);
    const report = useReportEdition(edition.editionId);
    const relay = useRelay(novel);
    const hide = useMutation({ meta: { errorToast: true },
        mutationFn: (reason: string) => adminApi.hide('edition', edition.editionId, reason),
        onSuccess: () => void navigate({ to: '/admin/moderation' }),
    });
    const close = (then?: () => void) => {
        setOpen(false);
        then?.();
    };
    const team = novel.viewer?.teamRole;
    const admin = me?.role === 'admin' || me?.role === 'owner';
    return (
        <>
            <AriaButton className={styles.libraryButton} aria-label="Ще" onPress={() => setOpen(true)}>
                <Ellipsis size={20} aria-hidden />
            </AriaButton>
            <Sheet open={open} onClose={() => setOpen(false)} title={novel.title}>
                <div className={styles.sheetList}>
                    {novel.editions.length > 1 && (
                        <>
                            <div className={styles.label}>Переклади</div>
                            {novel.editions.map((other) => (
                                <Link key={other.editionId} to="/n/$slug" params={{ slug: novel.slug }} search={{ t: other.teamHandle }}
                                    className={styles.sheetItem} onClick={() => setOpen(false)}
                                    aria-current={other.editionId === edition.editionId ? 'true' : undefined}>
                                    <span>{other.teamName}</span>
                                    <span className={styles.muted}>
                                        {other.editionId === edition.editionId ? 'цей · ' : ''}{other.chapterCount} {chaptersWord(other.chapterCount)}
                                    </span>
                                </Link>
                            ))}
                            <div className={styles.label}>Ще</div>
                        </>
                    )}
                    {me && (edition.downloadAllowed || novel.viewer?.teamRole) && (
                        <button type="button" className={styles.sheetItem} onClick={() => close(() => setBook(true))}>Завантажити EPUB</button>
                    )}
                    {me && novel.origin === 'translation' && (
                        <button type="button" className={styles.sheetItem} onClick={() => close(() => setOwn(true))}>Перекласти самому</button>
                    )}
                    {relay.offer && (
                        <button type="button" className={styles.sheetItem} onClick={() => close(relay.offer!.run)}>
                            <span>{relay.offer.label}</span>
                            {relay.offer.hint && <span className={styles.muted}>{relay.offer.hint}</span>}
                        </button>
                    )}
                    {relay.asked && <div className={`${styles.sheetItem} ${styles.muted}`}>Запит продовжити переклад надіслано, чекаємо відповіді власника.</div>}
                    {novel.viewer?.chapterNumber != null && (
                        <button type="button" className={styles.sheetItem} onClick={() => close(() => void askConfirm({
                            title: 'Скинути прогрес?', text: 'Місце читання й позначки «прочитано» зникнуть. Новела лишиться в бібліотеці.',
                            confirmLabel: 'Скинути', danger: true,
                        }).then((yes) => { if (yes) reset.mutate(); }))}>
                            Скинути прогрес читання
                        </button>
                    )}
                    {!team && (
                        <button type="button" className={styles.sheetItem} onClick={() => close(report.ask)}>Поскаржитися на переклад</button>
                    )}
                    {team && (
                        <>
                            <div className={styles.label}>Команда</div>
                            <Link to="/studio/$editionId" params={{ editionId: String(edition.editionId) }} className={styles.sheetItem}>
                                Керувати в Студії
                            </Link>
                        </>
                    )}
                    {admin && (
                        <>
                            <div className={styles.label}>Модерація</div>
                            <button type="button" className={`${styles.sheetItem} ${styles.remove}`} onClick={() => close(() => void askText({
                                title: 'Приховати переклад', label: 'Причина', hint: 'Її побачать інші модератори.', confirmLabel: 'Приховати', danger: true,
                            }).then((reason) => { if (reason) hide.mutate(reason); }))}>
                                Приховати переклад
                            </button>
                        </>
                    )}
                </div>
            </Sheet>
            {own && <OwnTranslation novel={novel} onClose={() => setOwn(false)} />}
            {book && <EpubSheet novel={novel} onClose={() => setBook(false)} />}
        </>
    );
}

/** «Завантажити EPUB»: the whole translation or one of its volumes, as a book for a reader's app. */
function EpubSheet({ novel, onClose }: { novel: Novel; onClose: () => void }) {
    const team = novel.editions.length > 1 ? novel.edition.teamHandle : undefined;
    const volumes = useQuery({ queryKey: ['volumes', novel.slug, team ?? ''], queryFn: () => readingApi.volumes(novel.slug, team) });
    const get = useMutation({ meta: { errorToast: true },
        mutationFn: (volume: number | undefined) => download(readingApi.epubUrl(novel.slug, team, volume)),
        onSuccess: ({ blob, name }) => {
            saveFile(blob, name);
            onClose();
        },
    });
    const count = novel.edition.chapterCount;
    const option = (volume: number | undefined, title: string, chapters: number) => (
        <button key={volume ?? 'all'} type="button" className={styles.sheetItem} disabled={get.isPending}
            onClick={() => get.mutate(volume)}>
            <span>{title}</span>
            <span className={styles.muted}>
                {get.isPending && get.variables === volume ? 'Збираємо книжку…' : `${chapters} ${chaptersWord(chapters)}`}
            </span>
        </button>
    );
    return (
        <Sheet open onClose={onClose} title="Завантажити EPUB">
            <div className={styles.sheetList}>
                {option(undefined, 'Усі глави', count)}
                {volumes.isError && <Notice tone="error">{volumes.error.message}</Notice>}
                {(volumes.data?.length ?? 0) > 0 && <div className={styles.label}>Томи</div>}
                {volumes.data?.map((volume) => option(volume.firstNumber, volume.title, volume.chapters))}
                <p className={styles.muted} style={{ marginTop: 12 }}>
                    Відкривається в читалках книжок: Apple Books, Google Play Книги, PocketBook, Moon+ Reader.
                </p>
            </div>
        </Sheet>
    );
}

const RELAY_REASONS = {
    abandoned: 'Команда позначила переклад покинутим.',
    inactive: 'Власник перекладу давно не заходив на сайт.',
    unanswered: 'Нових глав давно немає, а на запит продовжити ніхто не відповів.',
};

/** Going on with someone else's translation: at once when it is free, otherwise by asking its owner. */
function useRelay(novel: Novel) {
    const me = useMe();
    const navigate = useNavigate();
    const [asked, setAsked] = useState(false);
    const start = useMutation({ meta: { errorToast: true },
        mutationFn: () => relayApi.start(novel.edition.editionId, '', 'human'),
        onSuccess: ({ editionId }) => void navigate({ to: '/studio/$editionId', params: { editionId: String(editionId) } }),
    });
    const ask = useMutation({ meta: { errorToast: true },
        mutationFn: (message: string) => relayApi.ask(novel.edition.editionId, '', message),
        onSuccess: () => {
            setAsked(true);
            showInfo('Запит надіслано власнику перекладу.');
        },
    });
    if (!me || novel.origin !== 'translation' || novel.viewer?.teamRole) return { offer: null, asked: false };
    if (novel.relay.free) {
        return {
            offer: {
                label: 'Продовжити цей переклад',
                hint: `${novel.relay.reason ? RELAY_REASONS[novel.relay.reason] : 'Переклад вільний.'} Ваш почнеться з глави ${novel.relay.lastNumber + 1}.`,
                run: () => start.mutate(),
            },
            asked: false,
        };
    }
    if (novel.edition.status === 'completed') return { offer: null, asked: false };
    if (asked || novel.viewer?.relayAsked) return { offer: null, asked: true };
    return {
        offer: {
            label: 'Хочу продовжити цей переклад',
            hint: null,
            run: () => void askText({
                title: 'Продовжити переклад', label: 'Кілька слів власнику', hint: 'Необовʼязково.', optional: true, multiline: true,
                confirmLabel: 'Надіслати запит',
            }).then((message) => { if (message !== null) ask.mutate(message); }),
        },
        asked: false,
    };
}

/**
 * «Перекласти самому» (етап 17): anyone with an account starts their own translation of this
 * novel — from the first chapter or going on after this one — without asking anybody.
 */
function OwnTranslation({ novel, onClose }: { novel: Novel; onClose: () => void }) {
    const navigate = useNavigate();
    const [from, setFrom] = useState<'start' | 'after'>('after');
    const teams = useQuery({ queryKey: ['my-teams'], queryFn: teamApi.mine });
    const mine = (teams.data ?? []).filter((team) => team.role !== 'editor');
    const [team, setTeam] = useState('');
    const chosen = team || mine[0]?.handle || '';
    const start = useMutation({
        mutationFn: () => relayApi.own(novel.slug, chosen, from === 'after' ? novel.edition.editionId : null),
        onSuccess: ({ editionId }) => void navigate({ to: '/studio/$editionId', params: { editionId: String(editionId) } }),
    });
    return (
        <Sheet open onClose={onClose} title="Свій переклад">
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
        for (const key of [['novel', slug], ['chapters', slug], ['contents', slug], ['home'], ['library']]) void client.invalidateQueries({ queryKey: key });
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
    const { mark } = useReadMarks(slug, editionId);
    const [marking, setMarking] = useState<ChapterRowType | null>(null);
    const [order, setOrder] = useState<'asc' | 'desc'>('asc');
    // Without volumes (or if they fail to come) the list is simply «Усі глави».
    const contents = useQuery({ queryKey: ['contents', slug, team ?? ''], queryFn: () => readingApi.contents(slug, team), retry: false });
    const volumes = contents.data ?? [];
    const [mode, setModeState] = useState<ListMode>(() => remembered(MODE_KEY, 'volumes') as ListMode);
    const byVolumes = mode === 'volumes' && volumes.length > 0;
    const setMode = (next: ListMode) => { setModeState(next); remember(MODE_KEY, next); };
    const [folded, setFolded] = useState<number[]>(() => {
        try { return JSON.parse(remembered(`${FOLD_KEY}${slug}`, '[]')) as number[]; } catch { return []; }
    });
    const fold = (first: number) => {
        const next = folded.includes(first) ? folded.filter((item) => item !== first) : [...folded, first];
        setFolded(next);
        remember(`${FOLD_KEY}${slug}`, JSON.stringify(next));
    };
    const top = useRef<HTMLDivElement>(null);

    const row = (item: ChapterRowType) => (
        <li key={item.number} className={editionId ? styles.markedRow : undefined}>
            <Link to="/n/$slug/$number" params={{ slug, number: String(item.number) }} search={team ? { t: team } : {}}
                className={`${styles.chapter} ${item.number === current ? styles.here : ''} ${item.read ? styles.read : ''}`}>
                <span>{chapterHeading(item)}</span>
                {item.number === current && <span className={styles.muted}>тут зупинились</span>}
            </Link>
            {editionId && (
                <button type="button" className={`${styles.mark} ${item.read ? styles.markOn : ''}`} onClick={() => setMarking(item)}
                    aria-label={`${chapterHeading(item)}: ${item.read ? 'прочитано' : 'не прочитано'}`}>
                    {item.read && <Check size={14} aria-hidden />}
                </button>
            )}
        </li>
    );
    const shown = order === 'asc' ? volumes : [...volumes].reverse();
    const holds = (volume: ContentsVolume) => current !== null && current >= volume.firstNumber
        && (volume.lastNumber === null || current <= volume.lastNumber);

    return (
        <div className={styles.chapters} id="chapters" ref={top}>
            <div className={styles.chaptersHead}>
                {volumes.length > 0 && (
                    <div className={styles.listMode} role="group" aria-label="Як показати глави">
                        <button type="button" aria-pressed={byVolumes} onClick={() => setMode('volumes')}>По томах</button>
                        <button type="button" aria-pressed={!byVolumes} onClick={() => setMode('all')}>Усі глави</button>
                    </div>
                )}
                <button type="button" className={styles.order} onClick={() => setOrder(order === 'asc' ? 'desc' : 'asc')}>
                    <ArrowDownUp size={14} aria-hidden /> {order === 'asc' ? 'від першої' : 'від останньої'}
                </button>
            </div>
            {contents.isPending ? null : byVolumes
                ? shown.map((volume) => (
                    <VolumeSection key={`${volume.firstNumber}-${order}`} volume={volume} slug={slug} team={team} order={order} row={row}
                        initiallyOpen={holds(volume) || current === null && volume === shown[0]}
                        current={current} />
                ))
                : <AllChapters slug={slug} team={team} order={order} total={total} current={current} row={row} folded={folded} onFold={fold} top={top} />}
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

type ListMode = 'volumes' | 'all';
const MODE_KEY = 'chapter-list-mode';
const FOLD_KEY = 'chapter-list-folded:';

/** A per-viewer convenience: the browser may refuse storage, then the list just starts fresh. */
function remembered(key: string, fallback: string): string {
    try { return window.localStorage.getItem(key) ?? fallback; } catch { return fallback; }
}

function remember(key: string, value: string) {
    try { window.localStorage.setItem(key, value); } catch { /* the list works without it */ }
}

/** «Усі глави»: 20 to a page as before; a volume's heading folds its chapters on the page. */
function AllChapters({ slug, team, order, total, current, row, folded, onFold, top }: {
    slug: string; team: string | undefined; order: 'asc' | 'desc'; total: number; current: number | null;
    row: (item: ChapterRowType) => React.ReactNode; folded: number[]; onFold: (first: number) => void;
    top: React.RefObject<HTMLDivElement | null>;
}) {
    const pages = Math.max(1, Math.ceil(total / CHAPTERS_PER_PAGE));
    const [page, setPage] = useState(() => (current && order === 'asc'
        ? Math.min(pages, Math.max(1, Math.ceil(current / CHAPTERS_PER_PAGE))) : 1));
    const chapters = useQuery({
        queryKey: ['chapters', slug, team ?? '', order, page],
        queryFn: () => readingApi.chapters(slug, team, order, page),
        placeholderData: (previous) => previous,
    });
    const rows = chapters.data?.items ?? [];
    const turn = (next: number) => {
        setPage(next);
        top.current?.scrollIntoView?.({ block: 'start' });
    };
    return (
        <>
            {chapters.isError && <Notice tone="error">{chapters.error.message}</Notice>}
            <ol className={styles.list}>
                {rows.map((item, index) => {
                    const first = item.volume?.firstNumber;
                    const heading = item.volume && first !== rows[index - 1]?.volume?.firstNumber;
                    const closed = first !== undefined && folded.includes(first);
                    return (
                        <Fragment key={item.number}>
                            {heading && (
                                <li className={styles.volumeHead}>
                                    <button type="button" className={styles.volumeToggle} aria-expanded={!closed} onClick={() => onFold(first!)}>
                                        <span aria-hidden>{closed ? '▸' : '▾'}</span> {volumeTitle(item.volume!)}
                                    </button>
                                </li>
                            )}
                            {!closed && row(item)}
                        </Fragment>
                    );
                })}
            </ol>
            <Pager page={page} total={total} size={CHAPTERS_PER_PAGE} onPage={turn} />
        </>
    );
}

/** «По томах»: one volume, folded or open, its chapters 20 to a page. */
function VolumeSection({ volume, slug, team, order, row, initiallyOpen, current }: {
    volume: ContentsVolume; slug: string; team: string | undefined; order: 'asc' | 'desc';
    row: (item: ChapterRowType) => React.ReactNode; initiallyOpen: boolean; current: number | null;
}) {
    const [open, setOpen] = useState(initiallyOpen);
    const pages = Math.max(1, Math.ceil(volume.chapters / CHAPTERS_PER_PAGE));
    const [page, setPage] = useState(() => {
        if (current === null || !initiallyOpen || order !== 'asc') return 1;
        return Math.min(pages, Math.max(1, Math.ceil((current - volume.firstNumber + 1) / CHAPTERS_PER_PAGE)));
    });
    const chapters = useQuery({
        queryKey: ['chapters', slug, team ?? '', order, page, volume.firstNumber],
        queryFn: () => readingApi.chapters(slug, team, order, page, volume.firstNumber, volume.lastNumber ?? undefined),
        enabled: open,
        placeholderData: (previous) => previous,
    });
    const range = volume.lastNumber === null || volume.lastNumber === volume.firstNumber
        ? `з глави ${volume.firstNumber}` : `глави ${volume.firstNumber}–${volume.lastNumber}`;
    return (
        <section className={styles.volume}>
            <button type="button" className={styles.volumeBar} aria-expanded={open} onClick={() => setOpen(!open)}>
                <span aria-hidden>{open ? '▾' : '▸'}</span>
                <span className={styles.volumeName}>{volume.title}</span>
                <span className={styles.muted}>
                    {range} · {volume.chapters} {chaptersWord(volume.chapters)}
                    {volume.read !== null && volume.read > 0 && ` · прочитано ${volume.read === volume.chapters ? 'всі' : volume.read}`}
                </span>
            </button>
            {open && (
                <>
                    {chapters.isError && <Notice tone="error">{chapters.error.message}</Notice>}
                    <ol className={styles.list}>{(chapters.data?.items ?? []).map((item) => row(item))}</ol>
                    <Pager page={page} total={volume.chapters} size={CHAPTERS_PER_PAGE} onPage={setPage} />
                </>
            )}
        </section>
    );
}

/** The reader's own stars (tap the same star again to take them back); the average is in the heading. */
function MyRating({ novel }: { novel: Novel }) {
    const me = useMe();
    const client = useQueryClient();
    const mine = novel.viewer?.myRating ?? null;
    const rate = useMutation({ meta: { errorToast: true },
        mutationFn: (score: number | null) => commentApi.rate(novel.edition.editionId, score),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['novel', novel.slug] }),
    });
    if (!me) return null;
    return (
        <div>
            <h2 className={styles.label}>Ваша оцінка перекладу</h2>
            <span role="radiogroup" aria-label="Ваша оцінка перекладу" className={styles.stars}>
                {[1, 2, 3, 4, 5].map((score) => (
                    <button key={score} type="button" role="radio" aria-checked={mine === score} aria-label={`${score} з 5`}
                        className={(mine ?? 0) >= score ? styles.starOn : styles.star}
                        onClick={() => rate.mutate(mine === score ? null : score)}>★</button>
                ))}
            </span>
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

/** How much of the original this translation covers: «Перекладено 44 з 822 · оригінал виходить». */
function OriginalProgress({ novel }: { novel: Novel }) {
    const facts = novel.facts;
    if (!facts?.sourceStatus && !facts?.sourceChapterCount) return null;
    const total = facts.sourceChapterCount;
    const done = novel.edition.chapterCount;
    const status = facts.sourceStatus && `оригінал ${SOURCE_STATUS_LABELS[facts.sourceStatus]}`;
    return (
        <div className={styles.original}>
            {total ? (
                <div className={styles.progressBar} role="progressbar" aria-label="Перекладено" aria-valuemin={0}
                    aria-valuemax={total} aria-valuenow={Math.min(done, total)}>
                    <span style={{ width: `${Math.min(100, (done / total) * 100)}%` }} />
                </div>
            ) : null}
            <div className={styles.muted}>{[total && `Перекладено ${done} з ${total}`, status].filter(Boolean).join(' · ')}</div>
        </div>
    );
}
