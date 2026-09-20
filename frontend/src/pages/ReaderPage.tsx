import { useEffect, useState } from 'react';
import type { NovelDetail, ReaderChapter } from '../api/types';
import { useResource } from '../hooks/useResource';
import { chapterPath, novelPath } from '../lib/routes';
import { readPreference, savePreference } from '../lib/preferences';
import { ErrorState, Loading } from '../components/Status';

export function ReaderPage({ id, number }: { id: string; number: number }) {
    const chapter = useResource<ReaderChapter>(chapterPath(id, number));
    const novel = useResource<NovelDetail>(novelPath(id));
    const [theme, setTheme] = useState(() => readPreference('theme') === 'dark' ? 'dark' : 'light');
    const [fontSize, setFontSize] = useState(() => {
        const saved = Number(readPreference('font-size'));
        return saved >= 16 && saved <= 28 ? saved : 20;
    });
    const data = chapter.data;
    useEffect(() => {
        document.documentElement.dataset.theme = theme;
        savePreference('theme', theme);
        return () => { delete document.documentElement.dataset.theme; };
    }, [theme]);
    useEffect(() => { savePreference('font-size', String(fontSize)); }, [fontSize]);
    useEffect(() => {
        if (data) {
            document.title = data.title + ' — Новелка';
            savePreference('chapter:' + data.novelId, String(data.number));
        }
    }, [data]);
    const error = chapter.error || novel.error;
    if (error) return <ErrorState message={error} retry={() => { chapter.retry(); novel.retry(); }} />;
    if (!data || !novel.data) return <Loading />;
    const index = novel.data.chapters.findIndex(item => item.number === data.number);
    const previous = index > 0 ? novel.data.chapters[index - 1] : undefined;
    const next = index >= 0 ? novel.data.chapters[index + 1] : undefined;
    return <div className="reader-page">
        <div className="reader-toolbar">
            <a className="back-link" href={'#' + novelPath(data.novelId)}>← Зміст</a>
            <div className="reader-controls">
                <button aria-label="Зменшити текст" disabled={fontSize <= 16} onClick={() => setFontSize(size => size - 2)}>А−</button>
                <span aria-label="Розмір тексту">{fontSize}</span>
                <button aria-label="Збільшити текст" disabled={fontSize >= 28} onClick={() => setFontSize(size => size + 2)}>А+</button>
                <span className="control-divider" />
                <button aria-label={theme === 'dark' ? 'Світла тема' : 'Темна тема'} aria-pressed={theme === 'dark'} onClick={() => setTheme(value => value === 'dark' ? 'light' : 'dark')}>{theme === 'dark' ? '☀' : '☾'}</button>
            </div>
        </div>
        <article className="reading-sheet" style={{ fontSize }}>
            <header className="chapter-heading"><p className="eyebrow">Глава {data.number} · Український переклад</p><h1>{data.title}</h1><p className="reading-novel-title">{novel.data.title}</p><div className="chapter-ornament" aria-hidden="true">✦</div></header>
            <div className="reading-text">{data.blocks.map((block, blockIndex) => {
                const key = block.id + ':' + blockIndex;
                if (blockIndex === 0 && block.kind === 'heading' && block.text === data.title) return null;
                if (block.kind === 'separator') return <hr key={key} />;
                if (block.kind === 'heading') return <h2 key={key}>{block.text}</h2>;
                return <p key={key} className={block.kind === 'preface' || block.kind === 'afterword' ? 'author-note' : undefined}>{block.text}</p>;
            })}</div>
            <div className="chapter-end" aria-hidden="true">◇</div>
        </article>
        <nav className="chapter-navigation" aria-label="Навігація між главами">
            {previous ? <a href={'#' + chapterPath(data.novelId, previous.number)}>← Попередня глава</a> : <span />}
            {next ? <a className="button" href={'#' + chapterPath(data.novelId, next.number)}>Наступна глава →</a> : <a className="button" href={'#' + novelPath(data.novelId)}>До змісту ↑</a>}
        </nav>
        <p className="reader-footnote">{next ? 'Історія триває. Перегорніть сторінку.' : 'Ви прочитали всі доступні глави цієї новели.'}</p>
    </div>;
}
