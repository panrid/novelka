import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from '@tanstack/react-router';
import { Plus } from '../../ui/icons';
import { useState } from 'react';
import { useCanRun } from '../../ledger/api';
import { autotranslateApi } from '../../studio/autotranslate';
import { Button } from '../../ui/Button';
import { LinkButton } from '../../ui/LinkButton';
import { JobCard } from './AutotranslatePage';
import { EditionShell, editionTabs, useEditionOverview } from './EditionShell';
import { NewChapterSheet } from './NewChapterSheet';
import shell from './editionShell.module.css';
import styles from './studio.module.css';

export function useEditionId() {
    const { editionId } = useParams({ strict: false }) as { editionId: string };
    return Number(editionId);
}

/** «Огляд»: where the translation stands and what waits for the team, with the next steps at hand. */
export function EditionPage() {
    return (
        <EditionShell tab="overview">
            <OverviewTab />
        </EditionShell>
    );
}

function OverviewTab() {
    const id = useEditionId();
    const client = useQueryClient();
    const edition = useEditionOverview(id).data!;
    const canRun = useCanRun();
    const tabs = editionTabs(edition, canRun);
    const [adding, setAdding] = useState(false);
    const translator = edition.role !== 'editor';
    const run = useQuery({
        queryKey: ['autotranslate', id],
        queryFn: () => autotranslateApi.overview(id),
        enabled: tabs.translate,
        refetchInterval: (query) => (['queued', 'running'].includes(query.state.data?.jobs[0]?.state ?? '') ? 5_000 : false),
    });
    const job = run.data?.jobs[0];
    const working = job && (job.state === 'queued' || job.state === 'running' || job.state === 'failed');
    const params = { editionId: String(id) };
    const attention = [
        { count: edition.pendingSuggestions, label: 'Правки на перевірку', to: '/studio/$editionId/suggestions', hot: true },
        ...(tabs.words ? [{ count: edition.newWords, label: 'Нові слова в словнику', to: '/studio/$editionId/glossary', hot: false }] : []),
        { count: edition.drafts, label: 'Ваші чернетки глав', to: '/studio/$editionId/chapters', hot: false },
    ].filter((row) => row.count > 0);

    return (
        <div className={styles.overview}>
            <div className={styles.stats}>
                <div className={styles.stat}><b>{edition.chapterCount}</b><span>опубліковано</span></div>
                {edition.sourceChapters !== null && <div className={styles.stat}><b>{edition.sourceChapters}</b><span>в оригіналі</span></div>}
                <div className={styles.stat}><b>{edition.pendingSuggestions}</b><span>правок</span></div>
            </div>

            {working && run.data && (
                <JobCard job={job} editionId={id} showShah={run.data.showShah} usdPerShah={run.data.usdPerShah}
                    title={<Link to="/studio/$editionId/translate" params={params} className={styles.processTitle}>Автопереклад</Link>}
                    refreshed={{ at: Math.max(run.dataUpdatedAt, run.errorUpdatedAt), failed: run.isRefetchError }}
                    onCancel={() => void autotranslateApi.cancel(id, job.id).then(() => client.invalidateQueries({ queryKey: ['autotranslate', id] }))}
                    onResume={() => void autotranslateApi.resume(id, job.id).then(() => client.invalidateQueries({ queryKey: ['autotranslate', id] }))}
                    pending={false} />
            )}

            <div className={styles.attention}>
                <h2 className={styles.sectionTitle} style={{ margin: 0 }}>Потребує уваги</h2>
                {attention.length === 0 && <p className={shell.muted}>Усе переглянуто.</p>}
                {attention.map((row) => (
                    <Link key={row.label} to={row.to} params={params} className={styles.menuItem}>
                        <span>{row.label}</span>
                        <span className={`${styles.badge} ${row.hot ? styles.badgeOn : ''}`}>{row.count}</span>
                    </Link>
                ))}
            </div>

            <div className={styles.actions}>
                {translator && <Button onPress={() => setAdding(true)}><Plus size={18} aria-hidden />Нова глава</Button>}
                {edition.chapterCount > 0 && (
                    <LinkButton to="/n/$slug" params={{ slug: edition.novelSlug }} search={{ t: edition.teamHandle }} variant="secondary">
                        Як бачать читачі
                    </LinkButton>
                )}
            </div>
            {adding && <NewChapterSheet editionId={id} machine={tabs.translate} onClose={() => setAdding(false)} />}
        </div>
    );
}
