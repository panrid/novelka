import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { useCanRun } from '../../ledger/api';
import { Cover } from '../../reading/Cover';
import { chaptersWord } from '../../reading/api';
import { ROLE_LABELS, studioApi, type MyEdition } from '../../studio/api';
import { STAGE_LABELS, autotranslateApi, type Process } from '../../studio/autotranslate';
import { Loading } from '../../ui/Loading';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import styles from './myTranslations.module.css';

/** Search shows once the list is long enough to need it. */
const SEARCH_FROM = 6;

/** «з англійської»: the language a translation is made from. */
const FROM_LANGUAGE: Record<string, string> = { ja: 'з японської', en: 'з англійської', zh: 'з китайської', ko: 'з корейської' };

/** 1 чернетка, 2 чернетки, 5 чернеток. */
export function plural(count: number, one: string, few: string, many: string): string {
    const tens = count % 100;
    const ones = count % 10;
    if (tens >= 11 && tens <= 14) return many;
    if (ones === 1) return one;
    if (ones >= 2 && ones <= 4) return few;
    return many;
}

type Chip = { text: string; tone: 'warn' | 'info' | 'gray' };

/**
 * «Мої переклади» (етап 20, Studio.dc.html): the person's translations with how far each has got and
 * what waits in it; beside them what is being translated right now and what waits for the person.
 */
export function StudioHome() {
    const canRun = useCanRun();
    const mine = useQuery({ queryKey: ['studio'], queryFn: studioApi.mine });
    const [search, setSearch] = useState('');
    const needle = search.trim().toLocaleLowerCase('uk');
    const shown = mine.data?.filter((item) => item.title.toLocaleLowerCase('uk').includes(needle));
    return (
        <div className={styles.columns} data-wide>
            <section className={styles.main} aria-labelledby="my-translations">
                <h1 id="my-translations" className={styles.title}>Мої переклади</h1>
                {mine.isPending && <Loading shape="list" onRetry={() => void mine.refetch()} />}
                {mine.isError && <Notice tone="error">{mine.error.message}</Notice>}
                {mine.isSuccess && mine.data.length === 0 && (
                    <p className={styles.muted}>
                        Поки порожньо. Почніть із <Link to="/studio/new">«Новий переклад»</Link>: переклад з оригіналу або свій твір.
                    </p>
                )}
                {(mine.data?.length ?? 0) >= SEARCH_FROM && <TextInput label="Пошук за назвою" value={search} onChange={setSearch} />}
                {shown?.length === 0 && <p className={styles.muted}>Нічого не знайдено.</p>}
                {shown?.map((item) => <EditionCard key={item.editionId} item={item} canRun={canRun} />)}
            </section>
            <aside className={styles.side}>
                {canRun && <TranslatingNow />}
                {mine.data && <Waiting items={mine.data} canRun={canRun} />}
            </aside>
        </div>
    );
}

function EditionCard({ item, canRun }: { item: MyEdition; canRun: boolean }) {
    const id = String(item.editionId);
    const own = item.kind === 'original';
    const originals = item.originals ?? 0;
    const percent = originals > 0 ? Math.min(100, Math.round((item.chapterCount / originals) * 100)) : null;
    const from = item.sourceLanguage ? FROM_LANGUAGE[item.sourceLanguage] : undefined;
    const progress = own
        ? `${item.chapterCount} ${chaptersWord(item.chapterCount)} · свій твір`
        : originals > 0
            ? `Перекладено ${item.chapterCount} з ${originals} ${originals % 10 === 1 && originals % 100 !== 11 ? 'глави' : 'глав'}${from ? ` · ${from}` : ''}`
            : `Перекладено ${item.chapterCount} ${chaptersWord(item.chapterCount)}${from ? ` · ${from}` : ''}`;
    const chips: Chip[] = [];
    if (item.drafts > 0) chips.push({ text: `${item.drafts} ${plural(item.drafts, 'чернетка', 'чернетки', 'чернеток')}`, tone: 'warn' });
    if (item.jobState === 'running') {
        chips.push({ text: `перекладається${item.jobChapter !== null ? ` · гл. ${item.jobChapter}` : ''}`, tone: 'info' });
    }
    if (item.jobState === 'queued') chips.push({ text: `у черзі${item.jobChapter !== null ? ` · гл. ${item.jobChapter}` : ''}`, tone: 'info' });
    if (item.jobState === 'failed') chips.push({ text: 'переклад зупинився', tone: 'warn' });
    if (item.pendingSuggestions > 0) {
        chips.push({ text: `${item.pendingSuggestions} ${plural(item.pendingSuggestions, 'правка', 'правки', 'правок')}`, tone: 'gray' });
    }
    if (canRun && item.newWords > 0) {
        chips.push({ text: `${item.newWords} ${plural(item.newWords, 'нове слово', 'нові слова', 'нових слів')}`, tone: 'warn' });
    }
    return (
        <article className={styles.card}>
            <Link to="/studio/$editionId" params={{ editionId: id }} className={styles.cover} tabIndex={-1} aria-hidden>
                <Cover url={item.coverUrl} title={item.title} seed={item.novelSlug} width={64} />
            </Link>
            <div className={styles.body}>
                <Link to="/studio/$editionId" params={{ editionId: id }} className={styles.name}>{item.title}</Link>
                <div className={styles.muted}>{progress}</div>
                {percent !== null && (
                    <div className={styles.bar} role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}
                        aria-label={`${item.title}: перекладено, відсотків`}>
                        <span style={{ width: `${percent}%` }} />
                    </div>
                )}
                <div className={styles.muted}>${item.teamHandle} · {ROLE_LABELS[item.role]}</div>
                {chips.length > 0 && (
                    <div className={styles.chips}>
                        {chips.map((chip) => <span key={chip.text} className={`${styles.chip} ${styles[chip.tone]}`}>{chip.text}</span>)}
                    </div>
                )}
            </div>
            <div className={styles.actions}>
                {item.lastChapter
                    ? (
                        <Link to="/studio/$editionId/chapters/$number" params={{ editionId: id, number: String(item.lastChapter) }} className={styles.open}>
                            Глава {item.lastChapter}
                        </Link>
                    )
                    : <Link to="/studio/$editionId" params={{ editionId: id }} className={styles.open}>Відкрити</Link>}
                {canRun && !own
                    ? <Link to="/studio/$editionId/translate" params={{ editionId: id }} className={styles.next}>Перекласти далі</Link>
                    : <Link to="/studio/$editionId/chapters" params={{ editionId: id }} className={styles.next}>Глави</Link>}
            </div>
        </article>
    );
}

/** The person's runs going now, each opening its «Хід перекладу» наживо. */
function TranslatingNow() {
    const active = useQuery({
        queryKey: ['processes', 'active', 1],
        queryFn: () => autotranslateApi.processes({ state: 'active', page: 1 }),
        refetchInterval: 10_000,
    });
    const items = active.data?.items ?? [];
    return (
        <section className={styles.panel} aria-labelledby="translating-now">
            <b id="translating-now" className={styles.panelTitle}>Перекладається зараз</b>
            {active.isPending && <Loading shape="lines" />}
            {active.isSuccess && items.length === 0 && <span className={styles.muted}>Зараз нічого не перекладається.</span>}
            {items.map((process) => <Running key={process.job.id} process={process} />)}
            <span className={styles.hint}>Натисніть — відкриється хід перекладу наживо.</span>
        </section>
    );
}

function Running({ process }: { process: Process }) {
    const { job } = process;
    const total = Math.max(1, job.to - job.from + 1);
    const step = job.current;
    const percent = Math.min(100, Math.round(((job.done + (step?.progress ?? 0)) / total) * 100));
    const chapters = job.from === job.to ? `глава ${job.from}` : `глави ${job.from}–${job.to}`;
    const stage = job.state === 'queued' ? 'у черзі'
        : step ? `${STAGE_LABELS[step.stage] ?? step.stage}${step.parts > 1 && (step.stage === 'translate' || step.stage === 'proofread')
            ? ` ${Math.min(step.part + 1, step.parts)}/${step.parts}` : ''}` : '';
    return (
        <Link to="/studio/$editionId/translate/jobs/$jobId" params={{ editionId: String(process.editionId), jobId: String(job.id) }}
            className={styles.running}>
            <span className={styles.runningHead}>
                <span>{process.title} · {step ? `глава ${step.number}` : chapters}</span>
                <span className={styles.small}>{stage}</span>
            </span>
            <span className={styles.bar} aria-hidden><span style={{ width: `${percent}%` }} /></span>
        </Link>
    );
}

/** What waits for the person across their translations; each line opens the translation with the most of it. */
function Waiting({ items, canRun }: { items: MyEdition[]; canRun: boolean }) {
    const most = (count: (item: MyEdition) => number) => items.reduce<MyEdition | null>(
        (best, item) => (count(item) > 0 && (!best || count(item) > count(best)) ? item : best), null);
    const sum = (count: (item: MyEdition) => number) => items.reduce((total, item) => total + count(item), 0);
    const suggestions = (item: MyEdition) => item.pendingSuggestions;
    const words = (item: MyEdition) => item.newWords;
    const drafts = (item: MyEdition) => item.drafts;
    return (
        <section className={styles.panel} aria-labelledby="waiting">
            <b id="waiting" className={styles.panelTitle}>Чекає на вас</b>
            <WaitRow label="Правки читачів" count={sum(suggestions)} top={most(suggestions)} where="suggestions" />
            {canRun && <WaitRow label="Нові слова у словниках" count={sum(words)} top={most(words)} where="glossary" />}
            <WaitRow label="Чернетки до публікації" count={sum(drafts)} top={most(drafts)} where="chapters" />
        </section>
    );
}

function WaitRow({ label, count, top, where }: { label: string; count: number; top: MyEdition | null; where: 'suggestions' | 'glossary' | 'chapters' }) {
    const body = <><span>{label}</span><b className={count === 0 ? styles.zero : undefined}>{count}</b></>;
    if (!top) return <span className={styles.waitRow}>{body}</span>;
    const params = { editionId: String(top.editionId) };
    if (where === 'suggestions') return <Link to="/studio/$editionId/suggestions" params={params} className={styles.waitRow}>{body}</Link>;
    if (where === 'glossary') return <Link to="/studio/$editionId/glossary" params={params} className={styles.waitRow}>{body}</Link>;
    return <Link to="/studio/$editionId/chapters" params={params} className={styles.waitRow}>{body}</Link>;
}
