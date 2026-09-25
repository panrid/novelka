import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { chapterHeading } from '../../reading/api';
import { KIND_LABELS, autotranslateApi, type ChapterAnalysis, type Gender, type GlossaryItem, type GlossaryKind } from '../../studio/autotranslate';
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
    const client = useQueryClient();
    const entries = useQuery({ queryKey: ['glossary', id], queryFn: () => autotranslateApi.glossary(id) });
    const analysis = useQuery({ queryKey: ['analysis', id], queryFn: () => autotranslateApi.analysis(id) });
    const [filter, setFilter] = useState('');
    const [onlyNew, setOnlyNew] = useState(false);
    const [editing, setEditing] = useState<number | null>(null);
    const unchecked = (entries.data ?? []).filter((entry) => !entry.manual).length;
    const shown = (entries.data ?? [])
        .filter((entry) => !onlyNew || !entry.manual)
        .filter((entry) => entry.ukrainian.toLowerCase().includes(filter.trim().toLowerCase()));
    const checkAll = useMutation({
        mutationFn: () => autotranslateApi.allChecked(id),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['glossary', id] }),
    });

    return (
        <section className={styles.page}>
            <Link to="/studio/$editionId" params={{ editionId: String(id) }} className={styles.muted}>‹ До перекладу</Link>
            <h1 className={styles.title}>Словник</h1>
            <p className={styles.muted}>
                Імена й терміни, які автопереклад пише однаково в усіх главах. Виправлення діє з наступної перекладеної глави.
            </p>
            {(analysis.data?.length ?? 0) > 0 && (
                <>
                    <h2 className={styles.sectionTitle}>Назви глав перед перекладом</h2>
                    <p className={styles.muted}>Номер — як на сайті: 0, 12, 31.1 або порожньо, якщо без номера (пролог, побічна історія).</p>
                    {analysis.data!.map((chapter) => <AnalysisRow key={chapter.number} editionId={id} chapter={chapter} />)}
                </>
            )}
            <h2 className={styles.sectionTitle}>Імена й терміни</h2>
            {entries.isError && <Notice tone="error">{entries.error.message}</Notice>}
            {unchecked > 0 && (
                <div className={styles.actions}>
                    <Button variant={onlyNew ? 'primary' : 'secondary'} onPress={() => setOnlyNew(!onlyNew)}>
                        Неперевірені: {unchecked}
                    </Button>
                    <Button variant="secondary" onPress={() => checkAll.mutate()} pending={checkAll.isPending}>Усе перевірено</Button>
                </div>
            )}
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
                        {!entry.manual && <span className={`${styles.badge} ${styles.badgeOn}`}>не перевірено</span>}
                        {entry.chapter && <span className={styles.badge}>гл. {entry.chapter}</span>}
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

/** One analysed chapter: its number on the site and its title, fixed before translating. */
function AnalysisRow({ editionId, chapter }: { editionId: number; chapter: ChapterAnalysis }) {
    const client = useQueryClient();
    const [label, setLabel] = useState(chapter.label ?? String(chapter.number));
    const [title, setTitle] = useState(chapter.title);
    const changed = label !== (chapter.label ?? String(chapter.number)) || title !== chapter.title;
    const save = useMutation({
        mutationFn: () => autotranslateApi.editAnalysis(editionId, chapter.number,
            { title, label: label.trim() === String(chapter.number) ? null : label.trim() }),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['analysis', editionId] }),
    });
    return (
        <form className={`${styles.entry} ${styles.analysisRow}`} onSubmit={(event) => { event.preventDefault(); save.mutate(); }}
            aria-label={`Глава ${chapter.number}: ${chapterHeading({ ...chapter, title })}`}>
            <TextInput label="№" value={label} onChange={setLabel} inputMode="decimal" />
            <TextInput label="Назва" value={title} onChange={setTitle} />
            {changed && <Button type="submit" pending={save.isPending}>Зберегти</Button>}
            {save.isError && <Notice tone="error">{save.error.message}</Notice>}
        </form>
    );
}
