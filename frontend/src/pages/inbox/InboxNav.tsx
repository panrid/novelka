import { Link } from '@tanstack/react-router';
import { useInboxCounts } from '../../inbox/live';
import styles from './inbox.module.css';

/** «Сповіщення», «Повідомлення», «Чат»: one row, never tabs inside tabs. */
export function InboxNav() {
    const counts = useInboxCounts().data;
    const item = (to: '/inbox' | '/inbox/messages' | '/inbox/chat', label: string, count = 0) => (
        <Link to={to} className={styles.navItem} activeProps={{ className: `${styles.navItem} ${styles.navActive}` }}
            activeOptions={{ exact: to === '/inbox' }}>
            {label}{count > 0 && <span className={styles.count}>{count}</span>}
        </Link>
    );
    return (
        <nav className={styles.nav} aria-label="Вхідні">
            {item('/inbox', 'Сповіщення', counts?.notifications)}
            {item('/inbox/messages', 'Повідомлення', counts?.messages)}
            {item('/inbox/chat', 'Чат')}
        </nav>
    );
}
