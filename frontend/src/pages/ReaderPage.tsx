import { useEffect, useState } from 'react';
import type { NovelDetail, ReaderChapter } from '../api/types';
import { useResource } from '../hooks/useResource';
import { chapterPath, novelPath } from '../lib/routes';
import { readPreference, savePreference } from '../lib/preferences';
import { ErrorState, Loading } from '../components/Status';
import { EditableBlock } from '../components/EditableBlock';
import { useAuth } from '../auth/AuthContext';
import { ThemePicker } from '../theme/ThemePicker';

export function ReaderPage({ id, number }: { id: string; number: number }) {
    const { user } = useAuth();
    const chapter = useResource<ReaderChapter>(chapterPath(id, number));
    const novel = useResource<NovelDetail>(novelPath(id));
    const [correctionMode, setCorrectionMode] = useState(false);
    const [selection, setSelection] = useState<{ index: number; text: string } | null>(null);
    const [fontSize, setFontSize] = useState(() => {
        const saved = Number(readPreference('font-size'));
        return saved >= 16 && saved <= 28 ? saved : 20;
    });
    const data = chapter.data;
    useEffect(() => {
        if (!user || !data) return;
        const changed = () => {
            const current = window.getSelection();
            const container = (node: Node | null | undefined) => (node instanceof Element ? node : node?.parentElement)?.closest<HTMLElement>('[data-correction-index]');
            const start = container(current?.anchorNode), end = container(current?.focusNode);
            setSelection(current && !current.isCollapsed && start && start === end
                ? { index: Number(start.dataset.correctionIndex), text: current.toString().slice(0, 500) } : null);
        };
        document.addEventListener('selectionchange', changed);
        return () => document.removeEventListener('selectionchange', changed);
    }, [user, data]);
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
    const previous = data.previousNumber;
    const next = data.nextNumber;
    return <div className="reader-page">
        <div className="reader-toolbar">
            <a className="back-link" href={'#' + novelPath(data.novelId)}>← Зміст</a>
            <div className="reader-controls">
                <button aria-label="Зменшити текст" title="Зменшити текст" disabled={fontSize <= 16} onClick={() => setFontSize(size => size - 2)}>А−</button>
                <span aria-label="Розмір тексту">{fontSize}</span>
                <button aria-label="Збільшити текст" title="Збільшити текст" disabled={fontSize >= 28} onClick={() => setFontSize(size => size + 2)}>А+</button>
                <span className="control-divider" />
                <ThemePicker />
            </div>
        </div>
        {user && <div className="reader-edit-tools"><button type="button" aria-pressed={correctionMode} onClick={() => setCorrectionMode(value => !value)}>Режим правок</button>
            <span>{correctionMode ? 'Натисніть олівець біля потрібного абзацу.' : 'Виділіть текст, щоб запропонувати правку.'}</span></div>}
        <article className="reading-sheet" style={{ fontSize }}>
            <header className="chapter-heading"><p className="eyebrow">Глава {data.number} · Український переклад</p>
                {data.blocks[0]?.kind === 'heading' && data.blocks[0].text === data.title
                    ? <EditableBlock key={data.jobId + ':title:' + user?.id} block={data.blocks[0]} index={0} chapter={data} title
                        showCorrection={correctionMode} selected={selection?.index === 0 ? selection.text : ''} /> : <h1>{data.title}</h1>}
                <p className="reading-novel-title">{novel.data.title}</p><div className="chapter-ornament" aria-hidden="true">✦</div>
                {!user && <p className="correction-hint"><a href="#/login">Увійдіть, щоб запропонувати правку</a></p>}
            </header>
            <div className="reading-text">{data.blocks.map((block, blockIndex) => {
                const key = block.id + ':' + blockIndex;
                if (blockIndex === 0 && block.kind === 'heading' && block.text === data.title) return null;
                if (block.kind === 'separator') return <hr key={key} />;
                return <EditableBlock key={key + ':' + data.jobId + ':' + user?.id} block={block} index={blockIndex} chapter={data} heading={block.kind === 'heading'}
                    showCorrection={correctionMode} selected={selection?.index === blockIndex ? selection.text : ''} />;
            })}</div>
            <div className="chapter-end" aria-hidden="true">◇</div>
        </article>
        <nav className="chapter-navigation" aria-label="Навігація між главами">
            {previous ? <a href={'#' + chapterPath(data.novelId, previous)}>← Попередня глава</a> : <span />}
            {next ? <a className="button" href={'#' + chapterPath(data.novelId, next)}>Наступна глава →</a> : <a className="button" href={'#' + novelPath(data.novelId)}>До змісту ↑</a>}
        </nav>
        <p className="reader-footnote">{next ? 'Історія триває. Перегорніть сторінку.' : 'Ви прочитали всі доступні глави цієї новели.'}</p>
    </div>;
}
