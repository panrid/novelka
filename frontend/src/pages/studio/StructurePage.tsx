import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useMemo, useState } from 'react';
import { useDebounced } from '../../lib/useDebounced';
import {
    VOLUME_KINDS, structureApi, volumeIndexes, volumeName, volumeOf,
    type Numbering, type Structure, type StructureChange, type Volume, type VolumeKind,
} from '../../studio/structure';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
import { Sheet } from '../../ui/Sheet';
import { TextInput } from '../../ui/TextInput';
import { useEditionId } from './EditionPage';
import styles from './structure.module.css';

/**
 * «Структура й томи» (етап 15): mark chapters, start a volume there, make them a prologue or
 * side stories; every chapter's old and new number shows before anything is saved.
 */
export function StructurePage() {
    const id = useEditionId();
    const structure = useQuery({ queryKey: ['structure', id], queryFn: () => structureApi.get(id) });
    if (structure.isError) return <section className={styles.page}><Notice tone="error">{structure.error.message}</Notice></section>;
    if (!structure.data) return <section className={styles.page}><p className={styles.muted}>Завантажуємо…</p></section>;
    return <Editor key={structure.dataUpdatedAt} id={id} saved={structure.data} />;
}

function Editor({ id, saved }: { id: number; saved: Structure }) {
    const client = useQueryClient();
    const [volumes, setVolumes] = useState<Volume[]>(saved.volumes);
    const [numbering, setNumbering] = useState<Numbering>(saved.numbering);
    const [automatic, setAutomatic] = useState<number[]>([]);
    const [unnumbered, setUnnumbered] = useState<number[]>([]);
    const [selected, setSelected] = useState<number[]>([]);
    const [editing, setEditing] = useState<Volume | null>(null);
    const [done, setDone] = useState(false);
    const chapters = saved.chapters;
    const exists = new Set(chapters.map((chapter) => chapter.number));

    const change: StructureChange = { numbering, volumes, automatic, unnumbered };
    const changed = JSON.stringify({ numbering, volumes: sorted(volumes) }) !== JSON.stringify({ numbering: saved.numbering, volumes: sorted(saved.volumes) })
        || automatic.length > 0 || unnumbered.length > 0;
    const settled = useDebounced(JSON.stringify(change), 300);
    const preview = useQuery({
        queryKey: ['structure-preview', id, settled],
        queryFn: () => structureApi.preview(id, JSON.parse(settled) as StructureChange),
        enabled: changed,
        placeholderData: (previous) => previous,
    });
    const save = useMutation({
        mutationFn: () => structureApi.save(id, change),
        onSuccess: (result) => {
            client.setQueryData(['structure', id], result);
            void client.invalidateQueries({ queryKey: ['studio-chapters', id] });
            setDone(true);
        },
    });

    const first = selected.length ? Math.min(...selected) : 0;
    const last = selected.length ? Math.max(...selected) : 0;
    const touch = (update: () => void) => { update(); setDone(false); };

    /** Chapters first..last become one volume of this kind; the chapter after them stays where it was. */
    const wrap = (kind: VolumeKind, title: string) => touch(() => {
        const after = last + 1;
        const before = volumeOf(volumes, after);
        let next = volumes.filter((volume) => volume.firstNumber < first || volume.firstNumber > last);
        next = [...next, { firstNumber: first, title, kind }];
        if (exists.has(after) && !next.some((volume) => volume.firstNumber === after)) {
            next.push(before ? { ...before, firstNumber: after } : { firstNumber: after, title: '', kind: 'volume' });
        }
        setVolumes(sorted(next));
        if (kind !== 'volume') {
            // A prologue or side stories show no number: typed ones there («0») give way.
            const inside = chapters.map((chapter) => chapter.number).filter((number) => number >= first && number <= last);
            setAutomatic([...new Set([...automatic, ...inside])]);
            setUnnumbered(unnumbered.filter((number) => number < first || number > last));
        }
        setSelected([]);
    });
    const startVolume = () => touch(() => {
        setVolumes(sorted([...volumes.filter((volume) => volume.firstNumber !== first), { firstNumber: first, title: '', kind: 'volume' }]));
        setEditing({ firstNumber: first, title: '', kind: 'volume' });
        setSelected([]);
    });
    const setNumbers = (to: 'auto' | 'none') => touch(() => {
        const marked = new Set(selected);
        if (to === 'auto') {
            setAutomatic([...new Set([...automatic, ...selected])]);
            setUnnumbered(unnumbered.filter((number) => !marked.has(number)));
        } else {
            setUnnumbered([...new Set([...unnumbered, ...selected])]);
            setAutomatic(automatic.filter((number) => !marked.has(number)));
        }
        setSelected([]);
    });
    const toggle = (number: number) => setSelected(selected.includes(number) ? selected.filter((n) => n !== number) : [...selected, number]);

    const indexes = volumeIndexes(volumes);
    const groups = useMemo(() => group(chapters, volumes), [chapters, volumes]);
    const shownNow = (chapter: Structure['chapters'][number]) => chapter.label ?? String(chapter.number);

    return (
        <section className={styles.page}>
            <Link to="/studio/$editionId" params={{ editionId: String(id) }} className={styles.muted}>‹ До перекладу</Link>
            <h1 className={styles.title}>Структура й томи</h1>
            <p className={styles.muted}>
                Позначте глави й виберіть дію. Адреси глав не змінюються — закладки й «Продовжити» в читачів лишаться.
                Новий номер видно поруч зі старим, доки ви не збережете.
            </p>

            <div className={styles.layout}>
                <div className={styles.list}>
                    {chapters.length === 0 && <p className={styles.empty}>Глав ще немає.</p>}
                    {groups.map(({ volume, rows }) => (
                        <div key={volume?.firstNumber ?? 0} className={styles.group}>
                            {volume ? (
                                <div className={styles.volume}>
                                    <b>{volumeName(volume, indexes.get(volume.firstNumber))}</b>
                                    <span className={volume.kind === 'volume' ? styles.chipOn : styles.chip}>
                                        {volume.kind === 'volume' ? 'рахується' : 'без номерів'}
                                    </span>
                                    <span className={styles.grow} />
                                    <button type="button" className={styles.link} onClick={() => setEditing(volume)}>змінити</button>
                                    <button type="button" className={styles.link}
                                        onClick={() => touch(() => setVolumes(volumes.filter((v) => v.firstNumber !== volume.firstNumber)))}>прибрати</button>
                                </div>
                            ) : volumes.length > 0 && rows.length > 0 ? (
                                <div className={styles.volume}><span className={styles.muted}>Поза томами</span></div>
                            ) : null}
                            {rows.map((chapter) => {
                                const now = shownNow(chapter);
                                const next = changed ? preview.data?.[String(chapter.number)] : undefined;
                                const differs = next !== undefined && next !== now;
                                const own = (chapter.manual && !automatic.includes(chapter.number)) || unnumbered.includes(chapter.number);
                                return (
                                    <label key={chapter.number} className={`${styles.row} ${selected.includes(chapter.number) ? styles.selected : ''}`}>
                                        <input type="checkbox" checked={selected.includes(chapter.number)} onChange={() => toggle(chapter.number)}
                                            aria-label={`Глава ${now || chapter.title}`} />
                                        <span className={styles.number}>{differs ? <s>{now || '—'}</s> : (now || '—')}</span>
                                        {differs && <span className={styles.next}>{next || '—'}</span>}
                                        <span className={styles.name}>{chapter.title || 'Без назви'}</span>
                                        {own && <span className={styles.chip} title="Номер вписано вручну — автоматичний його не змінює">свій номер</span>}
                                        {!chapter.published && <span className={styles.chip}>не опубліковано</span>}
                                    </label>
                                );
                            })}
                        </div>
                    ))}
                </div>

                <aside className={styles.panel}>
                    {selected.length > 0 ? (
                        <div className={styles.card}>
                            <b>{selected.length === 1 ? `Обрано главу` : `Обрано глави`} {first === last ? '' : `з ${shownOf(chapters, first)} по ${shownOf(chapters, last)}`}</b>
                            <Button onPress={startVolume}>Почати новий том тут</Button>
                            <Button variant="secondary" onPress={() => wrap('prologue', 'Пролог')}>Виокремити в пролог</Button>
                            <Button variant="secondary" onPress={() => wrap('side', 'Побічні історії')}>Зробити побічними історіями</Button>
                            <Button variant="secondary" onPress={() => wrap('extra', 'Екстра')}>Зробити екстрою</Button>
                            <div className={styles.inline}>
                                <button type="button" className={styles.link} onClick={() => setNumbers('auto')}>автоматичний номер</button>
                                <button type="button" className={styles.link} onClick={() => setNumbers('none')}>без номера</button>
                                <button type="button" className={styles.link} onClick={() => setSelected([])}>зняти позначки</button>
                            </div>
                        </div>
                    ) : (
                        <p className={styles.muted}>Позначте одну чи кілька глав, щоб почати том, пролог чи побічні історії.</p>
                    )}
                    <div className={styles.card}>
                        <b>Нумерація</b>
                        <Segmented label="Як рахувати" value={numbering} onChange={(value) => touch(() => setNumbering(value))} options={[
                            { value: 'continuous', label: 'Наскрізна' },
                            { value: 'per_volume', label: 'З 1 у кожному томі' },
                        ]} />
                        <span className={styles.muted}>Глави в пролозі, побічних історіях й екстрі номерів не мають. Свої номери (437.2) лишаються як є.</span>
                    </div>
                    {save.isError && <Notice tone="error">{save.error.message}</Notice>}
                    {done && !changed && <Notice tone="success">Збережено. Читачі вже бачать томи й нові номери.</Notice>}
                    <Button onPress={() => save.mutate()} pending={save.isPending} pendingLabel="Зберігаємо…" isDisabled={!changed}>Зберегти</Button>
                    {changed && (
                        <button type="button" className={styles.link} onClick={() => {
                            setVolumes(saved.volumes); setNumbering(saved.numbering); setAutomatic([]); setUnnumbered([]); setSelected([]);
                        }}>Скасувати зміни</button>
                    )}
                </aside>
            </div>

            {editing && (
                <VolumeSheet volume={editing} onClose={() => setEditing(null)} onSave={(updated) => touch(() => {
                    setVolumes(sorted([...volumes.filter((v) => v.firstNumber !== updated.firstNumber), updated]));
                    setEditing(null);
                })} />
            )}
        </section>
    );
}

function VolumeSheet({ volume, onClose, onSave }: { volume: Volume; onClose: () => void; onSave: (volume: Volume) => void }) {
    const [title, setTitle] = useState(volume.title);
    const [kind, setKind] = useState<VolumeKind>(volume.kind);
    return (
        <Sheet open onClose={onClose} title="Том">
            <form className={styles.sheetForm} onSubmit={(event) => { event.preventDefault(); onSave({ ...volume, title: title.trim(), kind }); }}>
                <TextInput label="Назва" value={title} onChange={setTitle} autoFocus
                    placeholder={kind === 'volume' ? 'Наприклад, Повільне життя' : VOLUME_KINDS[kind]}
                    hint={kind === 'volume' ? 'Номер тому сайт поставить сам: «Том 2. Назва».' : undefined} />
                <Segmented label="Вид" value={kind} onChange={setKind}
                    options={(Object.keys(VOLUME_KINDS) as VolumeKind[]).map((value) => ({ value, label: VOLUME_KINDS[value] }))} />
                <p className={styles.muted}>{kind === 'volume' ? 'Глави тому мають номери.' : 'Глави тут без номерів і не забирають їх у наступних.'}</p>
                <Button type="submit">Готово</Button>
            </form>
        </Sheet>
    );
}

function sorted(volumes: Volume[]): Volume[] {
    return [...volumes].sort((a, b) => a.firstNumber - b.firstNumber);
}

function group(chapters: Structure['chapters'], volumes: Volume[]) {
    const out: { volume: Volume | undefined; rows: Structure['chapters'] }[] = [];
    for (const chapter of chapters) {
        const volume = volumeOf(volumes, chapter.number);
        const lastGroup = out[out.length - 1];
        if (lastGroup && lastGroup.volume === volume) lastGroup.rows.push(chapter);
        else out.push({ volume, rows: [chapter] });
    }
    // A volume that starts after the last chapter waits for new ones.
    sorted(volumes).filter((volume) => !out.some((g) => g.volume === volume)).forEach((volume) => out.push({ volume, rows: [] }));
    return out;
}

function shownOf(chapters: Structure['chapters'], number: number): string {
    const chapter = chapters.find((c) => c.number === number);
    return chapter ? (chapter.label || chapter.title || String(number)) : String(number);
}
