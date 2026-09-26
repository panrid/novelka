import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { chaptersWord } from '../../reading/api';
import { JOB_LABELS, STAGE_LABELS, autotranslateApi, dollars, money, shahWord, type Job, type JobKind, type ModelShow, type Plan } from '../../studio/autotranslate';
import { ModelPicker } from '../../studio/ModelPicker';
import pickerStyles from '../../studio/modelPicker.module.css';
import { useDebounced } from '../../lib/useDebounced';
import { relativeTime } from '../../lib/dates';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
import { TextInput } from '../../ui/TextInput';
import { Toggle } from '../../ui/Toggle';
import { useEditionId } from './EditionPage';
import styles from './studio.module.css';

const active = (job: Job | undefined) => job?.state === 'queued' || job?.state === 'running';
const number = (text: string) => (/^\s*\d{1,5}\s*$/.test(text) ? Number(text) : undefined);

/** «Перекласти до глави N»: the price first, then progress, all in шаги or dollars. */
export function AutotranslatePage() {
    const id = useEditionId();
    const client = useQueryClient();
    const overview = useQuery({
        queryKey: ['autotranslate', id],
        queryFn: () => autotranslateApi.overview(id),
        // Live events refresh it at once; the slow poll only covers a lost connection.
        refetchInterval: (query) => (active(query.state.data?.jobs[0]) ? 20_000 : false),
    });
    const [kind, setKind] = useState<JobKind>('analyze');
    const [to, setTo] = useState('');
    const [advanced, setAdvanced] = useState(false);
    const [from, setFrom] = useState('');
    const [redo, setRedo] = useState(false);
    const [models, setModels] = useState<NonNullable<Plan['models']>>({});
    const [onlyRecommended, setOnlyRecommended] = useState(true);
    const [withWeak, setWithWeak] = useState(false);
    const modelShow: ModelShow = onlyRecommended ? 'recommended' : withWeak ? 'weak' : 'usual';

    const data = overview.data;
    const firstOpen = data ? (kind === 'analyze' ? data.nextToAnalyze : data.nextNumber) : 1;
    const plan: Plan | null = number(to) === undefined ? null : {
        kind, to: number(to)!,
        ...(advanced && number(from) !== undefined ? { from: number(from)! } : {}),
        ...(advanced && redo ? { redo: true } : {}),
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
    const model = (stage: 'analyze' | 'translate' | 'proofread') => models[stage] ?? data.settings[stage].model;

    return (
        <section className={styles.page}>
            <Link to="/studio/$editionId" params={{ editionId: String(id) }} className={styles.muted}>‹ До перекладу</Link>
            <h1 className={styles.title}>Автопереклад</h1>
            <p className={styles.muted}>
                В оригіналі {data.sourceChapters} {chaptersWord(data.sourceChapters)}, перекладено {data.publishedChapters}
                {data.lastAnalyzed > 0 && <>, проаналізовано до {data.lastAnalyzed}</>}.
                {data.personal
                    ? <> У вас <b>{data.balance?.shah ?? 0} {shahWord(data.balance?.shah ?? 0)}</b>{data.reserved > 0 && <>, ще {data.reserved} у резерві запусків</>}.</>
                    : data.balance && <> Баланс: <b>{money(data.balance.shah, data.balance.usd, show)}</b>.</>}
            </p>
            {!data.configured && <Notice tone="error">Ключ OpenRouter не налаштовано на сервері.</Notice>}

            {job && <JobCard job={job} showShah={show} usdPerShah={data.usdPerShah}
                onCancel={() => cancel.mutate(job.id)} onResume={() => resume.mutate(job.id)}
                pending={cancel.isPending || resume.isPending} />}
            {(cancel.isError || resume.isError) && <Notice tone="error">{(cancel.error ?? resume.error)!.message}</Notice>}

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
                            <ModelPicker label="Модель аналізу" show={modelShow} stage="analyze" value={model('analyze')} chars={data.averageChars}
                                onChange={(analyze) => setModels({ ...models, analyze })}
                                hint={kind === 'translate' ? 'Для глав, які ще не проаналізовано.' : undefined} />
                            {kind === 'translate' && (
                                <>
                                    <ModelPicker label="Модель перекладу" show={modelShow} stage="translate" value={model('translate')} chars={data.averageChars}
                                        onChange={(translate) => setModels({ ...models, translate })} />
                                    <Toggle label="Вичитка" isSelected={models.proofreadEnabled ?? data.settings.proofread.enabled}
                                        onChange={(proofreadEnabled) => setModels({ ...models, proofreadEnabled })} />
                                    {(models.proofreadEnabled ?? data.settings.proofread.enabled) && (
                                        <ModelPicker label="Модель вичитки" show={modelShow} stage="proofread" value={model('proofread')} chars={data.averageChars}
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
                <Link to="/studio/$editionId/titles" params={{ editionId: String(id) }} className={styles.menuItem}>Назви глав після аналізу</Link>
                <Link to="/studio/$editionId/glossary" params={{ editionId: String(id) }} className={styles.menuItem}>Словник імен і термінів</Link>
                <Link to="/studio/processes" className={styles.menuItem}>Усі процеси</Link>
                {data.personal
                    ? <Link to="/me/shahs" className={styles.menuItem}>Мої шаги</Link>
                    : <Link to="/me/wallet" className={styles.menuItem}>Моделі, ціни й собівартість</Link>}
            </nav>

            {data.jobs.length > 1 && (
                <>
                    <h2 className={styles.sectionTitle}>Раніше</h2>
                    {data.jobs.slice(1).map((old) => (
                        <div key={old.id} className={styles.row}>
                            <div className={styles.grow}>{old.kind === 'analyze' ? 'Аналіз' : 'Переклад'} {old.from}–{old.to} · {JOB_LABELS[old.state]}</div>
                            <span className={styles.muted}>
                                {old.personal ? `${old.chargedShah} ${shahWord(old.chargedShah)}` : money(old.spentShah, old.spentUsd, show)} · {relativeTime(new Date(old.createdAt))}
                            </span>
                        </div>
                    ))}
                </>
            )}
        </section>
    );
}

export function JobCard({ job, showShah, usdPerShah, onCancel, onResume, pending, title }: {
    job: Job; showShah: boolean; usdPerShah: number; onCancel: () => void; onResume: () => void; pending: boolean; title?: React.ReactNode;
}) {
    const total = Math.max(1, job.to - job.from + 1);
    const percent = Math.min(100, Math.round((job.done / total) * 100));
    return (
        <div className={styles.jobCard} aria-live="polite">
            {title}
            <div className={styles.jobHead}>
                <b>{job.kind === 'analyze' ? 'Аналіз' : 'Переклад'}: глави {job.from}–{job.to}</b>
                <span className={styles.badge}>{JOB_LABELS[job.state]}</span>
            </div>
            <div className={styles.progress} role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={job.done}
                aria-label="Опрацьовано глав">
                <span style={{ width: `${percent}%` }} />
            </div>
            <div className={styles.muted}>
                Готово {job.done}
                {job.current && active(job) && <> · глава {job.current.number}: {STAGE_LABELS[job.current.stage] ?? job.current.stage}</>}
                {job.personal && !active(job) && job.state !== 'failed'
                    ? <>{' · '}списано {job.chargedShah} {shahWord(job.chargedShah)}</>
                    : <>{' · '}витрачено {money(job.spentShah, job.spentUsd, showShah)} · {job.personal ? 'резерв' : 'кошторис'}{' '}
                        {money(job.quoteShah, job.quoteShah * usdPerShah, showShah)}</>}
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
