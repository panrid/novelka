import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useEffect, useState } from 'react';
import { chaptersWord } from '../../reading/api';
import { JOB_LABELS, STAGE_LABELS, autotranslateApi, dollars, money, type Job } from '../../studio/autotranslate';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import { relativeTime } from '../../lib/dates';
import { useEditionId } from './EditionPage';
import styles from './studio.module.css';

const active = (job: Job | undefined) => job?.state === 'queued' || job?.state === 'running';

/** «Перекласти до глави N»: the price first, then progress, all in шаги or dollars. */
export function AutotranslatePage() {
    const id = useEditionId();
    const client = useQueryClient();
    const [to, setTo] = useState('');
    const target = /^\d{1,5}$/.test(to) ? Number(to) : undefined;
    const overview = useQuery({
        queryKey: ['autotranslate', id, target ?? null],
        queryFn: () => autotranslateApi.overview(id, target),
        placeholderData: (previous) => previous,
        refetchInterval: (query) => (active(query.state.data?.jobs[0]) ? 3_000 : false),
        retry: false,
    });
    const data = overview.data;
    const job = data?.jobs[0];
    const refresh = () => {
        void client.invalidateQueries({ queryKey: ['autotranslate', id] });
        void client.invalidateQueries({ queryKey: ['studio-chapters', id] });
    };
    const start = useMutation({ mutationFn: () => autotranslateApi.start(id, target!), onSuccess: () => { setTo(''); refresh(); } });
    const cancel = useMutation({ mutationFn: (jobId: number) => autotranslateApi.cancel(id, jobId), onSuccess: refresh });
    const resume = useMutation({ mutationFn: (jobId: number) => autotranslateApi.resume(id, jobId), onSuccess: refresh });

    // Chapters appear in the Studio list as they are published.
    const done = job?.done;
    useEffect(() => {
        if (done !== undefined) void client.invalidateQueries({ queryKey: ['studio-chapters', id] });
    }, [done, id, client]);

    if (!data && overview.isError) return <Notice tone="error">{overview.error.message}</Notice>;
    if (!data) return <p className={styles.muted} style={{ paddingTop: 24 }}>Завантажуємо…</p>;
    const show = data.showShah;
    const left = data.sourceChapters - data.nextNumber + 1;
    const busy = job && (active(job) || job.state === 'failed');
    const quoteError = overview.isError && target ? overview.error.message : null;

    return (
        <section className={styles.page}>
            <Link to="/studio/$editionId" params={{ editionId: String(id) }} className={styles.muted}>‹ До перекладу</Link>
            <h1 className={styles.title}>Автопереклад</h1>
            <p className={styles.muted}>
                В оригіналі {data.sourceChapters} {chaptersWord(data.sourceChapters)}, перекладено {data.publishedChapters}.
                {data.balance && <> Баланс: <b>{money(data.balance.shah, data.balance.usd, show)}</b>.</>}
            </p>
            {!data.configured && <Notice tone="error">Ключ OpenRouter не налаштовано на сервері.</Notice>}

            {job && <JobCard job={job} showShah={show} usdPerShah={data.usdPerShah}
                onCancel={() => cancel.mutate(job.id)} onResume={() => resume.mutate(job.id)}
                pending={cancel.isPending || resume.isPending} />}
            {(cancel.isError || resume.isError) && <Notice tone="error">{(cancel.error ?? resume.error)!.message}</Notice>}

            {!busy && left > 0 && (
                <form className={styles.form} onSubmit={(event) => { event.preventDefault(); if (target) start.mutate(); }}>
                    <TextInput label={`Перекласти з глави ${data.nextNumber} до глави…`} value={to} onChange={setTo}
                        inputMode="numeric" hint={`Щонайбільше ${data.sourceChapters}.`} />
                    {data.quote && target === data.quote.to && (
                        <div className={styles.quote} aria-live="polite">
                            <div>
                                {data.quote.chapters} {chaptersWord(data.quote.chapters)} · {data.quote.estimated ? 'орієнтовно ' : ''}
                                <b>{money(data.quote.shah, data.quote.usd, show)}</b>
                            </div>
                            <div className={styles.muted}>
                                Шаг — до 10 000 знаків оригіналу{show ? ` (≈ ${dollars(data.usdPerShah, 3)})` : ''}.
                                {data.quote.estimated && ' Довжину ще не завантажених глав оцінено за вже відомими.'}
                            </div>
                        </div>
                    )}
                    {quoteError && <Notice tone="error">{quoteError}</Notice>}
                    {start.isError && <Notice tone="error">{start.error.message}</Notice>}
                    <Button type="submit" wide pending={start.isPending} pendingLabel="Запускаємо…"
                        isDisabled={!data.quote || target !== data.quote.to || !data.configured}>
                        Почати переклад
                    </Button>
                </form>
            )}
            {!busy && left <= 0 && <p className={styles.muted}>Усі глави оригіналу вже перекладено.</p>}

            <nav className={styles.menu} aria-label="Ще">
                <Link to="/studio/$editionId/glossary" params={{ editionId: String(id) }} className={styles.menuItem}>Словник імен і термінів</Link>
                <Link to="/me/wallet" className={styles.menuItem}>Моделі, ціни й собівартість</Link>
            </nav>

            {data.jobs.length > 1 && (
                <>
                    <h2 className={styles.sectionTitle}>Раніше</h2>
                    {data.jobs.slice(1).map((old) => (
                        <div key={old.id} className={styles.row}>
                            <div className={styles.grow}>Глави {old.from}–{old.to} · {JOB_LABELS[old.state]}</div>
                            <span className={styles.muted}>{money(old.spentShah, old.spentUsd, show)} · {relativeTime(new Date(old.createdAt))}</span>
                        </div>
                    ))}
                </>
            )}
        </section>
    );
}

function JobCard({ job, showShah, usdPerShah, onCancel, onResume, pending }: {
    job: Job; showShah: boolean; usdPerShah: number; onCancel: () => void; onResume: () => void; pending: boolean;
}) {
    const total = job.to - job.from + 1;
    const percent = Math.round((job.done / total) * 100);
    return (
        <div className={styles.jobCard} aria-live="polite">
            <div className={styles.jobHead}>
                <b>Глави {job.from}–{job.to}</b>
                <span className={styles.badge}>{JOB_LABELS[job.state]}</span>
            </div>
            <div className={styles.progress} role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={job.done}
                aria-label="Перекладено глав">
                <span style={{ width: `${percent}%` }} />
            </div>
            <div className={styles.muted}>
                Готово {job.done} з {total}
                {job.current && active(job) && <> · глава {job.current.number}: {STAGE_LABELS[job.current.stage] ?? job.current.stage}</>}
                {' · '}витрачено {money(job.spentShah, job.spentUsd, showShah)} з {money(job.quoteShah, job.quoteShah * usdPerShah, showShah)}
            </div>
            {job.current?.error && active(job) && <p className={styles.muted}>{job.current.error}</p>}
            {job.state === 'failed' && job.error && <Notice tone="error">{job.error}</Notice>}
            {(active(job) || job.state === 'failed') && (
                <div className={styles.actions}>
                    {job.state === 'failed' && <Button onPress={onResume} pending={pending}>Продовжити</Button>}
                    <Button variant="secondary" onPress={onCancel} isDisabled={pending}>Скасувати</Button>
                </div>
            )}
        </div>
    );
}
