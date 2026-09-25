import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams, useRouterState, useSearch } from '@tanstack/react-router';
import { ArrowLeft, ChevronLeft, ChevronRight, List, MessageCircle, Type } from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type MouseEvent } from 'react';
import { Dialog, DialogTrigger, Button as AriaButton, Popover } from 'react-aria-components';
import { useMe } from '../../auth/me';
import { Discussion, useCommentCount } from '../../community/Discussion';
import { Blocks } from '../../reading/Blocks';
import { chapterHeading, readingApi, type ReaderChapter } from '../../reading/api';
import { localProgress, saveLocalProgress } from '../../reading/progress';
import { chapterQuery } from '../../reading/queries';
import { useReaderSize, useTheme, type Theme } from '../../reading/theme';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Sheet } from '../../ui/Sheet';
import { EditSheet, ReplaceSheet, ReviewCard, SelectionBar, useMySuggestions, useReview } from './Suggestions';
import suggestionStyles from './suggestions.module.css';
import { Segmented } from '../../ui/Segmented';
import styles from './reader.module.css';

const SAVE_EVERY_MS = 15_000;

export function ReaderPage() {
    const { slug, number } = useParams({ strict: false }) as { slug: string; number: string };
    const { t }: { t?: string } = useSearch({ strict: false });
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
    return <Reader key={`${slug}:${chapterNumber}`} chapter={chapter.data} team={t} />;
}

function Reader({ chapter, team }: { chapter: ReaderChapter; team: string | undefined }) {
    const me = useMe();
    const client = useQueryClient();
    const navigate = useNavigate();
    const [size, setSize] = useReaderSize();
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

    // Start where the reader stopped: from the server (any device), else from this browser.
    useEffect(() => {
        const local = localProgress(opened.novelSlug, opened.edition.teamHandle);
        const position = opened.savedPosition ?? (local?.number === opened.number ? local.position : 0);
        place.current = position;
        if (position > 0.02) {
            requestAnimationFrame(() => window.scrollTo(0, position * scrollable()));
        } else {
            window.scrollTo(0, 0);
        }
    }, [opened]);

    const save = useCallback((force: boolean) => {
        const position = Math.round(place.current * 1000) / 1000;
        saveLocalProgress(opened.novelSlug, opened.edition.teamHandle, { number: opened.number, position, label: opened.label ?? null });
        const now = Date.now();
        const changed = Math.abs(position - lastSaved.current.position) > 0.01;
        // Only what really reached the server counts as saved; a guest (or an account still
        // loading) keeps the place in this browser and sends it once signed in.
        if (me && changed && (force || now - lastSaved.current.at > SAVE_EVERY_MS)) {
            lastSaved.current = { at: now, position };
            // «Назад» into this chapter should restore the fresh place, not the one it was opened with.
            client.setQueryData<ReaderChapter>(['chapter', opened.novelSlug, team ?? '', opened.number],
                (cached) => (cached ? { ...cached, savedPosition: position } : cached));
            void readingApi.saveProgress(opened.edition.editionId, opened.number, position).catch(() => {
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
            setProgress(position);
            const down = y >= lastY.current;
            if (down !== runStart.current.down) {
                runStart.current = { y: lastY.current, down };
            }
            const travelled = Math.abs(y - runStart.current.y);
            if (y < 60 || position > 0.985) {
                setBarsVisible(true);
            } else if (down && travelled > 8) {
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

    useEffect(() => {
        const onKey = (event: KeyboardEvent) => {
            if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) return;
            if (event.key === 'ArrowLeft' && chapter.previous) {
                void navigate({ to: '/n/$slug/$number', params: { slug: chapter.novelSlug, number: String(chapter.previous) }, search });
            } else if (event.key === 'ArrowRight' && chapter.next) {
                void navigate({ to: '/n/$slug/$number', params: { slug: chapter.novelSlug, number: String(chapter.next) }, search });
            }
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    });

    const mine = useMySuggestions(chapter, me !== null);
    const review = useReview(chapter);
    const [editMode, setEditMode] = useState(false);
    const [editing, setEditing] = useState<string | null>(null);
    const [replacing, setReplacing] = useState<string | null>(null);
    const [reviewing, setReviewing] = useState(false);
    const editingBlock = chapter.blocks.find((block) => block.id === editing);
    // A link to a comment (#c40 from the inbox) opens the discussion at it.
    const hash = useRouterState({ select: (state) => state.location.hash });
    const focus = /^c(\d+)$/.exec(hash)?.[1];
    const [talking, setTalking] = useState(Boolean(focus));
    const comments = useCommentCount(chapter.edition.editionId, chapter.number);

    /** A tap on the text toggles the controls; in «режим правок» it opens the paragraph instead. */
    function toggleBars(event: MouseEvent) {
        const target = event.target as HTMLElement;
        if (target.closest('a, button') || window.getSelection()?.toString()) return;
        const block = target.closest('p[data-block-id], h2[data-block-id]');
        if (editMode && block) {
            setEditing(block.getAttribute('data-block-id'));
            return;
        }
        setBarsVisible((visible) => !visible);
    }

    const chapterLink = (target: number) => ({
        to: '/n/$slug/$number' as const, params: { slug: chapter.novelSlug, number: String(target) }, search,
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
                <TextSettings size={size} setSize={setSize} />
                <Link to="/n/$slug" params={{ slug: chapter.novelSlug }} search={search} hash="chapters" className={styles.icon} aria-label="Зміст">
                    <List size={22} aria-hidden />
                </Link>
            </header>

            <article className={styles.text} style={{ fontSize: size }} onClick={toggleBars}>
                <h1 className={styles.chapterTitle}>{chapterHeading(chapter)}</h1>
                {review.items.length > 0 && !reviewing && (
                    <div className={suggestionStyles.banner}>
                        <span>Правок на перевірку: {review.items.length}</span>
                        <Button variant="secondary" onPress={() => setReviewing(true)}>Перевірити</Button>
                    </div>
                )}
                {reviewing && review.items.filter((item) => item.kind !== 'block').map((item) => (
                    <ReviewCard key={item.id} item={item} verdict={review.verdicts[item.id]} currentBlocks={chapter.blocks}
                        onDecide={(verdict) => review.decide(item.id, verdict)} />
                ))}
                {editMode && <p className={suggestionStyles.banner}>Режим правок: торкніться абзацу, щоб його виправити.</p>}
                <Blocks blocks={chapter.blocks} overlay={mine.overlay}
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
            </article>

            {me && !reviewing && <SelectionBar onEdit={setEditing} onReplace={setReplacing} />}
            {editingBlock && (
                <EditSheet chapter={chapter} block={editingBlock} onClose={() => setEditing(null)} onSaved={mine.refresh}
                    existing={mine.items.find((item) => item.kind === 'block' && item.blockId === editingBlock.id && item.state === 'draft')} />
            )}
            <Sheet open={talking} onClose={() => setTalking(false)} title="Обговорення глави" tall>
                <Discussion editionId={chapter.edition.editionId} chapter={chapter.number} focus={focus ? Number(focus) : undefined} />
            </Sheet>
            {replacing !== null && <ReplaceSheet chapter={chapter} find={replacing} onClose={() => setReplacing(null)} onSaved={mine.refresh} />}

            {reviewing ? (
                <footer className={styles.bottom}>
                    {review.apply.isError && <Notice tone="error">{review.apply.error.message}</Notice>}
                    <div className={styles.buttons}>
                        <Button variant="secondary" onPress={() => setReviewing(false)}>Закрити</Button>
                        <Button variant="secondary" onPress={review.acceptAll}>Прийняти всі</Button>
                        <Button onPress={() => review.apply.mutate(undefined, { onSuccess: () => setReviewing(false) })}
                            pending={review.apply.isPending} isDisabled={Object.keys(review.verdicts).length === 0}>
                            Застосувати ({Object.keys(review.verdicts).length})
                        </Button>
                    </div>
                </footer>
            ) : (
            <footer className={`${styles.bottom} ${barsVisible ? '' : styles.hiddenBottom}`}>
                <div className={styles.percent}>{Math.round(progress * 100)}%</div>
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

function TextSettings({ size, setSize }: { size: number; setSize: (size: number) => void }) {
    const [theme, setTheme] = useTheme();
    return (
        <DialogTrigger>
            <AriaButton className={styles.icon} aria-label="Розмір тексту й тема"><Type size={22} aria-hidden /></AriaButton>
            <Popover className={styles.settings} placement="bottom end">
                <Dialog className={styles.settingsDialog} aria-label="Розмір тексту й тема">
                    <div className={styles.sizeRow}>
                        <AriaButton className={styles.sizeButton} onPress={() => setSize(size - 1)} isDisabled={size <= 15} aria-label="Менший текст">А−</AriaButton>
                        <span aria-live="polite">{size}</span>
                        <AriaButton className={styles.sizeButton} onPress={() => setSize(size + 1)} isDisabled={size >= 26} aria-label="Більший текст">А+</AriaButton>
                    </div>
                    <Segmented<Theme> label="Тема" value={theme} onChange={setTheme}
                        options={[{ value: 'dark', label: 'Темна' }, { value: 'black', label: 'Чорна' }, { value: 'light', label: 'Світла' }]} />
                </Dialog>
            </Popover>
        </DialogTrigger>
    );
}

function scrollable() {
    return Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
}

function currentPosition() {
    return Math.min(1, Math.max(0, window.scrollY / scrollable()));
}
