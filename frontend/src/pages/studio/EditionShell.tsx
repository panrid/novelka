import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import type { ReactNode } from 'react';
import { useCanRun } from '../../ledger/api';
import { Cover } from '../../reading/Cover';
import { ROLE_LABELS, studioApi, type Overview } from '../../studio/api';
import { Notice } from '../../ui/Notice';
import { useEditionId } from './EditionPage';
import styles from './editionShell.module.css';

export type EditionTab = 'overview' | 'chapters' | 'translate' | 'words' | 'suggestions' | 'settings';

const KIND_LABELS: Record<string, string> = {
    machine: 'машинний переклад', mixed: 'машинний з правками', human: 'переклад', original: 'власний твір',
};

export function useEditionOverview(id: number) {
    return useQuery({ queryKey: ['studio-edition', id], queryFn: () => studioApi.overview(id) });
}

/**
 * Who sees which tab: autotranslation and its glossary belong to machine translations and to
 * those who can run them (шаги or the site owner); the editor of a team does not start runs.
 */
export function editionTabs(edition: Overview, canRun: boolean) {
    const machine = canRun && (edition.kind === 'machine' || edition.kind === 'mixed');
    return {
        translate: machine && edition.role !== 'editor',
        words: machine,
    };
}

/**
 * The top of every page of a translation in the Studio (the owner's design, 2026-10-07): its
 * cover and name, and the tabs «Огляд · Глави · Автопереклад · Словник · Правки ·
 * Налаштування», so everything about it is one tap away on a phone and on a computer.
 */
export function EditionShell({ tab, children }: { tab: EditionTab; children: ReactNode }) {
    const id = useEditionId();
    const overview = useEditionOverview(id);
    const canRun = useCanRun();
    if (overview.isError) return <section className={styles.page}><Notice tone="error">{overview.error.message}</Notice></section>;
    if (!overview.data) return <p className={styles.muted} style={{ paddingTop: 24 }}>Завантажуємо…</p>;
    const edition = overview.data;
    const shown = editionTabs(edition, canRun);
    const params = { editionId: String(id) };
    const item = (name: EditionTab, label: ReactNode, to: string) => (
        <Link key={name} to={to} params={params} className={name === tab ? styles.tabOn : styles.tab} aria-current={name === tab ? 'page' : undefined}>
            {label}
        </Link>
    );
    return (
        <section className={styles.page}>
            <header className={styles.head}>
                <Link to="/studio" className={styles.back}>‹ Студія</Link>
                <div className={styles.name}>
                    <Cover url={edition.coverUrl} title={edition.title} seed={edition.novelSlug} width={40} />
                    <div className={styles.grow}>
                        <h1 className={styles.title}>{edition.title}</h1>
                        <p className={styles.muted}>
                            ${edition.teamHandle} · ви {ROLE_LABELS[edition.role]} · {KIND_LABELS[edition.kind] ?? edition.kind}
                        </p>
                    </div>
                </div>
            </header>
            <nav className={styles.tabs} aria-label="Розділи перекладу">
                {item('overview', 'Огляд', '/studio/$editionId')}
                {item('chapters', 'Глави', '/studio/$editionId/chapters')}
                {shown.translate && item('translate', 'Автопереклад', '/studio/$editionId/translate')}
                {shown.words && item('words', <>Словник{edition.newWords > 0 && <span className={styles.count}>{edition.newWords}</span>}</>, '/studio/$editionId/glossary')}
                {item('suggestions', <>Правки{edition.pendingSuggestions > 0 && <span className={styles.count}>{edition.pendingSuggestions}</span>}</>, '/studio/$editionId/suggestions')}
                {item('settings', 'Налаштування', '/studio/$editionId/settings')}
            </nav>
            <div className={styles.body}>{children}</div>
        </section>
    );
}
