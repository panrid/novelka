import { HeadContent, Link, Outlet, useRouterState } from '@tanstack/react-router';
import { BookOpen, Home, Inbox, Search, User, type LucideIcon } from 'lucide-react';
import { useMe } from '../auth/me';
import { Avatar } from '../ui/Avatar';
import styles from './Shell.module.css';

type Tab = { to: '/' | '/catalog' | '/library' | '/inbox' | '/me'; label: string; icon: LucideIcon };

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
    // The reader draws its own bars that hide while reading; no site chrome there.
    // The page always sits at the same place in the tree: moving it would remount the reader.
    const reading = useRouterState({ select: (state) => /^\/n\/[^/]+\/\d+\/?$/.test(state.location.pathname) });
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
        </div>
    );
}

function Tabs({ className }: { className: string | undefined }) {
    const me = useMe();
    return (
        <nav className={className} aria-label="Розділи">
            {TABS.map(({ to, label, icon: Icon }) => (
                <Link
                    key={to}
                    to={to}
                    className={styles.tab}
                    activeProps={{ className: styles.active, 'aria-current': 'page' }}
                    activeOptions={{ exact: to === '/' }}
                >
                    {to === '/me' && me ? <Avatar nick={me.nick} url={me.avatarUrl} size={24} /> : <Icon aria-hidden size={22} strokeWidth={1.75} />}
                    <span>{label}</span>
                </Link>
            ))}
        </nav>
    );
}
