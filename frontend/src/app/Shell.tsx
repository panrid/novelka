import { HeadContent, Link, Outlet } from '@tanstack/react-router';
import { BookOpen, Home, Inbox, Search, User, type LucideIcon } from 'lucide-react';
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
    return (
        <div className={styles.shell}>
            <HeadContent />
            <header className={styles.header}>
                <Link to="/" className={styles.logo} aria-label="Новелка, головна">
                    новелка<span className={styles.dot}>.</span>
                </Link>
                <Tabs className={styles.topTabs} />
            </header>
            <main className={styles.main}>
                <Outlet />
            </main>
            <Tabs className={styles.bottomTabs} />
        </div>
    );
}

function Tabs({ className }: { className: string | undefined }) {
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
                    <Icon aria-hidden size={22} strokeWidth={1.75} />
                    <span>{label}</span>
                </Link>
            ))}
        </nav>
    );
}
