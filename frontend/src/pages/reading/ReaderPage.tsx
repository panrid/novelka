import { DiffModeSwitch } from '../../ui/WordDiff';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, Navigate, useNavigate, useParams, useRouterState, useSearch } from '@tanstack/react-router';
import { ArrowLeft, ChevronLeft, ChevronRight, Lightbulb, List, MessageCircle, PenLine, Type } from '../../ui/icons';
import { createPortal } from 'react-dom';
import { hintsRemembered, rememberHints, useGlossaryHints } from '../../reading/useGlossaryHints';
import { useCallback, useEffect, useRef, useState, type MouseEvent } from 'react';
import { useMe } from '../../auth/me';
import { Discussion, useCommentCount } from '../../community/Discussion';
import { Blocks } from '../../reading/Blocks';
import { chapterHeading, volumeTitle, readingApi, type NovelPage, type ReaderChapter } from '../../reading/api';
import { localProgress, movesPlace, saveLocalProgress } from '../../reading/progress';
import { chapterQuery } from '../../reading/queries';
import { FONT_STACKS } from '../../appearance/fonts';
import { READER_WIDTHS, readerLook, tapAt } from '../../appearance/model';
import { ReaderLookForm } from '../../appearance/ReaderLookForm';
import { readerOpened, useAppearance } from '../../appearance/store';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Sheet } from '../../ui/Sheet';
import { TextInput } from '../../ui/TextInput';
import { autotranslateApi } from '../../studio/autotranslate';
import { ReportEdition } from '../../reading/ReportEdition';
import { usePages } from './usePages';
import { InlineEdit, ReplaceSheet, ReviewCard, SelectionBar, useMySuggestions, useReview } from './Suggestions';
import suggestionStyles from './suggestions.module.css';
import styles from './reader.module.css';

const SAVE_EVERY_MS = 15_000;

export function ReaderPage() {
    const { slug, number } = useParams({ strict: false }) as { slug: string; number: string };
    const { t, find, look }: { t?: string; find?: string; look?: true } = useSearch({ strict: false });
    const chapterNumber = Number(number);
    const client = useQueryClient();
    const chapter = useQuery(chapterQuery(slug, chapterNumber, t));

    // The next chapter is loaded in the background, so turning the page is instant.
    const next = chapter.data?.next;
    useEffect(() => {
        if (next) {
            void client.prefetchQuery(chapterQuery(slug, next, t));
        }
    }, [client, next, slug, t]);

    if (chapter.isPending) {
        return <p className={styles.status}>Завантажуємо главу…</p>;
    }
    if (chapter.isError) {
        return (
            <div className={styles.status}>
                <Notice tone="error">{chapter.error.message}</Notice>
                <p style={{ marginTop: 12 }}><Link to="/n/$slug" params={{ slug }} search={t ? { t } : {}}>До новели</Link></p>
            </div>
        );
    }
    if (chapter.data.novelSlug !== slug) {
        // An address the novel had before: the same chapter under the one it has now.
        return <Navigate to="/n/$slug/$number" params={{ slug: chapter.data.novelSlug, number }} search={{ ...(t ? { t } : {}), ...(find ? { find } : {}), ...(look ? { look } : {}) }} replace />;
    }
    return <Reader key={`${slug}:${chapterNumber}`} chapter={chapter.data} team={t} find={find} look={Boolean(look)} />;
}

function Reader({ chapter, team, find, look = false }: { chapter: ReaderChapter; team: string | undefined; find?: string | undefined; look?: boolean }) {
    const me = useMe();
    // Read when a chapter opens, not a reason to scroll again once the account has loaded.
    const signedIn = useRef(me !== null);
    useEffect(() => {
        signedIn.current = me !== null;
    }, [me]);
    const client = useQueryClient();
    const navigate = useNavigate();
    const appearance = useAppearance();
    const view = readerLook(appearance);
    // The scroll listener lives as long as the chapter; it reads the latest choice from here.
    const hideBars = useRef(view.hideBars);
    useEffect(() => {
        hideBars.current = view.hideBars;
    }, [view.hideBars]);
    useWakeLock(view.awake);
    // The reader's own colours while it is open; the site's style comes back on leaving.
    useEffect(() => {
        readerOpened(true);
        return () => readerOpened(false);
    }, []);
    const [barsVisible, setBarsVisible] = useState(true);
    const [progress, setProgress] = useState(0);
    const lastY = useRef(0);
    // Where the current run of scrolling in one direction started: a swipe is many small events.
    const runStart = useRef({ y: 0, down: true });
    const lastSaved = useRef({ at: 0, position: -1 });
    // The place is taken from scroll events, never read at exit: by then the router may
    // already have scrolled the next page to the top, and 0 would overwrite the real place.
    const place = useRef(0);
    const search = team ? { t: team } : {};

    // The component is keyed by chapter, so what it was opened with stays fixed for its life;
    // later cache updates (saved place) must not re-run the restore or re-subscribe listeners.
    const [opened] = useState(chapter);
    const lookOnly = useRef(Boolean(find) || look);

    // Start where the reader stopped: from the server (any device), else from this browser.
    useEffect(() => {
        // Opened to show a word (from the glossary): go to where it first appears.
        const mark = find ? document.querySelector('mark[data-found]') : null;
        if (mark) {
            requestAnimationFrame(() => mark.scrollIntoView({ block: 'center' }));
            return;
        }
        const local = signedIn.current ? null : localProgress(opened.novelSlug, opened.edition.teamHandle);
        const position = opened.savedPosition ?? (local?.number === opened.number ? local.position : 0);
        place.current = position;
        if (position > 0.02) {
            requestAnimationFrame(() => window.scrollTo(0, position * scrollable()));
        } else {
            window.scrollTo(0, 0);
        }
    }, [opened, find]);

    const save = useCallback((force: boolean) => {
        // Opened to show a word or to review suggestions: a look, not reading — the place stays.
        if (lookOnly.current) return;
        const position = Math.round(place.current * 1000) / 1000;
        // Only a guest's place lives in the browser; an account's is on the server.
        if (!me && movesPlace(localProgress(opened.novelSlug, opened.edition.teamHandle), opened.number, position)) {
            saveLocalProgress(opened.novelSlug, opened.edition.teamHandle, { number: opened.number, position, label: opened.label ?? null });
        }
        const now = Date.now();
        const changed = Math.abs(position - lastSaved.current.position) > 0.01;
        // Only what really reached the server counts as saved; a guest (or an account still
        // loading) keeps the place in this browser and sends it once signed in.
        if (me && changed && (force || now - lastSaved.current.at > SAVE_EVERY_MS)) {
            lastSaved.current = { at: now, position };
            // «Назад» into this chapter should restore the fresh place, not the one it was opened with.
            client.setQueryData<ReaderChapter>(['chapter', opened.novelSlug, team ?? '', opened.number],
                (cached) => (cached ? { ...cached, savedPosition: position } : cached));
            // The novel page shows «Продовжити · гл. N» at once, even if its request beats this save.
            client.setQueriesData<NovelPage>({ queryKey: ['novel', opened.novelSlug] }, (cached) =>
                cached?.viewer && cached.edition.editionId === opened.edition.editionId
                    && movesPlace(cached.viewer.chapterNumber ? { number: cached.viewer.chapterNumber } : null, opened.number, position)
                    ? { ...cached, viewer: { ...cached.viewer, chapterNumber: opened.number, position, chapterLabel: opened.label ?? null } }
                    : cached);
            void readingApi.saveProgress(opened.edition.editionId, opened.number, position).then(() => {
                // Pages opened meanwhile may have read the place before it was saved.
                for (const key of [['novel', opened.novelSlug], ['home'], ['library']]) {
                    void client.invalidateQueries({ queryKey: key });
                }
            }, () => {
                // Offline or signed out elsewhere: the local copy keeps the place meanwhile.
            });
        }
    }, [opened, client, me, team]);

    useEffect(() => {
        save(true); // opening a chapter already counts as reading it
        const onScroll = () => {
            const y = window.scrollY;
            const position = currentPosition();
            place.current = position;
            // A tenth of a percent is all the line shows: the page is not drawn again for less.
            setProgress(Math.round(position * 1000) / 1000);
            const down = y >= lastY.current;
            if (down !== runStart.current.down) {
                runStart.current = { y: lastY.current, down };
            }
            const travelled = Math.abs(y - runStart.current.y);
            if (y < 60 || position > 0.985) {
                setBarsVisible(true);
            } else if (down && travelled > 8 && hideBars.current) {
                setBarsVisible(false); // reading on: give the text the whole screen
            } else if (!down && travelled > 24) {
                setBarsVisible(true); // a deliberate swipe back up brings the controls
            }
            lastY.current = y;
            save(false);
        };
        const onHide = () => {
            if (document.visibilityState === 'hidden') save(true);
        };
        window.addEventListener('scroll', onScroll, { passive: true });
        document.addEventListener('visibilitychange', onHide);
        return () => {
            window.removeEventListener('scroll', onScroll);
            document.removeEventListener('visibilitychange', onHide);
            save(true);
        };
    }, [save]);


    const mine = useMySuggestions(chapter, me !== null);
    const review = useReview(chapter);
    const [editMode, setEditMode] = useState(false);
    const [editing, setEditing] = useState<string | null>(null);
    const [replacing, setReplacing] = useState<string | null>(null);
    const [asking, setAsking] = useState<{ text: string; blockId: string } | null>(null);
    const [linking, setLinking] = useState<{ text: string; blockId: string } | null>(null);
    const team$ = chapter.teamRole !== null;
    // «Лампочка»: the glossary's words marked in the text, a tap on one says who or what it is.
    const [hintsOn, setHintsOn] = useState(hintsRemembered);
    const [reviewing, setReviewing] = useState(false);
    useEffect(() => {
        if (reviewing) lookOnly.current = true;
    }, [reviewing]);
    const editingBlock = chapter.blocks.find((block) => block.id === editing);
    // The paragraph being rewritten becomes a field where it stands (етап 20, рішення 3).
    const inlineEdit = editingBlock ? {
        blockId: editingBlock.id,
        node: <InlineEdit chapter={chapter} block={editingBlock} onClose={() => setEditing(null)} onSaved={mine.refresh}
            existing={mine.items.find((item) => item.kind === 'block' && item.blockId === editingBlock.id && item.state === 'draft')} />,
    } : undefined;
    // A link to a comment (#c40 from the inbox) opens the discussion at it.
    const hash = useRouterState({ select: (state) => state.location.hash });
    const focus = /^c(\d+)$/.exec(hash)?.[1];
    const [talking, setTalking] = useState(Boolean(focus));
    const [quote, setQuote] = useState<{ blockId: string; text: string } | null>(null);
    const comments = useCommentCount(chapter.edition.editionId, chapter.number);

    /** A tap on the text toggles the controls; in «режим правок» it opens the paragraph instead. */
    function toggleBars(event: MouseEvent) {
        const target = event.target as HTMLElement;
        if (target.closest('a, button, [data-inline-edit]') || window.getSelection()?.toString()) return;
        const marked = hints.at(event.clientX, event.clientY);
        if (marked && !editMode) {
            setAsking({ text: marked.text, blockId: marked.blockId });
            return;
        }
        const block = target.closest('p[data-block-id], h2[data-block-id]');
        if (editMode && block) {
            setEditing(block.getAttribute('data-block-id'));
            return;
        }
        // Pages turn with a tap where the person chose (the edges by default); the rest shows the controls.
        if (paged) {
            const zone = tapAt(view.taps, event.clientX / window.innerWidth, event.clientY / window.innerHeight);
            if (zone === 'back') return pages.turn(-1);
            if (zone === 'next') return pages.turn(1);
        }
        if (view.hideBars) setBarsVisible((visible) => !visible);
    }

    const chapterLink = (target: number) => ({
        to: '/n/$slug/$number' as const, params: { slug: chapter.novelSlug, number: String(target) }, search,
    });

    // Pages like a book (етап 18); while making or checking suggestions the chapter scrolls as usual.
    // A paragraph being rewritten needs the text to scroll: pages wait until it is saved or left.
    const paged = view.mode === 'pages' && !editMode && !reviewing && !find && !editing;
    // A book does not scroll: without this a phone moves the page a little up and down before turning it.
    useEffect(() => {
        if (!paged) return;
        const root = document.documentElement;
        const before = { overflow: root.style.overflow, overscroll: root.style.overscrollBehavior };
        root.style.overflow = 'hidden';
        root.style.overscrollBehavior = 'none';
        window.scrollTo(0, 0);
        return () => {
            root.style.overflow = before.overflow;
            root.style.overscrollBehavior = before.overscroll;
        };
    }, [paged]);
    const frame = useRef<HTMLDivElement>(null);
    const textRef = useRef<HTMLElement>(null);
    const hints = useGlossaryHints({
        enabled: hintsOn, slug: chapter.novelSlug, team, chapter: chapter.number,
        layoutKey: [chapter.number, paged, editMode, chapter.blocks.length, mine.drafts, review.items.length, Object.keys(mine.overlay).length].join(':'),
    });
    const pages = usePages({
        enabled: paged, frame, text: textRef, turnStyle: view.pageAnim, position: () => place.current,
        layoutKey: [view.font, view.size, view.lineHeight, view.margin, view.width, view.align, view.paragraphs,
            chapter.blocks.length, mine.drafts, review.items.length].join(':'),
        onPage: (position) => {
            place.current = position;
            setProgress(position);
            save(false);
            if (hideBars.current) setBarsVisible(false);
        },
        onBeforeStart: () => { if (chapter.previous) void navigate(chapterLink(chapter.previous)); },
        onAfterEnd: () => { if (chapter.next) void navigate(chapterLink(chapter.next)); },
    });

    useEffect(() => {
        const onKey = (event: KeyboardEvent) => {
            // Typing (a field, or a paragraph rewritten in its place) moves the caret, not the chapter.
            if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement
                || event.target instanceof HTMLElement && event.target.isContentEditable) return;
            if (paged && (event.key === 'ArrowLeft' || event.key === 'ArrowRight')) {
                pages.turn(event.key === 'ArrowLeft' ? -1 : 1);
            } else if (event.key === 'ArrowLeft' && chapter.previous) {
                void navigate({ to: '/n/$slug/$number', params: { slug: chapter.novelSlug, number: String(chapter.previous) }, search });
            } else if (event.key === 'ArrowRight' && chapter.next) {
                void navigate({ to: '/n/$slug/$number', params: { slug: chapter.novelSlug, number: String(chapter.next) }, search });
            }
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    });

    return (
        <div className={styles.reader}>
            <div className={styles.progressLine} aria-hidden><span style={{ width: `${progress * 100}%` }} /></div>

            <header className={`${styles.top} ${barsVisible ? '' : styles.hidden}`}>
                <Link to="/n/$slug" params={{ slug: chapter.novelSlug }} search={search} className={styles.icon} aria-label="До новели">
                    <ArrowLeft size={22} aria-hidden />
                </Link>
                <div className={styles.where}>
                    <div className={styles.novel}>{chapter.novelTitle}</div>
                    <div className={styles.count}>{chapter.label === null ? `Глава ${chapter.number}` : chapter.number} з {chapter.edition.chapterCount}</div>
                </div>
                <button type="button" className={`${styles.icon} ${hintsOn ? styles.iconOn : ''}`} aria-pressed={hintsOn}
                    aria-label="Підказки зі словника" title={hintsOn ? 'Підказки зі словника: увімкнено' : 'Підказки зі словника'}
                    onClick={() => { setHintsOn(!hintsOn); rememberHints(!hintsOn); }}>
                    <Lightbulb size={22} aria-hidden />
                </button>
                <TextSettings />
                <Link to="/n/$slug" params={{ slug: chapter.novelSlug }} search={search} hash="chapters" className={styles.icon} aria-label="Зміст">
                    <List size={22} aria-hidden />
                </Link>
            </header>

            {paged ? (
                <div className={styles.pages} style={{ maxWidth: READER_WIDTHS[view.width], paddingInline: view.margin }}>
                    <div ref={frame} className={styles.pageWindow}>
            <article ref={textRef} className={`${styles.text} ${paged ? styles.pagedText : ''}`} lang="uk" data-paragraphs={view.paragraphs}
                onClick={toggleBars} style={{
                fontSize: view.size, fontFamily: FONT_STACKS[view.font], lineHeight: view.lineHeight, textAlign: view.align,
                hyphens: view.align === 'justify' ? 'auto' : 'manual',
                ...(paged ? {
                    columnWidth: pages.width || undefined, columnGap: Math.max(24, view.margin * 2), transform: `translateX(${-pages.page * pages.step}px)`,
                    transition: pages.animated ? 'transform 0.3s ease, opacity 0.15s' : 'opacity 0.15s', opacity: pages.effect === 'fadeOut' ? 0 : 1,
                } : { maxWidth: READER_WIDTHS[view.width], paddingInline: view.margin }),
            }}>
                {chapter.volume && volumeTitle(chapter.volume) !== chapterHeading(chapter) && <p className={styles.volumeLine}>{volumeTitle(chapter.volume)}</p>}
                <h1 className={styles.chapterTitle}>{chapterHeading(chapter)}</h1>
                {review.items.length > 0 && !reviewing && (
                    <div className={suggestionStyles.banner}>
                        <span>Правок на перевірку: {review.items.length}</span>
                        <Button variant="secondary" onPress={() => setReviewing(true)}>Перевірити</Button>
                    </div>
                )}
                {reviewing && <div className={styles.diffMode}><DiffModeSwitch /></div>}
                {reviewing && review.items.filter((item) => item.kind !== 'block').map((item) => (
                    <ReviewCard key={item.id} item={item} verdict={review.verdicts[item.id]} currentBlocks={chapter.blocks}
                        onDecide={(verdict) => review.decide(item.id, verdict)} />
                ))}
                {editMode && <p className={suggestionStyles.banner}>Режим правок: торкніться абзацу, щоб його виправити.</p>}
                <Blocks blocks={chapter.blocks} highlight={find} overlay={mine.overlay} replace={inlineEdit}
                    after={reviewing ? (blockId) => review.items.filter((item) => item.kind === 'block' && item.blockId === blockId).map((item) => (
                        <ReviewCard key={item.id} item={item} verdict={review.verdicts[item.id]} onDecide={(verdict) => review.decide(item.id, verdict)} />
                    )) : undefined} />
                {mine.drafts > 0 && (
                    <div className={suggestionStyles.banner}>
                        <span>Ненадісланих правок: {mine.drafts}</span>
                        <Button onPress={() => mine.submit.mutate()} pending={mine.submit.isPending}>Надіслати</Button>
                    </div>
                )}
                {mine.submit.isSuccess && mine.drafts === 0 && <Notice tone="success">Правки надіслано команді. Дякуємо!</Notice>}
                {!me && (
                    <p className={styles.finished}>
                        <Link to="/login" search={{ next: `/n/${chapter.novelSlug}/${chapter.number}` }}>Увійдіть</Link>, щоб запропонувати правку.
                    </p>
                )}
                {me && !chapter.teamRole && (
                    <p className={styles.finished}>
                        <Link to="/n/$slug/$number/propose" params={{ slug: chapter.novelSlug, number: String(chapter.number) }} search={search}>
                            Запропонувати зміни в усій главі
                        </Link>
                    </p>
                )}
                {chapter.teamRole && (
                    <p className={styles.finished}>
                        <Link to="/studio/$editionId/chapters/$number" params={{ editionId: String(chapter.edition.editionId), number: String(chapter.number) }}>
                            Редагувати главу
                        </Link>
                    </p>
                )}
                <nav className={styles.end} aria-label="Інші глави">
                    {chapter.next ? (
                        <Link {...chapterLink(chapter.next)} className={styles.nextButton}>Наступна глава →</Link>
                    ) : chapter.continuation ? (
                        <Link to="/n/$slug/$number" params={{ slug: chapter.novelSlug, number: String(chapter.continuation.firstNumber) }}
                            search={{ t: chapter.continuation.teamHandle }} className={styles.nextButton}>
                            Продовження від ${chapter.continuation.teamHandle} — глава {chapter.continuation.firstNumber} →
                        </Link>
                    ) : (
                        <p className={styles.finished}>Це остання перекладена глава. Нові зʼявляться на головній і у «Вхідних».</p>
                    )}
                </nav>
                {!chapter.teamRole && <ReportEdition editionId={chapter.edition.editionId} chapterLabel={chapter.label ?? String(chapter.number)} chapterNumber={chapter.number} />}
            </article>
                    </div>
                </div>
            ) : (
            <article ref={textRef} className={`${styles.text} ${paged ? styles.pagedText : ''}`} lang="uk" data-paragraphs={view.paragraphs}
                onClick={toggleBars} style={{
                fontSize: view.size, fontFamily: FONT_STACKS[view.font], lineHeight: view.lineHeight, textAlign: view.align,
                hyphens: view.align === 'justify' ? 'auto' : 'manual',
                ...(paged ? {
                    columnWidth: pages.width || undefined, columnGap: Math.max(24, view.margin * 2), transform: `translateX(${-pages.page * pages.step}px)`,
                    transition: pages.animated ? 'transform 0.3s ease, opacity 0.15s' : 'opacity 0.15s', opacity: pages.effect === 'fadeOut' ? 0 : 1,
                } : { maxWidth: READER_WIDTHS[view.width], paddingInline: view.margin }),
            }}>
                {chapter.volume && volumeTitle(chapter.volume) !== chapterHeading(chapter) && <p className={styles.volumeLine}>{volumeTitle(chapter.volume)}</p>}
                <h1 className={styles.chapterTitle}>{chapterHeading(chapter)}</h1>
                {review.items.length > 0 && !reviewing && (
                    <div className={suggestionStyles.banner}>
                        <span>Правок на перевірку: {review.items.length}</span>
                        <Button variant="secondary" onPress={() => setReviewing(true)}>Перевірити</Button>
                    </div>
                )}
                {reviewing && <div className={styles.diffMode}><DiffModeSwitch /></div>}
                {reviewing && review.items.filter((item) => item.kind !== 'block').map((item) => (
                    <ReviewCard key={item.id} item={item} verdict={review.verdicts[item.id]} currentBlocks={chapter.blocks}
                        onDecide={(verdict) => review.decide(item.id, verdict)} />
                ))}
                {editMode && <p className={suggestionStyles.banner}>Режим правок: торкніться абзацу, щоб його виправити.</p>}
                <Blocks blocks={chapter.blocks} highlight={find} overlay={mine.overlay} replace={inlineEdit}
                    after={reviewing ? (blockId) => review.items.filter((item) => item.kind === 'block' && item.blockId === blockId).map((item) => (
                        <ReviewCard key={item.id} item={item} verdict={review.verdicts[item.id]} onDecide={(verdict) => review.decide(item.id, verdict)} />
                    )) : undefined} />
                {mine.drafts > 0 && (
                    <div className={suggestionStyles.banner}>
                        <span>Ненадісланих правок: {mine.drafts}</span>
                        <Button onPress={() => mine.submit.mutate()} pending={mine.submit.isPending}>Надіслати</Button>
                    </div>
                )}
                {mine.submit.isSuccess && mine.drafts === 0 && <Notice tone="success">Правки надіслано команді. Дякуємо!</Notice>}
                {!me && (
                    <p className={styles.finished}>
                        <Link to="/login" search={{ next: `/n/${chapter.novelSlug}/${chapter.number}` }}>Увійдіть</Link>, щоб запропонувати правку.
                    </p>
                )}
                {me && !chapter.teamRole && (
                    <p className={styles.finished}>
                        <Link to="/n/$slug/$number/propose" params={{ slug: chapter.novelSlug, number: String(chapter.number) }} search={search}>
                            Запропонувати зміни в усій главі
                        </Link>
                    </p>
                )}
                {chapter.teamRole && (
                    <p className={styles.finished}>
                        <Link to="/studio/$editionId/chapters/$number" params={{ editionId: String(chapter.edition.editionId), number: String(chapter.number) }}>
                            Редагувати главу
                        </Link>
                    </p>
                )}
                <nav className={styles.end} aria-label="Інші глави">
                    {chapter.next ? (
                        <Link {...chapterLink(chapter.next)} className={styles.nextButton}>Наступна глава →</Link>
                    ) : chapter.continuation ? (
                        <Link to="/n/$slug/$number" params={{ slug: chapter.novelSlug, number: String(chapter.continuation.firstNumber) }}
                            search={{ t: chapter.continuation.teamHandle }} className={styles.nextButton}>
                            Продовження від ${chapter.continuation.teamHandle} — глава {chapter.continuation.firstNumber} →
                        </Link>
                    ) : (
                        <p className={styles.finished}>Це остання перекладена глава. Нові зʼявляться на головній і у «Вхідних».</p>
                    )}
                </nav>
                {!chapter.teamRole && <ReportEdition editionId={chapter.edition.editionId} chapterLabel={chapter.label ?? String(chapter.number)} chapterNumber={chapter.number} />}
            </article>
            )}

            {!reviewing && (
                <SelectionBar onWho={(text, blockId) => setAsking({ text, blockId })}
                    {...(team$ ? { onLink: (text: string, blockId: string) => setLinking({ text, blockId }) } : {})}
                    {...(me ? {
                        onEdit: setEditing, onReplace: setReplacing,
                        onQuote: (blockId: string, text: string) => { setQuote({ blockId, text }); setTalking(true); },
                    } : {})} />
            )}
            {asking && (
                <WhoSheet slug={chapter.novelSlug} team={team} chapter={chapter.number} text={asking.text} blockId={asking.blockId}
                    editionId={team$ ? chapter.edition.editionId : null} onClose={() => setAsking(null)} />
            )}
            {linking && (
                <LinkSheet slug={chapter.novelSlug} editionId={chapter.edition.editionId} chapter={chapter.number} text={linking.text}
                    blockId={linking.blockId} onClose={() => setLinking(null)} />
            )}
            {me && !editMode && !paged && !editing && <HoverPencil onEdit={setEditing} />}
            <Sheet open={talking} onClose={() => setTalking(false)} title="Обговорення глави" tall>
                <Discussion editionId={chapter.edition.editionId} chapter={chapter.number} focus={focus ? Number(focus) : undefined}
                    quote={quote} onDropQuote={() => setQuote(null)} goTo={(blockId) => { setTalking(false); showBlock(blockId); }} />
            </Sheet>
            {replacing !== null && <ReplaceSheet chapter={chapter} find={replacing} onClose={() => setReplacing(null)} onSaved={mine.refresh} />}

            {reviewing ? (
                <footer className={styles.bottom}>
                    {review.apply.isError && <Notice tone="error">{review.apply.error.message}</Notice>}
                    <ReviewSteps count={review.items.length} />
                    <div className={styles.buttons}>
                        <Button variant="secondary" onPress={() => setReviewing(false)}>Закрити</Button>
                        <Button variant="secondary" onPress={review.acceptAll}>Прийняти всі</Button>
                        {/* Done reviewing means done with this chapter: back to the translation's page in the Studio. */}
                        <Button onPress={() => review.apply.mutate(undefined, {
                            onSuccess: () => {
                                setReviewing(false);
                                void navigate({ to: '/studio/$editionId', params: { editionId: String(chapter.edition.editionId) } });
                            },
                        })}
                            pending={review.apply.isPending} isDisabled={Object.keys(review.verdicts).length === 0}>
                            Застосувати ({Object.keys(review.verdicts).length})
                        </Button>
                    </div>
                </footer>
            ) : (
            <footer className={`${styles.bottom} ${barsVisible ? '' : styles.hiddenBottom}`}>
                <div className={styles.percent}>
                    {[view.clock && <Clock key="clock" />, paged ? `сторінка ${pages.page + 1} з ${pages.pages}` : view.percent && `${Math.round(progress * 100)}%`].filter(Boolean).reduce<React.ReactNode[]>(
                        (parts, part, index) => (index > 0 ? [...parts, ' · ', part] : [part]), [])}
                </div>
                <div className={styles.buttons}>
                    {chapter.previous ? (
                        <Link {...chapterLink(chapter.previous)} className={styles.navButton} aria-label="Попередня глава"><ChevronLeft size={18} aria-hidden /></Link>
                    ) : <span className={`${styles.navButton} ${styles.disabled}`} aria-hidden><ChevronLeft size={18} /></span>}
                    {chapter.next ? (
                        <Link {...chapterLink(chapter.next)} className={`${styles.navButton} ${styles.primary}`} aria-label="Наступна глава">Далі <ChevronRight size={18} aria-hidden /></Link>
                    ) : <span className={`${styles.navButton} ${styles.disabled}`} aria-hidden><ChevronRight size={18} /></span>}
                    <button type="button" className={styles.navButton} aria-label={`Обговорення глави, коментарів: ${comments}`}
                        onClick={() => setTalking(true)}>
                        <MessageCircle size={18} aria-hidden />{comments > 0 ? ` ${comments}` : ''}
                    </button>
                    {me && (
                        <button type="button" className={`${styles.navButton} ${editMode ? styles.primary : ''}`} aria-pressed={editMode}
                            aria-label="Режим правок" onClick={() => setEditMode(!editMode)}>
                            ✎{mine.drafts > 0 ? ` ${mine.drafts}` : ''}
                        </button>
                    )}
                </div>
            </footer>
            )}
        </div>
    );
}

/**
 * «Хто це»: the names and terms of the selection, with what the novel has told about them by
 * this chapter — so a reader never meets a spoiler here.
 */
function WhoSheet({ slug, team, chapter, text, blockId, editionId, onClose }: {
    slug: string; team: string | undefined; chapter: number; text: string; blockId: string;
    /** The team's translation: each card can be untied from these words here. */
    editionId: number | null;
    onClose: () => void;
}) {
    const client = useQueryClient();
    const cards = useQuery({ queryKey: ['who', slug, team ?? '', chapter, text, blockId], queryFn: () => readingApi.who(slug, team, chapter, text, blockId) });
    const untie = useMutation({ meta: { errorToast: true },
        mutationFn: (entryId: number) => autotranslateApi.linkWord(editionId!, chapter, { blockId, entryId, form: text.trim().slice(0, 100), linked: false }),
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: ['who', slug] });
            void client.invalidateQueries({ queryKey: ['hints', slug] });
        },
    });
    return (
        <Sheet open onClose={onClose} title={whoTitle(cards.data)} peek>
            <div className={styles.who}>
                {cards.isPending && <p className={styles.whoMuted}>Шукаємо в словнику…</p>}
                {cards.isError && <Notice tone="error">{cards.error.message}</Notice>}
                {cards.data?.length === 0 && (
                    <p className={styles.whoMuted}>«{text.length > 60 ? `${text.slice(0, 60)}…` : text}» — у словнику цього перекладу такого немає.</p>
                )}
                {cards.data?.map((card) => (
                    <section key={card.id} className={styles.whoCard}>
                        <h3>{card.ukrainian} <span className={styles.whoMuted}>· {WHO_KINDS[card.kind] ?? 'інше'}</span></h3>
                        {card.facts.length > 0
                            ? <ul>{card.facts.map((fact, index) => <li key={index}>{fact}</li>)}</ul>
                            : <p className={styles.whoMuted}>Поки що про це нічого не відомо.</p>}
                        {editionId !== null && (
                            <button type="button" className={styles.whoAction} onClick={() => untie.mutate(card.id)} disabled={untie.isPending}>
                                Тут «{text.trim().slice(0, 30)}» не про це — відв’язати
                            </button>
                        )}
                    </section>
                ))}
            </div>
        </Sheet>
    );
}

/**
 * «🔗 До словника» (the team): the selected words of a paragraph tied to a glossary entry, so the
 * lamp marks them and «Хто це» tells about them here — a word that came with an edit, say.
 */
function LinkSheet({ slug, editionId, chapter, text, blockId, onClose }: {
    slug: string; editionId: number; chapter: number; text: string; blockId: string; onClose: () => void;
}) {
    const client = useQueryClient();
    const [query, setQuery] = useState(text.trim().split(/\s+/)[0] ?? '');
    const found = useQuery({
        queryKey: ['glossary', editionId, 'link', query],
        queryFn: () => autotranslateApi.glossary(editionId, { q: query.trim(), sort: 'alpha', page: 1 }),
        enabled: query.trim().length > 0,
    });
    const tie = useMutation({
        mutationFn: (entryId: number) => autotranslateApi.linkWord(editionId, chapter, { blockId, entryId, form: text.trim(), linked: true }),
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: ['who', slug] });
            void client.invalidateQueries({ queryKey: ['hints', slug] });
            onClose();
        },
    });
    return (
        <Sheet open onClose={onClose} title={`«${text.trim().slice(0, 40)}» — до словника`} peek>
            <div className={styles.who}>
                <TextInput label="Запис словника" value={query} onChange={setQuery} hint="Пошук за українською формою." />
                {found.isError && <Notice tone="error">{found.error.message}</Notice>}
                {found.data?.items.length === 0 && <p className={styles.whoMuted}>Нічого не знайшли.</p>}
                {found.data?.items.map((entry) => (
                    <button key={entry.id} type="button" className={styles.whoPick} onClick={() => tie.mutate(entry.id)} disabled={tie.isPending}>
                        <b>{entry.ukrainian}</b> <span className={styles.whoMuted}>· {WHO_KINDS[entry.kind] ?? 'інше'}{entry.note ? ` · ${entry.note.slice(0, 80)}` : ''}</span>
                    </button>
                ))}
                {tie.isError && <Notice tone="error">{tie.error.message}</Notice>}
            </div>
        </Sheet>
    );
}

/** «Хто це» for people, «Що це» for places, terms and things, both when the selection names both. */
function whoTitle(cards: { kind: string }[] | undefined): string {
    if (!cards || cards.length === 0) return 'Хто чи що це';
    const people = cards.filter((card) => card.kind === 'character').length;
    return people === cards.length ? 'Хто це' : people === 0 ? 'Що це' : 'Хто й що це';
}

const WHO_KINDS: Record<string, string> = {
    character: 'персонаж', place: 'місце', organization: 'організація', term: 'термін', other: 'інше',
};

/** «Аа»: the reader's own look — the same form as in «Налаштування», every change shows at once. */
function TextSettings() {
    const [open, setOpen] = useState(false);
    return (
        <>
            <button type="button" className={styles.icon} aria-label="Вигляд читалки" onClick={() => setOpen(true)}><Type size={22} aria-hidden /></button>
            <Sheet open={open} onClose={() => setOpen(false)} title="Вигляд читалки" peek>
                <ReaderLookForm />
                <Button variant="secondary" wide onPress={() => setOpen(false)}>Готово</Button>
            </Sheet>
        </>
    );
}

/** The time in the bar, for those who read late. */
function Clock() {
    const [now, setNow] = useState(() => new Date());
    useEffect(() => {
        const timer = setInterval(() => setNow(new Date()), 20_000);
        return () => clearInterval(timer);
    }, []);
    return <span>{now.toLocaleTimeString('uk-UA', { hour: '2-digit', minute: '2-digit' })}</span>;
}

/** Keeps the screen on while the chapter is open, where the browser allows it. */
function useWakeLock(on: boolean) {
    useEffect(() => {
        if (!on || !('wakeLock' in navigator)) return;
        let lock: WakeLockSentinel | null = null;
        let gone = false;
        const take = () => {
            if (document.visibilityState !== 'visible') return;
            navigator.wakeLock.request('screen').then((sentinel) => {
                if (gone) void sentinel.release();
                else lock = sentinel;
            }, () => { /* Refused (battery saver): the screen dims as usual. */ });
        };
        take();
        document.addEventListener('visibilitychange', take);
        return () => {
            gone = true;
            document.removeEventListener('visibilitychange', take);
            void lock?.release();
        };
    }, [on]);
}

function scrollable() {
    return Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
}

function currentPosition() {
    return Math.min(1, Math.max(0, window.scrollY / scrollable()));
}

/** Scrolls to a paragraph and lights it up for a moment (a quote's «До місця в главі»). */
function showBlock(blockId: string) {
    const block = document.querySelector(`[data-block-id="${CSS.escape(blockId)}"]`);
    if (!block) return;
    requestAnimationFrame(() => {
        block.scrollIntoView({ block: 'center' });
        block.classList.add(styles.flash!);
        window.setTimeout(() => block.classList.remove(styles.flash!), 2200);
    });
}

/** «‹ Правка 2 з 5 ›»: goes from one suggestion card to the next instead of scrolling by hand. */
function ReviewSteps({ count }: { count: number }) {
    const [at, setAt] = useState(0);
    const cards = () => [...document.querySelectorAll<HTMLElement>('[data-review-card]')];
    const middle = () => window.innerHeight / 2;
    const go = (card: HTMLElement | undefined) => card?.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
    useEffect(() => {
        const update = () => setAt(cards().filter((card) => card.getBoundingClientRect().top < middle() + 10).length);
        // Entering the review goes straight to the first card.
        requestAnimationFrame(() => { go(cards()[0]); update(); });
        window.addEventListener('scroll', update, { passive: true });
        return () => window.removeEventListener('scroll', update);
    }, [count]);
    if (count === 0) return null;
    return (
        <div className={styles.reviewSteps}>
            <button type="button" aria-label="Попередня правка"
                onClick={() => go(cards().reverse().find((card) => card.getBoundingClientRect().bottom < middle() - 10))}>‹</button>
            <span>Правка {Math.max(1, at)} з {count}</span>
            <button type="button" aria-label="Наступна правка"
                onClick={() => go(cards().find((card) => card.getBoundingClientRect().top > middle() + 10))}>›</button>
        </div>
    );
}

/**
 * On a computer a pencil stands left of the paragraph under the mouse (етап 20, рішення 3): a click
 * rewrites it right there. Touch screens have «режим правок» and «Виправити абзац» instead.
 */
function HoverPencil({ onEdit }: { onEdit: (blockId: string) => void }) {
    const [at, setAt] = useState<{ id: string; top: number; left: number } | null>(null);
    useEffect(() => {
        if (!window.matchMedia?.('(hover: hover) and (pointer: fine)').matches) return;
        function over(event: globalThis.MouseEvent) {
            const target = event.target as HTMLElement | null;
            if (!target || target.closest('[data-pencil]')) return;
            const block = target.closest('article p[data-block-id], article h2[data-block-id]');
            if (!block) return;
            const rect = block.getBoundingClientRect();
            setAt({ id: block.getAttribute('data-block-id')!, top: rect.top + window.scrollY, left: rect.left + window.scrollX - 40 });
        }
        document.addEventListener('mouseover', over);
        return () => document.removeEventListener('mouseover', over);
    }, []);
    if (!at) return null;
    return createPortal(
        <button type="button" data-pencil className={styles.pencil} style={{ top: at.top, left: at.left }} aria-label="Виправити абзац"
            onClick={() => onEdit(at.id)}>
            <PenLine size={18} aria-hidden />
        </button>,
        document.body,
    );
}
