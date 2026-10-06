import { useQuery } from '@tanstack/react-query';
import { Link, useRouterState } from '@tanstack/react-router';
import { Users } from 'lucide-react';
import { useMe } from '../auth/me';
import { messagingApi } from '../inbox/api';
import { useInboxCounts } from '../inbox/live';
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
            <div className={styles.label}>Особисте</div>
            {item('/me', 'Огляд')}
            {item('/studio', 'Студія')}
            {me.role === 'owner' && item('/me/wallet', 'Шаги й автопереклад')}
            {me.role !== 'owner' && hasShahs && item('/me/shahs', `Шаги · ${shahs.data!.available} ${shahWord(shahs.data!.available)}`)}
            {item('/me/suggestions', 'Мої правки')}
            {item('/me/settings', 'Налаштування')}
            {item('/me/settings/privacy', 'Приватність')}
            {rank >= RANK.moderator && (
                <>
                    <div className={styles.label}>Адміністрування</div>
                    {item('/admin', 'Огляд')}
                    {item('/admin/moderation', 'Скарги й приховане')}
                    {rank >= RANK.admin && item('/admin/users', 'Користувачі й ролі')}
                    {rank >= RANK.owner && item('/admin/analytics', 'Аналітика')}
                    {rank >= RANK.owner && item('/admin/settings', 'Налаштування сайту')}
                    {rank >= RANK.owner && item('/admin/audit', 'Журнал дій')}
                </>
            )}
        </nav>
    );
}

/** The side menu of «Вхідні» on a wide screen: its sections and the conversations, one click to each. */
export function InboxRail() {
    const path = useRouterState({ select: (state) => state.location.pathname });
    const counts = useInboxCounts().data;
    const list = useQuery({ queryKey: ['conversations'], queryFn: messagingApi.list });
    const section = (to: string, label: string, count = 0) => (
        <Link to={to} className={`${styles.item} ${path === to ? styles.on : ''}`}>
            <span className={styles.name}>{label}</span>
            {count > 0 && <span className={styles.badge}>{count}</span>}
        </Link>
    );
    return (
        <nav className={styles.rail} aria-label="Вхідні">
            {section('/inbox', 'Сповіщення', counts?.notifications)}
            {section('/inbox/messages', 'Повідомлення', counts?.messages)}
            {section('/inbox/chat', 'Чат')}
            {section('/inbox/messages/new', '＋ Нова група')}
            {(list.data?.items.length ?? 0) > 0 && <div className={styles.label}>Розмови</div>}
            {list.data?.items.map((conversation) => {
                const to = `/inbox/messages/${conversation.id}`;
                return (
                    <Link key={conversation.id} to="/inbox/messages/$id" params={{ id: String(conversation.id) }}
                        className={`${styles.item} ${path.startsWith(to) ? styles.on : ''}`}>
                        {conversation.kind === 'team'
                            ? <Users size={20} aria-hidden />
                            : <Avatar nick={conversation.title} url={conversation.avatarUrl} size={24} />}
                        <span className={styles.name}>{conversation.kind === 'team' ? `$${conversation.teamHandle}` : conversation.title}</span>
                        {conversation.unread > 0 && <span className={styles.badge}>{conversation.unread}</span>}
                    </Link>
                );
            })}
        </nav>
    );
}
