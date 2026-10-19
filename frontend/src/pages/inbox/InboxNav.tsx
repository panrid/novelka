import { Link } from '@tanstack/react-router';
import { useMe } from '../../auth/me';
import { useInboxCounts } from '../../inbox/live';
import styles from './inbox.module.css';

/**
 * «Вхідні · 3», «Повідомлення · 2», «Загальний чат»: one row over the three pages, on a phone and
 * on a computer alike (етап 20, крок 6). Guests have only the chat.
 */
export function InboxNav() {
    const me = useMe();
    const counts = useInboxCounts().data;
    const item = (to: '/inbox' | '/inbox/messages' | '/inbox/chat', label: string, count = 0) => (
        <Link to={to} className={styles.navItem} activeProps={{ className: `${styles.navItem} ${styles.navActive}`, 'aria-current': 'page' }}
            activeOptions={{ exact: to === '/inbox', includeSearch: false }}>
            {label}{count > 0 && <span className={styles.count}> · {count}</span>}
        </Link>
    );
    return (
        <nav className={styles.nav} aria-label="Вхідні й розмови">
            {me && item('/inbox', 'Вхідні', counts?.notifications)}
            {me && item('/inbox/messages', 'Повідомлення', counts?.messages)}
            {item('/inbox/chat', 'Загальний чат')}
        </nav>
    );
}
