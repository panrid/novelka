import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from '@tanstack/react-router';
import { useState } from 'react';
import { studioApi } from '../../studio/api';
import {
    KIND_LABELS, autotranslateApi, shahWord, type Job, type LiveChapter, type LiveParagraph, type LiveStep,
} from '../../studio/autotranslate';
import { askConfirm } from '../../ui/ask';
import { Button } from '../../ui/Button';
import { Check } from '../../ui/icons';
import { Loading } from '../../ui/Loading';
import { Notice } from '../../ui/Notice';
import styles from './translateLive.module.css';

const LANGUAGE_NAMES: Record<string, string> = { ja: 'японська', en: 'англійська', zh: 'китайська', ko: 'корейська' };

const STEP_WORDS: Record<string, string> = { fetch: 'оригінал', analyze: 'словник', translate: 'переклад', proofread: 'вичитка', publish: 'зберігаємо' };

const running = (job: Job | undefined) => job?.state === 'queued' || job?.state === 'running';

/** The stages a chapter of this run goes through, as the chips above it name them. */
function stagesOf(job: Job, chapter: LiveChapter | null): { name: string; stages: string[] }[] {
    if (job.kind === 'analyze') return [{ name: 'Словник', stages: ['fetch', 'analyze'] }, { name: 'Готово', stages: ['done'] }];
    if (job.kind === 'proofread') {
        return [{ name: 'Вичитка', stages: ['fetch', 'analyze', 'translate', 'proofread', 'publish'] }, { name: 'Готово', stages: ['done'] }];
    }
    return [
        { name: 'Словник', stages: ['fetch', 'analyze'] },
        { name: 'Переклад', stages: ['translate'] },
        ...(chapter?.proofreading ?? true ? [{ name: 'Вичитка', stages: ['proofread'] }] : []),
        { name: 'Готово', stages: ['publish', 'done'] },
    ];
}

/** How a chapter of the run stands, in a word. */
function stepLabel(step: LiveStep): string {
    if (step.state === 'done') return 'готово';
    if (step.state === 'failed') return 'зупинено';
    if (step.state === 'cancelled') return 'скасовано';
    if (step.state === 'pending' && step.stage === 'fetch') return 'у черзі';
    return STEP_WORDS[step.stage] ?? step.stage;
}

/**
 * «Хід перекладу» наживо (етап 20, рішення 17; TranslateLive.dc.html): the original on the left, the
 * translation on the right paragraph by paragraph as the run saves it, the paragraph at work lit in
 * both; the chapter's glossary words and what proofreading changed beside them. The page follows the
 * real run: a part appears when the model has answered it — nothing is «typed» for show.
 */
export function TranslateLivePage() {
    const { editionId, jobId } = useParams({ strict: false }) as { editionId: string; jobId: string };
    const id = Number(editionId);
    const run = Number(jobId);
    const client = useQueryClient();
    // A chapter the person picked; otherwise the one the run works on now.
    const [chosen, setChosen] = useState<number | undefined>(undefined);
    const live = useQuery({
        queryKey: ['live-run', id, run, chosen ?? null],
        queryFn: () => autotranslateApi.live(id, run, chosen),
        placeholderData: (previous) => previous,
        refetchInterval: (query) => (running(query.state.data?.job) || !query.state.data ? 2000 : false),
    });
    const edition = useQuery({ queryKey: ['studio-edition', id], queryFn: () => studioApi.overview(id) });
    const act = useMutation({
        meta: { errorToast: true },
        mutationFn: (action: () => Promise<unknown>) => action(),
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: ['live-run', id, run] });
            void client.invalidateQueries({ queryKey: ['autotranslate'] });
            void client.invalidateQueries({ queryKey: ['processes'] });
        },
    });

    if (live.isPending) return <Loading onRetry={() => void live.refetch()} />;
    if (live.isError) return <Notice tone="error">{live.error.message}</Notice>;
    const { job, steps, chapter } = live.data;
    const total = Math.max(1, job.to - job.from + 1);
    const atWork = running(job) && job.current ? job.current.progress : 0;
    const overall = Math.min(100, Math.round(((job.done + atWork) / total) * 100));
    const current = job.current && chapter && job.current.number === chapter.number ? job.current : null;
    const cancel = async () => {
        if (await askConfirm({
            title: 'Скасувати переклад?',
            text: `Готові глави залишаться. ${job.personal ? 'Невитрачені шаги повернуться.' : 'Відновити запуск можна, поки він останній для цієї новели.'}`,
            confirmLabel: 'Скасувати переклад', danger: true,
        })) act.mutate(() => autotranslateApi.cancel(id, job.id));
    };
    const stages = stagesOf(job, chapter);
    const stageAt = chapter ? stages.findIndex((stage) => stage.stages.includes(chapter.stage)) : -1;

    return (
        <section className={styles.page} data-wide>
            <header className={styles.top} aria-label="Запуск">
                <div className={styles.heading}>
                    <nav aria-label="Шлях" className={styles.path}>
                        <Link to="/studio/$editionId/translate" params={{ editionId }}>{edition.data?.title ?? 'Переклад'}</Link> / Хід перекладу
                    </nav>
                    <h1 className={styles.title}>
                        {chapter
                            ? `Глава ${chapter.number}${chapter.translatedTitle || chapter.title ? ` · ${chapter.translatedTitle || chapter.title}` : ''}`
                            : 'Хід перекладу'}
                    </h1>
                </div>
                {chapter && (
                    <ol aria-label="Етапи глави" className={styles.stages}>
                        {stages.map((stage, index) => {
                            const done = index < stageAt || chapter.stage === 'done';
                            const now = index === stageAt && chapter.stage !== 'done' && chapter.state !== 'failed';
                            return (
                                <li key={stage.name} aria-current={now ? 'step' : undefined}
                                    className={now ? styles.stageNow : done ? styles.stageDone : undefined}>
                                    {done && <Check size={15} aria-hidden />}
                                    {now && <span className={styles.spin} aria-hidden />}
                                    {stage.name}
                                </li>
                            );
                        })}
                    </ol>
                )}
                <div className={styles.actions}>
                    {job.state === 'failed' && (
                        <Button onPress={() => act.mutate(() => autotranslateApi.resume(id, job.id))} pending={act.isPending}>Продовжити</Button>
                    )}
                    {(running(job) || job.state === 'failed') && (
                        <Button variant="secondary" onPress={() => void cancel()} isDisabled={act.isPending}>Скасувати</Button>
                    )}
                    <Link to="/studio/$editionId/translate/jobs/$jobId/log" params={{ editionId, jobId }} className={styles.journal}>
                        Подробиці по кроках
                    </Link>
                </div>
            </header>
            {job.state === 'failed' && job.error && <Notice tone="error">{job.error}</Notice>}

            <div className={styles.columns}>
                <aside aria-label="Глави запуску" className={styles.left}>
                    <div className={styles.panel}>
                        <div className={styles.split}>
                            <b className={styles.strong}>{job.from === job.to ? `Глава ${job.from}` : `Глави ${job.from}–${job.to}`}</b>
                            <span className={styles.muted}>{overall}%</span>
                        </div>
                        <div className={styles.bar} role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={overall}
                            aria-label="Зроблено, відсотків">
                            <span style={{ width: `${overall}%` }} />
                        </div>
                        {steps.map((step) => (
                            <button key={step.number} type="button" className={`${styles.step} ${chapter?.number === step.number ? styles.stepOn : ''}`}
                                aria-pressed={chapter?.number === step.number} onClick={() => setChosen(step.number)}>
                                <span>Глава {step.number}</span><span className={styles.small}>{stepLabel(step)}</span>
                            </button>
                        ))}
                        <span className={styles.small}>
                            {job.personal && !running(job) && job.state !== 'failed'
                                ? `Списано ${job.chargedShah} ${shahWord(job.chargedShah)}`
                                : `Витрачено ${job.spentShah} з ${job.quoteShah} ${shahWord(job.quoteShah)}`}
                        </span>
                    </div>
                    {running(job) && (
                        <span className={styles.small}>Сторінку можна закрити — переклад продовжиться, а про готові глави прийде сповіщення.</span>
                    )}
                </aside>

                <div className={styles.center}>
                    {!chapter && <p className={styles.muted}>Запуск ще не взявся за жодну главу.</p>}
                    {chapter && <ChapterText editionId={editionId} chapter={chapter} part={current ? { at: current.part, of: current.parts } : null} />}
                </div>

                {chapter && (
                    <aside aria-label="Словник і вичитка" className={styles.right}>
                        <Words editionId={editionId} chapter={chapter} />
                        {(chapter.proofreading || chapter.fixes.length > 0) && <Fixes chapter={chapter} />}
                    </aside>
                )}
            </div>
        </section>
    );
}

/** The paragraph at work: the first not translated yet, or while proofreading the first it has not reached. */
function atWork(chapter: LiveChapter): string | null {
    if (chapter.state === 'done' || chapter.state === 'failed' || chapter.state === 'cancelled') return null;
    if (chapter.stage === 'translate') return chapter.paragraphs.find((p) => p.translation === null)?.id ?? null;
    if (chapter.stage === 'proofread') return chapter.paragraphs.find((p) => !p.revised)?.id ?? null;
    return null;
}

function ChapterText({ editionId, chapter, part }: { editionId: string; chapter: LiveChapter; part: { at: number; of: number } | null }) {
    const now = atWork(chapter);
    const done = chapter.stage === 'done';
    const doing: Record<string, string> = {
        fetch: 'беремо оригінал',
        analyze: 'шукаємо імена й терміни',
        translate: part && part.of > 1 ? `переклад · частина ${Math.min(part.at + 1, part.of)} з ${part.of}` : 'перекладаємо',
        proofread: 'ШІ перечитує переклад',
        publish: 'зберігаємо главу',
    };
    const status = chapter.state === 'failed' ? 'зупинено' : done ? 'готово' : doing[chapter.stage] ?? chapter.stage;
    const language = LANGUAGE_NAMES[chapter.language];
    return (
        <div className={styles.text}>
            <div className={`${styles.row} ${styles.rowHead}`}>
                <span>№</span>
                <span>Оригінал{language ? ` · ${language}` : ''}</span>
                <span className={styles.split}><span>Переклад</span><span role="status" aria-live="polite" className={styles.status}>{status}</span></span>
            </div>
            {chapter.title && (
                <div className={`${styles.row} ${styles.titleRow}`}>
                    <span className={styles.number}>Назва</span>
                    <p lang={chapter.language}>{chapter.title}</p>
                    <p lang="uk">{chapter.translatedTitle ?? <span className={styles.waiting} />}</p>
                </div>
            )}
            {chapter.paragraphs.length === 0 && (
                <p className={styles.empty}>Оригіналу цієї глави на сайті вже немає — показати нічого.</p>
            )}
            {/* Parts arrive whole: their paragraphs show one after another, not all at once. */}
            {chapter.paragraphs.map((paragraph, index) => (
                <Row key={paragraph.id} paragraph={paragraph} index={index} now={paragraph.id === now} delay={(index % 12) * 90} animate={!done} />
            ))}
            {done && (
                <div role="status" className={styles.done}>
                    <span>
                        <b>Глава {chapter.number} готова.</b>
                        {chapter.fixes.length > 0 ? ` Вичитка змінила ${chapter.fixes.length} ${chapter.fixes.length === 1 ? 'абзац' : chapter.fixes.length < 5 ? 'абзаци' : 'абзаців'}.` : ''}
                    </span>
                    <Link to="/studio/$editionId/chapters/$number" params={{ editionId, number: String(chapter.number) }} className={styles.primary}>
                        Відкрити в робочому місці
                    </Link>
                </div>
            )}
        </div>
    );
}

function Row({ paragraph, index, now, delay, animate }: { paragraph: LiveParagraph; index: number; now: boolean; delay: number; animate: boolean }) {
    return (
        <div className={`${styles.row} ${now ? styles.rowNow : ''}`} aria-current={now ? 'true' : undefined}>
            <span className={styles.number}>{index + 1}</span>
            <p className={paragraph.translation === null && !now ? styles.quiet : undefined}>{paragraph.original}</p>
            <p lang="uk" className={styles.translation}>
                {paragraph.translation !== null
                    ? <span key={paragraph.translation} className={animate ? styles.appear : undefined} style={animate ? { animationDelay: `${delay}ms` } : undefined}>
                        {paragraph.translation}
                    </span>
                    : now ? <span className={styles.caret} aria-hidden /> : <span className={styles.waiting} />}
            </p>
        </div>
    );
}

function Words({ editionId, chapter }: { editionId: string; chapter: LiveChapter }) {
    return (
        <section className={styles.panel} aria-labelledby="live-words">
            <b id="live-words" className={styles.strong}>Словник у цій главі</b>
            {chapter.words.length === 0 && (
                <span className={styles.muted}>{chapter.stage === 'fetch' || chapter.stage === 'analyze' ? 'Шукаємо імена й терміни…' : 'Записів словника тут немає.'}</span>
            )}
            {chapter.words.map((word) => (
                <div key={word.id} className={`${styles.word} ${styles.appear}`}>
                    <span className={styles.split}>
                        <b className={styles.strong}>{word.ukrainian}</b>
                        <span className={`${styles.chip} ${word.status === 'new' ? styles.chipNew : ''}`}>{word.status === 'new' ? 'нове' : 'погоджено'}</span>
                    </span>
                    <span className={styles.small}>
                        {word.original && <><span lang={chapter.language}>{word.original}</span> · </>}{(KIND_LABELS[word.kind] ?? String(word.kind)).toLowerCase()}
                    </span>
                </div>
            ))}
            <span className={styles.small}>
                Нове слово ШІ вже вживає однаково в усіх главах запуску; перевірте його в{' '}
                <Link to="/studio/$editionId/glossary" params={{ editionId }}>«Словнику»</Link>.
            </span>
        </section>
    );
}

function Fixes({ chapter }: { chapter: LiveChapter }) {
    const numberOf = (id: string) => chapter.paragraphs.findIndex((paragraph) => paragraph.id === id) + 1;
    return (
        <section className={styles.panel} aria-labelledby="live-fixes">
            <b id="live-fixes" className={styles.strong}>Вичитка змінила</b>
            {chapter.fixes.length === 0 && (
                <span className={styles.muted}>{chapter.stage === 'proofread' ? 'Перечитуємо…' : chapter.stage === 'done' ? 'Нічого не змінила.' : 'З’явиться після вичитки.'}</span>
            )}
            {chapter.fixes.map((fix) => (
                <div key={fix.id} className={`${styles.fix} ${styles.appear}`}>
                    <span className={styles.small}>Абзац {numberOf(fix.id) || '?'}</span>
                    <span><del>{fix.before}</del> → <ins>{fix.after}</ins></span>
                </div>
            ))}
        </section>
    );
}
