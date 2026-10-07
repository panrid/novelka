import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { PAGE_SIZE, usePaged } from '../../lib/usePage';
import { chapterHeading } from '../../reading/api';
import { suggestionApi } from '../../reading/suggestions';
import { Pager } from '../../ui/Pager';
import { useEditionId } from './EditionPage';
import { EditionShell, useEditionOverview } from './EditionShell';
import styles from './studio.module.css';

/** «Правки»: chapters with readers' suggestions waiting; a tap opens the chapter in review. */
export function SuggestionQueuePage() {
    return (
        <EditionShell tab="suggestions">
            <QueueTab />
        </EditionShell>
    );
}

function QueueTab() {
    const id = useEditionId();
    const edition = useEditionOverview(id).data!;
    const queue = useQuery({ queryKey: ['suggestion-queue', id], queryFn: () => suggestionApi.queue(id) });
    const paged = usePaged(queue.data);
    return (
        <>
            <p className={styles.muted}>
                Читачі пропонують виправлення в тексті. Відкрийте главу — правки будуть просто в тексті, кожну можна прийняти чи відхилити.
            </p>
            {queue.data?.length === 0 && <p className={styles.muted} style={{ marginTop: 12 }}>Правок на перевірку немає.</p>}
            {paged.shown.map((row) => (
                <Link key={row.number} className={styles.row} to="/n/$slug/$number"
                    params={{ slug: edition.novelSlug, number: String(row.number) }} search={{ t: edition.teamHandle, look: true }}>
                    <div className={`${styles.grow} ${styles.ellipsis}`}>{chapterHeading(row)}</div>
                    <span className={`${styles.badge} ${styles.badgeOn}`}>{row.pending}</span>
                </Link>
            ))}
            <Pager page={paged.page} total={paged.total} size={PAGE_SIZE} onPage={paged.setPage} />
        </>
    );
}
