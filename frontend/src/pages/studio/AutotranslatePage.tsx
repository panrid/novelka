import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { chaptersWord } from '../../reading/api';
import { JOB_LABELS, JOB_NAMES, STAGE_LABELS, STEP_NAMES, autotranslateApi, dollars, money, shahWord, type Job, type JobKind, type ModelShow, type Plan, type Step } from '../../studio/autotranslate';
import { ModelPicker } from '../../studio/ModelPicker';
import { useDebounced } from '../../lib/useDebounced';
import { relativeTime } from '../../lib/dates';
import { askConfirm } from '../../ui/ask';
import { Collapsible } from '../../ui/Collapsible';
import { EditionShell } from './EditionShell';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import { RefreshTick } from '../../ui/RefreshTick';
import { Toggle } from '../../ui/Toggle';
import { useEditionId } from './EditionPage';
import styles from './studio.module.css';

const active = (job: Job | undefined) => job?.state === 'queued' || job?.state === 'running';
const number = (text: string) => (/^\s*\d{1,5}\s*$/.test(text) ? Number(text) : undefined);

/** «Автопереклад» of a translation: what runs now, a new run with its price, and the runs before. */
export function AutotranslatePage() {
    return (
        <EditionShell tab="translate">
            <AutotranslateTab />
        </EditionShell>
    );
}

/** «Перекласти до глави N»: the price first, then progress, all in шаги or dollars. */
function AutotranslateTab() {
    const id = useEditionId();
    const client = useQueryClient();
    const overview = useQuery({
        queryKey: ['autotranslate', id],
        queryFn: () => autotranslateApi.overview(id),
        // Live events refresh it at once; the slow poll only covers a lost connection.
        refetchInterval: (query) => (active(query.state.data?.jobs[0]) ? 5_000 : false),
    });
    const [steps, setSteps] = useState<Step[]>(['analyze', 'translate', 'proofread']);
    const [to, setTo] = useState('');
    const [from, setFrom] = useState('');
    const [redo, setRedo] = useState(false);
    const [models, setModels] = useState<NonNullable<Plan['models']>>({});
    const [preset, setPreset] = useState<number | null>(null);
    const [withWeak, setWithWeak] = useState(false);
    const modelShow: ModelShow = withWeak ? 'weak' : 'usual';
    const has = (step: Step) => steps.includes(step);
    const kind: JobKind = has('translate') ? 'translate' : has('proofread') ? 'proofread' : 'analyze';
    const toggle = (step: Step) => {
        const next = has(step) ? steps.filter((item) => item !== step) : STEPS.filter((item) => item === step || has(item));
        if (next.length > 0) setSteps(next);
    };

    const data = overview.data;
    const firstOpen = data ? (kind === 'analyze' ? data.nextToAnalyze : kind === 'proofread' ? 1 : data.nextNumber) : 1;
    const plan: Plan | null = number(to) === undefined ? null : {
        kind, steps, to: number(to)!,
        ...(number(from) !== undefined ? { from: number(from)! } : {}),
        ...(redo && kind !== 'proofread' ? { redo: true } : {}),
        ...(preset !== null ? { preset } : {}),
        ...(Object.keys(models).length > 0 ? { models } : {}),
    };
    // Checked only once typing stops: «3» on the way to «30» is not an error.
    const settled = useDebounced(to, 500);
    const settledPlan = useDebounced(plan ? JSON.stringify(plan) : '', 500);
    const typing = settled !== to || settledPlan !== (plan ? JSON.stringify(plan) : '');
    const target = number(settled);
    const start = plan?.from ?? firstOpen;
    let problem: string | null = null;
    if (data && settled.trim() && !typing) {
        if (target === undefined) problem = 'Вкажіть номер глави числом.';
        else if (target > data.sourceChapters) problem = `В оригіналі поки ${data.sourceChapters} ${chaptersWord(data.sourceChapters)}.`;
        else if (target < start && kind !== 'proofread' && !(redo || number(from) !== undefined)) {
            problem = `Глави до ${firstOpen - 1} уже ${kind === 'analyze' ? 'проаналізовано' : 'перекладено'}. `
                + 'Щоб повторити їх, увімкніть «Зробити заново» або вкажіть, з якої глави.';
        }
    }
    const quote = useQuery({
        queryKey: ['autotranslate-quote', id, settledPlan],
        queryFn: () => autotranslateApi.quote(id, JSON.parse(settledPlan) as Plan),
        enabled: Boolean(settledPlan) && !typing && !problem,
        retry: false,
    });
    const refresh = () => {
        void client.invalidateQueries({ queryKey: ['autotranslate', id] });
        void client.invalidateQueries({ queryKey: ['studio-chapters', id] });
    };
    const startJob = useMutation({ mutationFn: () => autotranslateApi.start(id, plan!), onSuccess: () => { setTo(''); refresh(); } });
    const cancel = useMutation({ mutationFn: (jobId: number) => autotranslateApi.cancel(id, jobId), onSuccess: refresh });
    const resume = useMutation({ mutationFn: (jobId: number) => autotranslateApi.resume(id, jobId), onSuccess: refresh });

    if (!data && overview.isError) return <Notice tone="error">{overview.error.message}</Notice>;
    if (!data) return <p className={styles.muted} style={{ paddingTop: 24 }}>Завантажуємо…</p>;
    const show = data.showShah;
    const job = data.jobs[0];
    const busy = job && (active(job) || job.state === 'failed');
    const ready = quote.data && !typing && !problem && JSON.stringify(plan) === settledPlan;
    const fieldError = problem ?? (quote.isError && !typing ? quote.error.message : undefined);
    const chosen = data.presets.find((item) => item.id === preset);
    const model = (stage: 'analyze' | 'translate' | 'proofread') => models[stage] ?? chosen?.[stage] ?? data.settings[stage].model;
    const short = (id: string) => id.slice(id.indexOf('/') + 1);

    return (
        <>
            <p className={styles.muted}>
                В оригіналі {data.sourceChapters} {chaptersWord(data.sourceChapters)}, перекладено {data.publishedChapters}
                {data.lastAnalyzed > 0 && <>, проаналізовано до {data.lastAnalyzed}</>}.
                {data.personal
                    ? <> У вас <b>{data.balance?.shah ?? 0} {shahWord(data.balance?.shah ?? 0)}</b>{data.reserved > 0 && <>, ще {data.reserved} у резерві запусків</>}.</>
                    : data.balance && <> Баланс: <b>{money(data.balance.shah, data.balance.usd, show)}</b>.</>}
            </p>
            {!data.configured && <Notice tone="error">Ключ OpenRouter не налаштовано на сервері.</Notice>}

            {job && <h2 className={styles.sectionTitle}>{busy ? 'Зараз іде' : 'Останній запуск'}</h2>}
            {job && <JobCard job={job} editionId={id} showShah={show} usdPerShah={data.usdPerShah}
                refreshed={{ at: Math.max(overview.dataUpdatedAt, overview.errorUpdatedAt), failed: overview.isRefetchError }}
                onCancel={() => cancel.mutate(job.id)} onResume={() => resume.mutate(job.id)} restorable
                pending={cancel.isPending || resume.isPending} />}
            {(cancel.isError || resume.isError) && <Notice tone="error">{(cancel.error ?? resume.error)!.message}</Notice>}

            {!busy && <h2 className={styles.sectionTitle}>Новий запуск</h2>}
            {!busy && (
                <form className={styles.form} onSubmit={(event) => { event.preventDefault(); if (ready) startJob.mutate(); }}>
                    <div className={styles.steps} role="group" aria-label="Етапи">
                        {STEPS.map((step) => (
                            <button key={step} type="button" className={styles.step} aria-pressed={has(step)} onClick={() => toggle(step)}>
                                {STEP_NAMES[step]}
                                <small>{STEP_HINTS[step]}</small>
                            </button>
                        ))}
                    </div>
                    <p className={styles.muted}>{explain(steps)}</p>
                    <div className={styles.range}>
                        <TextInput label="З глави" value={from} onChange={setFrom} inputMode="numeric" placeholder={String(firstOpen)} />
                        <TextInput label="По главу" value={to} onChange={setTo} inputMode="numeric" error={fieldError}
                            hint={`Щонайбільше ${data.sourceChapters}.`} />
                    </div>
                    {kind !== 'proofread' && <Toggle label="Зробити заново вже опрацьовані глави" isSelected={redo} onChange={setRedo} />}
                    {redo && kind !== 'proofread' && (
                        <p className={styles.muted}>
                            {kind === 'analyze'
                                ? 'Модель проаналізує глави знову; назви глав, які ви виправили, буде замінено новими. Словник лише доповниться.'
                                : 'Глави перекладуться знову й вийдуть новою версією; попередня лишиться в історії глави.'}
                        </p>
                    )}

                    {!data.personal && (<>
                        <h3 className={styles.subTitle}>Моделі</h3>
                        {data.presets.length > 0 && (
                            <div className={styles.presets} role="group" aria-label="Набір моделей">
                                <button type="button" aria-pressed={preset === null} onClick={() => { setPreset(null); setModels({}); }}>Як на сайті</button>
                                {data.presets.map((item) => (
                                    <button key={item.id} type="button" aria-pressed={preset === item.id}
                                        onClick={() => { setPreset(item.id); setModels({}); }}>{item.name}</button>
                                ))}
                            </div>
                        )}
                        {chosen && <p className={styles.muted}>{chosen.summary} Оцінка якості — {String(chosen.rating).replace('.', ',')} з 5.</p>}
                        {STEPS.filter((step) => has(step) || step === 'analyze' && kind === 'translate').map((step) => (
                            <StepModel key={`${step}-${preset}`} step={step} model={model(step)} chars={data.averageChars} show={modelShow}
                                note={step === 'analyze' && !has('analyze') ? 'лише для глав, які ще не проаналізовано' : undefined}
                                onChange={(id) => setModels({ ...models, [step]: id })} />
                        ))}
                        <label className={styles.check}>
                            <input type="checkbox" checked={withWeak} onChange={(event) => setWithWeak(event.target.checked)} />
                            <span>Показувати й слабкі моделі</span>
                        </label>
                        <p className={styles.muted}>
                            Ціна — для середньої глави цієї новели (~{data.averageChars.toLocaleString('uk-UA')} знаків оригіналу): «виміряно» — скільки модель
                            справді витратила тут, «оцінка» — з цін за токени (моделі, що роздумують, витрачають більше). Вибір діє лише для цього запуску;
                            постійні моделі — на <Link to="/me/wallet">«Шагах»</Link>.
                        </p>
                    </>)}

                    {ready && quote.data && (
                        <div className={styles.quote} aria-live="polite">
                            <div>
                                {quote.data.chapters} {chaptersWord(quote.data.chapters)} · {quote.data.estimated ? 'орієнтовно ' : ''}
                                <b>{data.personal ? `≈ ${quote.data.shah} ${shahWord(quote.data.shah)}` : money(quote.data.shah, quote.data.usd, show)}</b>
                                {quote.data.skipped > 0 && <span className={styles.muted}> · {kind === 'proofread' ? 'ще не перекладено' : 'пропускаємо вже зроблені'}: {quote.data.skipped}</span>}
                            </div>
                            {data.personal ? (
                                <div className={styles.muted}>
                                    Спишемо фактичні витрати моделей, округлені вгору до цілого шагу. На час запуску заблокуємо{' '}
                                    {quote.data.reserveShah} {shahWord(quote.data.reserveShah)}, решту повернемо.
                                </div>
                            ) : (
                                <div className={styles.muted}>
                                    Очікувана собівартість ≈ {dollars(quote.data.expectedUsd, 3)} ·{' '}
                                    {chosen && <>набір «{chosen.name}»: </>}
                                    {quote.data.steps.map((step) => `${STEP_NAMES[step].toLowerCase()} ${short(quote.data[`${step}Model`].model)}`).join(', ')}
                                </div>
                            )}
                            {quote.data.unanalyzed > 0 && (
                                <div className={styles.muted}>
                                    {quote.data.unanalyzed === quote.data.chapters ? 'Ці глави' : `${quote.data.unanalyzed} з них`} ще не проаналізовано:
                                    словник для них складеться під час перекладу, перевірити його заздалегідь не вийде.
                                </div>
                            )}
                        </div>
                    )}
                    {startJob.isError && <Notice tone="error">{startJob.error.message}</Notice>}
                    <Button type="submit" wide pending={startJob.isPending} pendingLabel="Запускаємо…" isDisabled={!ready || !data.configured}>
                        Запустити: {steps.map((step) => STEP_NAMES[step].toLowerCase()).join(', ')}
                    </Button>
                </form>
            )}

            <nav className={styles.menu} aria-label="Ще">
                <Link to="/studio/processes" className={styles.menuItem}>Усі процеси</Link>
                {data.personal
                    ? <Link to="/me/shahs" className={styles.menuItem}>Мої шаги</Link>
                    : <Link to="/me/wallet" className={styles.menuItem}>Моделі, ціни й собівартість</Link>}
            </nav>

            {data.jobs.length > 1 && (
                <Collapsible id="autotranslate-earlier" title="Історія запусків" count={data.jobs.length - 1}>
                    {data.jobs.slice(1).map((old) => (
                        <div key={old.id} className={styles.row}>
                            <div className={styles.grow}>{JOB_NAMES[old.kind]} {range(old)} · {JOB_LABELS[old.state]}</div>
                            <span className={styles.muted}>
                                {old.personal ? `${old.chargedShah} ${shahWord(old.chargedShah)}` : money(old.spentShah, old.spentUsd, show)} · {relativeTime(new Date(old.createdAt))}
                            </span>
                        </div>
                    ))}
                </Collapsible>
            )}
        </>
    );
}

const STEPS: Step[] = ['analyze', 'translate', 'proofread'];

/** One step's model in a line — its price for an average chapter, measured or estimated — and «Змінити» to pick another. */
function StepModel({ step, model, chars, show, note, onChange }: {
    step: Step; model: string; chars: number; show: ModelShow; note: string | undefined; onChange: (model: string) => void;
}) {
    const [picking, setPicking] = useState(false);
    const found = useQuery({
        queryKey: ['models', model, chars, step, 'weak', 'text'],
        queryFn: () => autotranslateApi.models(model, chars, 'text', step, 'weak'),
        staleTime: 5 * 60_000,
    });
    const choice = found.data?.find((item) => item.id === model);
    const short = model.slice(model.indexOf('/') + 1);
    return (
        <div className={styles.stepModel}>
            <div className={styles.stepLine}>
                <span className={styles.grow}>
                    {STEP_NAMES[step]}{note && <span className={styles.muted}> ({note})</span>}<br />
                    <b>{short}</b>
                </span>
                <span className={styles.stepPrice}>
                    {choice ? <>≈ {dollars(choice.chapterUsd, 3)}<br /><span className={styles.muted}>{choice.measuredChapters
                        ? `за главу, виміряно на ${choice.measuredChapters}` : 'за главу, оцінка'}</span></> : <span className={styles.muted}>…</span>}
                </span>
                <button type="button" className={styles.link} aria-expanded={picking} onClick={() => setPicking(!picking)}
                    aria-label={`${STEP_NAMES[step]}: змінити модель`}>{picking ? 'Згорнути' : 'Змінити'}</button>
            </div>
            {picking && (
                <ModelPicker label={`Модель: ${STEP_NAMES[step].toLowerCase()}`} show={show} stage={step} value={model} chars={chars}
                    onChange={(id) => { onChange(id); setPicking(false); }} />
            )}
        </div>
    );
}
const STEP_HINTS: Record<Step, string> = { analyze: 'словник, назви', translate: 'з оригіналу', proofread: 'редагує текст' };

/** What the chosen steps will do, in a sentence. */
function explain(steps: Step[]): string {
    const has = (step: Step) => steps.includes(step);
    if (has('translate')) {
        return `${has('analyze') ? 'Спершу аналіз (словник і назви глав), тоді переклад' : 'Переклад; глави без аналізу проаналізуються дорогою'}${has('proofread') ? ' і вичитка' : ' без вичитки'}.`;
    }
    if (has('proofread')) {
        return `${has('analyze') ? 'Аналіз, тоді вичитка' : 'Вичитка'} вже перекладених глав: виправлена версія публікується, попередня лишається в історії глави.`;
    }
    return 'Модель збере імена й терміни в словник і перекладе назви глав. Перевірте їх, а тоді запускайте переклад: він візьме готовий аналіз і не платитиме за нього вдруге.';
}

/** «глава 3» or «глави 3–5». */
const range = (job: Pick<Job, 'from' | 'to'>) => (job.from === job.to ? `глава ${job.from}` : `глави ${job.from}–${job.to}`);

/**
 * @param refreshed  when the card's data last came (or failed to): a live run shows a turn per refresh
 * @param restorable the novel's last run: cancelled by the site owner, it can be taken up again
 */
export function JobCard({ job, editionId, showShah, usdPerShah, onCancel, onResume, pending, title, refreshed, restorable = false }: {
    job: Job; editionId: number; showShah: boolean; usdPerShah: number; onCancel: () => void; onResume: () => void; pending: boolean;
    title?: React.ReactNode; refreshed?: { at: number; failed: boolean }; restorable?: boolean;
}) {
    // One tap on «Скасувати» by mistake stopped a run: it asks first.
    const confirmCancel = async () => {
        if (await askConfirm({
            title: 'Скасувати переклад?',
            text: `Готові глави залишаться. ${job.personal ? 'Невитрачені шаги повернуться.' : 'Відновити запуск можна, поки він останній для цієї новели.'}`,
            confirmLabel: 'Скасувати переклад', danger: true,
        })) onCancel();
    };
    const total = Math.max(1, job.to - job.from + 1);
    // The chapter at work counts by its parts, so a long chapter does not leave the bar empty.
    const working = active(job) && job.current ? job.current.progress : 0;
    const percent = Math.min(100, Math.round(((job.done + working) / total) * 100));
    const step = job.current;
    return (
        <div className={styles.jobCard} aria-live="polite">
            {title}
            {/* The progress opens the run's journal: what each step did (етап 17). */}
            <Link to="/studio/$editionId/translate/jobs/$jobId" params={{ editionId: String(editionId), jobId: String(job.id) }}
                className={styles.jobLink} aria-label={`Журнал запуску: ${JOB_NAMES[job.kind].toLowerCase()} ${range(job)}`}>
            <div className={styles.jobHead}>
                <b>{JOB_NAMES[job.kind]}: {range(job)}</b>
                <span className={styles.jobState}>
                    {refreshed && active(job) && <RefreshTick at={refreshed.at} failed={refreshed.failed} />}
                    <span className={styles.badge}>{JOB_LABELS[job.state]}</span>
                </span>
            </div>
            <div className={styles.progress} role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}
                aria-label="Зроблено, відсотків">
                <span style={{ width: `${percent}%` }} />
            </div>
            <div className={styles.muted}>
                Готово {job.done}
                {step && active(job) && <> · глава {step.number}: {STAGE_LABELS[step.stage] ?? step.stage}
                    {step.parts > 0 && (step.stage === 'translate' || step.stage === 'proofread')
                        && `, частина ${Math.min(step.part + 1, step.parts)} з ${step.parts}`}</>}
                {job.personal && !active(job) && job.state !== 'failed'
                    ? <>{' · '}списано {job.chargedShah} {shahWord(job.chargedShah)}</>
                    : <>{' · '}витрачено {money(job.spentShah, job.spentUsd, showShah)} · {job.personal ? 'резерв' : 'кошторис'}{' '}
                        {money(job.quoteShah, job.quoteShah * usdPerShah, showShah)}</>}
            </div>
            <span className={styles.jobMore}>Що відбувається ›</span>
            </Link>
            {job.current?.error && active(job) && <p className={styles.muted}>{job.current.error}</p>}
            {job.state === 'failed' && job.error && <Notice tone="error">{job.error}</Notice>}
            {(active(job) || job.state === 'failed') && (
                <div className={styles.actions}>
                    {job.state === 'failed' && <Button onPress={onResume} pending={pending}>Продовжити</Button>}
                    <Button variant="secondary" onPress={() => void confirmCancel()} isDisabled={pending}>Скасувати</Button>
                </div>
            )}
            {job.state === 'cancelled' && restorable && !job.personal && job.done < job.to - job.from + 1 && (
                <div className={styles.actions}>
                    <Button variant="secondary" onPress={onResume} pending={pending}>Відновити</Button>
                </div>
            )}
        </div>
    );
}
