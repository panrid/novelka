import { useEffect, useState, type ReactNode } from 'react';
import type { Block, ReaderChapter } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from './ActionNotice';
import { ReplaceForm } from './ReplaceForm';

type OpenState = 'draft' | 'pending';

/**
 * One paragraph of the reader. A proposal is saved as the reader's private draft; a second edit updates it.
 * Drafts reach reviewers only when the reader submits them together (see ReaderPage), so a batch becomes one revision.
 */
export function EditableBlock({ block, index, chapter, heading = false, title = false, showCorrection = false, selected = '',
    submitted = 0, onDraftsChanged }: {
    block: Block; index: number; chapter: ReaderChapter; heading?: boolean; title?: boolean; showCorrection?: boolean; selected?: string;
    /** Increments when the reader submits drafts, so local drafts show as sent. */
    submitted?: number; onDraftsChanged?: (delta: number) => void;
}) {
    const { user } = useAuth();
    const [editing, setEditing] = useState(false);
    const [replacing, setReplacing] = useState(false);
    const [quoted, setQuoted] = useState('');
    const [saved, setSaved] = useState<string | null>(chapter.personalReplacements?.[index] ?? null);
    const [state, setState] = useState<OpenState | null>(saved === null ? null : chapter.personalStates?.[index] ?? 'pending');
    const [id, setId] = useState<string | null>(chapter.personalIds?.[index] ?? null);
    const [text, setText] = useState(saved ?? block.text);
    const [reason, setReason] = useState('');
    const action = useAction();
    useEffect(() => { if (submitted) setState(current => current === 'draft' ? 'pending' : current); }, [submitted]);
    const content = user ? saved ?? block.text : block.text;
    const tag = (children: ReactNode) => title ? <h1>{children}</h1> : heading
        ? <h2>{children}</h2> : <p className={['preface', 'afterword'].includes(block.kind) ? 'author-note' : undefined}>{children}</p>;
    const open = (quote = '') => { setQuoted(quote); setText(saved ?? block.text); setEditing(true); };
    return <div className={'editable-block' + (saved ? ' personal-block' : '') + (state === 'draft' ? ' draft-block' : '')}>
        <div className="editable-content" data-correction-index={index}>{tag(content)}</div>
        {user && saved && !editing && <div className="personal-note">
            <span>{state === 'draft' ? 'Ваша чернетка · ще не надіслана' : 'Ваша версія · очікує перевірки'}</span>
            <button type="button" className="plain-button" onClick={() => open()}>Змінити</button>
            {id && <button type="button" className="plain-button" disabled={action.busy} onClick={() => void action.run(async () => {
                await mutate('/corrections/' + id, undefined, 'DELETE');
                if (state === 'draft') onDraftsChanged?.(-1);
                setSaved(null); setState(null); setId(null); setText(block.text);
            }, 'Правку відкликано.')}>Відкликати</button>}
        </div>}
        {user && !saved && !editing && !replacing && (showCorrection || selected) && <div className="suggest-actions">
            <button type="button" className="suggest-button" aria-label="Запропонувати правку" title="Запропонувати правку"
                onPointerDown={event => event.preventDefault()} onClick={() => open(selected)}>
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
                    <path d="m15 5 4 4M4 20l5-1L20 8a2.8 2.8 0 0 0-4-4L5 15l-1 5Z" />
                </svg>
            </button>
            {selected && <button type="button" className="suggest-button" aria-label="Замінити всі входження" title="Замінити всі входження"
                onPointerDown={event => event.preventDefault()} onClick={() => { setQuoted(selected); setReplacing(true); }}>
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" aria-hidden="true">
                    <path d="M4 8h13l-3-3M20 16H7l3 3" />
                </svg>
            </button>}
        </div>}
        {replacing && <ReplaceForm chapter={chapter} find={quoted.trim()} onClose={() => setReplacing(false)}
            onSaved={() => { setReplacing(false); onDraftsChanged?.(1); }} />}
        {editing && <form className="correction-form stack-form" onSubmit={event => {
            event.preventDefault();
            void action.run(async () => {
                const result = await mutate<{ id: string }>('/corrections', { novelId: chapter.novelId, chapter: chapter.number, baseJobId: chapter.jobId,
                    blockIndex: index, original: block.text, replacement: text, reason });
                if (!saved) onDraftsChanged?.(1);
                setSaved(text); setId(result.id); setState(current => current ?? 'draft'); setEditing(false);
            }, 'Правку збережено в чернетку. Надішліть правки, коли дочитаєте главу.');
        }}>
            <h3>{saved ? 'Змінити вашу правку' : 'Ваша правка'}</h3>
            {quoted && <blockquote>{quoted}</blockquote>}
            <label>Виправлений абзац<textarea autoFocus required maxLength={20000} rows={5} value={text} onChange={event => setText(event.target.value)} /></label>
            <label>Пояснення (необов’язково)<input maxLength={2000} value={reason} onChange={event => setReason(event.target.value)} /></label>
            <div className="button-row"><button className="button" disabled={action.busy || text === block.text || text === saved}>Зберегти правку</button>
                <button type="button" onClick={() => setEditing(false)}>Скасувати</button></div>
        </form>}
        <ActionNotice {...action} />
    </div>;
}
