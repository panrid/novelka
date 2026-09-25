import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from '@tanstack/react-router';
import { ArrowLeft, History } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { studioApi, type EditorView, type StudioBlock } from '../../studio/api';
import { TextEditor } from '../../studio/TextEditor';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { relativeTime } from '../../lib/dates';
import styles from './editor.module.css';

const AUTOSAVE_MS = 1500;

export function ChapterEditorPage() {
    const { editionId, number } = useParams({ strict: false }) as { editionId: string; number: string };
    const id = Number(editionId);
    const chapter = Number(number);
    const view = useQuery({ queryKey: ['studio-editor', id, chapter], queryFn: () => studioApi.editor(id, chapter), staleTime: Infinity });

    if (view.isError) return <div className={styles.page}><Notice tone="error">{view.error.message}</Notice></div>;
    if (!view.data) return <p className={styles.status}>Відкриваємо главу…</p>;
    return <Editor key={`${id}:${chapter}:${view.dataUpdatedAt}`} editionId={id} view={view.data} />;
}

type Save = 'idle' | 'saving' | 'saved' | 'error';

function Editor({ editionId, view }: { editionId: number; view: EditorView }) {
    const client = useQueryClient();
    // Opening a chapter with my draft continues the draft; otherwise the published text.
    const [title, setTitle] = useState(view.draft?.title || view.title);
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
        mutationFn: () => studioApi.publish(editionId, view.number, { title, blocks, baseRevisionId: base }),
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
    const discard = useMutation({
        mutationFn: () => studioApi.discardDraft(editionId, view.number),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['studio-editor', editionId, view.number] }),
    });

    const changed = (update: () => void) => {
        update();
        setDirty(true);
        setPublished(false);
        setUnpublished(true);
    };
    const conflict = publish.error instanceof ApiError && publish.error.reason === 'chapter-changed';
    const params = { editionId: String(editionId) };

    return (
        <div className={styles.page}>
            <header className={styles.top}>
                <Link to="/studio/$editionId" params={params} className={styles.icon} aria-label="До публікації"><ArrowLeft size={22} aria-hidden /></Link>
                <div className={styles.where}>
                    <b>Глава {view.number}</b>
                    <div className={styles.state} aria-live="polite">
                        {save === 'saving' ? 'зберігаємо…' : save === 'saved' ? 'чернетку збережено' : save === 'error' ? 'не вдалося зберегти, спробуємо ще' : live ? 'опубліковано' : 'ще не опубліковано'}
                    </div>
                </div>
                {live && (
                    <Link to="/studio/$editionId/chapters/$number/history" params={{ ...params, number: String(view.number) }}
                        className={styles.icon} aria-label="Історія змін"><History size={20} aria-hidden /></Link>
                )}
                <Button onPress={() => publish.mutate()} pending={publish.isPending} pendingLabel="Публікуємо…" isDisabled={!unpublished || blocks.length === 0 || !title.trim()}>
                    Опублікувати
                </Button>
            </header>

            <div className={styles.body}>
                {view.draft && !published && (
                    <Notice tone="info">
                        Відкрито вашу чернетку від {relativeTime(new Date(view.draft.updatedAt))}.
                        {outdated && ' Відтоді главу оновили — перевірте, щоб не повернути старий текст.'}{' '}
                        <button type="button" className={styles.link} onClick={() => discard.mutate()}>Відкинути чернетку</button>
                    </Notice>
                )}
                {published && <Notice tone="success">Опубліковано. Читачі вже бачать нову версію.</Notice>}
                {publish.isError && (
                    <Notice tone="error">
                        {publish.error.message}
                        {conflict && <> <button type="button" className={styles.link} onClick={() => void client.invalidateQueries({ queryKey: ['studio-editor', editionId, view.number] })}>Відкрити главу знову</button></>}
                    </Notice>
                )}
                <input className={styles.titleInput} aria-label="Назва глави" placeholder="Назва глави" value={title}
                    onChange={(event) => changed(() => setTitle(event.target.value))} />
                <TextEditor mode="chapter" blocks={blocks} onChange={(next) => changed(() => setBlocks(next))}
                    mayAddPictures={view.mayAddPictures} label="Текст глави" placeholder="Почніть писати або вставте текст…" />
            </div>
        </div>
    );
}
