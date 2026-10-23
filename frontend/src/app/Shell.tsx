import { SearchBox } from '../reading/SearchBox';
import { useQuery } from '@tanstack/react-query';
import { HeadContent, Link, Outlet, useNavigate, useRouterState } from '@tanstack/react-router';
import { useEffect, useState } from 'react';
import { BookOpen, Home, Inbox, Moon, PenLine, Search, Sun, User, type LucideIcon } from '../ui/icons';
import { syncWithAccount, toggleSiteBase, useSite } from '../appearance/store';
import { meQuery, useMe } from '../auth/me';
import { useInboxCounts, useLiveEvents } from '../inbox/live';
import { Avatar } from '../ui/Avatar';
import styles from './Shell.module.css';
import { AskHost } from '../ui/ask';
import { MeRail } from './SideRails';
import { StudioRail } from './StudioRail';
import { StudioBar } from './StudioBar';
import { ToastHost } from '../ui/toast';
import { useKeyboardInset } from '../ui/keyboard';
import { studioApi } from '../studio/api';

type Tab = { to: '/' | '/catalog' | '/library' | '/studio' | '/inbox' | '/me'; label: string; icon: LucideIcon };

export const TABS: readonly Tab[] = [
    { to: '/', label: 'Головна', icon: Home },
    { to: '/catalog', label: 'Каталог', icon: Search },
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
    useAppearanceSync();
    useKeyboardInset();
    // The reader draws its own bars that hide while reading; no site chrome there.
    // The page always sits at the same place in the tree: moving it would remount the reader.
    // The chapter editor too: its formatting bar sits where the tabs would be.
    const reading = useRouterState({
        select: (state) => /^\/n\/[^/]+\/\d+(\/propose)?\/?$|^\/studio\/\d+\/chapters\/\d+\/?$/.test(state.location.pathname),
    });
    // A wide screen shows a section's side menu beside its pages: the Studio, «Я» with
    // administration. «Вхідні» and «Налаштування» draw their own (етап 20, крок 6).
    const rail = useRouterState({ select: (state) => railOf(state.location.pathname) });
    const side = reading ? null : rail;
    return (
        <div className={reading ? undefined : styles.shell}>
            <HeadContent />
            {!reading && (
                <header className={styles.header}>
                    <Link to="/" className={styles.logo} aria-label="Новелка, головна">
                        новелка<span className={styles.dot}>.</span>
                    </Link>
                    <Tabs className={styles.topTabs} wide />
                    <HeaderSearch />
                    <ThemeButton />
                </header>
            )}
            <main className={reading ? undefined : styles.main}>
                <div className={side ? `${styles.withRail} ${side !== 'studio' ? styles.capped : ''}` : undefined}>
                    {side === 'studio' && <StudioRail />}
                    {side === 'me' && <MeRail />}
                    <div className={styles.slot}>
                        {side === 'studio' && <StudioBar />}
                        <Outlet />
                    </div>
                </div>
                {!reading && !side && (
                    <footer className={styles.footer}>
                        <Link to="/rules">Правила сайту</Link>
                        <Link to="/developers">Для розробників</Link>
                    </footer>
                )}
            </main>
            {!reading && <Tabs className={styles.bottomTabs} />}
            <AskHost />
            <ToastHost />
        </div>
    );
}

/** The look the account keeps follows the person to every device. */
function useAppearanceSync() {
    const me = useMe();
    const loaded = useQuery(meQuery).isFetched;
    useEffect(() => {
        if (loaded) syncWithAccount(me ? me.appearance ?? {} : null);
    }, [me, loaded]);
}

/**
 * Sun / moon in the header on a computer (етап 20, рішення 2): the light look at once, and back.
 * A phone keeps the look chosen in «Вигляд».
 */
function ThemeButton() {
    const dark = !useSite().light;
    const label = dark ? 'Світла тема' : 'Темна тема';
    return (
        <button type="button" className={styles.themeButton} aria-label={label} title={label} onClick={toggleSiteBase}>
            {dark ? <Sun size={20} aria-hidden /> : <Moon size={20} aria-hidden />}
        </button>
    );
}

function railOf(path: string): 'studio' | 'me' | null {
    if (/^\/studio(\/|$)/.test(path)) return 'studio';
    // «Налаштування», «Вхідні», «Повідомлення» and the chat carry their own menus (етап 20, крок 6).
    if (/^\/me\/settings(\/|$)/.test(path)) return null;
    // «Я» itself is already a desk with its menu; its pages and administration get the side menu.
    if (/^\/me\/.+/.test(path) || /^\/admin(\/|$)/.test(path)) return 'me';
    return null;
}

/**
 * On a wide screen the search box is always at hand; it opens the catalog with the words typed.
 * The catalog has its own, so there it steps aside.
 */
function HeaderSearch() {
    const navigate = useNavigate();
    const [text, setText] = useState('');
    const catalog = useRouterState({ select: (state) => state.location.pathname === '/catalog' });
    if (catalog) return <div className={styles.search} />;
    return (
        <div className={styles.search}>
            <SearchBox compact label="Пошук новел" placeholder="Назва, автор, тег чи глава…" value={text} onChange={setText}
                onSubmit={(words) => {
                    void navigate({ to: '/catalog', search: words ? { q: words } : {} });
                    setText('');
                }} />
        </div>
    );
}

const STUDIO: Tab = { to: '/studio', label: 'Студія', icon: PenLine };

/**
 * «Студія» (етап 20, рішення 11): on a computer it is always in the menu of a signed-in person, after
 * «Бібліотека»; the phone keeps its five tabs and adds it only for those who asked in «Вигляд».
 */
function Tabs({ className, wide = false }: { className: string | undefined; wide?: boolean }) {
    const me = useMe();
    const withStudio = Boolean(me) && (wide || Boolean(me?.studioInMenu));
    const tabs = withStudio ? [...TABS.slice(0, 3), STUDIO, ...TABS.slice(3)] : TABS;
    const counts = useInboxCounts().data;
    const unread = (counts?.notifications ?? 0) + (counts?.messages ?? 0);
    // Suggestions waiting in the person's translations, on the Studio tab when it is in the menu.
    const studio = useQuery({ queryKey: ['studio'], queryFn: studioApi.mine, enabled: withStudio });
    const pending = studio.data?.reduce((sum, item) => sum + (item.pendingSuggestions ?? 0), 0) ?? 0;
    const badge = (to: string) => (to === '/inbox' ? unread : to === '/studio' ? pending : 0);
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
                        {badge(to) > 0 && <span className={styles.badge}>{badge(to) > 99 ? '99+' : badge(to)}</span>}
                    </span>
                    <span>{label}{badge(to) > 0 && <span className={styles.hidden}> ({to === '/studio' ? 'правок' : 'нових'}: {badge(to)})</span>}</span>
                </Link>
            ))}
        </nav>
    );
}
