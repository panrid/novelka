import { HeadContent, Link, Outlet, useRouterState } from '@tanstack/react-router';
import { BookOpen, Home, Inbox, PenLine, Search, User, type LucideIcon } from 'lucide-react';
import { useMe } from '../auth/me';
import { useInboxCounts, useLiveEvents } from '../inbox/live';
import { Avatar } from '../ui/Avatar';
import styles from './Shell.module.css';
import { AskHost } from '../ui/ask';
import { ToastHost } from '../ui/toast';

type Tab = { to: '/' | '/catalog' | '/library' | '/studio' | '/inbox' | '/me'; label: string; icon: LucideIcon };

export const TABS: readonly Tab[] = [
    { to: '/', label: 'Головна', icon: Home },
    { to: '/catalog', label: 'Пошук', icon: Search },
    { to: '/library', label: 'Бібліотека', icon: BookOpen },
    { to: '/inbox', label: 'Вхідні', icon: Inbox },
    { to: '/me', label: 'Я', icon: User },
];

/**
 * App frame: five sections. On a phone they sit in a bottom tab bar within thumb reach;
 * on a wide screen the same links move to the top row next to the logo.
 */
export function Shell() {
    useLiveEvents();
    // The reader draws its own bars that hide while reading; no site chrome there.
    // The page always sits at the same place in the tree: moving it would remount the reader.
    // The chapter editor too: its formatting bar sits where the tabs would be.
    const reading = useRouterState({
        select: (state) => /^\/n\/[^/]+\/\d+(\/propose)?\/?$|^\/studio\/\d+\/chapters\/\d+\/?$/.test(state.location.pathname),
    });
    return (
        <div className={reading ? undefined : styles.shell}>
            <HeadContent />
            {!reading && (
                <header className={styles.header}>
                    <Link to="/" className={styles.logo} aria-label="Новелка, головна">
                        новелка<span className={styles.dot}>.</span>
                    </Link>
                    <Tabs className={styles.topTabs} />
                </header>
            )}
            <main className={reading ? undefined : styles.main}>
                <Outlet />
            </main>
            {!reading && <Tabs className={styles.bottomTabs} />}
            <AskHost />
            <ToastHost />
        </div>
    );
}

const STUDIO: Tab = { to: '/studio', label: 'Студія', icon: PenLine };

function Tabs({ className }: { className: string | undefined }) {
    const me = useMe();
    const tabs = me?.studioInMenu ? [...TABS.slice(0, 3), STUDIO, ...TABS.slice(3)] : TABS;
    const counts = useInboxCounts().data;
    const unread = (counts?.notifications ?? 0) + (counts?.messages ?? 0);
    return (
        <nav className={className} aria-label="Розділи">
            {tabs.map(({ to, label, icon: Icon }) => (
                <Link
                    key={to}
                    to={to}
                    className={styles.tab}
                    activeProps={{ className: styles.active, 'aria-current': 'page' }}
                    activeOptions={{ exact: to === '/' }}
                >
                    <span className={styles.icon}>
                        {to === '/me' && me ? <Avatar nick={me.nick} url={me.avatarUrl} size={24} /> : <Icon aria-hidden size={22} strokeWidth={1.75} />}
                        {to === '/inbox' && unread > 0 && <span className={styles.badge}>{unread > 99 ? '99+' : unread}</span>}
                    </span>
                    <span>{label}{to === '/inbox' && unread > 0 && <span className={styles.hidden}> (нових: {unread})</span>}</span>
                </Link>
            ))}
        </nav>
    );
}
