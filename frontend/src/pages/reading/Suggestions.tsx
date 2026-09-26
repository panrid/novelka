import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { diffWords } from 'diff';
import { useEffect, useRef, useState } from 'react';
import type { ReaderChapter, Span, TextBlock } from '../../reading/api';
import { suggestionApi, type MineItem, type ReviewItem } from '../../reading/suggestions';
import { TextEditor, type EditorHandle } from '../../studio/TextEditor';
import type { StudioBlock } from '../../studio/api';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Sheet } from '../../ui/Sheet';
import { TextInput } from '../../ui/TextInput';
import styles from './suggestions.module.css';

const text = (spans: Span[]) => spans.map((span) => span.text).join('');

// ---- the reader's own suggestions ------------------------------------------------------------

export function useMySuggestions(chapter: ReaderChapter, signedIn: boolean) {
    const key = ['suggestions-mine', chapter.edition.editionId, chapter.number];
    const client = useQueryClient();
    const mine = useQuery({
        queryKey: key,
        queryFn: () => suggestionApi.mine(chapter.edition.editionId, chapter.number),
        enabled: signedIn,
    });
    const refresh = () => void client.invalidateQueries({ queryKey: ['suggestions-mine', chapter.edition.editionId] });
    const submit = useMutation({ mutationFn: () => suggestionApi.submit(chapter.edition.editionId), onSuccess: refresh });
    const overlay: Record<string, { spans: Span[]; label: string }> = {};
    for (const item of mine.data?.items ?? []) {
        if (item.kind === 'block' && item.blockId && item.proposed) {
            overlay[item.blockId] = { spans: item.proposed, label: item.state === 'draft' ? 'ваша правка · ще не надіслано' : 'ваша правка · на перевірці' };
        }
    }
    return { items: mine.data?.items ?? [], drafts: mine.data?.draftsInEdition ?? 0, overlay, submit, refresh };
}

/** Appears while text in one paragraph is selected: fix the paragraph or replace the fragment in the chapter. */
export function SelectionBar({ onEdit, onReplace }: { onEdit: (blockId: string) => void; onReplace: (find: string) => void }) {
    const [selection, setSelection] = useState<{ blockId: string; text: string } | null>(null);
    useEffect(() => {
        const update = () => {
            const current = window.getSelection();
            const chosen = current?.toString().trim() ?? '';
            const anchor = current?.anchorNode instanceof Element ? current.anchorNode : current?.anchorNode?.parentElement;
            const focus = current?.focusNode instanceof Element ? current.focusNode : current?.focusNode?.parentElement;
            const block = anchor?.closest('[data-block-id]');
            if (chosen && block && block === focus?.closest('[data-block-id]') && block.closest('article')) {
                setSelection({ blockId: block.getAttribute('data-block-id')!, text: chosen.slice(0, 200) });
            } else {
                setSelection(null);
            }
        };
        document.addEventListener('selectionchange', update);
        return () => document.removeEventListener('selectionchange', update);
    }, []);
    if (!selection) return null;
    return (
        <div className={styles.selectionBar} role="toolbar" aria-label="Правка виділеного">
            <button type="button" onMouseDown={(event) => event.preventDefault()} onClick={() => onEdit(selection.blockId)}>✎ Виправити абзац</button>
            <button type="button" onMouseDown={(event) => event.preventDefault()} onClick={() => onReplace(selection.text)}>⇄ Замінити в главі</button>
        </div>
    );
}

/** Rewrite one paragraph; the draft joins the batch. */
export function EditSheet({ chapter, block, existing, onClose, onSaved }: {
    chapter: ReaderChapter; block: TextBlock; existing: MineItem | undefined; onClose: () => void; onSaved: () => void;
}) {
    const start: StudioBlock[] = [{ id: block.id, type: 'paragraph', content: existing?.proposed ?? block.content }];
    const [blocks, setBlocks] = useState<StudioBlock[]>(start);
    const [note, setNote] = useState(existing?.note ?? '');
    const joined = (list: StudioBlock[]) => list.flatMap((b, index) => (index > 0 ? [{ text: ' ', marks: [] }, ...b.content] : b.content));
    const proposed = joined(blocks);
    const editor = useRef<EditorHandle>(null);
    const save = useMutation({
        mutationFn: () => suggestionApi.block(chapter.edition.editionId, chapter.number, block.id, joined(editor.current?.read() ?? blocks), note),
        onSuccess: () => { onSaved(); onClose(); },
    });
    const withdraw = useMutation({ mutationFn: () => suggestionApi.withdraw(existing!.id), onSuccess: () => { onSaved(); onClose(); } });
    return (
        <Sheet open onClose={onClose} title="Правка абзацу">
            <TextEditor handle={editor} mode="description" blocks={blocks} onChange={setBlocks} label="Текст абзацу" />
            {text(proposed) !== text(block.content) && (
                <p className={styles.was}>
                    {diffWords(text(block.content), text(proposed)).map((part, index) =>
                        part.added ? <ins key={index}>{part.value}</ins> : part.removed ? <del key={index}>{part.value}</del> : <span key={index}>{part.value}</span>)}
                </p>
            )}
            <TextInput label="Пояснення (необовʼязково)" value={note} onChange={setNote} />
            {save.isError && <Notice tone="error">{save.error.message}</Notice>}
            <div className={styles.buttons}>
                {existing ? <Button variant="danger" onPress={() => withdraw.mutate()}>Скасувати правку</Button>
                    : <Button variant="secondary" onPress={onClose}>Скасувати</Button>}
                <Button onPress={() => save.mutate()} pending={save.isPending} pendingLabel="Зберігаємо…">Додати до пакета</Button>
            </div>
            <p className={styles.hint}>Правки збираються в пакет. Надіслати його можна з панелі читалки або наприкінці глави.</p>
        </Sheet>
    );
}

/** Replace every occurrence of a fragment in this chapter only (рішення 26). */
export function ReplaceSheet({ chapter, find: initial, onClose, onSaved }: {
    chapter: ReaderChapter; find: string; onClose: () => void; onSaved: () => void;
}) {
    const [find, setFind] = useState(initial);
    const [replacement, setReplacement] = useState(initial);
    const [note, setNote] = useState('');
    const count = useQuery({
        queryKey: ['suggestion-count', chapter.edition.editionId, chapter.number, find],
        queryFn: () => suggestionApi.count(chapter.edition.editionId, chapter.number, find),
        enabled: find.trim().length > 0,
    });
    const save = useMutation({
        mutationFn: () => suggestionApi.replace(chapter.edition.editionId, chapter.number, find, replacement, note),
        onSuccess: () => { onSaved(); onClose(); },
    });
    const occurrences = count.data?.occurrences ?? 0;
    return (
        <Sheet open onClose={onClose} title="Замінити в цій главі">
            <TextInput label="Що замінити" value={find} onChange={setFind} />
            <TextInput label="На що" value={replacement} onChange={setReplacement} />
            <p className={styles.hint}>{count.isFetching ? 'Рахуємо…' : `У цій главі: ${occurrences}`}</p>
            <TextInput label="Пояснення (необовʼязково)" value={note} onChange={setNote} />
            {save.isError && <Notice tone="error">{save.error.message}</Notice>}
            <div className={styles.buttons}>
                <Button variant="secondary" onPress={onClose}>Скасувати</Button>
                <Button onPress={() => save.mutate()} pending={save.isPending} isDisabled={occurrences === 0 || find === replacement}>
                    Додати до пакета
                </Button>
            </div>
        </Sheet>
    );
}

// ---- review for the team ---------------------------------------------------------------------

export type Verdict = 'accept' | 'reject';

export function useReview(chapter: ReaderChapter) {
    const client = useQueryClient();
    const enabled = chapter.teamRole !== null && chapter.teamRole !== undefined;
    const key = ['suggestions-review', chapter.edition.editionId, chapter.number];
    const pending = useQuery({ queryKey: key, queryFn: () => suggestionApi.pending(chapter.edition.editionId, chapter.number), enabled });
    const [verdicts, setVerdicts] = useState<Record<number, Verdict>>({});
    const apply = useMutation({
        mutationFn: () => suggestionApi.review(chapter.edition.editionId, chapter.number,
            Object.entries(verdicts).map(([id, verdict]) => ({ id: Number(id), accept: verdict === 'accept' }))),
        onSuccess: () => {
            setVerdicts({});
            void client.invalidateQueries({ queryKey: key });
            void client.invalidateQueries({ queryKey: ['chapter', chapter.novelSlug] });
        },
    });
    const items = pending.data ?? [];
    const decide = (id: number, verdict: Verdict) => setVerdicts((current) => ({ ...current, [id]: verdict }));
    const acceptAll = () => setVerdicts(Object.fromEntries(items.filter((item) => !item.stale).map((item) => [item.id, 'accept' as Verdict])));
    return { items, verdicts, decide, acceptAll, apply };
}

/** One suggestion with the change highlighted word by word, and the reviewer's choice. */
export function ReviewCard({ item, verdict, onDecide, currentBlocks = [] }: {
    item: ReviewItem; verdict: Verdict | undefined; onDecide: (verdict: Verdict) => void; currentBlocks?: TextBlock[];
}) {
    return (
        <div className={`${styles.card} ${verdict === 'accept' ? styles.accepted : verdict === 'reject' ? styles.rejected : ''}`}>
            {item.kind === 'block' && item.current && item.proposed && (
                <p className={styles.diff}>
                    {diffWords(text(item.current), text(item.proposed)).map((part, index) =>
                        part.added ? <ins key={index}>{part.value}</ins> : part.removed ? <del key={index}>{part.value}</del> : <span key={index}>{part.value}</span>)}
                </p>
            )}
            {item.kind === 'replace' && <p className={styles.diff}><del>{item.find}</del> → <ins>{item.replacement}</ins> · {item.occurrences} у главі</p>}
            {item.kind === 'chapter' && <ChapterDiff proposed={item.proposedBlocks ?? []} current={currentBlocks} title={item.proposedTitle} />}
            <div className={styles.meta}>{item.authorNick}{item.note ? ` · «${item.note}»` : ''}{item.stale ? ' · текст уже змінився' : ''}</div>
            {!item.stale && (
                <div className={styles.buttons}>
                    <Button variant={verdict === 'accept' ? 'primary' : 'secondary'} onPress={() => onDecide('accept')}>✓ Прийняти</Button>
                    <Button variant={verdict === 'reject' ? 'danger' : 'secondary'} onPress={() => onDecide('reject')}>✕ Відхилити</Button>
                </div>
            )}
            {item.stale && (
                <div className={styles.buttons}><Button variant="secondary" onPress={() => onDecide('reject')}>Прибрати</Button></div>
            )}
        </div>
    );
}

/** Changed paragraphs of a whole-chapter suggestion, by paragraph id, word by word. */
function ChapterDiff({ proposed, current, title }: { proposed: TextBlock[]; current: TextBlock[]; title: string | null }) {
    const before = new Map(current.map((block) => [block.id, text(block.content)]));
    const after = new Set(proposed.map((block) => block.id));
    const changed = proposed.filter((block) => before.get(block.id) !== text(block.content));
    const removed = current.filter((block) => !after.has(block.id) && text(block.content));
    return (
        <div>
            <p className={styles.meta}>Зміни в усій главі{title ? ` · назва «${title}»` : ''} · абзаців: {changed.length + removed.length}</p>
            {changed.slice(0, 30).map((block) => (
                <p key={block.id} className={styles.diff}>
                    {diffWords(before.get(block.id) ?? '', text(block.content)).map((part, index) =>
                        part.added ? <ins key={index}>{part.value}</ins> : part.removed ? <del key={index}>{part.value}</del> : <span key={index}>{part.value}</span>)}
                </p>
            ))}
            {removed.slice(0, 10).map((block) => <p key={`gone-${block.id}`} className={styles.diff}><del>{text(block.content)}</del></p>)}
        </div>
    );
}
