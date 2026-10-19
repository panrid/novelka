import { Editor } from '@tiptap/core';
import Bold from '@tiptap/extension-bold';
import Document from '@tiptap/extension-document';
import Paragraph from '@tiptap/extension-paragraph';
import Text from '@tiptap/extension-text';
import { UndoRedo } from '@tiptap/extensions';
import { afterEach, describe, expect, it } from 'vitest';
import { replaceAll } from './TextEditor';

let editor: Editor | null = null;
afterEach(() => editor?.destroy());

describe('«Замінити в главі» (етап 19)', () => {
    it('replaces every occurrence in every paragraph, keeps the formatting and undoes in one step', () => {
        editor = new Editor({
            extensions: [Document, Paragraph, Text, Bold, UndoRedo],
            content: '<p>Велика лінза світила. Лінза гасла.</p><p>Він глянув на <b>велику лінзу</b> й на лінзу ще раз.</p>',
        });
        // Case counts: «Лінза» at a sentence's start is another word here.
        expect(replaceAll(editor, 'лінз', 'лінз')).toBe(3);
        expect(replaceAll(editor, 'велику лінзу', 'великий кристал')).toBe(1);
        expect(editor.getHTML()).toContain('<strong>великий кристал</strong>');
        expect(replaceAll(editor, 'лінза', 'призма')).toBe(1);
        expect(editor.getText()).toContain('Велика призма світила. Лінза гасла.');
        editor.commands.undo();
        expect(editor.getText()).toContain('Велика лінза світила.');
        expect(replaceAll(editor, 'немає такого', 'x')).toBe(0);
    });
});
