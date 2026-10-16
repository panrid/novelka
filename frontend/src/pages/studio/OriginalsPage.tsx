import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { chaptersWord } from '../../reading/api';
import { characters } from '../../lib/plural';
import {
    LANGUAGES, LANGUAGE_SHORT, originalsApi, type OriginalInput, type OriginalLanguage, type OriginalPreview,
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

/**
 * «Оригінал» (етап 19): the text a translation is made from, pasted or uploaded by the team —
 * one chapter or many, each with its language. On a computer the form and the list stand side
 * by side; on a phone, one under the other.
 */
export function OriginalsPage() {
    const id = useEditionId();
    const [page, setPage] = useState(1);
    const list = useQuery({ queryKey: ['originals', id, page], queryFn: () => originalsApi.list(id, page), placeholderData: keepPreviousData });
    return (
        <EditionShell tab="originals">
            <div className={styles.layout}>
                <AddOriginal editionId={id} nextNumber={list.data?.nextNumber ?? 1} lastLanguage={list.data?.items.at(-1)?.language} />
                <div className={styles.listSide}>
                    <h2 className={styles.heading}>
                        Оригінали{list.data ? ` · ${list.data.total} ${chaptersWord(list.data.total)}` : ''}
                    </h2>
                    {list.isError && <Notice tone="error">{list.error.message}</Notice>}
                    {list.data && list.data.total === 0 && (
                        <p className={styles.muted}>Поки немає. Вставте текст оригіналу або завантажте файл — сайт поділить його на глави.</p>
                    )}
                    {list.data && list.data.total > 0 && (
                        <>
                            <Languages counts={list.data.languages} />
                            <ol className={styles.rows}>
                                {list.data.items.map((row) => <OriginalRowView key={row.number} editionId={id} row={row} />)}
                            </ol>
                            <Pager page={page} total={list.data.total} size={PAGE} onPage={setPage} />
                            <ChangeLanguage editionId={id} />
                            <RemoveAll editionId={id} total={list.data.total} />
                        </>
                    )}
                </div>
            </div>
        </EditionShell>
    );
}

/** Paste or upload → see the split → save. */
function AddOriginal({ editionId, nextNumber, lastLanguage }: { editionId: number; nextNumber: number; lastLanguage: OriginalLanguage | undefined }) {
    const client = useQueryClient();
    const picker = useRef<HTMLInputElement>(null);
    const [way, setWay] = useState<'text' | 'file'>('text');
    const [text, setText] = useState('');
    const [file, setFile] = useState<File | null>(null);
    const [chosenLanguage, setLanguage] = useState<OriginalLanguage | null>(null);
    const language = chosenLanguage ?? lastLanguage ?? 'ja';
    const [first, setFirst] = useState('');
    const [preview, setPreview] = useState<OriginalPreview | null>(null);
    const input: OriginalInput | null = way === 'text' ? (text.trim() ? { text } : null) : file ? { file } : null;
    const from = first.trim() ? Number(first) : undefined;

    const split = useMutation({ mutationFn: (given: OriginalInput) => originalsApi.preview(editionId, given, from), onSuccess: setPreview });
    const save = useMutation({
        mutationFn: () => originalsApi.save(editionId, input!, language, preview?.firstNumber),
        onSuccess: () => {
            setPreview(null);
            setText('');
            setFile(null);
            setFirst('');
            void client.invalidateQueries({ queryKey: ['originals', editionId] });
        },
    });
    const changed = () => { setPreview(null); split.reset(); save.reset(); };

    return (
        <div className={styles.addSide}>
            <h2 className={styles.heading}>Додати оригінал</h2>
            <p className={styles.muted}>Оригінал бачить лише команда перекладу. Читачам і пошуковим системам він не показується.</p>
            <Segmented label="Звідки" value={way} onChange={(next) => { setWay(next); changed(); }}
                options={[{ value: 'text', label: 'Вставити текст' }, { value: 'file', label: 'Файл .txt чи .epub' }]} />
            {way === 'text' ? (
                <label className={styles.field}>
                    <span className={styles.label}>Текст однієї чи кількох глав</span>
                    <textarea className={styles.text} value={text} rows={14} lang={language}
                        placeholder={'第1話　…\n…\n第2話　…'}
                        onChange={(event) => { setText(event.target.value); changed(); }} />
                    <span className={styles.hint}>
                        Нова глава починається з заголовка: «第12話», «第十二章», «제12화», «Chapter 12», «Prologue». Без заголовків увесь текст — одна глава.
                    </span>
                </label>
            ) : (
                <div className={styles.field}>
                    <input ref={picker} type="file" accept=".txt,.epub,text/plain,application/epub+zip" hidden
                        onChange={(event) => { setFile(event.target.files?.[0] ?? null); changed(); event.target.value = ''; }} />
                    <div className={styles.fileRow}>
                        <Button variant="secondary" onPress={() => picker.current?.click()}>{file ? 'Інший файл' : 'Вибрати файл'}</Button>
                        {file && <span className={styles.fileName}>{file.name}</span>}
                    </div>
                    <span className={styles.hint}>.txt — глави за заголовками; .epub — глави книжки по порядку, з ілюстраціями, до 30 МБ.</span>
                </div>
            )}
            <div className={styles.inline}>
                <label className={styles.field}>
                    <span className={styles.label}>Мова оригіналу</span>
                    <select className={styles.select} value={language} onChange={(event) => setLanguage(event.target.value as OriginalLanguage)}>
                        {LANGUAGES.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                    </select>
                </label>
                <label className={styles.field}>
                    <span className={styles.label}>З глави №</span>
                    <input className={styles.number} type="number" min={1} inputMode="numeric" value={first} placeholder={String(nextNumber)}
                        onChange={(event) => { setFirst(event.target.value); changed(); }} />
                </label>
            </div>
            {!preview && (
                <Button onPress={() => input && split.mutate(input)} isDisabled={!input} pending={split.isPending} pendingLabel="Ділимо на глави…">
                    Поділити на глави
                </Button>
            )}
            {split.isError && <Notice tone="error">{split.error.message}</Notice>}
            {preview && (
                <div className={styles.preview}>
                    <h3 className={styles.subheading}>
                        {preview.chapters.length} {chaptersWord(preview.chapters.length)}: {preview.firstNumber}
                        {preview.chapters.length > 1 ? `–${preview.firstNumber + preview.chapters.length - 1}` : ''}
                    </h3>
                    {preview.chapters.some((chapter) => chapter.replaces) && (
                        <Notice tone="info">Оригінали з цими номерами вже є — їх буде замінено.</Notice>
                    )}
                    <ol className={styles.planned}>
                        {preview.chapters.map((chapter) => (
                            <li key={chapter.number}>
                                <div className={styles.plannedHead}>
                                    <b>{chapter.number}. {chapter.title}</b>
                                    <span className={styles.muted}>
                                        {characters(chapter.chars)}
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
                            Зберегти {preview.chapters.length} {chaptersWord(preview.chapters.length)}
                        </Button>
                        <Button variant="quiet" onPress={changed}>Змінити</Button>
                    </div>
                    {save.isError && <Notice tone="error">{save.error.message}</Notice>}
                </div>
            )}
            {save.isSuccess && (
                <Notice tone="info">Збережено глави {save.data.from}{save.data.to > save.data.from ? `–${save.data.to}` : ''}.</Notice>
            )}
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

function OriginalRowView({ editionId, row }: { editionId: number; row: { number: number; title: string; chars: number; language: OriginalLanguage; translated: boolean } }) {
    const client = useQueryClient();
    const remove = useMutation({ meta: { errorToast: true },
        mutationFn: () => originalsApi.remove(editionId, row.number),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['originals', editionId] }),
    });
    return (
        <li className={styles.row}>
            <span className={styles.rowNumber}>{row.number}</span>
            <span className={styles.rowTitle} lang={row.language}>{row.title}</span>
            <span className={styles.chip}>{LANGUAGE_SHORT[row.language]}</span>
            <span className={styles.rowMeta}>{characters(row.chars)}{row.translated ? ' · перекладено' : ''}</span>
            <button type="button" className={styles.icon} aria-label={`Видалити оригінал глави ${row.number}`} disabled={remove.isPending}
                onClick={() => void askConfirm({
                    title: `Видалити оригінал глави ${row.number}?`, text: 'Переклад глави, якщо він є, лишиться.', confirmLabel: 'Видалити', danger: true,
                }).then((yes) => { if (yes) remove.mutate(); })}>
                <Trash2 size={16} aria-hidden />
            </button>
        </li>
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
