import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useId, useRef, useState } from 'react';
import { chaptersWord } from '../../reading/api';
import { characters } from '../../lib/plural';
import {
    LANGUAGES, LANGUAGE_SHORT, originalsApi, type GivenChapter, type OriginalInput, type OriginalLanguage, type OriginalPreview,
    type OriginalRow,
} from '../../studio/originals';
import { askConfirm } from '../../ui/ask';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Pager } from '../../ui/Pager';
import { Segmented } from '../../ui/Segmented';
import { Trash2 } from '../../ui/icons';
import { useEditionId } from './EditionPage';
import { EditionShell } from './EditionShell';
import styles from './originals.module.css';

const PAGE = 50;

/** One chapter; several, each in its own fields; several in one field, split by headings; or a file. */
type Way = 'one' | 'separate' | 'together' | 'file';

const EXAMPLE = `Chapter 1: Good Morning Brother
Zorian's eyes abruptly shot open as a sharp pain erupted from his stomach.
"Good morning, brother!" an annoyingly cheerful voice sounded right on top of him.

Chapter 2: The Academy
The train to Cyoria was crowded, as always at the start of the year.`;

const empty = (): GivenChapter => ({ title: '', text: '' });

/**
 * «Оригінал» (етап 19): the text a translation is made from, pasted or uploaded by the team — one
 * chapter or many, each with its language. The form takes the page's whole width: originals are
 * long, and they are pasted on a computer.
 */
export function OriginalsPage() {
    const id = useEditionId();
    const [page, setPage] = useState(1);
    const list = useQuery({ queryKey: ['originals', id, page], queryFn: () => originalsApi.list(id, page), placeholderData: keepPreviousData });
    const total = list.data?.total ?? 0;
    return (
        <EditionShell tab="originals">
            <div className={styles.page}>
                <AddOriginal editionId={id} nextNumber={list.data?.nextNumber ?? 1} lastLanguage={list.data?.items.at(-1)?.language} />
                <section className={styles.listSide} aria-labelledby="originals-heading">
                    <div className={styles.listHead}>
                        <h2 className={styles.heading} id="originals-heading">
                            Оригінали{list.data ? ` · ${total} ${chaptersWord(total)}` : ''}
                        </h2>
                        {total > 0 && <TranslateLink editionId={id} />}
                    </div>
                    {list.isError && <Notice tone="error">{list.error.message}</Notice>}
                    {list.data && total === 0 && (
                        <p className={styles.muted}>Поки немає. Вставте текст оригіналу вище — потім його можна буде перекласти.</p>
                    )}
                    {list.data && total > 0 && (
                        <>
                            <Languages counts={list.data.languages} />
                            <p className={styles.hint}>Натисніть на назву глави, щоб переглянути чи виправити її текст.</p>
                            <ol className={styles.rows}>
                                {list.data.items.map((row) => <OriginalRowView key={row.number} editionId={id} row={row} />)}
                            </ol>
                            <Pager page={page} total={total} size={PAGE} onPage={setPage} />
                            <ChangeLanguage editionId={id} />
                            <RemoveAll editionId={id} total={total} />
                        </>
                    )}
                </section>
            </div>
        </EditionShell>
    );
}

function TranslateLink({ editionId, label = 'Перекласти →' }: { editionId: number; label?: string }) {
    return (
        <Link to="/studio/$editionId/translate" params={{ editionId: String(editionId) }} className={styles.translate}>
            {label}
        </Link>
    );
}

/** Paste or upload → see how it splits → save. */
function AddOriginal({ editionId, nextNumber, lastLanguage }: { editionId: number; nextNumber: number; lastLanguage: OriginalLanguage | undefined }) {
    const client = useQueryClient();
    const [many, setMany] = useState<'one' | 'many'>('one');
    const [manyWay, setManyWay] = useState<'separate' | 'together' | 'file'>('separate');
    const way: Way = many === 'one' ? 'one' : manyWay;
    const [single, setSingle] = useState<GivenChapter>(empty);
    const [separate, setSeparate] = useState<GivenChapter[]>(() => [empty(), empty()]);
    const [text, setText] = useState('');
    const [file, setFile] = useState<File | null>(null);
    const [chosenLanguage, setLanguage] = useState<OriginalLanguage | null>(null);
    const language = chosenLanguage ?? lastLanguage ?? 'en';
    const [first, setFirst] = useState('');
    const [preview, setPreview] = useState<OriginalPreview | null>(null);
    const from = first.trim() ? Number(first) : undefined;
    const startsAt = from ?? nextNumber;

    const filled = separate.filter((chapter) => chapter.text.trim() || chapter.title.trim());
    const given: GivenChapter[] | null = way === 'one' ? (single.text.trim() ? [single] : null)
        : way === 'separate' ? (filled.length > 0 ? filled : null) : null;
    const input: OriginalInput | null = way === 'together' ? (text.trim() ? { text } : null) : way === 'file' ? (file ? { file } : null) : null;

    const split = useMutation({
        mutationFn: () => given ? originalsApi.previewSeparate(editionId, given, from) : originalsApi.preview(editionId, input!, from),
        onSuccess: setPreview,
    });
    const save = useMutation({
        mutationFn: () => given ? originalsApi.saveSeparate(editionId, given, language, preview?.firstNumber)
            : originalsApi.save(editionId, input!, language, preview?.firstNumber),
        onSuccess: () => {
            setPreview(null);
            setSingle(empty());
            setSeparate([empty(), empty()]);
            setText('');
            setFile(null);
            setFirst('');
            void client.invalidateQueries({ queryKey: ['originals', editionId] });
            void client.invalidateQueries({ queryKey: ['studio-edition', editionId] });
        },
    });
    const changed = () => { setPreview(null); split.reset(); save.reset(); };

    return (
        <section className={styles.add} aria-labelledby="add-original">
            <h2 className={styles.heading} id="add-original">Додати оригінал</h2>
            <p className={styles.muted}>Оригінал бачить лише команда перекладу. Читачам і пошуковим системам він не показується.</p>
            <div className={styles.switches}>
                <Segmented label="Скільки глав" value={many} onChange={(next) => { setMany(next); changed(); }}
                    options={[{ value: 'one', label: 'Одна глава' }, { value: 'many', label: 'Кілька глав' }]} />
                {many === 'many' && (
                    <Segmented label="Як вставити" value={manyWay} onChange={(next) => { setManyWay(next); changed(); }}
                        options={[
                            { value: 'separate', label: 'Кожна окремо' },
                            { value: 'together', label: 'Усі в одне поле' },
                            { value: 'file', label: 'З файлу' },
                        ]} />
                )}
            </div>
            <div className={styles.inline}>
                <label className={styles.field}>
                    <span className={styles.label}>Мова оригіналу</span>
                    <select className={styles.select} value={language} onChange={(event) => setLanguage(event.target.value as OriginalLanguage)}>
                        {LANGUAGES.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                    </select>
                </label>
                <label className={styles.field}>
                    <span className={styles.label}>{way === 'one' ? 'Номер глави' : 'Номер першої глави'}</span>
                    <input className={styles.number} type="number" min={1} inputMode="numeric" value={first} placeholder={String(nextNumber)}
                        onChange={(event) => { setFirst(event.target.value); changed(); }} />
                </label>
            </div>

            {way === 'one' && (
                <ChapterFields chapter={single} number={startsAt} big lang={language} onChange={(next) => { setSingle(next); changed(); }} />
            )}

            {way === 'separate' && (
                <div className={styles.chapters}>
                    {separate.map((chapter, index) => (
                        <div key={index} className={styles.chapterCard}>
                            <div className={styles.chapterCardHead}>
                                <b>Глава {startsAt + index}</b>
                                {separate.length > 1 && (
                                    <button type="button" className={styles.icon} aria-label={`Прибрати главу ${startsAt + index}`}
                                        onClick={() => { setSeparate(separate.filter((_, at) => at !== index)); changed(); }}>
                                        <Trash2 size={16} aria-hidden />
                                    </button>
                                )}
                            </div>
                            <ChapterFields chapter={chapter} number={startsAt + index} lang={language}
                                onChange={(next) => { setSeparate(separate.map((item, at) => at === index ? next : item)); changed(); }} />
                        </div>
                    ))}
                    <div>
                        <Button variant="secondary" onPress={() => { setSeparate([...separate, empty()]); changed(); }}>+ Ще глава</Button>
                    </div>
                </div>
            )}

            {way === 'together' && (
                <>
                    <div className={styles.howto}>
                        <b>Як сайт ділить текст на глави</b>
                        <p>
                            Кожна глава починається з окремого рядка-заголовка: <code>Chapter 12</code>, <code>Chapter 12: The Gate</code>,{' '}
                            <code>Глава 12</code>, <code>Розділ 12</code>, <code>Prologue</code>, <code>Epilogue</code>. Усе, що йде після
                            заголовка до наступного, — текст цієї глави, кожен рядок стає абзацом, порожні рядки нічого не означають. Якщо
                            заголовків немає, увесь текст — одна глава. Перед збереженням сайт покаже, як поділив, — перевірте.
                        </p>
                        <span className={styles.label}>Приклад:</span>
                        <pre className={styles.example}>{EXAMPLE}</pre>
                    </div>
                    <label className={styles.field}>
                        <span className={styles.label}>Текст кількох глав разом із заголовками</span>
                        <textarea className={`${styles.text} ${styles.textBig}`} value={text} lang={language}
                            onChange={(event) => { setText(event.target.value); changed(); }} />
                    </label>
                </>
            )}

            {way === 'file' && <FilePicker file={file} onFile={(next) => { setFile(next); changed(); }} />}

            {!preview && (
                <div>
                    <Button onPress={() => split.mutate()} isDisabled={given === null && input === null} pending={split.isPending}
                        pendingLabel="Перевіряємо…">
                        {way === 'together' || way === 'file' ? 'Поділити на глави' : 'Далі'}
                    </Button>
                </div>
            )}
            {split.isError && <Notice tone="error">{split.error.message}</Notice>}
            {preview && (
                <div className={styles.preview}>
                    <h3 className={styles.subheading}>
                        {preview.chapters.length === 1
                            ? `Глава ${preview.firstNumber}`
                            : `${preview.chapters.length} ${chaptersWord(preview.chapters.length)}: ${preview.firstNumber}–${preview.firstNumber + preview.chapters.length - 1}`}
                    </h3>
                    {preview.chapters.some((chapter) => chapter.replaces) && (
                        <Notice tone="info">Оригінали з цими номерами вже є — їх буде замінено.</Notice>
                    )}
                    <ol className={styles.planned}>
                        {preview.chapters.map((chapter) => (
                            <li key={chapter.number}>
                                <div className={styles.plannedHead}>
                                    <b>{chapter.number}. {chapter.title || 'Без заголовка'}</b>
                                    <span className={styles.muted}>
                                        {characters(chapter.chars)} · абзаців: {chapter.paragraphs}
                                        {chapter.pictures > 0 && ` · картинок: ${chapter.pictures}`}
                                        {chapter.replaces && ' · замінить'}
                                        {chapter.translated && ' · уже перекладена'}
                                    </span>
                                </div>
                                {chapter.start && <p className={styles.start} lang={language}>{chapter.start}</p>}
                            </li>
                        ))}
                    </ol>
                    <div className={styles.actions}>
                        <Button onPress={() => save.mutate()} pending={save.isPending} pendingLabel="Зберігаємо…">
                            {preview.chapters.length === 1 ? 'Зберегти главу' : `Зберегти ${preview.chapters.length} ${chaptersWord(preview.chapters.length)}`}
                        </Button>
                        <Button variant="quiet" onPress={changed}>Змінити</Button>
                    </div>
                    {save.isError && <Notice tone="error">{save.error.message}</Notice>}
                </div>
            )}
            {save.isSuccess && (
                <Notice tone="success">
                    Збережено {save.data.to > save.data.from ? `глави ${save.data.from}–${save.data.to}` : `главу ${save.data.from}`}.{' '}
                    <TranslateLink editionId={editionId} label="Перекласти →" />
                </Notice>
            )}
        </section>
    );
}

/** The title and the text of one chapter, each in its own field. */
function ChapterFields({ chapter, number, big = false, lang, onChange }: {
    chapter: GivenChapter; number: number; big?: boolean; lang: string; onChange: (next: GivenChapter) => void;
}) {
    const id = useId();
    return (
        <>
            <div className={styles.field}>
                <label className={styles.label} htmlFor={`${id}-title`}>Заголовок</label>
                <input id={`${id}-title`} className={styles.title} value={chapter.title} lang={lang} placeholder={`Chapter ${number}: …`}
                    aria-describedby={`${id}-title-hint`} onChange={(event) => onChange({ ...chapter, title: event.target.value })} />
                <span className={styles.hint} id={`${id}-title-hint`}>Як в оригіналі. Номер глави сайт покаже сам; можна лишити порожнім.</span>
            </div>
            <div className={styles.field}>
                <label className={styles.label} htmlFor={`${id}-text`}>Текст глави</label>
                <textarea id={`${id}-text`} className={big ? `${styles.text} ${styles.textBig}` : styles.text} value={chapter.text} lang={lang}
                    aria-describedby={`${id}-text-hint`} onChange={(event) => onChange({ ...chapter, text: event.target.value })} />
                <span className={styles.hint} id={`${id}-text-hint`}>Кожен рядок — окремий абзац. Заголовок сюди не вставляйте — для нього поле вище.</span>
            </div>
        </>
    );
}

function FilePicker({ file, onFile }: { file: File | null; onFile: (file: File | null) => void }) {
    const picker = useRef<HTMLInputElement>(null);
    return (
        <div className={styles.field}>
            <input ref={picker} type="file" accept=".txt,.epub,text/plain,application/epub+zip" hidden
                onChange={(event) => { onFile(event.target.files?.[0] ?? null); event.target.value = ''; }} />
            <div className={styles.fileRow}>
                <Button variant="secondary" onPress={() => picker.current?.click()}>{file ? 'Інший файл' : 'Вибрати файл'}</Button>
                {file && <span className={styles.fileName}>{file.name}</span>}
            </div>
            <span className={styles.hint}>
                .txt — глави за рядками-заголовками, як у режимі «Усі в одне поле»; .epub — глави книжки по порядку, з ілюстраціями, до 30 МБ.
            </span>
        </div>
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

function OriginalRowView({ editionId, row }: { editionId: number; row: OriginalRow }) {
    const client = useQueryClient();
    const [open, setOpen] = useState(false);
    const remove = useMutation({ meta: { errorToast: true },
        mutationFn: () => originalsApi.remove(editionId, row.number),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['originals', editionId] }),
    });
    return (
        <li className={styles.row}>
            <div className={styles.rowLine}>
                <span className={styles.rowNumber}>{row.number}</span>
                <button type="button" className={styles.rowTitle} lang={row.language} aria-expanded={open} onClick={() => setOpen(!open)}>
                    {row.title || 'Без заголовка'}
                </button>
                <span className={styles.chip}>{LANGUAGE_SHORT[row.language]}</span>
                <span className={styles.rowMeta}>{characters(row.chars)}{row.translated ? ' · перекладено' : ''}</span>
                <button type="button" className={styles.icon} aria-label={`Видалити оригінал глави ${row.number}`} disabled={remove.isPending}
                    onClick={() => void askConfirm({
                        title: `Видалити оригінал глави ${row.number}?`, text: 'Переклад глави, якщо він є, лишиться.', confirmLabel: 'Видалити', danger: true,
                    }).then((yes) => { if (yes) remove.mutate(); })}>
                    <Trash2 size={16} aria-hidden />
                </button>
            </div>
            {open && <OriginalEditor editionId={editionId} row={row} onDone={() => setOpen(false)} />}
        </li>
    );
}

/** A saved original in its fields; a chapter with pictures from a book is only shown. */
function OriginalEditor({ editionId, row, onDone }: { editionId: number; row: OriginalRow; onDone: () => void }) {
    const client = useQueryClient();
    const original = useQuery({ queryKey: ['original', editionId, row.number], queryFn: () => originalsApi.one(editionId, row.number) });
    const [draft, setDraft] = useState<GivenChapter | null>(null);
    const save = useMutation({
        mutationFn: (chapter: GivenChapter) => originalsApi.saveSeparate(editionId, [chapter], row.language, row.number),
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: ['originals', editionId] });
            void client.invalidateQueries({ queryKey: ['original', editionId, row.number] });
            onDone();
        },
    });
    if (original.isError) return <Notice tone="error">{original.error.message}</Notice>;
    if (!original.data) return <p className={styles.muted}>Завантажуємо…</p>;
    const loaded: GivenChapter = {
        title: original.data.title,
        text: original.data.blocks.filter((block) => block.type !== 'image')
            .map((block) => block.type === 'separator' ? '* * *' : block.content.map((span) => span.text).join('')).join('\n'),
    };
    const shown = draft ?? loaded;
    if (original.data.blocks.some((block) => block.type === 'image')) {
        return (
            <div className={styles.editor}>
                <p className={styles.muted}>У цій главі є картинки з книжки, тож тут вона лише для перегляду. Щоб змінити її, завантажте файл ще раз.</p>
                <pre className={styles.readOnly} lang={row.language}>{shown.text}</pre>
            </div>
        );
    }
    return (
        <div className={styles.editor}>
            <ChapterFields chapter={shown} number={row.number} big lang={row.language} onChange={setDraft} />
            <div className={styles.actions}>
                <Button onPress={() => draft && save.mutate(draft)} isDisabled={!draft || !draft.text.trim()} pending={save.isPending}
                    pendingLabel="Зберігаємо…">
                    Зберегти
                </Button>
                <Button variant="quiet" onPress={onDone}>Закрити</Button>
            </div>
            {save.isError && <Notice tone="error">{save.error.message}</Notice>}
        </div>
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
