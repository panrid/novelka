import { Extension, type JSONContent } from '@tiptap/core';
import Bold from '@tiptap/extension-bold';
import Document from '@tiptap/extension-document';
import Heading from '@tiptap/extension-heading';
import HorizontalRule from '@tiptap/extension-horizontal-rule';
import Image from '@tiptap/extension-image';
import Italic from '@tiptap/extension-italic';
import Paragraph from '@tiptap/extension-paragraph';
import Strike from '@tiptap/extension-strike';
import Text from '@tiptap/extension-text';
import Underline from '@tiptap/extension-underline';
import { Placeholder, UndoRedo } from '@tiptap/extensions';
import { EditorContent, useEditor, useEditorState, type Editor } from '@tiptap/react';
import { ArrowRightLeft, BookmarkPlus, Check, ImagePlus, Images, Link2, Minus, RefreshCw, Redo2, Sparkles, Undo2 } from '../ui/icons';
import { askText } from '../ui/ask';
import { showInfo } from '../ui/toast';
import { useEffect, useImperativeHandle, useRef, useState } from 'react';
import { Dialog, Heading as DialogHeading, Modal, ModalOverlay } from 'react-aria-components';
import { Button } from '../ui/Button';
import { Notice } from '../ui/Notice';
import { TextInput } from '../ui/TextInput';
import { studioApi, type StudioBlock } from './api';
import { toBlocks, toDocument } from './document';
import { PicturePicker } from './PicturePicker';
import { gridKey, WorkspaceGrid, type GridOriginal, type GridSource } from './workspaceGrid';
import styles from './TextEditor.module.css';

/** Keeps each paragraph's id and kind (preface, afterword) on its editor node. */
const BlockIdentity = Extension.create({
    name: 'blockIdentity',
    addGlobalAttributes() {
        return [{
            types: ['paragraph', 'heading', 'horizontalRule', 'image'],
            attributes: {
                blockId: { default: null, keepOnSplit: false, parseHTML: () => null, renderHTML: () => ({}) },
                kind: { default: null, parseHTML: () => null, renderHTML: () => ({}) },
            },
        }];
    },
});

const Picture = Image.extend({
    addAttributes() {
        return { ...this.parent?.(), imageId: { default: null, renderHTML: () => ({}) } };
    },
});

/**
 * What the editor holds right now. A phone keyboard (IME) hands the last word over only when
 * the field loses focus, so a button pressed right after typing must read the text here,
 * not from state that may not have caught up yet.
 */
export type EditorHandle = {
    read: () => StudioBlock[];
    /** Puts new text into the paragraph with this id, as one step «Скасувати» takes back. */
    replaceBlock?: (blockId: string, text: string) => void;
};

type Props = {
    handle?: React.Ref<EditorHandle>;
    blocks: StudioBlock[];
    onChange: (blocks: StudioBlock[]) => void;
    /** A chapter gets separators, headings and (for translators) pictures; a description only marks. */
    mode: 'chapter' | 'description';
    mayAddPictures?: boolean;
    label: string;
    placeholder?: string;
    /**
     * «Намалювати»: gets the selected text (or the paragraph the cursor is in) and a
     * function that puts the finished picture right after that paragraph.
     */
    onIllustrate?: ((fragment: string, insert: (picture: { id: number; url: string }) => void) => void) | undefined;
    /** The id of the paragraph the cursor is in (null for one typed anew), as the cursor moves. */
    onCursorBlock?: (blockId: string | null) => void;
    /** «Інший варіант» (етап 19): the paragraph with the cursor, its id and its text as it is now. */
    onVariants?: ((pick: { blockId: string | null; paragraph: string }) => void) | undefined;
    /** «Вичитати абзац» (етап 19): the same, for AI to proofread against the original. */
    onProofread?: ((pick: { blockId: string | null; paragraph: string }) => void) | undefined;
    /** «До словника» (етап 19): the selected words, their paragraph's id and its text as it is now. */
    onGlossary?: ((pick: { selection: string; blockId: string | null; paragraph: string }) => void) | undefined;
    /**
     * The workspace grid on a wide screen (новий дизайн, рішення 9): numbers, and the original beside
     * each paragraph while {@code original} is given. Left out — the text alone, as on a phone.
     */
    grid?: { original: GridOriginal[] | null } | undefined;
};

/** The top-level block where the cursor or the selection starts (a select-all starts at the document). */
function cursorNode(editor: Editor) {
    const { $from } = editor.state.selection;
    return $from.depth >= 1 ? $from.node(1) : editor.state.doc.nodeAt($from.pos);
}

/** The top-level block holding the cursor keeps the paragraph's id from the original. */
function cursorBlock(editor: Editor): string | null {
    return (cursorNode(editor)?.attrs.blockId as string | null | undefined) ?? null;
}

export function TextEditor({ handle, blocks, onChange, mode, mayAddPictures = false, label, placeholder, onIllustrate, onCursorBlock, onGlossary, onVariants, onProofread, grid }: Props) {
    const chapter = mode === 'chapter';
    const editor = useEditor({
        extensions: [
            Document, Text, Paragraph, Bold, Italic, Underline, Strike, UndoRedo, BlockIdentity,
            Placeholder.configure({ placeholder: placeholder ?? '' }),
            ...(chapter ? [Heading.configure({ levels: [2] }), HorizontalRule, Picture.configure({ inline: false })] : []),
            ...(chapter ? [WorkspaceGrid] : []),
        ],
        content: toDocument(blocks) as JSONContent,
        editorProps: {
            attributes: { class: chapter ? styles.chapter : styles.description, 'aria-label': label, 'aria-multiline': 'true', role: 'textbox' },
            // The line being typed stays above the bottom tab bar or the formatting bar, not under them.
            scrollThreshold: { top: 16, right: 0, bottom: 110, left: 0 },
            scrollMargin: { top: 16, right: 0, bottom: 110, left: 0 },
            // Pasted Word or web text keeps only what the site supports: the schema drops the rest.
            transformPastedHTML: (html) => html.replace(/<(script|style)[^>]*>[\s\S]*?<\/\1>/gi, ''),
        },
        onUpdate: ({ editor: changed }) => onChange(toBlocks(changed.getJSON())),
        onSelectionUpdate: ({ editor: moved }) => onCursorBlock?.(cursorBlock(moved)),
    });

    useImperativeHandle(handle, () => ({
        read: () => {
            if (!editor) return blocks;
            // Take in what the keyboard typed but the editor has not read from the page yet.
            (editor.view as unknown as { domObserver?: { flush: () => void } }).domObserver?.flush();
            return toBlocks(editor.getJSON());
        },
        replaceBlock: (blockId, text) => {
            if (!editor) return;
            editor.state.doc.forEach((node, at) => {
                if (node.attrs.blockId !== blockId) return;
                editor.chain().focus().insertContentAt({ from: at + 1, to: at + node.nodeSize - 1 }, text).run();
            });
        },
    }), [editor, blocks]);

    const gridOn = Boolean(grid);
    const gridOriginal = grid?.original ?? null;
    // The grid is told what to draw by a transaction of its own.
    useEffect(() => {
        if (!chapter || !editor || editor.isDestroyed) return;
        const source: GridSource = { on: gridOn, original: gridOriginal };
        const tell = () => {
            try {
                editor.view.dispatch(editor.state.tr.setMeta(gridKey, source));
                return true;
            } catch {
                return false; // the view is not on the page yet
            }
        };
        if (tell()) return undefined;
        const later = setTimeout(tell, 0);
        return () => clearTimeout(later);
    }, [editor, chapter, gridOn, gridOriginal]);

    if (!editor) {
        return null;
    }
    const gridClass = grid ? ` ${styles.grid} ${grid.original ? styles.gridWithOriginal : ''}` : '';
    return (
        <div className={`${styles.wrap}${gridClass}`}>
            <EditorContent editor={editor} />
            <Toolbar editor={editor} chapter={chapter} mayAddPictures={chapter && mayAddPictures}
                onIllustrate={chapter && mayAddPictures ? onIllustrate : undefined} onGlossary={chapter ? onGlossary : undefined}
                onVariants={chapter ? onVariants : undefined} onProofread={chapter ? onProofread : undefined} />
        </div>
    );
}

function Toolbar({ editor, chapter, mayAddPictures, onIllustrate, onGlossary, onVariants, onProofread }: {
    editor: Editor; chapter: boolean; mayAddPictures: boolean; onIllustrate: Props['onIllustrate']; onGlossary: Props['onGlossary'];
    onVariants: Props['onVariants']; onProofread: Props['onProofread'];
}) {
    const state = useEditorState({
        editor,
        selector: ({ editor: e }) => ({
            bold: e.isActive('bold'), italic: e.isActive('italic'), underline: e.isActive('underline'), strike: e.isActive('strike'),
            undo: e.can().undo(), redo: e.can().redo(), selected: !e.state.selection.empty,
        }),
    });
    const button = (name: string, active: boolean, run: () => void, content: React.ReactNode) => (
        <button type="button" className={`${styles.tool} ${active ? styles.on : ''}`} aria-label={name} aria-pressed={active}
            onMouseDown={(event) => event.preventDefault()} onClick={run}>
            {content}
        </button>
    );
    return (
        <div className={chapter ? styles.toolbarFixed : styles.toolbar} role="toolbar" aria-label="Форматування">
            {button('Жирний', state.bold, () => editor.chain().focus().toggleBold().run(), <b>Ж</b>)}
            {button('Курсив', state.italic, () => editor.chain().focus().toggleItalic().run(), <i>К</i>)}
            {button('Підкреслений', state.underline, () => editor.chain().focus().toggleUnderline().run(), <u>П</u>)}
            {button('Закреслений', state.strike, () => editor.chain().focus().toggleStrike().run(), <s>З</s>)}
            {chapter && button('Розділювач сцен', false, () => editor.chain().focus().setHorizontalRule().run(), <Minus size={18} aria-hidden />)}
            {mayAddPictures && <PictureButtons editor={editor} />}
            {onIllustrate && button('Намалювати сцену', false, () => illustrate(editor, onIllustrate), <Sparkles size={18} aria-hidden />)}
            {onVariants && button('Інший варіант', false, () => {
                const paragraph = cursorNode(editor)?.textContent.trim() ?? '';
                if (paragraph) onVariants({ blockId: cursorBlock(editor), paragraph });
            }, <RefreshCw size={18} aria-hidden />)}
            {onProofread && button('Вичитати абзац', false, () => {
                const paragraph = cursorNode(editor)?.textContent.trim() ?? '';
                if (paragraph) onProofread({ blockId: cursorBlock(editor), paragraph });
            }, <Check size={18} aria-hidden />)}
            {onGlossary && (
                <button type="button" className={styles.tool} aria-label="До словника" disabled={!state.selected}
                    title={state.selected ? 'Додати виділене до словника' : 'Виділіть слово в тексті'}
                    onMouseDown={(event) => event.preventDefault()} onClick={() => glossary(editor, onGlossary)}>
                    <BookmarkPlus size={18} aria-hidden />
                </button>
            )}
            {chapter && (
                <button type="button" className={styles.tool} aria-label="Замінити в главі" disabled={!state.selected}
                    title={state.selected ? 'Замінити виділене по всій главі' : 'Виділіть слово в тексті'}
                    onMouseDown={(event) => event.preventDefault()} onClick={() => void replaceInChapter(editor)}>
                    <ArrowRightLeft size={18} aria-hidden />
                </button>
            )}
            <span className={styles.spacer} />
            {state.undo && button('Скасувати', false, () => editor.chain().focus().undo().run(), <Undo2 size={18} aria-hidden />)}
            {state.redo && button('Повторити', false, () => editor.chain().focus().redo().run(), <Redo2 size={18} aria-hidden />)}
        </div>
    );
}

/**
 * «Замінити в главі» (етап 19): the selected words, wherever they stand in this chapter's text, become
 * what the person types — one step «Скасувати» takes back. Only whole runs of the same formatting match.
 */
async function replaceInChapter(editor: Editor) {
    const { from, to } = editor.state.selection;
    const find = editor.state.doc.textBetween(from, to, ' ');
    if (!find.trim() || find.includes('\n')) return;
    const replacement = await askText({ title: `Замінити «${find.trim()}» у главі`, label: 'На що замінити', confirmLabel: 'Замінити всюди' });
    if (replacement === null) return;
    const count = replaceAll(editor, find, replacement);
    showInfo(count === 0 ? 'Такого в главі не знайшлося.' : `Замінено: ${count}.`);
}

/** Replaces every {@code find} inside a text run of the document, the last first so positions hold; returns how many. */
export function replaceAll(editor: Editor, find: string, replacement: string): number {
    const found: { from: number; to: number }[] = [];
    editor.state.doc.descendants((node, at) => {
        if (!node.isText || !node.text) return;
        for (let index = node.text.indexOf(find); index !== -1; index = node.text.indexOf(find, index + find.length)) {
            found.push({ from: at + index, to: at + index + find.length });
        }
    });
    if (found.length === 0) return 0;
    const transaction = editor.state.tr;
    for (const range of found.reverse()) {
        const marks = editor.state.doc.resolve(range.from + 1).marks();
        if (replacement) transaction.replaceWith(range.from, range.to, editor.state.schema.text(replacement, marks));
        else transaction.delete(range.from, range.to);
    }
    editor.view.dispatch(transaction);
    return found.length;
}

/** The selected words and the paragraph they are in, with the id it keeps from the original. */
function glossary(editor: Editor, onGlossary: NonNullable<Props['onGlossary']>) {
    const { from, to } = editor.state.selection;
    const selection = editor.state.doc.textBetween(from, to, ' ').trim();
    if (!selection) return;
    onGlossary({ selection, blockId: cursorBlock(editor), paragraph: cursorNode(editor)?.textContent ?? '' });
}

/** The selected text, or the whole paragraph when nothing is selected; the picture goes after that paragraph. */
function illustrate(editor: Editor, onIllustrate: NonNullable<Props['onIllustrate']>) {
    const { from, to, $to } = editor.state.selection;
    const selected = editor.state.doc.textBetween(from, to, '\n').trim();
    const fragment = selected || $to.parent.textContent.trim();
    const after = $to.depth >= 1 ? $to.after(1) : editor.state.doc.content.size;
    onIllustrate(fragment, (picture) => {
        editor.chain().focus().insertContentAt(after, { type: 'image', attrs: { src: picture.url, imageId: picture.id } }).run();
    });
}

function PictureButtons({ editor }: { editor: Editor }) {
    const input = useRef<HTMLInputElement>(null);
    const [linkOpen, setLinkOpen] = useState(false);
    const [url, setUrl] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    /** Where «З моїх картинок» puts the chosen ones: after the paragraph the cursor was in. */
    const [libraryAt, setLibraryAt] = useState<number | null>(null);

    const insert = (picture: { id: number; url: string }) =>
        editor.chain().focus().insertContent({ type: 'image', attrs: { src: picture.url, imageId: picture.id } }).run();

    async function fromFile(file: File | undefined) {
        if (!file) return;
        setBusy(true);
        try {
            insert(await studioApi.uploadImage(file, 'illustration'));
        } catch (failure) {
            window.alert((failure as Error).message);
        } finally {
            setBusy(false);
            if (input.current) input.current.value = '';
        }
    }

    async function fromLink() {
        setBusy(true);
        setError(null);
        try {
            insert(await studioApi.imageFromUrl(url.trim()));
            setLinkOpen(false);
            setUrl('');
        } catch (failure) {
            setError((failure as Error).message);
        } finally {
            setBusy(false);
        }
    }

    return (
        <>
            <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={(event) => void fromFile(event.target.files?.[0])} />
            <button type="button" className={styles.tool} aria-label="Картинка з файлу" disabled={busy}
                onMouseDown={(event) => event.preventDefault()} onClick={() => input.current?.click()}>
                <ImagePlus size={18} aria-hidden />
            </button>
            <button type="button" className={styles.tool} aria-label="З моїх картинок" disabled={busy}
                onMouseDown={(event) => event.preventDefault()} onClick={() => {
                    const { $to } = editor.state.selection;
                    setLibraryAt($to.depth >= 1 ? $to.after(1) : editor.state.doc.content.size);
                }}>
                <Images size={18} aria-hidden />
            </button>
            {libraryAt !== null && (
                <PicturePicker onClose={() => setLibraryAt(null)} onPick={(pictures) => {
                    editor.chain().focus().insertContentAt(Math.min(libraryAt, editor.state.doc.content.size),
                        pictures.map((picture) => ({ type: 'image', attrs: { src: picture.url, imageId: picture.id } }))).run();
                }} />
            )}
            <button type="button" className={styles.tool} aria-label="Картинка за посиланням" disabled={busy}
                onMouseDown={(event) => event.preventDefault()} onClick={() => setLinkOpen(true)}>
                <Link2 size={18} aria-hidden />
            </button>
            <ModalOverlay className={styles.overlay} isOpen={linkOpen} onOpenChange={setLinkOpen} isDismissable>
                <Modal className={styles.modal}>
                    <Dialog className={styles.dialog}>
                        <DialogHeading slot="title" className={styles.dialogTitle}>Картинка за посиланням</DialogHeading>
                        <TextInput label="Посилання" value={url} onChange={setUrl} placeholder="https://…" hint="Сайт збереже копію в себе." />
                        {error && <Notice tone="error">{error}</Notice>}
                        <div className={styles.dialogActions}>
                            <Button variant="secondary" onPress={() => setLinkOpen(false)}>Скасувати</Button>
                            <Button onPress={() => void fromLink()} pending={busy} pendingLabel="Завантажуємо…" isDisabled={!url.startsWith('https://')}>Вставити</Button>
                        </div>
                    </Dialog>
                </Modal>
            </ModalOverlay>
        </>
    );
}
