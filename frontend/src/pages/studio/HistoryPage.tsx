import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from '@tanstack/react-router';
import { DiffModeSwitch, WordDiff } from '../../ui/WordDiff';
import { useState } from 'react';
import { ORIGIN_LABELS } from '../../studio/labels';
import { chapterHeading } from '../../reading/api';
import { studioApi, type RevisionInfo, type StudioBlock } from '../../studio/api';
import { Segmented } from '../../ui/Segmented';
import { Button } from '../../ui/Button';
import { askConfirm } from '../../ui/ask';
import { Notice } from '../../ui/Notice';
import { relativeTime } from '../../lib/dates';
import { characters, paragraphs } from '../../lib/plural';
import styles from './studio.module.css';
import editor from './editor.module.css';
import { Loading } from '../../ui/Loading';

const text = (block: StudioBlock) => block.content.map((span) => span.text).join('');

/** «Усі · Команда · ШІ · Правки читачів» (етап 20, історія глави). */
type Filter = 'all' | 'team' | 'ai' | 'readers';
const FILTERS: { value: Filter; label: string }[] = [
    { value: 'all', label: 'Усі' }, { value: 'team', label: 'Команда' }, { value: 'ai', label: 'ШІ' }, { value: 'readers', label: 'Правки читачів' },
];
const kindOf = (revision: RevisionInfo): Filter => (revision.origin === 'ai' ? 'ai' : revision.origin === 'suggestion' ? 'readers' : 'team');

/** Who made a version and how: the team, AI (stage, models, run) or readers' правки (who proposed, who took them). */
function Made({ revision, editionId }: { revision: RevisionInfo; editionId: number }) {
    if (revision.origin === 'ai') {
        return (
            <div>
                ШІ · {revision.stage === 'proofread' ? 'вичитка' : 'переклад'}
                {revision.model && <span className={styles.muted}> · {revision.model}</span>}
                {revision.jobId != null && (
                    <> · <Link to="/studio/$editionId/translate/jobs/$jobId" params={{ editionId: String(editionId), jobId: String(revision.jobId) }}
                        onClick={(event) => event.stopPropagation()}>запуск №{revision.jobId}</Link></>
                )}
            </div>
        );
    }
    if (revision.origin === 'suggestion') {
        const proposers = revision.proposers ?? [];
        return (
            <div>
                Правки{proposers.length > 0 ? ` від ${proposers.join(', ')}` : ''} · прийняв {revision.authorNick ?? 'Новелка'}
                {(revision.notes ?? []).length > 0 && <span className={styles.muted}> · «{revision.notes!.join('», «')}»</span>}
            </div>
        );
    }
    return <div>{revision.authorNick ?? 'Новелка'} · {ORIGIN_LABELS[revision.origin] ?? revision.origin}</div>;
}

export function HistoryPage() {
    const { editionId, number } = useParams({ strict: false }) as { editionId: string; number: string };
    const id = Number(editionId);
    const chapter = Number(number);
    const revisions = useQuery({ queryKey: ['studio-revisions', id, chapter], queryFn: () => studioApi.revisions(id, chapter) });
    const view = useQuery({ queryKey: ['studio-editor', id, chapter], queryFn: () => studioApi.editor(id, chapter), staleTime: Infinity });
    const [open, setOpen] = useState<number | null>(null);
    const [filter, setFilter] = useState<Filter>('all');
    const client = useQueryClient();
    const restore = useMutation({
        mutationFn: (revisionId: number) => studioApi.restore(id, chapter, revisionId),
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: ['studio-revisions', id, chapter] });
            void client.invalidateQueries({ queryKey: ['studio-editor', id, chapter] });
        },
    });
    const shown = (revisions.data ?? []).filter((revision) => filter === 'all' || kindOf(revision) === filter);

    return (
        <section className={styles.page}>
            <Link to="/studio/$editionId/chapters/$number" params={{ editionId, number }} className={styles.muted}>‹ До робочого місця</Link>
            <h1 className={styles.title}>Історія: {view.data ? chapterHeading(view.data) : `глава ${chapter}`}</h1>
            {revisions.isError && <Notice tone="error">{revisions.error.message}</Notice>}
            {restore.isError && <Notice tone="error">{restore.error.message}</Notice>}
            {restore.isSuccess && <Notice tone="success">Версію повернуто: читачі вже бачать її. Попередні лишились в історії.</Notice>}
            <Segmented label="Показати" value={filter} options={FILTERS} onChange={setFilter} />
            {revisions.data && shown.length === 0 && <p className={styles.muted}>Таких версій у цієї глави немає.</p>}
            {shown.map((revision) => (
                <div key={revision.id}>
                    <button type="button" className={styles.row} style={{ width: '100%', background: 'none', border: 0, borderBottom: '1px solid var(--line)', textAlign: 'left', cursor: 'pointer' }}
                        aria-expanded={open === revision.id} onClick={() => setOpen(open === revision.id ? null : revision.id)}>
                        <div className={styles.grow}>
                            <Made revision={revision} editionId={id} />
                            <div className={styles.muted}>
                                {relativeTime(new Date(revision.createdAt))} · {paragraphs(revision.blocksChanged)} · {characters(revision.charsChanged)}
                            </div>
                        </div>
                        {revision.published && <span className={styles.badge}>зараз у читачів</span>}
                    </button>
                    {open === revision.id && <RevisionDiff editionId={id} chapter={chapter} revisionId={revision.id} />}
                    {open === revision.id && !revision.published && (
                        <Button variant="secondary" pending={restore.isPending} pendingLabel="Повертаємо…"
                            onPress={() => void askConfirm({
                                title: 'Повернути цю версію?', confirmLabel: 'Повернути',
                                text: 'Її текст стане новою версією глави, і читачі побачать його одразу. Пізніші версії лишаться в історії.',
                            }).then((yes) => { if (yes) restore.mutate(revision.id); })}>
                            Повернути цю версію
                        </Button>
                    )}
                </div>
            ))}
        </section>
    );
}

/** What this version changed compared with the one before: by paragraph, then by word. */
function RevisionDiff({ editionId, chapter, revisionId }: { editionId: number; chapter: number; revisionId: number }) {
    const revision = useQuery({ meta: { errorToast: true }, queryKey: ['studio-revision', revisionId], queryFn: () => studioApi.revision(editionId, chapter, revisionId) });
    if (revision.isError) return <p className={styles.muted}>Не вдалося показати зміни.</p>;
    if (!revision.data) return <Loading shape="lines" />;
    const before = new Map(revision.data.parentBlocks.map((block) => [block.id, block]));
    const after = new Set(revision.data.blocks.map((block) => block.id));
    const rows = revision.data.blocks.map((block) => {
        const old = before.get(block.id);
        const oldText = old ? text(old) : '';
        const newText = text(block);
        if (old && oldText === newText) return null;
        return (
            <WordDiff key={block.id} className={`${editor.diffBlock} ${editor.changed}`} before={oldText} after={newText} />
        );
    });
    const removed = revision.data.parentBlocks.filter((block) => !after.has(block.id) && text(block)).map((block) => (
        <p key={`gone-${block.id}`} className={`${editor.diffBlock} ${editor.changed}`}><del className={editor.removed}>{text(block)}</del></p>
    ));
    const titleChanged = revision.data.parentTitle !== null && revision.data.parentTitle !== revision.data.title;
    const all = [...rows.filter(Boolean), ...removed];
    return (
        <div style={{ padding: '12px 0 16px 14px' }}>
            {all.length > 0 && revision.data.parentTitle !== null && <div style={{ marginBottom: 10 }}><DiffModeSwitch /></div>}
            {titleChanged && <p className={styles.muted}>Назва: «{revision.data.parentTitle}» → «{revision.data.title}»</p>}
            {all.length === 0 && !titleChanged && <p className={styles.muted}>{revision.data.parentTitle === null ? 'Перша версія глави.' : 'Текст не змінився.'}</p>}
            {revision.data.parentTitle === null ? null : all}
        </div>
    );
}
