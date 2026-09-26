import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from '@tanstack/react-router';
import { diffWords } from 'diff';
import { useState } from 'react';
import { ORIGIN_LABELS } from '../../studio/labels';
import { chapterHeading } from '../../reading/api';
import { studioApi, type StudioBlock } from '../../studio/api';
import { Notice } from '../../ui/Notice';
import { relativeTime } from '../../lib/dates';
import { characters, paragraphs } from '../../lib/plural';
import styles from './studio.module.css';
import editor from './editor.module.css';

const text = (block: StudioBlock) => block.content.map((span) => span.text).join('');

export function HistoryPage() {
    const { editionId, number } = useParams({ strict: false }) as { editionId: string; number: string };
    const id = Number(editionId);
    const chapter = Number(number);
    const revisions = useQuery({ queryKey: ['studio-revisions', id, chapter], queryFn: () => studioApi.revisions(id, chapter) });
    const view = useQuery({ queryKey: ['studio-editor', id, chapter], queryFn: () => studioApi.editor(id, chapter), staleTime: Infinity });
    const [open, setOpen] = useState<number | null>(null);

    return (
        <section className={styles.page}>
            <Link to="/studio/$editionId/chapters/$number" params={{ editionId, number }} className={styles.muted}>‹ До редактора</Link>
            <h1 className={styles.title}>Історія: {view.data ? chapterHeading(view.data) : `глава ${chapter}`}</h1>
            {revisions.isError && <Notice tone="error">{revisions.error.message}</Notice>}
            {revisions.data?.map((revision) => (
                <div key={revision.id}>
                    <button type="button" className={styles.row} style={{ width: '100%', background: 'none', border: 0, borderBottom: '1px solid var(--line)', textAlign: 'left', cursor: 'pointer' }}
                        aria-expanded={open === revision.id} onClick={() => setOpen(open === revision.id ? null : revision.id)}>
                        <div className={styles.grow}>
                            <div>{revision.authorNick ?? 'Новелка'} · {ORIGIN_LABELS[revision.origin] ?? revision.origin}</div>
                            <div className={styles.muted}>
                                {relativeTime(new Date(revision.createdAt))} · {paragraphs(revision.blocksChanged)} · {characters(revision.charsChanged)}
                            </div>
                        </div>
                        {revision.published && <span className={styles.badge}>зараз у читачів</span>}
                    </button>
                    {open === revision.id && <RevisionDiff editionId={id} chapter={chapter} revisionId={revision.id} />}
                </div>
            ))}
        </section>
    );
}

/** What this version changed compared with the one before: by paragraph, then by word. */
function RevisionDiff({ editionId, chapter, revisionId }: { editionId: number; chapter: number; revisionId: number }) {
    const revision = useQuery({ meta: { errorToast: true }, queryKey: ['studio-revision', revisionId], queryFn: () => studioApi.revision(editionId, chapter, revisionId) });
    if (revision.isError) return <p className={styles.muted}>Не вдалося показати зміни.</p>;
    if (!revision.data) return <p className={styles.muted}>Завантажуємо…</p>;
    const before = new Map(revision.data.parentBlocks.map((block) => [block.id, block]));
    const after = new Set(revision.data.blocks.map((block) => block.id));
    const rows = revision.data.blocks.map((block) => {
        const old = before.get(block.id);
        const oldText = old ? text(old) : '';
        const newText = text(block);
        if (old && oldText === newText) return null;
        return (
            <p key={block.id} className={`${editor.diffBlock} ${editor.changed}`}>
                {diffWords(oldText, newText).map((part, index) =>
                    part.added ? <ins key={index} className={editor.added}>{part.value}</ins>
                        : part.removed ? <del key={index} className={editor.removed}>{part.value}</del>
                            : <span key={index}>{part.value}</span>)}
            </p>
        );
    });
    const removed = revision.data.parentBlocks.filter((block) => !after.has(block.id) && text(block)).map((block) => (
        <p key={`gone-${block.id}`} className={`${editor.diffBlock} ${editor.changed}`}><del className={editor.removed}>{text(block)}</del></p>
    ));
    const titleChanged = revision.data.parentTitle !== null && revision.data.parentTitle !== revision.data.title;
    const all = [...rows.filter(Boolean), ...removed];
    return (
        <div style={{ padding: '12px 0 16px 14px' }}>
            {titleChanged && <p className={styles.muted}>Назва: «{revision.data.parentTitle}» → «{revision.data.title}»</p>}
            {all.length === 0 && !titleChanged && <p className={styles.muted}>{revision.data.parentTitle === null ? 'Перша версія глави.' : 'Текст не змінився.'}</p>}
            {revision.data.parentTitle === null ? null : all}
        </div>
    );
}
