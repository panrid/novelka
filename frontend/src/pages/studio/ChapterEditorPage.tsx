import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from '@tanstack/react-router';
import { ArrowLeft, BookOpen, History, Languages, Trash2 } from '../../ui/icons';
import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { GlossarySheet, type GlossaryPick } from './GlossarySheet';
import { VariantsSheet } from './VariantsSheet';
import { ProofreadSheet } from './ProofreadSheet';
import { useEditionOverview } from './EditionShell';
import { autotranslateApi, KIND_LABELS } from '../../studio/autotranslate';
import { IllustrateSheet } from './IllustrateSheet';
import { studioApi, type EditorView, type StudioBlock, type StudioChapter } from '../../studio/api';
import { TextEditor, type EditorHandle } from '../../studio/TextEditor';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Loading } from '../../ui/Loading';
import { relativeTime } from '../../lib/dates';
import { chaptersWord } from '../../reading/api';
import { TRASH_NOTE } from './ChaptersPage';
import { BookView } from './BookView';
import styles from './editor.module.css';
import { askConfirm } from '../../ui/ask';
import { useCanDraw, useCanRun } from '../../ledger/api';
import { useWide } from '../../lib/useWide';

const AUTOSAVE_MS = 1500;

/** The Studio shows chapters 20 to a page (StudioController.STUDIO_CHAPTERS). */
const CHAPTERS_PAGE = 20;

export function ChapterEditorPage() {
    const { editionId, number } = useParams({ strict: false }) as { editionId: string; number: string };
    const id = Number(editionId);
    const chapter = Number(number);
    const view = useQuery({ queryKey: ['studio-editor', id, chapter], queryFn: () => studioApi.editor(id, chapter), staleTime: Infinity });

    if (view.isError) return <div className={styles.page}><Notice tone="error">{view.error.message}</Notice></div>;
    if (!view.data) return <div className={styles.page}><Loading shape="lines" onRetry={() => void view.refetch()} /></div>;
    return <Editor key={`${id}:${chapter}:${view.dataUpdatedAt}`} editionId={id} view={view.data} />;
}

type Save = 'idle' | 'saving' | 'saved' | 'error';

/** Which side columns of the workspace are open (новий дизайн, рішення 9); the original is remembered on its own. */
type Panels = { chapters: boolean; side: boolean };

/**
 * «Робоче місце» of a chapter (новий дизайн, Workspace.dc.html): on a computer — the chapters on the
 * left, «№ · оригінал · переклад» on one grid in the middle, the chapter's glossary on the right; the
 * switches «Глави · Оригінал · Словник» hide a column and the translation takes its room. «Як у
 * книжці» shows the translation alone with the state of each paragraph. On a phone — the text, and
 * the original of the paragraph with the cursor above the formatting bar.
 */
function Editor({ editionId, view }: { editionId: number; view: EditorView }) {
    const client = useQueryClient();
    // Opening a chapter with my draft continues the draft; otherwise the published text.
    const [title, setTitle] = useState(view.draft?.title || view.title);
    const canDraw = useCanDraw();
    const navigate = useNavigate();
    const removeChapter = useMutation({
        mutationFn: () => studioApi.deleteChapter(editionId, view.number),
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: ['studio-chapters', editionId] });
            void navigate({ to: '/studio/$editionId', params: { editionId: String(editionId) } });
        },
        onError: (error: Error) => window.alert(error.message),
    });
    // Drawing is for the site owner at launch (рішення 21).
    // «До словника» (етап 19): for translators who see AI, in chapters with an original.
    const [picked, setPicked] = useState<GlossaryPick | null>(null);
    // «Інший варіант» and «Перевірити главу» (етап 19, п. 5): for the team, for those who see AI.
    const [rewording, setRewording] = useState<{ blockId: string | null; paragraph: string } | null>(null);
    const [proofing, setProofing] = useState<{ blockId: string | null; paragraph: string } | null>(null);
    const edition = useEditionOverview(editionId).data;
    const check = useMutation({ mutationFn: () => autotranslateApi.check(editionId, view.number) });
    const canRun = useCanRun();
    const [drawing, setDrawing] = useState<{ fragment: string; insert: (picture: { id: number; url: string }) => void } | null>(null);
    const editor = useRef<EditorHandle>(null);
    const [blocks, setBlocks] = useState<StudioBlock[]>(view.draft?.blocks ?? view.blocks);
    const [base, setBase] = useState<number | null>(view.draft?.baseRevisionId ?? view.revisionId);
    const [save, setSave] = useState<Save>(view.draft ? 'saved' : 'idle');
    const [dirty, setDirty] = useState(false);
    const [published, setPublished] = useState(false);
    const [live, setLive] = useState(view.published);
    // Something readers do not see yet: my draft, fresh typing, or a chapter never published.
    const [unpublished, setUnpublished] = useState(view.draft !== null || !view.published);
    const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
    const outdated = view.draft !== null && view.draft.baseRevisionId !== view.revisionId;

    // Autosave: a draft is stored a moment after typing stops.
    useEffect(() => {
        if (!dirty) return undefined;
        clearTimeout(timer.current);
        timer.current = setTimeout(() => {
            setSave('saving');
            studioApi.saveDraft(editionId, view.number, { title, blocks, baseRevisionId: base })
                .then(() => { setSave('saved'); setDirty(false); })
                .catch(() => setSave('error'));
        }, AUTOSAVE_MS);
        return () => clearTimeout(timer.current);
    }, [blocks, title, dirty, editionId, view.number, base]);

    // Closing the tab with unsaved typing asks first.
    useEffect(() => {
        const warn = (event: BeforeUnloadEvent) => {
            if (dirty) event.preventDefault();
        };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [dirty]);

    const publish = useMutation({
        mutationFn: () => studioApi.publish(editionId, view.number, { title, blocks: editor.current?.read() ?? blocks, baseRevisionId: base }),
        onSuccess: ({ revisionId }) => {
            clearTimeout(timer.current);
            setBase(revisionId);
            setDirty(false);
            setSave('idle');
            setPublished(true);
            setLive(true);
            setUnpublished(false);
            void client.invalidateQueries({ queryKey: ['studio-chapters', editionId] });
            void client.invalidateQueries({ queryKey: ['studio-edition', editionId] });
        },
    });
    const discard = useMutation({ meta: { errorToast: true },
        mutationFn: () => studioApi.discardDraft(editionId, view.number),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['studio-editor', editionId, view.number] }),
    });

    const changed = (update: () => void) => {
        update();
        setDirty(true);
        setPublished(false);
        setUnpublished(true);
    };
    const wide = useWide();
    // On a computer the original is open unless the person closed it; on a phone it is asked for.
    const [chosenOriginal, setChosenOriginal] = useState<boolean | null>(rememberedOriginal);
    const showOriginal = view.hasOriginal && (chosenOriginal ?? wide);
    const [cursor, setCursor] = useState<string | null>(null);
    const [panels, setPanels] = useState<Panels>(rememberedPanels);
    const [book, setBook] = useState(false);
    const toggleOriginal = () => {
        setChosenOriginal(!showOriginal);
        remember(ORIGINAL_KEY, showOriginal ? '0' : '1');
    };
    const togglePanel = (name: keyof Panels) => {
        const next = { ...panels, [name]: !panels[name] };
        setPanels(next);
        remember(PANELS_KEY, JSON.stringify(next));
    };
    const original = useQuery({
        queryKey: ['studio-original', editionId, view.number],
        queryFn: () => studioApi.original(editionId, view.number),
        staleTime: Infinity,
        enabled: view.hasOriginal && wide && showOriginal,
    });
    const chapters = useQuery({
        queryKey: ['studio-chapters', editionId, 'workspace', chapterPage(view.number)],
        queryFn: () => studioApi.chapters(editionId, { order: 'asc', page: chapterPage(view.number) }),
        enabled: wide && (panels.chapters || panels.side),
    });
    const conflict = publish.error instanceof ApiError && publish.error.reason === 'chapter-changed';
    const params = { editionId: String(editionId) };
    const name = view.label === '' ? (title.trim() || 'Без номера') : `Глава ${view.label ?? view.number}`;
    const saveState = save === 'saving' ? 'зберігаємо…' : save === 'saved' ? 'чернетку збережено' : save === 'error' ? 'не вдалося зберегти, спробуємо ще'
        : live ? 'опубліковано' : 'ще не опубліковано';
    const gridOriginal = wide && showOriginal ? (original.data?.blocks ?? null) : null;
    const untranslated = original.data ? original.data.blocks.filter((block) => block.type !== 'separator' && !blocks.some((item) => item.id === block.id)).length : null;
    const here = chapters.data?.items.find((item) => item.number === view.number);
    const glossaryShown = Boolean(edition) && canRun && (view.hasOriginal || edition?.kind === 'machine' || edition?.kind === 'mixed');
    const checkButton = live && canRun && view.hasOriginal && (
        <Button variant="secondary" onPress={() => check.mutate()} pending={check.isPending} pendingLabel="ШІ вичитує главу…">
            Вичитати главу з ШІ
        </Button>
    );
    const publishButton = (
        <Button onPress={() => publish.mutate()} pending={publish.isPending} pendingLabel="Публікуємо…" isDisabled={!unpublished || blocks.length === 0 || !title.trim()}>
            Опублікувати
        </Button>
    );
    const publishNotices = (
        <>
            {published && <Notice tone="success">Опубліковано. Читачі вже бачать нову версію.</Notice>}
            {publish.isError && (
                <Notice tone="error">
                    {publish.error.message}
                    {conflict && <> <button type="button" className={styles.link} onClick={() => void client.invalidateQueries({ queryKey: ['studio-editor', editionId, view.number] })}>Відкрити главу знову</button></>}
                </Notice>
            )}
        </>
    );

    if (book) {
        return (
            <BookView editionId={editionId} number={view.number} heading={`${edition?.title ? `${edition.title} · ` : ''}${name.toLowerCase()}`}
                title={title} blocks={blocks} hasOriginal={view.hasOriginal} canRun={canRun} notices={publishNotices} publishButton={publishButton}
                onBack={() => setBook(false)}
                onReplace={(index, text) => changed(() => setBlocks((now) => now.map((block, at) => at === index ? { ...block, content: [{ text, marks: [] }] } : block)))} />
        );
    }
    const openBook = () => {
        const now = editor.current?.read();
        if (now) setBlocks(now);
        setBook(true);
    };

    return (
        <div className={`${styles.page} ${wide ? styles.pageWide : ''}`}>
            <header className={styles.top}>
                <Link to="/studio/$editionId/chapters" params={params} className={styles.icon} aria-label="До глав"><ArrowLeft size={22} aria-hidden /></Link>
                <div className={styles.where}>
                    {wide && edition && (
                        <span className={styles.crumbs}>
                            <Link to="/studio/$editionId" params={params}>{edition.title}</Link>
                            <span aria-hidden> / </span>
                        </span>
                    )}
                    {/* The number readers see; a chapter without one (a prologue) goes by its title. */}
                    <b>{wide ? name : view.label === '' ? name : `Гл. ${view.label ?? view.number}`}</b>
                    <div className={`${styles.state} ${wide && unpublished ? styles.stateDraft : ''}`} aria-live="polite">{saveState}</div>
                </div>
                {wide ? (
                    <div role="group" aria-label="Що показувати" className={styles.panels}>
                        <PanelSwitch label="Глави" title="Показати чи сховати список глав" on={panels.chapters} onToggle={() => togglePanel('chapters')} />
                        {view.hasOriginal && <PanelSwitch label="Оригінал" title="Показати чи сховати колонку оригіналу" on={showOriginal} onToggle={toggleOriginal} />}
                        <PanelSwitch label="Словник" title="Показати чи сховати словник глави" on={panels.side} onToggle={() => togglePanel('side')} />
                    </div>
                ) : view.hasOriginal && (
                    <button type="button" className={`${styles.icon} ${showOriginal ? styles.iconOn : ''}`} aria-label="Оригінал"
                        aria-pressed={showOriginal} onClick={toggleOriginal}><Languages size={20} aria-hidden /></button>
                )}
                <button type="button" className={wide ? styles.textButton : styles.icon} aria-label={wide ? undefined : 'Як у книжці'} onClick={openBook}>
                    {wide ? 'Як у книжці' : <BookOpen size={20} aria-hidden />}
                </button>
                {live && (
                    <Link to="/studio/$editionId/chapters/$number/history" params={{ ...params, number: String(view.number) }}
                        className={wide ? styles.textButton : styles.icon} aria-label="Історія змін">
                        {wide ? 'Історія' : <History size={20} aria-hidden />}
                    </Link>
                )}
                {wide && checkButton}
                {view.mayAddPictures && (
                    <button type="button" className={styles.icon} aria-label="Видалити главу" onClick={() => void askConfirm({
                        title: `Видалити главу ${view.number}?`,
                        text: TRASH_NOTE,
                        confirmLabel: 'Видалити', danger: true,
                    }).then((yes) => { if (yes) removeChapter.mutate(); })}><Trash2 size={20} aria-hidden /></button>
                )}
                {publishButton}
            </header>

            <div className={wide ? styles.desk : undefined}>
                {wide && panels.chapters && (
                    <ChapterList editionId={editionId} number={view.number} chapters={chapters.data?.items ?? null} hasMore={chapters.data?.hasMore ?? false}
                        total={chapters.data?.total ?? null} mayPaste={view.mayAddPictures} />
                )}
                <div className={`${styles.body} ${wide ? styles.bodyWide : ''} ${showOriginal && !wide ? styles.bodyWithOriginal : ''}`}>
                    {view.draft && !published && (
                        <Notice tone="info">
                            Відкрито вашу чернетку від {relativeTime(new Date(view.draft.updatedAt))}.
                            {outdated && ' Відтоді главу оновили — перевірте, щоб не повернути старий текст.'}{' '}
                            <button type="button" className={styles.link} onClick={() => discard.mutate()}>Відкинути чернетку</button>
                        </Notice>
                    )}
                    {publishNotices}
                    {wide ? (
                        <div className={`${styles.gridHead} ${gridOriginal ? styles.gridHeadWithOriginal : ''}`}>
                            <span />
                            {gridOriginal && <span className={styles.column}>Оригінал</span>}
                            <span className={styles.column}>Переклад</span>
                            <span />
                            {gridOriginal && <span className={styles.originalTitle} lang="und">{original.data?.title}</span>}
                            <div className={styles.titleRow}>
                                <LabelInput editionId={editionId} number={view.number} initial={view.label} />
                                <input className={styles.titleInput} aria-label="Назва глави" placeholder="Назва глави" value={title}
                                    onChange={(event) => changed(() => setTitle(event.target.value))} />
                            </div>
                        </div>
                    ) : (
                        <div className={styles.titleRow}>
                            <LabelInput editionId={editionId} number={view.number} initial={view.label} />
                            <input className={styles.titleInput} aria-label="Назва глави" placeholder="Назва глави" value={title}
                                onChange={(event) => changed(() => setTitle(event.target.value))} />
                        </div>
                    )}
                    {rewording && (
                        <VariantsSheet editionId={editionId} chapter={view.number} pick={rewording} onClose={() => setRewording(null)}
                            onPick={(text) => {
                                if (rewording.blockId) editor.current?.replaceBlock?.(rewording.blockId, text);
                                setRewording(null);
                            }} />
                    )}
                    {proofing && (
                        <ProofreadSheet editionId={editionId} chapter={view.number} pick={proofing} onClose={() => setProofing(null)}
                            onAccept={(text) => {
                                if (proofing.blockId) editor.current?.replaceBlock?.(proofing.blockId, text);
                                setProofing(null);
                            }} />
                    )}
                    {check.isError && <Notice tone="error">{check.error.message}</Notice>}
                    {check.data && (
                        <Notice tone={check.data.proposed > 0 ? 'success' : 'info'}>
                            {check.data.proposed > 0 ? <>ШІ пропонує правок: {check.data.proposed}. Приймайте чи відхиляйте їх по одній{' '}
                                {edition && <Link to="/n/$slug/$number" params={{ slug: edition.novelSlug, number: String(view.number) }}
                                    search={{ t: edition.teamHandle, look: true }}>у читалці</Link>}.</>
                                : 'ШІ не знайшов, що виправити в цій главі.'}
                        </Notice>
                    )}
                    {!wide && checkButton}
                    {picked && <GlossarySheet editionId={editionId} chapter={view.number} pick={picked} onClose={() => setPicked(null)} />}
                    {drawing && (
                        <IllustrateSheet editionId={editionId} chapter={view.number} fragment={drawing.fragment}
                            onInsert={drawing.insert} onClose={() => setDrawing(null)} />
                    )}
                    <TextEditor handle={editor} mode="chapter" blocks={blocks} onChange={(next) => changed(() => setBlocks(next))}
                        onIllustrate={canDraw ? (fragment, insert) => setDrawing({ fragment, insert }) : undefined}
                        onCursorBlock={setCursor}
                        onGlossary={canRun && view.hasOriginal && view.mayAddPictures ? setPicked : undefined}
                        onVariants={canRun ? setRewording : undefined}
                        onProofread={canRun && view.hasOriginal ? setProofing : undefined}
                        grid={wide ? { original: gridOriginal } : undefined}
                        mayAddPictures={view.mayAddPictures} label="Текст глави" placeholder="Почніть писати або вставте текст…" />
                </div>
                {wide && panels.side && (
                    <SidePanel editionId={editionId} number={view.number} glossary={glossaryShown} here={here ?? null}
                        untranslated={showOriginal ? untranslated : null} edition={edition ? { slug: edition.novelSlug, team: edition.teamHandle } : null} />
                )}
            </div>
            {showOriginal && !wide && <OriginalPanel editionId={editionId} number={view.number} blockId={cursor} />}
        </div>
    );
}

function PanelSwitch({ label, title, on, onToggle }: { label: string; title: string; on: boolean; onToggle: () => void }) {
    return (
        <button type="button" className={`${styles.panel} ${on ? styles.panelOn : ''}`} aria-pressed={on} title={title} onClick={onToggle}>
            <span className={styles.panelMark} aria-hidden />{label}
        </button>
    );
}

function chapterPage(number: number) {
    return Math.max(1, Math.ceil(number / CHAPTERS_PAGE));
}

function chapterState(chapter: StudioChapter): { text: string; draft: boolean } {
    const pending = chapter.pending > 0 ? ` · правок: ${chapter.pending}` : '';
    if (!chapter.published) return { text: `${chapter.hasMyDraft ? 'чернетка' : 'не опубліковано'}${pending}`, draft: true };
    return { text: `${chapter.hasMyDraft ? 'опубліковано · є чернетка' : 'опубліковано'}${pending}`, draft: chapter.hasMyDraft };
}

/** «Глави» on the left: the chapters around this one, each with its state; a click opens it here. */
function ChapterList({ editionId, number, chapters, hasMore, total, mayPaste }: {
    editionId: number; number: number; chapters: StudioChapter[] | null; hasMore: boolean; total: number | null; mayPaste: boolean;
}) {
    const params = { editionId: String(editionId) };
    return (
        <aside className={styles.sideList} aria-label="Глави">
            <div className={styles.sideHead}>
                <b>Глави</b>
                {total !== null && <span>{total} {chaptersWord(total)}</span>}
            </div>
            {chapters === null ? <Loading shape="list" /> : chapters.map((chapter) => {
                const state = chapterState(chapter);
                const current = chapter.number === number;
                return (
                    <Link key={chapter.number} to="/studio/$editionId/chapters/$number" params={{ ...params, number: String(chapter.number) }}
                        className={`${styles.chapterLink} ${current ? styles.chapterLinkOn : ''}`} aria-current={current ? 'page' : undefined}>
                        <span className={styles.chapterNumber}>{chapter.label ?? chapter.number}</span>
                        <span className={styles.chapterText}>
                            <span className={styles.chapterTitle}>{chapter.title || 'Без назви'}</span>
                            <span className={state.draft ? styles.chapterStateDraft : styles.chapterState}>{state.text}</span>
                        </span>
                    </Link>
                );
            })}
            {(hasMore || chapterPage(number) > 1) && <Link to="/studio/$editionId/chapters" params={params} className={styles.sideMore}>Усі глави</Link>}
            {mayPaste && <Link to="/studio/$editionId/originals" params={params} className={styles.sideAction}>+ Вставити оригінал</Link>}
        </aside>
    );
}

/** On the right: the words of the glossary this chapter brought, and what waits in it. */
function SidePanel({ editionId, number, glossary, here, untranslated, edition }: {
    editionId: number; number: number; glossary: boolean; here: StudioChapter | null; untranslated: number | null;
    edition: { slug: string; team: string } | null;
}) {
    const words = useQuery({
        queryKey: ['glossary', editionId, 'chapter', number],
        queryFn: () => autotranslateApi.glossary(editionId, { chapter: number, sort: 'alpha', page: 1 }),
        enabled: glossary,
    });
    const params = { editionId: String(editionId) };
    return (
        <aside className={styles.sidePanel} aria-label="Словник глави">
            {glossary && (
                <section className={styles.card}>
                    <div className={styles.sideHead}>
                        <b>Словник у главі</b>
                        <Link to="/studio/$editionId/glossary" params={params}>Весь словник</Link>
                    </div>
                    {words.isError && <p className={styles.sideHint}>{words.error.message}</p>}
                    {!words.data && !words.isError && <Loading shape="list" />}
                    {words.data && words.data.items.length === 0 && <p className={styles.sideHint}>Нових слів у цій главі немає.</p>}
                    {words.data && words.data.items.length > 0 && (
                        <ul className={styles.words}>
                            {words.data.items.slice(0, 12).map((item) => (
                                <li key={item.id} className={item.status === 'new' ? styles.wordNew : styles.word}>
                                    <b>{item.ukrainian}</b>
                                    <span className={item.status === 'new' ? styles.wordStateNew : styles.wordState}>
                                        {item.status === 'new' ? 'нове · ' : item.status === 'rejected' ? 'відхилено · ' : ''}{KIND_LABELS[item.kind].toLowerCase()}
                                        {item.note ? ` · ${item.note}` : ''}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    )}
                    <p className={styles.sideHint}>Виділіть слово в перекладі й натисніть «До словника» на панелі — побачите, з чого воно перекладено.</p>
                </section>
            )}
            <section className={styles.card}>
                <b className={styles.cardTitle}>У цій главі</b>
                {edition ? (
                    <Link to="/n/$slug/$number" params={{ slug: edition.slug, number: String(number) }} search={{ t: edition.team, look: true }}
                        className={styles.count}>
                        <span>Правки читачів</span><b>{here?.pending ?? 0}</b>
                    </Link>
                ) : <span className={styles.count}><span>Правки читачів</span><b>{here?.pending ?? 0}</b></span>}
                {untranslated !== null && <span className={styles.count}><span>Неперекладені абзаци</span><b>{untranslated}</b></span>}
            </section>
        </aside>
    );
}

const ORIGINAL_KEY = 'novelka:editor-original';
const PANELS_KEY = 'novelka:workspace-panels';

function remember(key: string, value: string) {
    try {
        localStorage.setItem(key, value);
    } catch {
        // Storage blocked: the choice just is not remembered.
    }
}

function rememberedOriginal(): boolean | null {
    try {
        const stored = localStorage.getItem(ORIGINAL_KEY);
        return stored === null ? null : stored === '1';
    } catch {
        return null;
    }
}

function rememberedPanels(): Panels {
    try {
        const stored = JSON.parse(localStorage.getItem(PANELS_KEY) ?? 'null') as Partial<Panels> | null;
        return { chapters: stored?.chapters !== false, side: stored?.side !== false };
    } catch {
        return { chapters: true, side: true };
    }
}

/**
 * The original of the paragraph the cursor is in, above the formatting bar (team only,
 * рішення 30). Paragraph ids are shared, so moving the cursor turns the original's page too.
 */
function OriginalPanel({ editionId, number, blockId }: { editionId: number; number: number; blockId: string | null }) {
    const original = useQuery({
        queryKey: ['studio-original', editionId, number],
        queryFn: () => studioApi.original(editionId, number),
        staleTime: Infinity,
    });
    const block = blockId ? original.data?.blocks.find((item) => item.id === blockId) : undefined;
    return (
        <aside className={styles.original} aria-label="Оригінал абзацу" aria-live="polite">
            {original.isError ? <span className={styles.originalHint}>{original.error.message}</span>
                : !original.data ? <Loading shape="inline" />
                : block ? (block.type === 'separator' ? <span className={styles.originalHint}>Розділювач сцен</span> : <p lang="und">{block.text}</p>)
                : blockId ? <span className={styles.originalHint}>Цього абзацу в оригіналі немає: його додали під час перекладу.</span>
                : <span className={styles.originalHint}>Поставте курсор в абзац — тут з'явиться його оригінал.</span>}
        </aside>
    );
}

/**
 * The number readers see, saved on its own when the field is left: «0», «31.1», or empty
 * for none. Untouched, it is the chapter's position.
 */
function LabelInput({ editionId, number, initial }: { editionId: number; number: number; initial: string | null }) {
    const client = useQueryClient();
    const [value, setValue] = useState(initial ?? String(number));
    const [saved, setSaved] = useState(initial ?? String(number));
    const save = useMutation({
        mutationFn: (label: string) => studioApi.setLabel(editionId, number, label === String(number) ? null : label),
        onSuccess: (_, label) => {
            setSaved(label);
            void client.invalidateQueries({ queryKey: ['studio-chapters', editionId] });
        },
    });
    return (
        <span className={styles.labelField}>
            <input className={styles.labelInput} aria-label="Номер глави на сайті" title="Номер на сайті: 0, 12, 31.1 або порожньо — без номера"
                inputMode="decimal" value={value} onChange={(event) => setValue(event.target.value)}
                onBlur={() => { if (value.trim() !== saved) save.mutate(value.trim()); }} />
            {save.isError && <span role="alert" className={styles.labelError}>{save.error.message}</span>}
        </span>
    );
}
