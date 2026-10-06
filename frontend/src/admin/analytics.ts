import { api } from '../api/client';

/** The owner's analytics (see the backend's Report): money in dollars, time in seconds, shares 0–1. */
export type Analytics = {
    period: { days: number; from: string; bucket: 'day' | 'week' };
    spend: {
        usd: number; previousUsd: number; calls: number; failedCalls: number; uncertainCalls: number; tokensIn: number; tokensOut: number;
        usdPerCall: number; estimateRatio: number | null; chapters: number; usdPerChapter: number;
    };
    spendSeries: { start: string; usdByStage: Record<string, number>; calls: number }[];
    stages: { stage: string; usd: number; calls: number; share: number; tokensIn: number; tokensOut: number }[];
    models: {
        model: string; stage: string; calls: number; failed: number; tokensIn: number; tokensOut: number; usd: number;
        secondsAverage: number | null; secondsP90: number | null; usdPerMillionTokens: number | null; outputPerInput: number | null;
    }[];
    combos: {
        analyze: string; translate: string; proofread: string; chapters: number; usd: number; usdPerChapter: number;
        usdPerThousandChars: number | null; secondsPerChapter: number | null; usdByStage: Record<string, number>;
    }[];
    novels: { title: string; slug: string; chapters: number; usd: number; usdPerChapter: number; usdByStage: Record<string, number> }[];
    funding: { siteUsd: number; peopleUsd: number; outsideRunsUsd: number; chargedShah: number; chargedUsd: number };
    translation: {
        translate: string; proofread: string; chapters: number; editedChapters: number; editedShare: number; editorRevisions: number;
        suggestions: number; acceptedSuggestions: number; rejectedSuggestions: number; suggestionsPerChapter: number;
        acceptedPerChapter: number; paragraphsChanged: number; wordsChanged: number; retriesPerChapter: number;
        splitsPerChapter: number; missingPerChapter: number; failedSteps: number; usdPerChapter: number | null;
        secondsPerChapter: number | null;
    }[];
    proofread: { model: string; parts: number; changedLines: number; changedPerPart: number; skipped: number; skippedShare: number; retriesPerPart: number }[];
    analysis: {
        model: string; chapters: number; entries: number; approved: number; changed: number; rejected: number; waiting: number;
        entriesPerChapter: number; rejectedShare: number; titlesEdited: number; titlesEditedShare: number; usdPerChapter: number | null;
        retriesPerChapter: number;
    }[];
    reasons: { stage: string; model: string; kind: string; reason: string; count: number }[];
    site: {
        newAccounts: number; activeAccounts: number; readers: number; chaptersPublished: number; machineChapters: number;
        comments: number; suggestions: number; acceptedSuggestions: number; rejectedSuggestions: number; libraryAdds: number;
        series: { start: string; accounts: number; chapters: number; comments: number; suggestions: number }[];
        top: { title: string; slug: string; team: string; readers: number; library: number; chapters: number }[];
    };
};

export const analyticsApi = {
    report: (days: number) => api<Analytics>(`/api/admin/analytics?days=${days}`),
};
