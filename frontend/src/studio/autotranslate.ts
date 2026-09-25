import { api } from '../api/client';

export type JobKind = 'analyze' | 'translate';
export type Quote = {
    kind: JobKind; from: number; to: number; chapters: number; shah: number; usd: number; estimated: boolean;
    /** Chapters of a translation without analysis: their glossary cannot be checked first. */
    unanalyzed: number;
};
export type Balance = { shah: number; usd: number };
export type JobState = 'queued' | 'running' | 'done' | 'failed' | 'cancelled';
export type Job = {
    id: number; kind: JobKind; state: JobState; from: number; to: number; done: number; quoteShah: number; spentUsd: number; spentShah: number;
    current: { number: number; stage: string; state: string; error: string | null } | null;
    error: string | null; createdAt: string; finishedAt: string | null;
};
export type AutotranslateOverview = {
    configured: boolean; showShah: boolean; sourceChapters: number; nextNumber: number; publishedChapters: number;
    lastAnalyzed: number; nextToAnalyze: number;
    balance: Balance | null; usdPerShah: number; quote: Quote | null; jobs: Job[];
};
/** A chapter analysed but not yet translated: its Ukrainian title and the number readers will see. */
export type ChapterAnalysis = { number: number; title: string; label: string | null; edited: boolean };
export type GlossaryKind = 'character' | 'place' | 'organization' | 'term' | 'other';
export type Gender = 'male' | 'female' | 'unknown';
export type GlossaryItem = {
    id: number; ukrainian: string; kind: GlossaryKind; gender: Gender | null; note: string | null; chapter: number | null; manual: boolean;
};
export type Stage = { model: string; inputPerMillion: number; outputPerMillion: number; enabled: boolean };
export type Settings = {
    analyze: Stage; translate: Stage; proofread: Stage; segmentChars: number; microUsdPerShah: number; capFactor: number;
};
export type CostRow = {
    model: string; chapters: number; minChapter: number; avgChapter: number; maxChapter: number;
    minPerShah: number; avgPerShah: number; maxPerShah: number; total: number;
};
export type Wallet = {
    configured: boolean; showShah: boolean; balance: Balance | null; usdPerShah: number; settings: Settings; report: CostRow[];
};

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });
const base = (id: number) => `/api/studio/editions/${id}`;

export const autotranslateApi = {
    prepare: (url: string, team: string) =>
        api<{ editionId: number; novelSlug: string }>('/api/studio/autotranslate/prepare', json('POST', { url, team })),
    overview: (id: number, to: number | undefined, kind: JobKind) =>
        api<AutotranslateOverview>(`${base(id)}/autotranslate?kind=${kind}${to ? `&to=${to}` : ''}`),
    start: (id: number, to: number, kind: JobKind) => api<Job>(`${base(id)}/autotranslate/jobs`, json('POST', { to, kind })),
    analysis: (id: number) => api<ChapterAnalysis[]>(`${base(id)}/analysis`),
    editAnalysis: (id: number, number: number, body: { title: string; label: string | null }) =>
        api<void>(`${base(id)}/analysis/${number}`, json('PUT', body)),
    allChecked: (id: number) => api<void>(`${base(id)}/glossary/checked`, json('POST', {})),
    cancel: (id: number, jobId: number) => api<void>(`${base(id)}/autotranslate/jobs/${jobId}/cancel`, json('POST', {})),
    resume: (id: number, jobId: number) => api<void>(`${base(id)}/autotranslate/jobs/${jobId}/resume`, json('POST', {})),
    glossary: (id: number) => api<GlossaryItem[]>(`${base(id)}/glossary`),
    updateEntry: (id: number, entryId: number, body: { ukrainian: string; kind: GlossaryKind; gender: Gender | null; note: string }) =>
        api<void>(`${base(id)}/glossary/${entryId}`, json('PUT', body)),
    deleteEntry: (id: number, entryId: number) => api<void>(`${base(id)}/glossary/${entryId}`, { method: 'DELETE' }),
    wallet: (days = 30) => api<Wallet>(`/api/studio/autotranslate/wallet?days=${days}`),
    saveSettings: (settings: Settings) => api<void>('/api/studio/autotranslate/settings', json('PUT', settings)),
};

export function shahWord(count: number): string {
    const tens = count % 100;
    const ones = count % 10;
    if (tens >= 11 && tens <= 14) return 'шагів';
    if (ones === 1) return 'шаг';
    if (ones >= 2 && ones <= 4) return 'шаги';
    return 'шагів';
}

const dollars = (usd: number, digits = 2) => `$${usd.toFixed(digits).replace('.', ',')}`;

/** A sum in шаги, or in dollars when the owner switched шаги off for themselves (рішення 23). */
export function money(shah: number, usd: number, showShah: boolean): string {
    return showShah ? `${shah} ${shahWord(shah)}` : dollars(usd);
}

export { dollars };

export const STAGE_LABELS: Record<string, string> = {
    fetch: 'завантажуємо оригінал',
    analyze: 'аналіз і словник',
    translate: 'переклад',
    proofread: 'вичитка',
    publish: 'публікуємо',
    done: 'готово',
};

export const JOB_LABELS: Record<JobState, string> = {
    queued: 'у черзі',
    running: 'перекладаємо',
    done: 'готово',
    failed: 'зупинено',
    cancelled: 'скасовано',
};

export const KIND_LABELS: Record<GlossaryKind, string> = {
    character: 'Персонаж',
    place: 'Місце',
    organization: 'Організація',
    term: 'Термін',
    other: 'Інше',
};
