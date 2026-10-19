import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { useId, useState } from 'react';
import { useCanRun } from '../../ledger/api';
import { chaptersWord } from '../../reading/api';
import { relativeTime } from '../../lib/dates';
import { LANGUAGES, LANGUAGE_SHORT, originalsApi, type GivenChapter, type OriginalLanguage, type OriginalRow } from '../../studio/originals';
import { askConfirm } from '../../ui/ask';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Pager } from '../../ui/Pager';
import { useEditionId } from './EditionPage';
import { EditionShell, useEditionOverview } from './EditionShell';
import styles from './originals.module.css';
import { Loading } from '../../ui/Loading';

const PAGE = 50;

/** What the form holds: a chapter not saved yet with the number it will get, or a saved one. */
type Picked = { kind: 'new'; number: number | null } | { kind: 'saved'; number: number };

/**
 * «Оригінал» (етап 19; новий дизайн, рішення 8): one chapter at a time — its language, number, title
 * and text — and beside it the chapters that have an original; a click opens another one, «+ Нова
 * глава» the next. Several chapters in one field and files are gone: too many questions about how
 * to split them.
 */
export function OriginalsPage() {
    const id = useEditionId();
    const [page, setPage] = useState(1);
    const list = useQuery({ queryKey: ['originals', id, page], queryFn: () => originalsApi.list(id, page), placeholderData: keepPreviousData });
    const edition = useEditionOverview(id).data;
    const [picked, setPicked] = useState<Picked>({ kind: 'new', number: null });
    const [saved, setSaved] = useState<number | null>(null);
    const [language, setLanguage] = useState<OriginalLanguage | null>(null);
    const total = list.data?.total ?? 0;
    const nextNumber = list.data?.nextNumber ?? 1;
    const items = list.data?.items ?? [];
    const lastLanguage = language ?? items.at(-1)?.language ?? 'en';
    const current = picked.kind === 'saved' ? picked.number : (picked.number ?? nextNumber);
    const open = (next: Picked) => {
        setPicked(next);
        setSaved(null);
    };
    return (
        <EditionShell tab="originals">
            <div className={styles.page}>
                {/* The next number comes with the list: the form waits for it rather than start over when it arrives. */}
                {list.data || list.isError ? (
                    <OneChapter key={`${picked.kind}:${current}`} editionId={id} picked={picked} number={current} language={lastLanguage}
                        known={items.find((row) => row.number === current) ?? null} saved={saved}
                        onSaved={(number, used, next) => {
                            setLanguage(used);
                            setSaved(number);
                            setPicked(next ? { kind: 'new', number: Math.max(number + 1, nextNumber) } : { kind: 'saved', number });
                        }}
                        onRemoved={() => open({ kind: 'new', number: null })} />
                ) : <section className={styles.add}><Loading shape="lines" /></section>}
                <aside className={styles.listSide} aria-label="Глави оригіналу">
                    <div className={styles.listHead}>
                        <h2 className={styles.heading}>Глави</h2>
                        {list.data && (
                            <span className={styles.muted}>
                                {edition && edition.chapterCount > 0 ? `оригінал є в ${total} з ${edition.chapterCount}` : `${total} ${chaptersWord(total)}`}
                            </span>
                        )}
                    </div>
                    {list.isError && <Notice tone="error">{list.error.message}</Notice>}
                    {list.data && total === 0 && <p className={styles.muted}>Поки немає. Вставте оригінал першої глави ліворуч.</p>}
                    {items.length > 0 && (
                        <ol className={styles.rows}>
                            {items.map((row) => (
                                <li key={row.number}>
                                    <ChapterButton row={row} current={picked.kind === 'saved' && picked.number === row.number}
                                        onPick={() => open({ kind: 'saved', number: row.number })} />
                                </li>
                            ))}
                        </ol>
                    )}
                    <button type="button" className={`${styles.newChapter} ${picked.kind === 'new' ? styles.newChapterOn : ''}`}
                        aria-current={picked.kind === 'new' ? 'true' : undefined} onClick={() => open({ kind: 'new', number: null })}>
                        + Нова глава
                    </button>
                    <Pager page={page} total={total} size={PAGE} onPage={setPage} />
                    {list.data && total > 0 && (
                        <>
                            <Languages counts={list.data.languages} />
                            <ChangeLanguage editionId={id} />
                            <RemoveAll editionId={id} total={total} />
                        </>
                    )}
                </aside>
            </div>
        </EditionShell>
    );
}

function ChapterButton({ row, current, onPick }: { row: OriginalRow; current: boolean; onPick: () => void }) {
    return (
        <button type="button" className={`${styles.chapterItem} ${current ? styles.chapterItemOn : ''}`} aria-current={current ? 'true' : undefined}
            onClick={onPick}>
            <span className={styles.rowNumber}>{row.number}</span>
            <span className={styles.chapterText}>
                <span className={styles.chapterTitle} lang={row.language}>{row.title || 'Без заголовка'}</span>
                <span className={styles.chapterState}>{row.translated ? 'перекладено' : 'є оригінал'} · {LANGUAGE_SHORT[row.language]}</span>
            </span>
        </button>
    );
}

/** To translate from the originals; nothing while AI is hidden from the person (етап 19, п. 7). */
function TranslateLink({ editionId }: { editionId: number }) {
    const canRun = useCanRun();
    if (!canRun) return null;
    return (
        <Link to="/studio/$editionId/translate" params={{ editionId: String(editionId) }} className={styles.translate}>
            Перекласти →
        </Link>
    );
}

/** The chapter in the form: a new one, or a saved one loaded into the same fields. */
function OneChapter({ editionId, picked, number, language, known, saved, onSaved, onRemoved }: {
    editionId: number; picked: Picked; number: number; language: OriginalLanguage; known: OriginalRow | null; saved: number | null;
    onSaved: (number: number, language: OriginalLanguage, next: boolean) => void; onRemoved: () => void;
}) {
    const isNew = picked.kind === 'new';
    const original = useQuery({
        queryKey: ['original', editionId, number],
        queryFn: () => originalsApi.one(editionId, number),
        enabled: !isNew,
    });
    if (!isNew && original.isError) return <section className={styles.add}><Notice tone="error">{original.error.message}</Notice></section>;
    if (!isNew && !original.data) return <section className={styles.add}><Loading shape="lines" /></section>;
    const loaded: GivenChapter | null = original.data && !isNew ? {
        title: original.data.title,
        text: original.data.blocks.filter((block) => block.type !== 'image')
            .map((block) => block.type === 'separator' ? '* * *' : block.content.map((span) => span.text).join('')).join('\n'),
    } : null;
    const pictures = !isNew && original.data?.blocks.some((block) => block.type === 'image');
    return (
        <ChapterForm editionId={editionId} isNew={isNew} number={number} language={original.data && !isNew ? original.data.language : language}
            initial={loaded ?? { title: '', text: '' }} known={known} saved={saved} readOnly={Boolean(pictures)}
            onSaved={onSaved} onRemoved={onRemoved} />
    );
}

function ChapterForm({ editionId, isNew, number: initialNumber, language: initialLanguage, initial, known, saved, readOnly, onSaved, onRemoved }: {
    editionId: number; isNew: boolean; number: number; language: OriginalLanguage; initial: GivenChapter; known: OriginalRow | null;
    saved: number | null; readOnly: boolean;
    onSaved: (number: number, language: OriginalLanguage, next: boolean) => void; onRemoved: () => void;
}) {
    const client = useQueryClient();
    const navigate = useNavigate();
    const canRun = useCanRun();
    const id = useId();
    const [chapter, setChapter] = useState<GivenChapter>(initial);
    const [language, setLanguage] = useState<OriginalLanguage>(initialLanguage);
    const [numberText, setNumberText] = useState(String(initialNumber));
    const number = Number(numberText);
    const validNumber = Number.isInteger(number) && number >= 1;
    const changed = chapter.title !== initial.title || chapter.text !== initial.text || language !== initialLanguage || number !== initialNumber;
    const save = useMutation({
        mutationFn: (then: 'stay' | 'translate' | 'next') =>
            originalsApi.saveSeparate(editionId, [chapter], language, number).then(() => then),
        onSuccess: (then) => {
            void client.invalidateQueries({ queryKey: ['originals', editionId] });
            void client.invalidateQueries({ queryKey: ['original', editionId, number] });
            void client.invalidateQueries({ queryKey: ['studio-edition', editionId] });
            if (then === 'translate') {
                void navigate({ to: '/studio/$editionId/translate', params: { editionId: String(editionId) } });
                return;
            }
            onSaved(number, language, then === 'next');
        },
    });
    const remove = useMutation({ meta: { errorToast: true },
        mutationFn: () => originalsApi.remove(editionId, initialNumber),
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: ['originals', editionId] });
            onRemoved();
        },
    });
    const ready = validNumber && chapter.text.trim().length > 0 && !readOnly;
    const replaces = isNew && known !== null;
    return (
        <section className={styles.add} aria-labelledby={`${id}-heading`}>
            <div className={styles.formHead}>
                <h2 className={styles.formTitle} id={`${id}-heading`}>{isNew ? `Нова глава ${initialNumber}` : `Оригінал глави ${initialNumber}`}</h2>
                <span className={styles.muted}>
                    {isNew ? 'ще не збережено'
                        : known ? `${known.translated ? 'перекладено' : 'є оригінал'} · збережено ${relativeTime(new Date(known.updatedAt))}` : ''}
                </span>
            </div>
            {saved !== null && (
                <Notice tone="success">Збережено главу {saved}. <TranslateLink editionId={editionId} /></Notice>
            )}
            <div className={styles.inline}>
                <label className={styles.field}>
                    <span className={styles.label}>Мова оригіналу</span>
                    <select className={styles.select} value={language} disabled={readOnly}
                        onChange={(event) => setLanguage(event.target.value as OriginalLanguage)}>
                        {LANGUAGES.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                    </select>
                </label>
                <label className={styles.field}>
                    <span className={styles.label}>Номер глави</span>
                    <input className={styles.number} type="number" min={1} inputMode="numeric" value={numberText} disabled={!isNew}
                        onChange={(event) => setNumberText(event.target.value)} />
                </label>
            </div>
            {replaces && <Notice tone="info">Оригінал глави {initialNumber} уже є — збереження його замінить.</Notice>}
            {readOnly ? (
                <>
                    <p className={styles.muted}>У цій главі є картинки з книжки, тож тут вона лише для перегляду.</p>
                    <pre className={styles.readOnly} lang={language}>{chapter.text}</pre>
                </>
            ) : (
                <>
                    <div className={styles.field}>
                        <label className={styles.label} htmlFor={`${id}-title`}>Заголовок</label>
                        <input id={`${id}-title`} className={styles.title} value={chapter.title} lang={language} placeholder={`Chapter ${number || ''}: …`}
                            onChange={(event) => setChapter({ ...chapter, title: event.target.value })} />
                    </div>
                    <div className={styles.field}>
                        <label className={styles.label} htmlFor={`${id}-text`}>Текст глави</label>
                        <textarea id={`${id}-text`} className={`${styles.text} ${styles.textBig}`} value={chapter.text} lang={language}
                            placeholder="Вставте текст глави" aria-describedby={`${id}-text-hint`}
                            onChange={(event) => setChapter({ ...chapter, text: event.target.value })} />
                        <span className={styles.hint} id={`${id}-text-hint`}>Кожен рядок стане абзацом. Заголовок сюди не вставляйте — для нього поле вище.</span>
                    </div>
                </>
            )}
            <div className={styles.actions}>
                {!readOnly && (
                    <>
                        <Button onPress={() => save.mutate('stay')} isDisabled={!ready || (!isNew && !changed)} pending={save.isPending && save.variables === 'stay'}
                            pendingLabel="Зберігаємо…">
                            Зберегти
                        </Button>
                        {canRun && (
                            <Button variant="secondary" onPress={() => save.mutate('translate')} isDisabled={!ready}
                                pending={save.isPending && save.variables === 'translate'} pendingLabel="Зберігаємо…">
                                Зберегти й перекласти
                            </Button>
                        )}
                        <Button variant="secondary" onPress={() => save.mutate('next')} isDisabled={!ready}
                            pending={save.isPending && save.variables === 'next'} pendingLabel="Зберігаємо…">
                            Зберегти й до наступної
                        </Button>
                    </>
                )}
                {!isNew && (
                    <Button variant="quiet" pending={remove.isPending} pendingLabel="Видаляємо…"
                        onPress={() => void askConfirm({
                            title: `Видалити оригінал глави ${initialNumber}?`, text: 'Переклад глави, якщо він є, лишиться.', confirmLabel: 'Видалити', danger: true,
                        }).then((yes) => { if (yes) remove.mutate(); })}>
                        Видалити оригінал
                    </Button>
                )}
                <span className={styles.hint}>Оригінал бачить лише команда перекладу.</span>
            </div>
            {save.isError && <Notice tone="error">{save.error.message}</Notice>}
        </section>
    );
}

function Languages({ counts }: { counts: Partial<Record<OriginalLanguage, number>> }) {
    const used = LANGUAGES.filter((item) => (counts[item.value] ?? 0) > 0);
    if (used.length < 2) return null;
    return (
        <p className={styles.muted}>
            З різних мов: {used.map((item) => `${item.label.toLowerCase()} — ${counts[item.value]}`).join(', ')}.
            Терміни між мовами словник узгоджує гірше, тож якість може трохи впасти.
        </p>
    );
}

/** «Англійське джерело скінчилось»: a range of chapters comes from another language. */
function ChangeLanguage({ editionId }: { editionId: number }) {
    const client = useQueryClient();
    const [from, setFrom] = useState('');
    const [to, setTo] = useState('');
    const [language, setLanguage] = useState<OriginalLanguage>('ja');
    const change = useMutation({ meta: { errorToast: true },
        mutationFn: () => originalsApi.changeLanguage(editionId, Number(from), Number(to || from), language),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['originals', editionId] }),
    });
    return (
        <details className={styles.more}>
            <summary>Змінити мову глав</summary>
            <div className={styles.inline}>
                <label className={styles.field}>
                    <span className={styles.label}>З глави</span>
                    <input className={styles.number} type="number" min={1} value={from} onChange={(event) => setFrom(event.target.value)} />
                </label>
                <label className={styles.field}>
                    <span className={styles.label}>по</span>
                    <input className={styles.number} type="number" min={1} value={to} placeholder={from} onChange={(event) => setTo(event.target.value)} />
                </label>
                <label className={styles.field}>
                    <span className={styles.label}>Мова</span>
                    <select className={styles.select} value={language} onChange={(event) => setLanguage(event.target.value as OriginalLanguage)}>
                        {LANGUAGES.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                    </select>
                </label>
            </div>
            <Button variant="secondary" isDisabled={!from} onPress={() => change.mutate()} pending={change.isPending}>Змінити</Button>
        </details>
    );
}

function RemoveAll({ editionId, total }: { editionId: number; total: number }) {
    const client = useQueryClient();
    const remove = useMutation({ meta: { errorToast: true },
        mutationFn: () => originalsApi.removeAll(editionId),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['originals', editionId] }),
    });
    return (
        <div className={styles.danger}>
            <Button variant="danger" pending={remove.isPending} pendingLabel="Видаляємо…"
                onPress={() => void askConfirm({
                    title: 'Видалити всі оригінали?',
                    text: `Тексти ${total} ${chaptersWord(total)} оригіналу зникнуть із сайту. Переклад лишиться.`,
                    confirmLabel: 'Видалити все', danger: true,
                }).then((yes) => { if (yes) remove.mutate(); })}>
                Видалити оригінали
            </Button>
        </div>
    );
}
