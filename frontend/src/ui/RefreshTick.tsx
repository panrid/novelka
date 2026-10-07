import styles from './refreshTick.module.css';

/**
 * A small circular arrow that turns once for every refresh of the data next to it: a live run
 * shows that the page still asks and the server still answers. {@code at} is when the last answer
 * (or failure) came; a failed refresh tints it.
 */
export function RefreshTick({ at, failed = false }: { at: number; failed?: boolean }) {
    const time = at ? new Date(at).toLocaleTimeString('uk-UA') : '';
    return (
        <span className={failed ? styles.failed : styles.tick} title={failed ? `Не вдалося оновити (${time})` : `Оновлено о ${time}`}
            role="img" aria-label={failed ? 'Не вдалося оновити' : 'Оновлюється'}>
            {/* A new key restarts the turn: one answer, one turn. */}
            <svg key={at} className={at ? styles.spin : undefined} viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <path d="M13.5 8a5.5 5.5 0 1 1-1.6-3.9" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
                <path d="M12.6 1.6v2.9H9.7" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
        </span>
    );
}
