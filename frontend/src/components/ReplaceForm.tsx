import { useEffect, useId, useState } from 'react';
import type { ReaderChapter } from '../api/types';
import { getJson, mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from './ActionNotice';

interface Preview { total: number; chapters: { chapter: number; count: number }[] }

/** Draft of a "replace every occurrence" correction, e.g. a wrongly translated name, for this chapter or the whole novel. */
export function ReplaceForm({ chapter, find: initial, onClose, onSaved }: {
    chapter: ReaderChapter; find: string; onClose: () => void; onSaved: () => void;
}) {
    const id = useId();
    const [find, setFind] = useState(initial);
    const [replacement, setReplacement] = useState('');
    const [scope, setScope] = useState<'chapter' | 'novel'>('chapter');
    const [reason, setReason] = useState('');
    const [preview, setPreview] = useState<Preview | null>(null);
    const action = useAction();
    useEffect(() => {
        const text = find.trim() ? find : '';
        if (!text) { setPreview(null); return; }
        const controller = new AbortController();
        const timer = window.setTimeout(() => {
            getJson<Preview>(`/corrections/replace-preview?novel=${encodeURIComponent(chapter.novelId)}&chapter=${chapter.number}&scope=${scope}&find=${encodeURIComponent(text)}`, controller.signal)
                .then(setPreview).catch(() => { if (!controller.signal.aborted) setPreview(null); });
        }, 300);
        return () => { window.clearTimeout(timer); controller.abort(); };
    }, [find, scope, chapter.novelId, chapter.number]);
    const chapters = preview?.chapters.length ?? 0;
    return <form className="correction-form stack-form replace-form" onSubmit={event => {
        event.preventDefault();
        void action.run(async () => {
            await mutate('/corrections/replace', { novelId: chapter.novelId, chapter: chapter.number, baseJobId: chapter.jobId,
                find, replacement, scope, reason });
            onSaved();
        }, 'Заміну збережено в чернетку.');
    }}>
        <h3>Замінити всі входження</h3>
        <div className="form-grid">
            <label>Замінити<input required maxLength={200} value={find} onChange={event => setFind(event.target.value)} /></label>
            <label>На<input maxLength={200} value={replacement} onChange={event => setReplacement(event.target.value)} autoFocus /></label>
        </div>
        <fieldset className="replace-scope"><legend>Де замінити</legend>
            <label className="check-label"><input type="radio" name={id} checked={scope === 'chapter'} onChange={() => setScope('chapter')} />Лише в цій главі</label>
            <label className="check-label"><input type="radio" name={id} checked={scope === 'novel'} onChange={() => setScope('novel')} />В усіх опублікованих главах новели</label>
        </fieldset>
        <p className="muted" role="status">{!find.trim() ? 'Вкажіть текст для пошуку.' : !preview ? 'Рахуємо входження…'
            : preview.total ? `Знайдено ${preview.total} ${preview.total === 1 ? 'входження' : 'входжень'}` + (scope === 'novel' ? ` у ${chapters} ${chapters === 1 ? 'главі' : 'главах'}.` : '.')
                : 'Такого тексту в опублікованому перекладі немає.'}</p>
        <label>Пояснення (необов’язково)<input maxLength={2000} value={reason} onChange={event => setReason(event.target.value)} /></label>
        <div className="button-row">
            <button className="button" disabled={action.busy || !find.trim() || find === replacement || !preview?.total}>Зберегти заміну</button>
            <button type="button" onClick={onClose}>Скасувати</button>
        </div>
        <ActionNotice {...action} />
    </form>;
}
