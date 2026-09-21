import { useState, type ReactNode } from 'react';
import type { Block, ReaderChapter } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from './ActionNotice';

export function EditableBlock({ block, index, chapter, heading = false, title = false }: {
    block: Block; index: number; chapter: ReaderChapter; heading?: boolean; title?: boolean;
}) {
    const { user } = useAuth();
    const [editing, setEditing] = useState(false);
    const [selected, setSelected] = useState('');
    const [text, setText] = useState(block.text);
    const [reason, setReason] = useState('');
    const [saved, setSaved] = useState<string | null>(chapter.personalReplacements?.[index] ?? null);
    const action = useAction();
    const content = user ? saved ?? block.text : block.text;
    const tag = (children: ReactNode) => title ? <h1>{children}</h1> : heading
        ? <h2>{children}</h2> : <p className={['preface', 'afterword'].includes(block.kind) ? 'author-note' : undefined}>{children}</p>;
    return <div className={'editable-block' + (saved ? ' personal-block' : '')} onMouseUp={event => {
        const selection = window.getSelection();
        if (selection && !selection.isCollapsed && selection.anchorNode && selection.focusNode
                && event.currentTarget.contains(selection.anchorNode) && event.currentTarget.contains(selection.focusNode))
            setSelected(selection.toString().slice(0, 500));
    }}>
        {tag(content)}
        {user && saved && <small className="personal-note">Ваша версія · очікує перевірки</small>}
        {user && !saved && !editing && <button className="suggest-button" onClick={() => setEditing(true)}>Запропонувати правку</button>}
        {editing && <form className="correction-form stack-form" onSubmit={event => {
            event.preventDefault();
            void action.run(async () => {
                await mutate('/corrections', { novelId: chapter.novelId, chapter: chapter.number, baseJobId: chapter.jobId,
                    blockIndex: index, original: block.text, replacement: text, reason });
                setSaved(text); setEditing(false);
            }, 'Правку надіслано редактору.');
        }}>
            <h3>Ваша правка</h3>
            {selected && <blockquote>{selected}</blockquote>}
            <label>Виправлений абзац<textarea required maxLength={20000} rows={5} value={text} onChange={event => setText(event.target.value)} /></label>
            <label>Пояснення (необов’язково)<input maxLength={2000} value={reason} onChange={event => setReason(event.target.value)} /></label>
            <div className="button-row"><button className="button" disabled={action.busy || text === block.text}>Надіслати</button><button type="button" onClick={() => setEditing(false)}>Скасувати</button></div>
            <ActionNotice {...action} />
        </form>}
    </div>;
}
