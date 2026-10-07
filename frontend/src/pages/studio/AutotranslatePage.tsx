import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { chaptersWord } from '../../reading/api';
import { JOB_LABELS, STAGE_LABELS, autotranslateApi, dollars, money, shahWord, type Job, type JobKind, type ModelShow, type Plan } from '../../studio/autotranslate';
import { ModelPicker } from '../../studio/ModelPicker';
import { PresetPicker } from '../../studio/PresetPicker';
import pickerStyles from '../../studio/modelPicker.module.css';
import { useDebounced } from '../../lib/useDebounced';
import { relativeTime } from '../../lib/dates';
import { askConfirm } from '../../ui/ask';
import { Collapsible } from '../../ui/Collapsible';
import { EditionShell } from './EditionShell';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
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
    const [kind, setKind] = useState<JobKind>('analyze');
    const [to, setTo] = useState('');
    const [advanced, setAdvanced] = useState(false);
    const [from, setFrom] = useState('');
    const [redo, setRedo] = useState(false);
    const [models, setModels] = useState<NonNullable<Plan['models']>>({});
    const [preset, setPreset] = useState<number | null>(null);
    const [onlyRecommended, setOnlyRecommended] = useState(true);
    const [withWeak, setWithWeak] = useState(false);
    const modelShow: ModelShow = onlyRecommended ? 'recommended' : withWeak ? 'weak' : 'usual';

    const data = overview.data;
    const firstOpen = data ? (kind === 'analyze' ? data.nextToAnalyze : data.nextNumber) : 1;
    const plan: Plan | null = number(to) === undefined ? null : {
        kind, to: number(to)!,
        ...(advanced && number(from) !== undefined ? { from: number(from)! } : {}),
        ...(advanced && redo ? { redo: true } : {}),
        ...(preset !== null ? { preset } : {}),
        ...(advanced && Object.keys(models).length > 0 ? { models } : {}),
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
        else if (target < start && !(advanced && (redo || number(from) !== undefined))) {
            problem = `Глави до ${firstOpen - 1} уже ${kind === 'analyze' ? 'проаналізовано' : 'перекладено'}. `
                + 'Щоб повторити їх або вибрати інший діапазон, відкрийте «Розширені налаштування».';
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
    const proofreading = models.proofreadEnabled ?? (chosen ? chosen.proofread !== null : data.settings.proofread.enabled);
    const short = (id: string) => id.slice(id.indexOf('/') + 1);
    const siteModels = kind === 'analyze' ? short(data.settings.analyze.model)
        : `${short(data.settings.translate.model)}, ${data.settings.proofread.enabled ? `вичитка ${short(data.settings.proofread.model)}` : 'без вичитки'}`;

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
                    <Segmented label="Що робимо" value={kind} onChange={(next) => { setKind(next); setModels({}); }} options={[
                        { value: 'analyze', label: 'Аналіз і словник' },
                        { value: 'translate', label: 'Переклад' },
                    ]} />
                    {kind === 'analyze' && (
                        <p className={styles.muted}>
                            Модель збере імена й терміни в словник і перекладе назви глав. Перевірте їх, а тоді запускайте переклад:
                            він візьме готовий аналіз і не платитиме за нього вдруге.
                        </p>
                    )}
                    {!data.personal && data.presets.length > 0 && (
                        <PresetPicker presets={data.presets} value={preset} kind={kind} siteModels={siteModels}
                            onChange={(next) => { setPreset(next); setModels({}); }} />
                    )}
                    <TextInput label={`${kind === 'analyze' ? 'Аналізувати' : 'Перекласти'} ${advanced && number(from) ? `з глави ${number(from)}` : `з глави ${firstOpen}`} до глави…`}
                        value={to} onChange={setTo} inputMode="numeric" error={fieldError}
                        hint={`Щонайбільше ${data.sourceChapters}.`} />

                    <button type="button" className={styles.disclosure} aria-expanded={advanced} onClick={() => setAdvanced(!advanced)}>
                        {advanced ? '▾' : '▸'} Розширені налаштування
                    </button>
                    {advanced && (
                        <div className={styles.advanced}>
                            <TextInput label="З глави" value={from} onChange={setFrom} inputMode="numeric"
                                placeholder={String(firstOpen)} hint="Порожньо — з першої ще не опрацьованої." />
                            <Toggle label="Зробити заново вже опрацьовані глави" isSelected={redo} onChange={setRedo} />
                            {redo && (
                                <p className={styles.muted}>
                                    {kind === 'analyze'
                                        ? 'Модель проаналізує глави знову; назви глав, які ви виправили, буде замінено новими. Словник лише доповниться.'
                                        : 'Глави перекладуться знову й вийдуть новою версією; попередня лишиться в історії глави.'}
                                </p>
                            )}
                            {!data.personal && (<>
                            <div className={pickerStyles.filters}>
                                <label>
                                    <input type="checkbox" checked={onlyRecommended} onChange={(event) => setOnlyRecommended(event.target.checked)} />
                                    <span>Лише рекомендовані моделі</span>
                                </label>
                                <label>
                                    <input type="checkbox" checked={withWeak} disabled={onlyRecommended} onChange={(event) => setWithWeak(event.target.checked)} />
                                    <span>Показати й слабкі</span>
                                </label>
                            </div>
                            <ModelPicker key={`analyze-${preset}`} label="Модель аналізу" show={modelShow} stage="analyze" value={model('analyze')} chars={data.averageChars}
                                onChange={(analyze) => setModels({ ...models, analyze })}
                                hint={kind === 'translate' ? 'Для глав, які ще не проаналізовано.' : undefined} />
                            {kind === 'translate' && (
                                <>
                                    <ModelPicker key={`translate-${preset}`} label="Модель перекладу" show={modelShow} stage="translate" value={model('translate')} chars={data.averageChars}
                                        onChange={(translate) => setModels({ ...models, translate })} />
                                    <Toggle label="Вичитка" isSelected={proofreading}
                                        onChange={(proofreadEnabled) => setModels({ ...models, proofreadEnabled })} />
                                    {proofreading && (
                                        <ModelPicker key={`proofread-${preset}`} label="Модель вичитки" show={modelShow} stage="proofread" value={model('proofread')} chars={data.averageChars}
                                            onChange={(proofread) => setModels({ ...models, proofread })} />
                                    )}
                                </>
                            )}
                            <p className={styles.muted}>
                                Ціна «за главу» — для середньої глави цієї новели (~{data.averageChars.toLocaleString('uk-UA')} знаків), якщо всі кроки
                                робить ця модель. Вибір діє лише для цього запуску; постійні моделі — на <Link to="/me/wallet">«Шагах»</Link>.
                            </p>
                            </>)}
                        </div>
                    )}

                    {ready && quote.data && (
                        <div className={styles.quote} aria-live="polite">
                            <div>
                                {quote.data.chapters} {chaptersWord(quote.data.chapters)} · {quote.data.estimated ? 'орієнтовно ' : ''}
                                <b>{data.personal ? `≈ ${quote.data.shah} ${shahWord(quote.data.shah)}` : money(quote.data.shah, quote.data.usd, show)}</b>
                                {quote.data.skipped > 0 && <span className={styles.muted}> · пропускаємо вже зроблені: {quote.data.skipped}</span>}
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
                                    {kind === 'analyze' ? quote.data.analyzeModel.model
                                        : `${quote.data.translateModel.model}${quote.data.proofreadModel.enabled ? `, вичитка ${quote.data.proofreadModel.model}` : ', без вичитки'}`}
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
                        {kind === 'analyze' ? 'Почати аналіз' : 'Почати переклад'}
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
                            <div className={styles.grow}>{old.kind === 'analyze' ? 'Аналіз' : 'Переклад'} {range(old)} · {JOB_LABELS[old.state]}</div>
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
                className={styles.jobLink} aria-label={`Журнал запуску: ${job.kind === 'analyze' ? 'аналіз' : 'переклад'} ${range(job)}`}>
            <div className={styles.jobHead}>
                <b>{job.kind === 'analyze' ? 'Аналіз' : 'Переклад'}: {range(job)}</b>
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
