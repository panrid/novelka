import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { KIND_LABELS, autotranslateApi, type Gender, type GlossaryKind } from '../../studio/autotranslate';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Sheet } from '../../ui/Sheet';
import { TextInput } from '../../ui/TextInput';
import styles from './studio.module.css';

/** What was selected in the editor: the words, the paragraph's id and its text as it is now. */
export type GlossaryPick = { selection: string; blockId: string | null; paragraph: string };

/**
 * «Словник» in the editor (етап 19, п. 4): the word selected in the translation, the piece of the
 * original it was translated from (the original's own text, found by AI, never a translation
 * back), both editable, then «Додати до словника».
 */
export function GlossarySheet({ editionId, chapter, pick, onClose }: {
    editionId: number; chapter: number; pick: GlossaryPick; onClose: () => void;
}) {
    const client = useQueryClient();
    const source = useQuery({
        queryKey: ['glossary-source', editionId, chapter, pick.blockId, pick.selection, pick.paragraph],
        queryFn: () => autotranslateApi.sourceOf(editionId, chapter, { blockId: pick.blockId ?? '', translation: pick.paragraph, selection: pick.selection }),
        enabled: pick.blockId !== null,
        retry: false,
        staleTime: Infinity,
    });
    const [ukrainian, setUkrainian] = useState(pick.selection);
    // Until the person types, the piece AI found.
    const [typed, setTyped] = useState<string | null>(null);
    const original = typed ?? source.data?.original ?? '';
    const [kind, setKind] = useState<GlossaryKind>('character');
    const [gender, setGender] = useState<Gender>('unknown');
    const add = useMutation({
        mutationFn: () => autotranslateApi.addEntry(editionId, { chapter, ukrainian: ukrainian.trim(), original: original.trim(), kind, gender }),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['glossary', editionId] }),
    });

    return (
        <Sheet open onClose={onClose} title="До словника">
            {add.isSuccess ? (
                <>
                    <Notice tone="success">«{ukrainian.trim()}» ← «{original.trim()}» — у словнику, погоджено.</Notice>
                    <Button wide onPress={onClose}>Готово</Button>
                </>
            ) : (
                <form className={styles.form} onSubmit={(event) => { event.preventDefault(); add.mutate(); }}>
                    <TextInput label="У перекладі" value={ukrainian} onChange={setUkrainian} isRequired />
                    {pick.blockId === null && <p className={styles.muted}>Цього абзацу в оригіналі немає — впишіть оригінал самі.</p>}
                    {source.isPending && pick.blockId !== null && <p className={styles.muted} aria-live="polite">Шукаємо в оригіналі…</p>}
                    {source.isError && <Notice tone="error">{source.error.message}</Notice>}
                    {source.data && source.data.paragraph === null && <p className={styles.muted}>У цієї глави немає оригіналу — впишіть його самі.</p>}
                    {source.data?.paragraph && (
                        <p className={styles.muted}>
                            {source.data.original ? <>З оригіналу: <b lang={source.data.language}>{source.data.original}</b></>
                                : 'ШІ не знайшов цих слів в оригіналі — скопіюйте їх з абзацу нижче.'}
                        </p>
                    )}
                    <TextInput label="В оригіналі" value={original} onChange={setTyped} isRequired />
                    {source.data?.paragraph && <blockquote className={styles.muted} lang={source.data.language}>{source.data.paragraph}</blockquote>}
                    <label>
                        <div className={styles.label}>Що це</div>
                        <select className={styles.select} value={kind} onChange={(event) => setKind(event.target.value as GlossaryKind)}>
                            {Object.entries(KIND_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                        </select>
                    </label>
                    {kind === 'character' && (
                        <label>
                            <div className={styles.label}>Стать</div>
                            <select className={styles.select} value={gender} onChange={(event) => setGender(event.target.value as Gender)}>
                                <option value="unknown">Невідомо</option>
                                <option value="female">Жіноча</option>
                                <option value="male">Чоловіча</option>
                            </select>
                        </label>
                    )}
                    {add.isError && <Notice tone="error">{add.error.message}</Notice>}
                    <Button type="submit" wide pending={add.isPending} pendingLabel="Додаємо…" isDisabled={!ukrainian.trim() || !original.trim()}>
                        Додати до словника
                    </Button>
                </form>
            )}
        </Sheet>
    );
}
