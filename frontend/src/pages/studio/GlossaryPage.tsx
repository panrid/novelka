import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { useDebounced } from '../../lib/useDebounced';
import {
    KIND_LABELS, autotranslateApi, type Gender, type GlossaryItem, type GlossaryKind, type GlossaryStatus,
} from '../../studio/autotranslate';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Pager } from '../../ui/Pager';
import { Segmented } from '../../ui/Segmented';
import { TextInput } from '../../ui/TextInput';
import { useEditionId } from './EditionPage';
import styles from './studio.module.css';

const GENDER_LABELS: Record<Gender, string> = { male: 'чоловічий', female: 'жіночий', unknown: 'невідомо' };
const STATUS_LABELS: Record<GlossaryStatus, string> = { new: 'Нові', approved: 'Затверджені', rejected: 'Відхилені' };
const STATUS_BADGE: Record<GlossaryStatus, string> = { new: 'нове', approved: 'затверджено', rejected: 'відхилено' };

/**
 * Names and terms as the translation writes them. The model fills it while analysing;
 * the owner goes chapter by chapter, approves or rejects (a rejected entry never reaches
 * the translation), and corrects what needs it.
 */
export function GlossaryPage() {
    const id = useEditionId();
    const client = useQueryClient();
    const [chosen, setStatus] = useState<GlossaryStatus | 'all' | null>(null);
    // Until a filter is picked: the new entries to review, or everything once none are new.
    const [counts, setCounts] = useState<Record<GlossaryStatus, number> | null>(null);
    const status: GlossaryStatus | 'all' = chosen ?? (counts && counts.new === 0 ? 'all' : 'new');
    const [chapter, setChapter] = useState<number | undefined>(undefined);
    const [sort, setSort] = useState<'alpha' | 'chapter'>('alpha');
    const [search, setSearch] = useState('');
    const [page, setPage] = useState(1);
    const [selecting, setSelecting] = useState(false);
    const [selected, setSelected] = useState<Set<number>>(new Set());
    const [editing, setEditing] = useState<number | null>(null);
    const q = useDebounced(search, 300);
    const filter = { ...(status === 'all' ? {} : { status }), ...(chapter ? { chapter } : {}), q, sort, page };
    const entries = useQuery({
        queryKey: ['glossary', id, filter],
        queryFn: () => autotranslateApi.glossary(id, filter),
        placeholderData: (previous) => previous,
    });
    const data = entries.data;
    if (data && data.counts !== counts && JSON.stringify(data.counts) !== JSON.stringify(counts)) setCounts(data.counts);
    const refresh = () => void client.invalidateQueries({ queryKey: ['glossary', id] });
    const change = useMutation({
        mutationFn: (next: GlossaryStatus) => autotranslateApi.setStatus(id, [...selected], next),
        onSuccess: () => { setSelected(new Set()); refresh(); },
    });
    const reset = (apply: () => void) => { apply(); setPage(1); setSelected(new Set()); };
    const chapters = data?.chapters ?? [];
    const chapterAt = chapter ? chapters.indexOf(chapter) : -1;
    // Readers' numbers, not positions: a prologue is «0» or goes by its title.
    const shown = (n: number) => {
        const label = data?.labels?.[n] ?? String(n);
        return /^\d/.test(label) ? { from: `з глави ${label}`, badge: `гл. ${label}` } : { from: `з «${label}»`, badge: label };
    };
    const toggle = (entryId: number) => setSelected((current) => {
        const next = new Set(current);
        if (next.has(entryId)) next.delete(entryId); else next.add(entryId);
        return next;
    });

    return (
        <section className={styles.page}>
            <Link to="/studio/$editionId/translate" params={{ editionId: String(id) }} className={styles.muted}>‹ Автопереклад</Link>
            <h1 className={styles.title}>Словник</h1>
            <p className={styles.muted} style={{ marginBottom: 12 }}>
                Імена й терміни, які автопереклад пише однаково в усіх главах. Відхилені до перекладу не потрапляють; виправлення
                діє з наступної перекладеної глави.
            </p>

            <div className={styles.filters} role="group" aria-label="Стан">
                {(['new', 'approved', 'rejected', 'all'] as const).map((value) => (
                    <button key={value} type="button" className={`${styles.chip} ${status === value ? styles.chipOn : ''}`}
                        aria-pressed={status === value} onClick={() => reset(() => setStatus(value))}>
                        {value === 'all' ? 'Усі' : STATUS_LABELS[value]}{value !== 'all' && data ? ` · ${data.counts[value] ?? 0}` : ''}
                    </button>
                ))}
            </div>
            <div className={styles.filters} style={{ margin: '12px 0' }}>
                <label>
                    <div className={styles.label}>Глава</div>
                    <select className={styles.select} value={chapter ?? ''} aria-label="Глава"
                        onChange={(event) => reset(() => setChapter(event.target.value ? Number(event.target.value) : undefined))}>
                        <option value="">Усі глави</option>
                        {chapters.map((n) => <option key={n} value={n}>{shown(n).from}</option>)}
                    </select>
                </label>
                <Button variant="secondary" isDisabled={chapterAt <= 0} onPress={() => reset(() => setChapter(chapters[chapterAt - 1]))}>‹</Button>
                <Button variant="secondary" isDisabled={chapters.length === 0 || chapterAt === chapters.length - 1}
                    onPress={() => reset(() => setChapter(chapters[chapterAt + 1]))}>›</Button>
            </div>
            <TextInput label="Пошук" value={search} onChange={(value) => { setSearch(value); setPage(1); }} />
            <Segmented label="Порядок" value={sort} onChange={(value) => reset(() => setSort(value))}
                options={[{ value: 'alpha', label: 'За абеткою' }, { value: 'chapter', label: 'За главами' }]} />

            <div className={styles.actions}>
                {!selecting
                    ? <Button variant="secondary" onPress={() => setSelecting(true)} isDisabled={!data?.items.length}>Виділити</Button>
                    : <Button variant="secondary" onPress={() => setSelected(new Set(data?.items.map((entry) => entry.id)))}>Виділити всі на сторінці</Button>}
                {data && <span className={styles.muted} style={{ alignSelf: 'center' }}>Знайдено: {data.total}</span>}
            </div>

            {entries.isError && <Notice tone="error">{entries.error.message}</Notice>}
            {data?.total === 0 && <p className={styles.muted}>Тут порожньо.</p>}
            {data?.items.map((entry) => editing === entry.id && !selecting
                ? <EntryForm key={entry.id} editionId={id} entry={entry} onDone={() => { setEditing(null); refresh(); }} />
                : (
                    <div key={entry.id} className={styles.row}>
                        {selecting && (
                            <input type="checkbox" className={styles.check} checked={selected.has(entry.id)} aria-label={`Виділити ${entry.ukrainian}`}
                                onChange={() => toggle(entry.id)} />
                        )}
                        <button type="button" className={`${styles.grow} ${styles.plainButton}`}
                            onClick={() => (selecting ? toggle(entry.id) : setEditing(entry.id))}>
                            <b>{entry.ukrainian}</b>
                            <div className={styles.muted}>
                                {KIND_LABELS[entry.kind]}
                                {entry.gender && entry.gender !== 'unknown' ? ` · ${GENDER_LABELS[entry.gender]}` : ''}
                                {entry.note ? ` · ${entry.note}` : ''}
                            </div>
                        </button>
                        <span className={`${styles.badge} ${entry.status === 'new' ? styles.badgeOn : ''}`}>{STATUS_BADGE[entry.status]}</span>
                        {entry.chapter ? <span className={styles.badge}>{shown(entry.chapter).badge}</span> : null}
                    </div>
                ))}
            {data && <Pager page={page} total={data.total} size={50} onPage={(next) => { setPage(next); setSelected(new Set()); }} />}

            {selecting && (
                <div className={styles.selectionBar} role="toolbar" aria-label="Дії з виділеним">
                    <Button onPress={() => change.mutate('approved')} isDisabled={selected.size === 0} pending={change.isPending}>
                        Затвердити ({selected.size})
                    </Button>
                    <Button variant="danger" onPress={() => change.mutate('rejected')} isDisabled={selected.size === 0}>Відхилити</Button>
                    {status !== 'new' && <Button variant="secondary" onPress={() => change.mutate('new')} isDisabled={selected.size === 0}>У нові</Button>}
                    <Button variant="secondary" onPress={() => { setSelecting(false); setSelected(new Set()); }}>Скасувати</Button>
                    {change.isError && <Notice tone="error">{change.error.message}</Notice>}
                </div>
            )}
        </section>
    );
}

function EntryForm({ editionId, entry, onDone }: { editionId: number; entry: GlossaryItem; onDone: () => void }) {
    const [ukrainian, setUkrainian] = useState(entry.ukrainian);
    const [kind, setKind] = useState<GlossaryKind>(entry.kind);
    const [gender, setGender] = useState<Gender>(entry.gender ?? 'unknown');
    const [note, setNote] = useState(entry.note ?? '');
    const save = useMutation({ mutationFn: () => autotranslateApi.updateEntry(editionId, entry.id, { ukrainian, kind, gender, note }), onSuccess: onDone });
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
            <p className={styles.muted}>Збережений запис стає затвердженим.</p>
            {save.isError && <Notice tone="error">{save.error.message}</Notice>}
            <div className={styles.actions}>
                <Button type="submit" pending={save.isPending} pendingLabel="Зберігаємо…">Зберегти</Button>
                <Button variant="secondary" onPress={onDone}>Скасувати</Button>
            </div>
        </form>
    );
}
