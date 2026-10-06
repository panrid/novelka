import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from '@tanstack/react-router';
import { useState } from 'react';
import { JOB_LABELS, autotranslateApi, type JobEvent } from '../../studio/autotranslate';
import { studioApi } from '../../studio/api';
import { Notice } from '../../ui/Notice';
import { messageTime } from '../../lib/dates';
import styles from './jobLog.module.css';

const STAGES: Record<string, string> = { analyze: 'аналіз', translate: 'переклад', proofread: 'вичитка' };

/**
 * A run's journal (етап 17): chapter by chapter what analysis added to the glossary, which
 * entries each part used, retries and why, what proofreading changed. It follows a live run.
 */
export function JobLogPage() {
    const { editionId, jobId } = useParams({ strict: false }) as { editionId: string; jobId: string };
    const id = Number(editionId);
    const log = useQuery({
        queryKey: ['job-log', id, Number(jobId)],
        queryFn: () => autotranslateApi.journal(id, Number(jobId)),
        refetchInterval: (query) => (['queued', 'running'].includes(query.state.data?.job.state ?? 'running') ? 3000 : false),
    });
    const edition = useQuery({ queryKey: ['studio-edition', id], queryFn: () => studioApi.overview(id) });
    const chapters = group(log.data?.events ?? []);

    return (
        <section className={styles.page}>
            <Link to="/studio/$editionId/translate" params={{ editionId }} className={styles.muted}>‹ Автопереклад</Link>
            <h1 className={styles.title}>Журнал запуску</h1>
            {log.isError && <Notice tone="error">{log.error.message}</Notice>}
            {log.data && (
                <p className={styles.muted}>
                    {log.data.job.kind === 'analyze' ? 'Аналіз' : 'Переклад'} глав {log.data.job.from}–{log.data.job.to} ·{' '}
                    {JOB_LABELS[log.data.job.state]} · готово {log.data.job.done}
                    {log.data.job.state === 'running' && ' · оновлюється само'}
                </p>
            )}
            {log.data?.job.state === 'failed' && log.data.job.error && <Notice tone="error">{log.data.job.error}</Notice>}
            {log.data && chapters.length === 0 && (
                <p className={styles.muted}>Записів ще немає: запуск щойно почався або зроблений до того, як з'явився журнал.</p>
            )}
            {chapters.map(({ chapter, events }) => (
                <details key={chapter} className={styles.chapter} open={chapter === chapters[chapters.length - 1].chapter}>
                    <summary>
                        <b>Глава {chapter}</b>
                        <span className={styles.muted}>{summaryOf(events)}</span>
                    </summary>
                    <ol className={styles.events}>
                        {events.map((event) => (
                            <li key={event.id}>
                                <span className={styles.time}>{messageTime(new Date(event.at))}</span>
                                <Line event={event} slug={edition.data?.novelSlug} team={edition.data?.teamHandle} />
                            </li>
                        ))}
                    </ol>
                </details>
            ))}
        </section>
    );
}

function group(events: JobEvent[]) {
    const out: { chapter: number; events: JobEvent[] }[] = [];
    for (const event of events) {
        const last = out[out.length - 1];
        if (last && last.chapter === event.chapter) last.events.push(event);
        else out.push({ chapter: event.chapter, events: [event] });
    }
    return out;
}

function summaryOf(events: JobEvent[]): string {
    if (events.some((event) => event.kind === 'published')) return 'опубліковано';
    const last = events[events.length - 1];
    return last ? ({ analysis: 'аналіз', translated: 'переклад', proofread: 'вичитка', retry: 'повтор' }[last.kind] ?? 'в роботі') : '';
}

const list = (value: unknown): string[] => (Array.isArray(value) ? value.map(String) : []);

/** One event in plain words. */
function Line({ event, slug, team }: { event: JobEvent; slug: string | undefined; team: string | undefined }) {
    const p = event.payload;
    const part = typeof p.part === 'number' ? (typeof p.of === 'number' && p.of > 1 ? ` (частина ${p.part + 1} з ${p.of})` : '') : '';
    switch (event.kind) {
        case 'start':
            return <span>Взято оригінал: {String(p.paragraphs)} абзаців, {String(p.chars)} знаків.</span>;
        case 'analysis': {
            const added = list(p.added);
            return (
                <span>
                    Аналіз{typeof p.part === 'number' && p.part > 0 ? ` (частина ${p.part + 1})` : ''}:{' '}
                    {added.length > 0 ? <>до словника додано <b>{added.join(', ')}</b></> : 'нових записів немає'}
                    {Number(p.linked) > 0 && `; знайдено форми для ${String(p.linked)} наявних записів`}
                    {p.title ? <>; назва глави «{String(p.title)}»</> : null}.
                </span>
            );
        }
        case 'analysis_reused':
            return <span>Аналіз уже був — взято готовий словник і назву.</span>;
        case 'translated': {
            const used = list(p.glossary);
            return (
                <span>
                    Переклад{part}: {String(p.lines)} рядків.{' '}
                    {used.length > 0 ? <>Зі словника: {used.join(', ')}.</> : 'Записів зі словника в цій частині немає.'}
                </span>
            );
        }
        case 'retry':
            return <span className={styles.warn}>Повтор ({STAGES[String(p.stage)] ?? String(p.stage)}{part}): {String(p.reason)}.</span>;
        case 'split':
            return <span className={styles.warn}>Частину поділено навпіл ({String(p.lines)} рядків): {String(p.reason)}.</span>;
        case 'missing':
            return <span className={styles.warn}>Модель пропустила {String(p.lines)} рядк(и) — їх перекладено окремо.</span>;
        case 'proofread':
            return <Proofread part={part} changes={Array.isArray(p.changes) ? (p.changes as { id: string; before: string; after: string }[]) : []} />;
        case 'proofread_skipped':
            return <span className={styles.warn}>Вичитку{part} пропущено: {String(p.reason)}. Лишилась чернетка.</span>;
        case 'published':
            return (
                <span>
                    Опубліковано: глава {String(p.label)}{p.title ? ` «${String(p.title)}»` : ''}.{' '}
                    {slug && <Link to="/n/$slug/$number" params={{ slug, number: String(event.chapter) }} search={team ? { t: team, look: true } : { look: true }}>Читати ›</Link>}
                </span>
            );
        default:
            return <span>{event.kind}</span>;
    }
}

function Proofread({ part, changes }: { part: string; changes: { id: string; before: string; after: string }[] }) {
    const [open, setOpen] = useState(false);
    if (changes.length === 0) return <span>Вичитка{part}: нічого не змінено.</span>;
    return (
        <span>
            Вичитка{part}: змінено {changes.length} рядк(и).{' '}
            <button type="button" className={styles.link} onClick={() => setOpen(!open)}>{open ? 'сховати' : 'що саме'}</button>
            {open && (
                <span className={styles.diff}>
                    {changes.map((change) => (
                        <span key={change.id} className={styles.pair}>
                            <del>{change.before}</del>
                            <ins>{change.after}</ins>
                        </span>
                    ))}
                </span>
            )}
        </span>
    );
}
