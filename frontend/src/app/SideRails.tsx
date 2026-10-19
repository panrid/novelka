import { Link, useRouterState } from '@tanstack/react-router';
import { useMe } from '../auth/me';
import { useOpenReports } from '../admin/api';
import { useMyShahs } from '../ledger/api';
import { shahWord } from '../studio/autotranslate';
import { Avatar } from '../ui/Avatar';
import styles from './StudioRail.module.css';

const RANK = { reader: 0, moderator: 1, admin: 2, owner: 3 } as const;

/**
 * The side menu of «Я» and administration on a wide screen: the person's own pages and, for
 * those who run the site, its administration — one click between settings without going back.
 */
export function MeRail() {
    const me = useMe();
    const path = useRouterState({ select: (state) => state.location.pathname });
    const shahs = useMyShahs();
    const reports = useOpenReports();
    if (!me) return <nav className={styles.rail} aria-label="Особисте" />;
    const rank = RANK[me.role];
    const hasShahs = Boolean(shahs.data && (shahs.data.available > 0 || shahs.data.reserved > 0 || shahs.data.history.length > 0));
    const item = (to: string, label: string) => (
        <Link to={to} className={`${styles.item} ${path === to ? styles.on : ''}`}>{label}</Link>
    );
    return (
        <nav className={styles.rail} aria-label="Особисте">
            <Link to="/u/$nick" params={{ nick: me.nick }} className={styles.item}>
                <Avatar nick={me.nick} url={me.avatarUrl} size={24} />
                <span className={styles.name}>{me.nick}</span>
            </Link>
            <div className={styles.label}>Моє</div>
            {item('/me', 'Огляд')}
            {item('/studio', 'Студія')}
            {item('/me/suggestions', 'Мої правки')}
            {item('/me/pictures', 'Мої картинки')}
            {me.role === 'owner' && item('/me/wallet', 'Шаги й автопереклад')}
            {me.role !== 'owner' && hasShahs && item('/me/shahs', `Шаги · ${shahs.data!.available} ${shahWord(shahs.data!.available)}`)}
            {item('/me/settings', 'Налаштування')}
            {rank >= RANK.moderator && (
                <>
                    <div className={styles.label}>Модерація</div>
                    {item('/admin/moderation', reports > 0 ? `Скарги й приховане · ${reports}` : 'Скарги й приховане')}
                </>
            )}
            {rank >= RANK.admin && (
                <>
                    <div className={styles.label}>Адміністрування</div>
                    {item('/admin', 'Огляд')}
                    {item('/admin/users', 'Користувачі й ролі')}
                    {rank >= RANK.owner && item('/admin/settings', 'Налаштування сайту')}
                    {rank >= RANK.owner && item('/admin/analytics', 'Аналітика')}
                    {rank >= RANK.owner && item('/admin/audit', 'Журнал дій')}
                </>
            )}
        </nav>
    );
}
