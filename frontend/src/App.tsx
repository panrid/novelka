import { useEffect, useState } from 'react';
import { CatalogPage } from './pages/CatalogPage';
import { NovelPage } from './pages/NovelPage';
import { ReaderPage } from './pages/ReaderPage';
import { AuthPage } from './pages/AuthPage';
import { CorrectionsPage } from './pages/CorrectionsPage';
import { Redirect } from './components/Redirect';
import { SettingsPage } from './pages/SettingsPage';
import { AuditPage } from './pages/AuditPage';
import { ProfilePage } from './pages/ProfilePage';
import { ChatPage } from './pages/ChatPage';
import { LibraryPage } from './pages/LibraryPage';
import { ManagePage } from './pages/ManagePage';
import { useAuth, permits, roleNames, type Role } from './auth/AuthContext';
import { useAction } from './hooks/useAction';
import { ActionNotice } from './components/ActionNotice';
import { ThemePicker } from './theme/ThemePicker';
import { NotificationBell } from './notifications/NotificationBell';
import type { ReactNode } from 'react';

function currentPath() { return window.location.hash.slice(1) || '/'; }

export function App() {
    const auth = useAuth();
    const action = useAction();
    const guarded = (role: Role, page: ReactNode) => auth.loading ? <p role="status">Перевіряємо сесію…</p>
        : permits(auth.user, role) ? page : <div className="status-panel"><h1>Потрібен доступ</h1><p>Ця сторінка потребує ролі «{roleNames[role]}».</p><a href="#/login">Увійти</a></div>;
    const [path, setPath] = useState(currentPath);
    const clearNotice = action.clear;
    // Page-level notices ("Ви вийшли") belong to the moment they happened, not to the next page or session.
    useEffect(() => { clearNotice(); }, [path, auth.user?.id, clearNotice]);
    useEffect(() => {
        const changed = () => { setPath(currentPath()); window.scrollTo(0, 0); };
        window.addEventListener('hashchange', changed);
        return () => window.removeEventListener('hashchange', changed);
    }, []);
    let content;
    try {
        const section = path.split('?')[0] || '/';
        const chapter = section.match(/^\/novels\/([^/]+)\/chapters\/([1-9]\d*)$/);
        const novel = section.match(/^\/novels\/([^/]+)$/);
        if (chapter && Number.isSafeInteger(Number(chapter[2]))) {
            content = <ReaderPage key={path + ':' + auth.user?.id} id={decodeURIComponent(chapter[1])} number={Number(chapter[2])} />;
        } else if (novel) {
            content = <NovelPage key={path} id={decodeURIComponent(novel[1])} />;
        } else if (section === '/') {
            content = <CatalogPage />;
        } else if (path === '/login') content = <AuthPage />;
        else if (section === '/corrections') content = guarded('READER', <CorrectionsPage />);
        else if (path === '/profile') content = guarded('READER', <ProfilePage />);
        else if (path === '/chat') content = guarded('READER', <ChatPage />);
        else if (section === '/library') content = guarded('READER', <LibraryPage />);
        else if (section === '/accounts') content = <Redirect to="/settings?section=users" />;
        else if (path.split('?')[0] === '/manage') content = guarded('READER', <ManagePage key={path} search={path.split('?')[1]} />);
        else if (section === '/settings') content = guarded('ADMIN', <SettingsPage search={path.split('?')[1]} />);
        else if (section === '/audit') content = guarded('OWNER', <AuditPage />);
    } catch { /* Malformed URL is handled by the not-found page. */ }
    return <>
        <a className="skip-link" href="#main" onClick={event => { event.preventDefault(); document.getElementById('main')?.focus(); }}>Перейти до вмісту</a>
        <header className="site-header">
            <a className="brand" href="#/" aria-label="Новелка — каталог"><span className="brand-icon">н</span>новелка<span className="brand-dot">.</span></a>
            <nav aria-label="Основна навігація">
                <a className="nav-link" href="#/">Каталог новел</a>
                {auth.user && <><a className="nav-link" href="#/library">Бібліотека</a><a className="nav-link" href="#/corrections">Правки</a><a className="nav-link" href="#/chat">Чат</a><a className="nav-link" href="#/manage">Майстерня</a></>}
                {permits(auth.user, 'ADMIN') && <a className="nav-link" href="#/settings">Налаштування</a>}
            </nav>
            <div className="session-controls"><ThemePicker compact />
                {auth.user ? <><NotificationBell key={auth.user.id + ':' + auth.user.role} /><a className="nav-link profile-link" href="#/profile" title="Профіль"><span className="profile-avatar" aria-hidden="true">{auth.user.username.slice(0, 1).toUpperCase()}</span><span className="profile-name">{auth.user.username} · {roleNames[auth.user.role]}</span></a><button className="logout-button" aria-label="Вийти" title="Вийти" disabled={action.busy} onClick={() => { void action.run(auth.logout, 'Ви вийшли.'); }}><svg aria-hidden="true" className="logout-icon" viewBox="0 0 20 20" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M8 4H4v12h4" /><path d="M11 6l4 4-4 4" /><path d="M15 10H7" /></svg><span aria-hidden="true" className="logout-text">Вийти</span></button></> : <a className="nav-link" href="#/login">Увійти</a>}
            </div>
        </header>
        <main id="main" tabIndex={-1}><ActionNotice {...action} />{content || <div className="status-panel">
            <h1>Сторінку не знайдено</h1><a href="#/">Повернутися до каталогу</a>
        </div>}</main>
        <footer className="site-footer"><span>новелка. <span className="muted">Простір для історій.</span></span><span>З японської · Українською</span></footer>
    </>;
}
