import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState, type ReactNode } from 'react';
import { analyticsApi, type Analytics } from '../../admin/analytics';
import { Bars, ShareBar, StackedColumns, type Series } from '../../ui/charts';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
import styles from './analytics.module.css';

const PERIODS = [
    { value: '7', label: '7 днів' },
    { value: '30', label: '30 днів' },
    { value: '90', label: '90 днів' },
    { value: '365', label: 'Рік' },
    { value: '0', label: 'Усе' },
] as const;

const STAGE_SERIES: Series[] = [
    { key: 'analyze', label: 'Аналіз', color: 'var(--chart-blue)' },
    { key: 'translate', label: 'Переклад', color: 'var(--accent)' },
    { key: 'proofread', label: 'Вичитка', color: 'var(--warn)' },
    { key: 'other', label: 'Інше', color: 'var(--chart-violet)' },
];

const STAGE_NAMES: Record<string, string> = {
    analyze: 'аналіз', translate: 'переклад', proofread: 'вичитка', metadata: 'дані новели', glossary: 'словник',
    agreement: 'узгодження слів', illustration: 'ілюстрація', illustration_prompt: 'опис ілюстрації', proposal: 'пропозиції новел',
    other: 'інше',
};

const KIND_NAMES: Record<string, string> = { retry: 'повтор', split: 'поділ', proofread_skipped: 'без вичитки', missing: 'пропущені рядки' };

/**
 * The owner's analytics: where the money for models goes, step by step and model by model, how
 * often people correct each model's work, and how the site lives.
 */
export function AnalyticsPage() {
    const [days, setDays] = useState<(typeof PERIODS)[number]['value']>('30');
    const report = useQuery({
        queryKey: ['analytics', days],
        queryFn: () => analyticsApi.report(Number(days)),
        placeholderData: keepPreviousData,
    });
    const data = report.data;
    return (
        <section className={styles.page} data-wide>
            <Link to="/admin" className={styles.muted}>‹ Адміністрування</Link>
            <h1 className={styles.title}>Аналітика</h1>
            <Segmented label="Період" value={days} options={PERIODS} onChange={setDays} />
            {report.isError && <Notice tone="error">{report.error.message}</Notice>}
            {!data && report.isPending && <p className={styles.muted}>Рахуємо…</p>}
            {data && (
                <div className={report.isPlaceholderData ? styles.stale : undefined}>
                    <Money data={data} />
                    <Quality data={data} />
                    <Site data={data} />
                </div>
            )}
        </section>
    );
}

// ---- money --------------------------------------------------------------------------------------

function Money({ data }: { data: Analytics }) {
    const { spend } = data;
    const change = data.period.days > 0 && spend.previousUsd > 0 ? (spend.usd - spend.previousUsd) / spend.previousUsd : null;
    return (
        <>
            <h2 className={styles.section}>Гроші на моделі</h2>
            <div className={styles.cards}>
                <Card value={usd(spend.usd)} label="витрачено"
                    note={change === null ? undefined : `${change >= 0 ? '+' : ''}${pct(change)} до попереднього періоду`} />
                <Card value={String(spend.chapters)} label="глав перекладено" />
                <Card value={usd(spend.usdPerChapter)} label="у середньому за главу" />
                <Card value={String(spend.calls)} label="запитів до моделей"
                    note={spend.failedCalls + spend.uncertainCalls > 0 ? `невдалих ${spend.failedCalls}, загублених ${spend.uncertainCalls}` : 'усі вдалі'} />
                <Card value={tokens(spend.tokensIn + spend.tokensOut)} label="токенів"
                    note={`прочитано ${tokens(spend.tokensIn)}, написано ${tokens(spend.tokensOut)}`} />
                <Card value={spend.estimateRatio === null ? '—' : pct(spend.estimateRatio)} label="фактично від оцінки"
                    note="скільки заплатили від того, що рахували наперед" />
            </div>

            <Panel title={data.period.bucket === 'day' ? 'Витрати за днями' : 'Витрати за тижнями'}>
                <StackedColumns label="Витрати за кроками" series={STAGE_SERIES} format={usd}
                    buckets={filled(data, data.spendSeries.map((b) => ({ start: b.start, values: b.usdByStage })))} />
            </Panel>

            <Panel title="На що йдуть гроші">
                <ShareBar format={usd} parts={STAGE_SERIES.map((s) => ({
                    ...s, value: data.stages.filter((row) => (s.key === 'other' ? !['analyze', 'translate', 'proofread'].includes(row.stage) : row.stage === s.key))
                        .reduce((sum, row) => sum + row.usd, 0),
                }))} />
                <Table head={['Крок', 'Витрачено', 'Частка', 'Запитів', 'Токенів прочитано', 'написано']}
                    rows={data.stages.map((row) => [STAGE_NAMES[row.stage] ?? row.stage, usd(row.usd), pct(row.share), row.calls,
                        tokens(row.tokensIn), tokens(row.tokensOut)])} />
            </Panel>

            <Panel title="Моделі на кожному кроці">
                <Bars format={usd} rows={data.models.slice(0, 12).map((row) => ({
                    label: `${short(row.model)} · ${STAGE_NAMES[row.stage] ?? row.stage}`, value: row.usd, hint: row.model,
                    color: STAGE_SERIES.find((s) => s.key === row.stage)?.color ?? 'var(--chart-violet)',
                }))} />
                <Table head={['Модель', 'Крок', 'Запитів', 'Невдалих', 'Прочитано', 'Написано', 'Витрачено', '$ за 1 млн токенів',
                    'Відповідь, сер.', '90% швидше за', 'Написано / прочитано']}
                    rows={data.models.map((row) => [<span title={row.model}>{short(row.model)}</span>, STAGE_NAMES[row.stage] ?? row.stage,
                        row.calls, row.failed ? `${row.failed} (${pct(row.failed / row.calls)})` : 0, tokens(row.tokensIn), tokens(row.tokensOut),
                        usd(row.usd), row.usdPerMillionTokens === null ? '—' : usd(row.usdPerMillionTokens), seconds(row.secondsAverage),
                        seconds(row.secondsP90), row.outputPerInput === null ? '—' : row.outputPerInput.toFixed(2)])} />
            </Panel>

            <Panel title="Скільки коштує глава за різних моделей"
                hint="Кожен рядок — свій набір моделей для аналізу, перекладу й вичитки; «—» — крок вимкнено.">
                <Bars format={usd} rows={data.combos.map((row) => ({
                    label: `${short(row.translate)} + ${short(row.proofread)}`, value: row.usdPerChapter,
                    hint: `аналіз ${row.analyze}, переклад ${row.translate}, вичитка ${row.proofread}`,
                }))} />
                <Table head={['Аналіз', 'Переклад', 'Вичитка', 'Глав', 'Усього', 'За главу', 'з них аналіз', 'переклад', 'вичитка',
                    'За 1000 знаків оригіналу', 'Час на главу']}
                    rows={data.combos.map((row) => [short(row.analyze), short(row.translate), short(row.proofread), row.chapters,
                        usd(row.usd), usd(row.usdPerChapter), usd(row.usdByStage.analyze ?? 0), usd(row.usdByStage.translate ?? 0),
                        usd(row.usdByStage.proofread ?? 0), row.usdPerThousandChars === null ? '—' : usd(row.usdPerThousandChars),
                        seconds(row.secondsPerChapter)])} />
            </Panel>

            <Panel title="Новели">
                <Table head={['Новела', 'Глав', 'Усього', 'За главу', 'Аналіз', 'Переклад', 'Вичитка', 'Інше']}
                    rows={data.novels.map((row) => [<Link to="/n/$slug" params={{ slug: row.slug }}>{row.title}</Link>, row.chapters,
                        usd(row.usd), usd(row.usdPerChapter), usd(row.usdByStage.analyze ?? 0), usd(row.usdByStage.translate ?? 0),
                        usd(row.usdByStage.proofread ?? 0), usd(row.usdByStage.other ?? 0)])} />
            </Panel>

            <Panel title="Хто платить">
                <div className={styles.cards}>
                    <Card value={usd(data.funding.siteUsd)} label="за рахунок сайту" />
                    <Card value={usd(data.funding.peopleUsd)} label="запуски за шаги людей" />
                    <Card value={usd(data.funding.outsideRunsUsd)} label="поза запусками" note="дані новел, словник, ілюстрації" />
                    <Card value={String(data.funding.chargedShah)} label="шагів списано" note={`≈ ${usd(data.funding.chargedUsd)}`} />
                </div>
            </Panel>
        </>
    );
}

// ---- quality ------------------------------------------------------------------------------------

function Quality({ data }: { data: Analytics }) {
    const label = (row: { translate: string; proofread: string }) => `${short(row.translate)} + ${short(row.proofread)}`;
    return (
        <>
            <h2 className={styles.section}>Якість моделей</h2>
            <Panel title="Як часто люди виправляють переклад"
                hint="Глави, перекладені за період, за моделлю перекладу й вичитки. «Змінено» — що в тексті тепер інакше, ніж опублікувала модель.">
                <h3 className={styles.sub}>Абзаців змінено людьми</h3>
                <Bars format={pct} color="var(--danger)" rows={data.translation.map((row) => ({ label: label(row), value: row.paragraphsChanged }))} />
                <h3 className={styles.sub}>Пропозицій правок на главу</h3>
                <Bars format={(v) => v.toFixed(2)} color="var(--warn)" rows={data.translation.map((row) => ({ label: label(row), value: row.suggestionsPerChapter }))} />
                <h3 className={styles.sub}>Повторів запиту на главу</h3>
                <Bars format={(v) => v.toFixed(2)} color="var(--chart-violet)" rows={data.translation.map((row) => ({ label: label(row), value: row.retriesPerChapter }))} />
                <Table head={['Переклад', 'Вичитка', 'Глав', 'Виправлено людьми', 'Правок редактором', 'Пропозицій', 'прийнято', 'відхилено',
                    'На главу', 'Абзаців змінено', 'Слів змінено', 'Повторів', 'Поділів', 'Пропущених рядків', 'Збоїв', '$ за главу', 'Час на главу']}
                    rows={data.translation.map((row) => [short(row.translate), short(row.proofread), row.chapters,
                        `${row.editedChapters} (${pct(row.editedShare)})`, row.editorRevisions, row.suggestions, row.acceptedSuggestions,
                        row.rejectedSuggestions, row.suggestionsPerChapter.toFixed(2), pct(row.paragraphsChanged), pct(row.wordsChanged),
                        row.retriesPerChapter.toFixed(2), row.splitsPerChapter.toFixed(2), row.missingPerChapter.toFixed(2), row.failedSteps,
                        row.usdPerChapter === null ? '—' : usd(row.usdPerChapter), seconds(row.secondsPerChapter)])} />
            </Panel>

            <Panel title="Вичитка" hint="Скільки рядків модель вичитки змінила в частині глави і як часто вичитку довелося пропустити.">
                <Bars format={(v) => v.toFixed(1)} color="var(--warn)" rows={data.proofread.map((row) => ({ label: short(row.model), value: row.changedPerPart }))} />
                <Table head={['Модель', 'Частин', 'Змінено рядків', 'На частину', 'Пропущено', 'Частка пропусків', 'Повторів на частину']}
                    rows={data.proofread.map((row) => [short(row.model), row.parts, row.changedLines, row.changedPerPart.toFixed(1),
                        row.skipped, pct(row.skippedShare), row.retriesPerPart.toFixed(2)])} />
            </Panel>

            <Panel title="Аналіз" hint="Записи словника, які запропонувала модель аналізу, і що з ними зробили люди; назви глав, які довелося виправити.">
                <Bars format={pct} color="var(--danger)" rows={data.analysis.map((row) => ({ label: short(row.model), value: row.rejectedShare }))} />
                <Table head={['Модель', 'Глав', 'Записів словника', 'На главу', 'Схвалено як є', 'Змінено', 'Відхилено', 'Чекають',
                    'Частка відхилених', 'Назв виправлено', 'Повторів на главу', '$ за главу']}
                    rows={data.analysis.map((row) => [short(row.model), row.chapters, row.entries, row.entriesPerChapter.toFixed(1), row.approved,
                        row.changed, row.rejected, row.waiting, pct(row.rejectedShare), `${row.titlesEdited} (${pct(row.titlesEditedShare)})`,
                        row.retriesPerChapter.toFixed(2), row.usdPerChapter === null ? '—' : usd(row.usdPerChapter)])} />
            </Panel>

            <Panel title="Чому моделі доводилося перепитувати">
                {data.reasons.length === 0 ? <p className={styles.muted}>Повторів не було.</p> : (
                    <Table head={['Скільки', 'Що', 'Крок', 'Модель', 'Причина']}
                        rows={data.reasons.map((row) => [row.count, KIND_NAMES[row.kind] ?? row.kind, STAGE_NAMES[row.stage] ?? row.stage,
                            short(row.model), <span className={styles.reason}>{row.reason || '—'}</span>])} />
                )}
            </Panel>
        </>
    );
}

// ---- the site -----------------------------------------------------------------------------------

const SITE_SERIES: Series[] = [
    { key: 'chapters', label: 'Глави', color: 'var(--accent)' },
    { key: 'accounts', label: 'Нові люди', color: 'var(--chart-blue)' },
    { key: 'comments', label: 'Коментарі', color: 'var(--chart-violet)' },
    { key: 'suggestions', label: 'Правки', color: 'var(--warn)' },
];

function Site({ data }: { data: Analytics }) {
    const { site } = data;
    return (
        <>
            <h2 className={styles.section}>Сайт</h2>
            <div className={styles.cards}>
                <Card value={String(site.readers)} label="читали" />
                <Card value={String(site.activeAccounts)} label="заходили" />
                <Card value={String(site.newAccounts)} label="нових людей" />
                <Card value={String(site.chaptersPublished)} label="глав опубліковано" note={`з них ШІ ${site.machineChapters}`} />
                <Card value={String(site.comments)} label="коментарів" />
                <Card value={String(site.suggestions)} label="правок надіслано"
                    note={`прийнято ${site.acceptedSuggestions}, відхилено ${site.rejectedSuggestions}`} />
                <Card value={String(site.libraryAdds)} label="додано в бібліотеки" />
            </div>
            <Panel title="Що відбувалося">
                <StackedColumns label="Події на сайті" series={SITE_SERIES} format={(v) => String(Math.round(v))}
                    buckets={filled(data, site.series.map((b) => ({ start: b.start, values: { chapters: b.chapters, accounts: b.accounts, comments: b.comments, suggestions: b.suggestions } })))} />
            </Panel>
            <Panel title="Що читають">
                <Bars format={(v) => String(v)} rows={site.top.map((row) => ({ label: row.title, value: row.readers, hint: `$${row.team}` }))} />
                <Table head={['Новела', 'Команда', 'Читачів за період', 'У бібліотеках', 'Глав']}
                    rows={site.top.map((row) => [<Link to="/n/$slug" params={{ slug: row.slug }} search={{ t: row.team }}>{row.title}</Link>,
                        row.team, row.readers, row.library, row.chapters])} />
            </Panel>
        </>
    );
}

// ---- pieces -------------------------------------------------------------------------------------

function Card({ value, label, note }: { value: string; label: string; note?: string | undefined }) {
    return (
        <div className={styles.card}>
            <b>{value}</b>
            <span>{label}</span>
            {note && <small>{note}</small>}
        </div>
    );
}

function Panel({ title, hint, children }: { title: string; hint?: string; children: ReactNode }) {
    return (
        <section className={styles.panel}>
            <h3 className={styles.panelTitle}>{title}</h3>
            {hint && <p className={styles.muted}>{hint}</p>}
            {children}
        </section>
    );
}

/** A wide table scrolls sideways inside its panel, never the page. */
function Table({ head, rows }: { head: string[]; rows: ReactNode[][] }) {
    if (rows.length === 0) return <p className={styles.muted}>Даних за цей період немає.</p>;
    return (
        <div className={styles.scroll}>
            <table className={styles.table}>
                <thead><tr>{head.map((cell) => <th key={cell}>{cell}</th>)}</tr></thead>
                <tbody>
                    {rows.map((row, i) => <tr key={i}>{row.map((cell, j) => <td key={j}>{cell}</td>)}</tr>)}
                </tbody>
            </table>
        </div>
    );
}

/** Every day (or week, from Monday) of the period, empty ones too, so time runs evenly along the chart. */
function filled(data: Analytics, buckets: { start: string; values: Record<string, number> }[]) {
    const known = new Map(buckets.map((b) => [b.start, b.values]));
    const start = new Date(`${data.period.from}T00:00:00Z`);
    if (data.period.bucket === 'week') start.setUTCDate(start.getUTCDate() - ((start.getUTCDay() + 6) % 7));
    const out: { start: string; values: Record<string, number> }[] = [];
    const step = data.period.bucket === 'week' ? 7 : 1;
    for (const day = start; day.getTime() <= Date.now(); day.setUTCDate(day.getUTCDate() + step)) {
        const key = day.toISOString().slice(0, 10);
        out.push({ start: key, values: known.get(key) ?? {} });
    }
    return out.length > 0 ? out : buckets;
}

/** «anthropic/claude-opus-5» → «claude-opus-5». */
function short(model: string) {
    return model.includes('/') ? model.slice(model.indexOf('/') + 1) : model;
}

function usd(value: number) {
    if (value === 0) return '$0';
    const digits = Math.abs(value) < 0.01 ? 4 : Math.abs(value) < 1 ? 3 : 2;
    return `$${value.toFixed(digits).replace('.', ',')}`;
}

function pct(value: number) {
    return `${(value * 100).toFixed(value < 0.1 && value > 0 ? 1 : 0).replace('.', ',')}%`;
}

function tokens(value: number) {
    if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1).replace('.', ',')} млн`;
    if (value >= 1000) return `${Math.round(value / 1000)} тис.`;
    return String(value);
}

function seconds(value: number | null) {
    if (value === null) return '—';
    if (value >= 90) return `${Math.round(value / 60)} хв`;
    return `${value.toFixed(value < 10 ? 1 : 0).replace('.', ',')} с`;
}
