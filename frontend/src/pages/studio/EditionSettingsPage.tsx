import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { changes, characters, paragraphs } from '../../lib/plural';
import { STATUS_LABELS, type Status } from '../../reading/api';
import { studioApi } from '../../studio/api';
import { Collapsible } from '../../ui/Collapsible';
import { useEditionId } from './EditionPage';
import { EditionShell, useEditionOverview } from './EditionShell';
import styles from './studio.module.css';

/** «Налаштування»: the translation's data, its people and where the original is. */
export function EditionSettingsPage() {
    return (
        <EditionShell tab="settings">
            <SettingsTab />
        </EditionShell>
    );
}

function SettingsTab() {
    const id = useEditionId();
    const edition = useEditionOverview(id).data!;
    const contributions = useQuery({ queryKey: ['studio-contributions', id], queryFn: () => studioApi.contributions(id) });
    const owner = edition.role === 'owner';
    const translator = edition.role !== 'editor';
    const params = { editionId: String(id) };
    return (
        <>
            <h2 className={styles.sectionTitle}>Публікація</h2>
            <nav className={styles.menu} aria-label="Публікація" style={{ marginTop: 0 }}>
                {owner && (
                    <Link to="/studio/$editionId/about" params={params} className={styles.menuItem}>
                        <span>Дані й обкладинка<span className={styles.menuHint}>назва, опис, теги, стан: {STATUS_LABELS[edition.status as Status]}</span></span>
                    </Link>
                )}
                {translator && (
                    <Link to="/studio/$editionId/structure" params={params} className={styles.menuItem}>
                        <span>Томи й нумерація<span className={styles.menuHint}>пролог, екстра, номери глав</span></span>
                    </Link>
                )}
                {edition.chapterCount > 0 && (
                    <Link to="/n/$slug" params={{ slug: edition.novelSlug }} search={{ t: edition.teamHandle }} className={styles.menuItem}>
                        <span>Як бачать читачі<span className={styles.menuHint}>/n/{edition.novelSlug}</span></span>
                    </Link>
                )}
                {edition.originalUrl && (
                    <a href={edition.originalUrl} target="_blank" rel="noopener noreferrer nofollow" className={styles.menuItem}>
                        <span>Оригінал ↗<span className={styles.menuHint}>{new URL(edition.originalUrl).host}</span></span>
                    </a>
                )}
            </nav>

            <h2 className={styles.sectionTitle}>Люди</h2>
            <nav className={styles.menu} aria-label="Люди" style={{ marginTop: 0 }}>
                <Link to="/team/$handle" params={{ handle: edition.teamHandle }} className={styles.menuItem}>
                    <span>Команда ${edition.teamHandle}<span className={styles.menuHint}>хто перекладає й редагує</span></span>
                </Link>
                {owner && edition.kind !== 'original' && (
                    <Link to="/studio/$editionId/relay" params={params} className={styles.menuItem}>
                        <span>Естафета<span className={styles.menuHint}>хто може продовжити переклад</span></span>
                    </Link>
                )}
            </nav>

            {(contributions.data?.length ?? 0) > 0 && (
                <Collapsible id="studio-contributions" title="Внесок" count={contributions.data!.length}>
                    <table className={styles.table}>
                        <tbody>
                            {contributions.data!.map((row) => (
                                <tr key={row.nick}>
                                    <td>{row.nick}</td>
                                    <td>{changes(row.revisions)} · {paragraphs(row.blocksChanged)} · {characters(row.charsChanged)}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </Collapsible>
            )}
        </>
    );
}
