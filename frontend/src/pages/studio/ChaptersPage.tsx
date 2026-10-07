import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { ArrowDownUp, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useCanRun } from '../../ledger/api';
import { relativeTime } from '../../lib/dates';
import { PAGE_SIZE, usePage } from '../../lib/usePage';
import { chapterHeading } from '../../reading/api';
import { studioApi, type ChapterFilter } from '../../studio/api';
import { askConfirm } from '../../ui/ask';
import { Button } from '../../ui/Button';
import { LinkButton } from '../../ui/LinkButton';
import { Notice } from '../../ui/Notice';
import { Pager } from '../../ui/Pager';
import { useEditionId } from './EditionPage';
import { EditionShell, editionTabs, useEditionOverview } from './EditionShell';
import { NewChapterSheet } from './NewChapterSheet';
import styles from './studio.module.css';

const FILTERS: [ChapterFilter, string][] = [['', 'Усі'], ['suggestions', 'З правками'], ['drafts', 'Мої чернетки'], ['unpublished', 'Не опубліковані']];

/** «Глави»: every chapter of the translation, 20 to a page, by what needs doing. */
export function ChaptersPage() {
    return (
        <EditionShell tab="chapters">
            <ChaptersTab />
        </EditionShell>
    );
}

function ChaptersTab() {
    const id = useEditionId();
    const client = useQueryClient();
    const edition = useEditionOverview(id).data!;
    const canRun = useCanRun();
    const translator = edition.role !== 'editor';
    const [filter, setFilter] = useState<ChapterFilter>('');
    const [order, setOrder] = useState<'asc' | 'desc'>('desc');
    const [page, setPage] = usePage();
    const [adding, setAdding] = useState(false);
    const chapters = useQuery({
        meta: { errorToast: true },
        queryKey: ['studio-chapters', id, filter, order, page],
        queryFn: () => studioApi.chapters(id, { filter, order, page }),
        placeholderData: (previous) => previous,
    });
    const remove = useMutation({
        mutationFn: (number: number) => studioApi.deleteChapter(id, number),
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: ['studio-chapters', id] });
            void client.invalidateQueries({ queryKey: ['studio-edition', id] });
        },
    });
    const params = { editionId: String(id) };
    const choose = (next: ChapterFilter) => {
        setFilter(next);
        setPage(1);
    };
    return (
        <>
            {translator && (
                <div className={styles.actions}>
                    <Button onPress={() => setAdding(true)}><Plus size={18} aria-hidden />Нова глава</Button>
                    <LinkButton to="/studio/$editionId/import" params={params} variant="secondary">З файлу</LinkButton>
                    <LinkButton to="/studio/$editionId/structure" params={params} variant="secondary">Томи й нумерація</LinkButton>
                </div>
            )}
            {adding && <NewChapterSheet editionId={id} machine={editionTabs(edition, canRun).translate} onClose={() => setAdding(false)} />}
            <div className={styles.filters} role="group" aria-label="Які глави" style={{ margin: '14px 0 8px' }}>
                {FILTERS.map(([value, label]) => (
                    <button key={value} type="button" className={`${styles.chip} ${filter === value ? styles.chipOn : ''}`}
                        aria-pressed={filter === value} onClick={() => choose(value)}>
                        {label}{value === '' ? ` · ${edition.chapterCount}` : ''}
                    </button>
                ))}
            </div>
            <div className={styles.listHead}>
                <span className={styles.muted}>{order === 'desc' ? 'Від останньої' : 'Від першої'}{chapters.data ? ` · ${chapters.data.total}` : ''}</span>
                <button type="button" className={styles.plainButton} onClick={() => { setOrder(order === 'desc' ? 'asc' : 'desc'); setPage(1); }}>
                    <ArrowDownUp size={14} aria-hidden /> порядок
                </button>
            </div>
            {remove.isError && <Notice tone="error">{remove.error.message}</Notice>}
            {chapters.data?.total === 0 && <p className={styles.muted}>{filter ? 'Таких глав немає.' : 'Глав ще немає.'}</p>}
            {chapters.data?.items.map((chapter) => (
                <div key={chapter.number} className={styles.row}>
                    <Link className={`${styles.grow} ${styles.rowLink}`}
                        to="/studio/$editionId/chapters/$number" params={{ editionId: String(id), number: String(chapter.number) }}>
                        <div className={styles.ellipsis}>{chapterHeading(chapter)}</div>
                        <div className={styles.muted}>{relativeTime(new Date(chapter.updatedAt))}</div>
                    </Link>
                    {chapter.pending > 0 && (
                        <Link to="/n/$slug/$number" params={{ slug: edition.novelSlug, number: String(chapter.number) }}
                            search={{ t: edition.teamHandle, look: true }} className={`${styles.badge} ${styles.badgeOn}`}
                            aria-label={`Правок у главі: ${chapter.pending}`}>
                            правок: {chapter.pending}
                        </Link>
                    )}
                    {!chapter.published && <span className={styles.badge}>не опубліковано</span>}
                    {chapter.hasMyDraft && <span className={`${styles.badge} ${styles.badgeOn}`}>чернетка</span>}
                    {translator && !chapter.published && (
                        <button type="button" className={styles.iconButton} aria-label={`Видалити главу ${chapter.number}`}
                            onClick={() => void askConfirm({ title: 'Видалити главу?', text: 'Вона ще не опублікована.', confirmLabel: 'Видалити', danger: true })
                                .then((yes) => { if (yes) remove.mutate(chapter.number); })}>
                            <Trash2 size={18} aria-hidden />
                        </button>
                    )}
                </div>
            ))}
            {chapters.data && <Pager page={page} total={chapters.data.total} size={PAGE_SIZE} onPage={setPage} />}
        </>
    );
}
