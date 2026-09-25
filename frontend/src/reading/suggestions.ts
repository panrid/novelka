import { api } from '../api/client';
import type { Span, TextBlock } from './api';

export type MineItem = {
    id: number; kind: 'block' | 'replace' | 'chapter'; state: 'draft' | 'pending'; blockId: string | null;
    proposed: Span[] | null; find: string | null; replacement: string | null; note: string | null;
};

export type ReviewItem = {
    id: number; kind: 'block' | 'replace' | 'chapter'; authorNick: string; note: string | null; blockId: string | null;
    current: Span[] | null; proposed: Span[] | null; proposedTitle: string | null; proposedBlocks: TextBlock[] | null;
    find: string | null; replacement: string | null; occurrences: number; stale: boolean; createdAt: string;
};

export type MySuggestion = {
    id: number; novelSlug: string; novelTitle: string; teamHandle: string; chapter: number; chapterLabel: string; kind: string;
    preview: string; state: 'draft' | 'pending' | 'accepted' | 'rejected' | 'stale'; reviewNote: string | null; updatedAt: string;
};

const put = <T>(path: string, body: unknown) => api<T>(path, { method: 'PUT', body: JSON.stringify(body) });
const studio = (editionId: number, number: number) => `/api/studio/editions/${editionId}/chapters/${number}/suggestions`;

export const suggestionApi = {
    block: (editionId: number, number: number, blockId: string, content: Span[], note: string) =>
        put<{ id: number }>('/api/suggestions/draft/block', { editionId, number, blockId, content, note }),
    replace: (editionId: number, number: number, find: string, replacement: string, note: string) =>
        put<{ id: number }>('/api/suggestions/draft/replace', { editionId, number, find, replacement, note }),
    chapter: (editionId: number, number: number, title: string, blocks: unknown[], note: string) =>
        put<{ id: number }>('/api/suggestions/draft/chapter', { editionId, number, title, blocks, note }),
    count: (editionId: number, number: number, find: string) =>
        api<{ occurrences: number }>(`/api/suggestions/count?editionId=${editionId}&number=${number}&find=${encodeURIComponent(find)}`),
    mine: (editionId: number, number: number) =>
        api<{ items: MineItem[]; draftsInEdition: number }>(`/api/suggestions/mine?editionId=${editionId}&number=${number}`),
    submit: (editionId: number) => api<{ count: number }>('/api/suggestions/submit', { method: 'POST', body: JSON.stringify({ editionId }) }),
    withdraw: (id: number) => api<void>(`/api/suggestions/${id}`, { method: 'DELETE' }),
    history: () => api<MySuggestion[]>('/api/me/suggestions'),
    queue: (editionId: number) => api<{ number: number; label: string | null; title: string; pending: number }[]>(`/api/studio/editions/${editionId}/suggestions`),
    pending: (editionId: number, number: number) => api<ReviewItem[]>(studio(editionId, number)),
    review: (editionId: number, number: number, decisions: { id: number; accept: boolean; content?: Span[] }[]) =>
        api<{ revisionId: number | null; accepted: number; rejected: number; stale: number }>(`${studio(editionId, number)}/review`,
            { method: 'POST', body: JSON.stringify({ decisions }) }),
};

export const SUGGESTION_STATES: Record<MySuggestion['state'], string> = {
    draft: 'чернетка', pending: 'на перевірці', accepted: 'прийнято', rejected: 'відхилено', stale: 'текст уже змінився',
};
