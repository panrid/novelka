import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useParams, useSearch } from '@tanstack/react-router';
import { ArrowLeft } from 'lucide-react';
import { useRef, useState } from 'react';
import { chapterQuery } from '../../reading/queries';
import { suggestionApi } from '../../reading/suggestions';
import type { StudioBlock } from '../../studio/api';
import { TextEditor, type EditorHandle } from '../../studio/TextEditor';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import styles from '../studio/editor.module.css';

/**
 * The full editor for a reader outside the team (рішення 6): the result is not published,
 * it becomes a suggestion for the whole chapter in the reader's batch.
 */
export function ProposeChapterPage() {
    const { slug, number } = useParams({ strict: false }) as { slug: string; number: string };
    const { t }: { t?: string } = useSearch({ strict: false });
    const chapter = useQuery(chapterQuery(slug, Number(number), t));
    if (chapter.isError) return <div className={styles.page}><Notice tone="error">{chapter.error.message}</Notice></div>;
    if (!chapter.data) return <p className={styles.status}>Відкриваємо главу…</p>;
    const data = chapter.data;
    return <Propose key={data.number} slug={slug} team={t} editionId={data.edition.editionId} number={data.number}
        title={data.title} blocks={data.blocks.map((block) => ({ id: block.id, type: block.type, content: block.content, imageUrl: block.imageUrl }))} />;
}

function Propose({ slug, team, editionId, number, title: startTitle, blocks: startBlocks }: {
    slug: string; team: string | undefined; editionId: number; number: number; title: string; blocks: StudioBlock[];
}) {
    const [title, setTitle] = useState(startTitle);
    const [blocks, setBlocks] = useState(startBlocks);
    const [note, setNote] = useState('');
    const editor = useRef<EditorHandle>(null);
    const save = useMutation({ mutationFn: () => suggestionApi.chapter(editionId, number, title, editor.current?.read() ?? blocks, note) });
    return (
        <div className={styles.page}>
            <header className={styles.top}>
                <Link to="/n/$slug/$number" params={{ slug, number: String(number) }} search={team ? { t: team } : {}} className={styles.icon} aria-label="До читалки">
                    <ArrowLeft size={22} aria-hidden />
                </Link>
                <div className={styles.where}>
                    <b>Правка глави {number}</b>
                    <div className={styles.state}>зміни піде до команди на перевірку</div>
                </div>
                <Button onPress={() => save.mutate()} pending={save.isPending} pendingLabel="Зберігаємо…">Додати до пакета</Button>
            </header>
            <div className={styles.body}>
                {save.isSuccess && <Notice tone="success">Додано до пакета. Надіслати його можна в читалці.</Notice>}
                {save.isError && <Notice tone="error">{save.error.message}</Notice>}
                <input className={styles.titleInput} aria-label="Назва глави" value={title} onChange={(event) => setTitle(event.target.value)} />
                <TextInput label="Пояснення для команди (необовʼязково)" value={note} onChange={setNote} />
                <TextEditor handle={editor} mode="chapter" blocks={blocks} onChange={setBlocks} label="Текст глави" />
            </div>
        </div>
    );
}
