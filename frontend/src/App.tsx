import { useEffect, useState } from 'react';
import { CatalogPage } from './pages/CatalogPage';
import { NovelPage } from './pages/NovelPage';
import { ReaderPage } from './pages/ReaderPage';

function currentPath() { return window.location.hash.slice(1) || '/'; }

export function App() {
    const [path, setPath] = useState(currentPath);
    useEffect(() => {
        const changed = () => { setPath(currentPath()); window.scrollTo(0, 0); };
        window.addEventListener('hashchange', changed);
        return () => window.removeEventListener('hashchange', changed);
    }, []);
    let content;
    try {
        const chapter = path.match(/^\/novels\/([^/]+)\/chapters\/([1-9]\d*)$/);
        const novel = path.match(/^\/novels\/([^/]+)$/);
        if (chapter && Number.isSafeInteger(Number(chapter[2]))) {
            content = <ReaderPage key={path} id={decodeURIComponent(chapter[1])} number={Number(chapter[2])} />;
        } else if (novel) {
            content = <NovelPage key={path} id={decodeURIComponent(novel[1])} />;
        } else if (path === '/') {
            content = <CatalogPage />;
        }
    } catch { /* Malformed URL is handled by the not-found page. */ }
    return <>
        <a className="skip-link" href="#main" onClick={event => { event.preventDefault(); document.getElementById('main')?.focus(); }}>Перейти до вмісту</a>
        <header className="site-header">
            <a className="brand" href="#/" aria-label="Новелка — каталог"><span className="brand-icon">н</span>новелка<span className="brand-dot">.</span></a>
            <nav aria-label="Основна навігація"><a className="nav-link" href="#/">Каталог новел</a></nav>
            <span className="header-note">Історії, що звучать українською</span>
        </header>
        <main id="main" tabIndex={-1}>{content || <div className="status-panel">
            <h1>Сторінку не знайдено</h1><a href="#/">Повернутися до каталогу</a>
        </div>}</main>
        <footer className="site-footer"><span>новелка. <span className="muted">Простір для історій.</span></span><span>З японської · Українською</span></footer>
    </>;
}
