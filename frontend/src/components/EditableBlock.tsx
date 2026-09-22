import { useState, type ReactNode } from 'react';
import type { Block, ReaderChapter } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from './ActionNotice';

export function EditableBlock({ block, index, chapter, heading = false, title = false, showCorrection = false, selected = '' }: {
    block: Block; index: number; chapter: ReaderChapter; heading?: boolean; title?: boolean; showCorrection?: boolean; selected?: string;
}) {
    const { user } = useAuth();
    const [editing, setEditing] = useState(false);
    const [quoted, setQuoted] = useState('');
    const [text, setText] = useState(block.text);
    const [reason, setReason] = useState('');
    const [saved, setSaved] = useState<string | null>(chapter.personalReplacements?.[index] ?? null);
    const action = useAction();
    const content = user ? saved ?? block.text : block.text;
    const tag = (children: ReactNode) => title ? <h1>{children}</h1> : heading
        ? <h2>{children}</h2> : <p className={['preface', 'afterword'].includes(block.kind) ? 'author-note' : undefined}>{children}</p>;
    return <div className={'editable-block' + (saved ? ' personal-block' : '')}>
        <div className="editable-content" data-correction-index={index}>{tag(content)}</div>
        {user && saved && <small className="personal-note">Ваша версія · очікує перевірки</small>}
        {user && !saved && !editing && (showCorrection || selected) && <button type="button" className="suggest-button"
            aria-label="Запропонувати правку" title="Запропонувати правку" onPointerDown={event => event.preventDefault()} onClick={() => { setQuoted(selected); setEditing(true); }}>
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
                <path d="m15 5 4 4M4 20l5-1L20 8a2.8 2.8 0 0 0-4-4L5 15l-1 5Z" />
            </svg>
        </button>}
        {editing && <form className="correction-form stack-form" onSubmit={event => {
            event.preventDefault();
            void action.run(async () => {
                await mutate('/corrections', { novelId: chapter.novelId, chapter: chapter.number, baseJobId: chapter.jobId,
                    blockIndex: index, original: block.text, replacement: text, reason });
                setSaved(text); setEditing(false);
            }, 'Правку надіслано редактору.');
        }}>
            <h3>Ваша правка</h3>
            {quoted && <blockquote>{quoted}</blockquote>}
            <label>Виправлений абзац<textarea autoFocus required maxLength={20000} rows={5} value={text} onChange={event => setText(event.target.value)} /></label>
            <label>Пояснення (необов’язково)<input maxLength={2000} value={reason} onChange={event => setReason(event.target.value)} /></label>
            <div className="button-row"><button className="button" disabled={action.busy || text === block.text}>Надіслати</button><button type="button" onClick={() => setEditing(false)}>Скасувати</button></div>
            <ActionNotice {...action} />
        </form>}
    </div>;
}
