import { api } from '../api/client';

export type Comment = {
    id: number; authorNick: string | null; authorAvatarUrl: string | null; authorLevel: number | null; body: string; createdAt: string;
    editedAt: string | null; removed: 'deleted' | 'hidden' | null; score: number; myVote: number; mine: boolean;
    replyTo: number | null; replies: Comment[];
};

/** {@code total}: told by the first page, null on those read by key after it; {@code next}: the cursor of the page after. */
export type Thread = { items: Comment[]; total: number | null; page: number; hasMore: boolean; next?: string | null };

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });
const place = (chapter?: number) => (chapter ? `chapter=${chapter}&` : '');

export type MentionSuggestion = { name: string; title: string | null; avatarUrl: string | null };

export const mentionApi = {
    search: (kind: '@' | '$', q: string) =>
        api<MentionSuggestion[]>(`/api/mentions?kind=${encodeURIComponent(kind)}&q=${encodeURIComponent(q)}`),
};

export const commentApi = {
    /** {@code after}: the cursor of the page before (its `next`); none for the first page. */
    thread: (editionId: number, chapter: number | undefined, sort: 'new' | 'top', after?: string | null) =>
        api<Thread>(`/api/editions/${editionId}/comments?${place(chapter)}sort=${sort}${after ? `&after=${encodeURIComponent(after)}` : ''}`),
    post: (editionId: number, chapter: number | undefined, body: string, replyTo: number | null) =>
        api<{ id: number }>(`/api/editions/${editionId}/comments`, json('POST', { chapter: chapter ?? null, body, replyTo })),
    edit: (id: number, body: string) => api<void>(`/api/comments/${id}`, json('PATCH', { body })),
    remove: (id: number) => api<void>(`/api/comments/${id}`, { method: 'DELETE' }),
    vote: (id: number, value: -1 | 0 | 1) => api<{ score: number }>(`/api/comments/${id}/vote`, json('PUT', { value })),
    rate: (editionId: number, score: number | null) =>
        api<{ average: number | null; count: number; mine: number | null }>(`/api/editions/${editionId}/rating`, json('PUT', { score })),
};
