import { Extension } from '@tiptap/core';
import type { Node as ProseNode } from '@tiptap/pm/model';
import { Plugin, PluginKey, TextSelection, type Selection } from '@tiptap/pm/state';
import { Decoration, DecorationSet, type EditorView } from '@tiptap/pm/view';
import styles from './TextEditor.module.css';

/** A paragraph of the original; its id is the id of its translation (рішення 30). */
export type GridOriginal = { id: string; type: string; text: string };

/** What the grid draws: off on a phone, and the original only while its column is shown. */
export type GridSource = { on: boolean; original: GridOriginal[] | null };

type GridState = { source: GridSource; version: number; decorations: DecorationSet };

/** A transaction with this meta tells the grid what to draw: `tr.setMeta(gridKey, source)`. */
export const gridKey = new PluginKey<GridState>('workspaceGrid');

/**
 * The workspace grid (новий дизайн, Workspace.dc.html, рішення 9): «№ · оригінал · переклад» on one
 * grid. The translation stays one editor — its paragraphs are the grid's third column — and before
 * each of them two cells nobody types in: the paragraph's number and its original. A paragraph of
 * the original nobody translated yet gets a row of its own, where a click starts its translation.
 * Each row is as tall as its taller side, so the two texts never drift apart.
 */
export const WorkspaceGrid = Extension.create({
    name: 'workspaceGrid',
    addProseMirrorPlugins() {
        return [new Plugin<GridState>({
            key: gridKey,
            state: {
                init: () => ({ source: { on: false, original: null }, version: 0, decorations: DecorationSet.empty }),
                apply: (transaction, old, _before, state) => {
                    const told = transaction.getMeta(gridKey) as GridSource | undefined;
                    if (told) {
                        const version = old.version + 1;
                        return { source: told, version, decorations: build(state.doc, state.selection, told, version) };
                    }
                    if (!transaction.docChanged && !transaction.selectionSet) return old;
                    return { ...old, decorations: build(state.doc, state.selection, old.source, old.version) };
                },
            },
            props: { decorations: (state) => gridKey.getState(state)?.decorations },
        })];
    },
});

function build(doc: ProseNode, selection: Selection, source: GridSource, version: number): DecorationSet {
    if (!source.on) return DecorationSet.empty;
    const original = source.original ?? [];
    const index = new Map(original.map((block, at) => [block.id, at]));
    const inText = new Set<string>();
    doc.forEach((node) => {
        const id = node.attrs.blockId as string | null;
        if (id) inText.add(id);
    });
    const active = selection.$from.depth >= 1 ? selection.$from.before(1) : -1;
    const decorations: Decoration[] = [];
    const used = new Set<string>();
    let row = 0;
    let next = 0;
    // Paragraphs of the original before position `upTo` that have no translation in the text.
    const untranslated = (upTo: number, at: number) => {
        for (; next < upTo; next++) {
            const block = original[next]!;
            if (inText.has(block.id)) continue;
            row++;
            decorations.push(Decoration.widget(at, missingRow(row, block), {
                side: -2, key: `m:${version}:${block.id}:${row}`, ignoreSelection: true, stopEvent: () => true,
            }));
        }
    };
    doc.forEach((node, offset) => {
        const id = node.attrs.blockId as string | null;
        let from: GridOriginal | undefined;
        if (id && !used.has(id) && index.has(id)) {
            used.add(id);
            const at = index.get(id)!;
            if (at >= next) {
                untranslated(at, offset);
                next = at + 1;
            }
            from = original[at];
        }
        row++;
        const lit = offset === active;
        decorations.push(Decoration.widget(offset, leadCells(row, from, lit), {
            side: -1, key: `l:${version}:${row}:${lit ? 1 : 0}:${from?.id ?? ''}`, ignoreSelection: true, stopEvent: () => true,
        }));
        if (lit) decorations.push(Decoration.node(offset, offset + node.nodeSize, { class: styles.rowActive ?? '' }));
    });
    untranslated(original.length, doc.content.size);
    return DecorationSet.create(doc, decorations);
}

/** The number (a click puts the cursor into the paragraph) and the original of one row. */
function leadCells(row: number, from: GridOriginal | undefined, lit: boolean) {
    // Positions move as the text is typed while the cells stay: where they stand is asked on the click.
    return (view: EditorView, getPos: () => number | undefined) => {
        const cells = document.createElement('span');
        cells.className = styles.gridLead ?? '';
        cells.append(numberCell(row, lit, () => {
            const offset = getPos();
            if (offset === undefined) return;
            const selection = TextSelection.near(view.state.doc.resolve(Math.min(offset + 1, view.state.doc.content.size)));
            view.dispatch(view.state.tr.setSelection(selection));
            view.focus();
        }), originalCell(from, lit));
        return cells;
    };
}

/** A paragraph of the original with no translation yet: a click adds an empty one in its place to type into. */
function missingRow(row: number, block: GridOriginal) {
    return (view: EditorView, getPos: () => number | undefined) => {
        const cells = document.createElement('span');
        cells.className = `${styles.gridLead ?? ''} ${styles.gridMissing ?? ''}`;
        const start = () => {
            const at = getPos();
            if (at === undefined) return;
            const paragraph = view.state.schema.nodes.paragraph!.create({ blockId: block.id });
            const transaction = view.state.tr.insert(at, paragraph);
            view.dispatch(transaction.setSelection(TextSelection.create(transaction.doc, at + 1)));
            view.focus();
        };
        const empty = document.createElement('button');
        empty.type = 'button';
        empty.className = styles.gridEmpty ?? '';
        empty.textContent = 'Ще не перекладено · вписати переклад';
        empty.addEventListener('mousedown', (event) => event.preventDefault());
        empty.addEventListener('click', start);
        cells.append(numberCell(row, false, start), originalCell(block, false), empty);
        return cells;
    };
}

function numberCell(row: number, lit: boolean, onPick: () => void) {
    const number = document.createElement('button');
    number.type = 'button';
    number.tabIndex = -1;
    number.className = `${styles.gridNumber ?? ''} ${lit ? styles.gridNumberLit ?? '' : ''}`;
    number.textContent = String(row);
    number.setAttribute('aria-label', `Абзац ${row}`);
    number.addEventListener('mousedown', (event) => event.preventDefault());
    number.addEventListener('click', onPick);
    return number;
}

function originalCell(from: GridOriginal | undefined, lit: boolean) {
    const cell = document.createElement('span');
    cell.className = `${styles.gridOriginal ?? ''} ${lit ? styles.gridOriginalLit ?? '' : ''}`;
    cell.lang = 'und';
    if (from) {
        cell.textContent = from.type === 'separator' ? '◇' : from.text;
        cell.dataset.block = from.id;
    }
    return cell;
}
