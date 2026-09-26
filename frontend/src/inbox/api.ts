import { api } from '../api/client';

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

// ---- notifications ------------------------------------------------------------------------------

export type NotificationKind = 'reply' | 'mention' | 'team_mention' | 'new_chapters' | 'suggestions_reviewed' | 'suggestions_submitted' | 'shahs_granted';

export type NotificationPayload = {
    editionId?: number; slug?: string; novelTitle?: string; teamHandle?: string;
    chapterNumber?: number; chapterLabel?: string; actorNick?: string; excerpt?: string; where?: 'comment' | 'chat';
    commentId?: number; first?: number; last?: number; accepted?: number; rejected?: number; count?: number; shah?: number; note?: string;
    /** New chapters: the numbers readers see, a lone chapter's name and the cover. */
    firstLabel?: string; lastLabel?: string; chapterTitle?: string; coverUrl?: string;
};

export type Notification = { id: number; kind: NotificationKind; payload: NotificationPayload; createdAt: string; read: boolean };

export const notificationApi = {
    page: (before?: number) => api<{ items: Notification[]; unread: number; hasMore: boolean }>(
        `/api/notifications${before ? `?before=${before}` : ''}`),
    unread: () => api<{ unread: number }>('/api/notifications/unread'),
    read: (upTo?: number) => api<{ unread: number }>('/api/notifications/read', json('POST', { upTo: upTo ?? null })),
};

// ---- conversations ------------------------------------------------------------------------------

export type ConversationKind = 'direct' | 'group' | 'team';

export type ConversationSummary = {
    id: number; kind: ConversationKind; title: string; avatarUrl: string | null; teamHandle: string | null; otherNick: string | null;
    lastText: string | null; lastAuthorNick: string | null; lastAt: string; unread: number; muted: boolean;
};

export type MessageLine = {
    id: number; kind: 'text' | 'system'; authorNick: string | null; authorAvatarUrl: string | null; body: string;
    pictures: { id: number; url: string; thumbUrl: string }[]; replyTo: number | null; replyExcerpt: string | null;
    createdAt: string; editedAt: string | null; deleted: boolean; mine: boolean;
};

export type Conversation = {
    id: number; kind: ConversationKind; title: string; avatarUrl: string | null; teamHandle: string | null; admin: boolean;
    muted: boolean; members: { nick: string; avatarUrl: string | null; role: 'admin' | 'member' }[];
    lines: MessageLine[]; hasOlder: boolean; canWrite: boolean; cannotWrite: string | null;
};

export const messagingApi = {
    list: () => api<{ items: ConversationSummary[]; unread: number }>('/api/conversations'),
    open: (id: number, before?: number) => api<Conversation>(`/api/conversations/${id}${before ? `?before=${before}` : ''}`),
    direct: (nick: string) => api<{ id: number }>('/api/conversations/direct', json('POST', { nick })),
    group: (title: string, nicks: string[]) => api<{ id: number }>('/api/conversations/groups', json('POST', { title, nicks })),
    send: (id: number, body: string, replyTo: number | null, imageIds: number[]) =>
        api<{ id: number }>(`/api/conversations/${id}/messages`, json('POST', { body, replyTo, imageIds })),
    edit: (messageId: number, body: string) => api<void>(`/api/messages/${messageId}`, json('PATCH', { body })),
    remove: (messageId: number) => api<void>(`/api/messages/${messageId}`, { method: 'DELETE' }),
    read: (id: number, upTo: number) => api<void>(`/api/conversations/${id}/read`, json('POST', { upTo })),
    mute: (id: number, muted: boolean) => api<void>(`/api/conversations/${id}/mute`, json('PUT', { muted })),
    change: (id: number, change: { title?: string; avatarImageId?: number | null; changeAvatar?: boolean }) =>
        api<void>(`/api/conversations/${id}`, json('PATCH', change)),
    addMember: (id: number, nick: string) => api<void>(`/api/conversations/${id}/members`, json('POST', { nick })),
    removeMember: (id: number, nick: string) => api<void>(`/api/conversations/${id}/members/${encodeURIComponent(nick)}`, { method: 'DELETE' }),
    setRole: (id: number, nick: string, role: 'admin' | 'member') =>
        api<void>(`/api/conversations/${id}/members/${encodeURIComponent(nick)}`, json('PATCH', { role })),
    uploadPicture: (file: Blob, kind: 'message' | 'group_avatar' = 'message') => {
        const form = new FormData();
        form.append('file', file, 'picture.jpg');
        form.append('kind', kind);
        return api<{ id: number; url: string }>('/api/media/images', { method: 'POST', body: form });
    },
    blocked: () => api<string[]>('/api/me/blocks'),
    block: (nick: string) => api<void>(`/api/me/blocks/${encodeURIComponent(nick)}`, { method: 'PUT' }),
    unblock: (nick: string) => api<void>(`/api/me/blocks/${encodeURIComponent(nick)}`, { method: 'DELETE' }),
    report: (target: 'comment' | 'chat' | 'message', targetId: number, reason: string) =>
        api<void>('/api/reports', json('POST', { target, targetId, reason })),
};

// ---- site chat ----------------------------------------------------------------------------------

export type ChatLine = {
    id: number; authorNick: string; authorAvatarUrl: string | null; body: string; createdAt: string;
    replyTo: number | null; replyExcerpt: string | null; mine: boolean;
};

export const chatApi = {
    lines: (before?: number) => api<ChatLine[]>(`/api/chat${before ? `?before=${before}` : ''}`),
    say: (body: string, replyTo: number | null) => api<{ id: number }>('/api/chat', json('POST', { body, replyTo })),
    remove: (id: number) => api<void>(`/api/chat/${id}`, { method: 'DELETE' }),
};
