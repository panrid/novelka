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
import { ImagePlus, Link2, Minus, Redo2, Sparkles, Undo2 } from 'lucide-react';
import { useImperativeHandle, useRef, useState } from 'react';
import { Dialog, Heading as DialogHeading, Modal, ModalOverlay } from 'react-aria-components';
import { Button } from '../ui/Button';
import { Notice } from '../ui/Notice';
import { TextInput } from '../ui/TextInput';
import { studioApi, type StudioBlock } from './api';
import { toBlocks, toDocument } from './document';
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
export type EditorHandle = { read: () => StudioBlock[] };

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
};

export function TextEditor({ handle, blocks, onChange, mode, mayAddPictures = false, label, placeholder, onIllustrate }: Props) {
    const chapter = mode === 'chapter';
    const editor = useEditor({
        extensions: [
            Document, Text, Paragraph, Bold, Italic, Underline, Strike, UndoRedo, BlockIdentity,
            Placeholder.configure({ placeholder: placeholder ?? '' }),
            ...(chapter ? [Heading.configure({ levels: [2] }), HorizontalRule, Picture.configure({ inline: false })] : []),
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
    });

    useImperativeHandle(handle, () => ({
        read: () => {
            if (!editor) return blocks;
            // Take in what the keyboard typed but the editor has not read from the page yet.
            (editor.view as unknown as { domObserver?: { flush: () => void } }).domObserver?.flush();
            return toBlocks(editor.getJSON());
        },
    }), [editor, blocks]);

    if (!editor) {
        return null;
    }
    return (
        <div className={styles.wrap}>
            <EditorContent editor={editor} />
            <Toolbar editor={editor} chapter={chapter} mayAddPictures={chapter && mayAddPictures}
                onIllustrate={chapter && mayAddPictures ? onIllustrate : undefined} />
        </div>
    );
}

function Toolbar({ editor, chapter, mayAddPictures, onIllustrate }: {
    editor: Editor; chapter: boolean; mayAddPictures: boolean; onIllustrate: Props['onIllustrate'];
}) {
    const state = useEditorState({
        editor,
        selector: ({ editor: e }) => ({
            bold: e.isActive('bold'), italic: e.isActive('italic'), underline: e.isActive('underline'), strike: e.isActive('strike'),
            undo: e.can().undo(), redo: e.can().redo(),
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
            <span className={styles.spacer} />
            {state.undo && button('Скасувати', false, () => editor.chain().focus().undo().run(), <Undo2 size={18} aria-hidden />)}
            {state.redo && button('Повторити', false, () => editor.chain().focus().redo().run(), <Redo2 size={18} aria-hidden />)}
        </div>
    );
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
