import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { KIND_LABELS, autotranslateApi, type Gender, type GlossaryItem, type GlossaryKind } from '../../studio/autotranslate';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import { useEditionId } from './EditionPage';
import styles from './studio.module.css';

const GENDER_LABELS: Record<Gender, string> = { male: 'чоловічий', female: 'жіночий', unknown: 'невідомо' };

/**
 * Names and terms as the translation writes them. The model fills it while analysing
 * chapters; a correction here is used from the next chapter on.
 */
export function GlossaryPage() {
    const id = useEditionId();
    const entries = useQuery({ queryKey: ['glossary', id], queryFn: () => autotranslateApi.glossary(id) });
    const [filter, setFilter] = useState('');
    const [editing, setEditing] = useState<number | null>(null);
    const shown = (entries.data ?? []).filter((entry) => entry.ukrainian.toLowerCase().includes(filter.trim().toLowerCase()));

    return (
        <section className={styles.page}>
            <Link to="/studio/$editionId" params={{ editionId: String(id) }} className={styles.muted}>‹ До перекладу</Link>
            <h1 className={styles.title}>Словник</h1>
            <p className={styles.muted}>
                Імена й терміни, які автопереклад пише однаково в усіх главах. Виправлення діє з наступної перекладеної глави.
            </p>
            {entries.isError && <Notice tone="error">{entries.error.message}</Notice>}
            {(entries.data?.length ?? 0) > 8 && <TextInput label="Пошук" value={filter} onChange={setFilter} />}
            {entries.data?.length === 0 && <p className={styles.muted}>Словник порожній: він заповниться під час перекладу.</p>}
            {shown.map((entry) => editing === entry.id
                ? <EntryForm key={entry.id} editionId={id} entry={entry} onDone={() => setEditing(null)} />
                : (
                    <button key={entry.id} type="button" className={`${styles.row} ${styles.plain}`} onClick={() => setEditing(entry.id)}>
                        <div className={styles.grow}>
                            <b>{entry.ukrainian}</b>
                            <div className={styles.muted}>
                                {KIND_LABELS[entry.kind]}
                                {entry.gender && entry.gender !== 'unknown' ? ` · ${GENDER_LABELS[entry.gender]}` : ''}
                                {entry.note ? ` · ${entry.note}` : ''}
                            </div>
                        </div>
                        {entry.chapter && <span className={styles.badge}>з глави {entry.chapter}</span>}
                    </button>
                ))}
        </section>
    );
}

function EntryForm({ editionId, entry, onDone }: { editionId: number; entry: GlossaryItem; onDone: () => void }) {
    const client = useQueryClient();
    const [ukrainian, setUkrainian] = useState(entry.ukrainian);
    const [kind, setKind] = useState<GlossaryKind>(entry.kind);
    const [gender, setGender] = useState<Gender>(entry.gender ?? 'unknown');
    const [note, setNote] = useState(entry.note ?? '');
    const done = () => { void client.invalidateQueries({ queryKey: ['glossary', editionId] }); onDone(); };
    const save = useMutation({ mutationFn: () => autotranslateApi.updateEntry(editionId, entry.id, { ukrainian, kind, gender, note }), onSuccess: done });
    const remove = useMutation({ mutationFn: () => autotranslateApi.deleteEntry(editionId, entry.id), onSuccess: done });
    return (
        <form className={`${styles.form} ${styles.entry}`} onSubmit={(event) => { event.preventDefault(); save.mutate(); }}>
            <TextInput label="Українською" value={ukrainian} onChange={setUkrainian} isRequired />
            <div className={styles.numbers}>
                <label>
                    <div className={styles.label}>Що це</div>
                    <select className={styles.select} value={kind} onChange={(event) => setKind(event.target.value as GlossaryKind)}>
                        {Object.entries(KIND_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                    </select>
                </label>
                <label>
                    <div className={styles.label}>Рід</div>
                    <select className={styles.select} value={gender} onChange={(event) => setGender(event.target.value as Gender)}>
                        {Object.entries(GENDER_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                    </select>
                </label>
            </div>
            <TextInput label="Примітка" value={note} onChange={setNote} hint="Хто це або що це — підказка для перекладу." />
            {(save.isError || remove.isError) && <Notice tone="error">{(save.error ?? remove.error)!.message}</Notice>}
            <div className={styles.actions}>
                <Button type="submit" pending={save.isPending} pendingLabel="Зберігаємо…">Зберегти</Button>
                <Button variant="secondary" onPress={onDone}>Скасувати</Button>
                <Button variant="danger" onPress={() => remove.mutate()} isDisabled={remove.isPending}>Видалити</Button>
            </div>
        </form>
    );
}
