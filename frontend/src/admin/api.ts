import { api } from '../api/client';

export type Target = 'comment' | 'chat' | 'message' | 'image' | 'edition';
export type Preview = {
    author: string | null; text: string | null; imageUrl: string | null; where: string | null;
    slug: string | null; chapter: number | null; team: string | null; hidden: boolean;
};
export type Reported = { target: Target; targetId: number; reports: number; reasons: string[]; firstAt: string; preview: Preview };
export type Hidden = { target: Target; targetId: number; hiddenAt: string; hiddenBy: string | null; reason: string | null; preview: Preview };
export type Person = { nick: string; role: 'reader' | 'moderator' | 'admin' | 'owner'; email: string | null; createdAt: string; lastSeenAt: string | null;
    /** Free and held шаги; only the site owner sees them. */
    shahs?: number | null };
export type SiteSettingsView = { relayInactiveMonths: number; registrationOpen: boolean; adultEnabled: boolean };
export type AuditEntry = {
    id: number; actor: string; action: string; targetType: string; targetId: number | null; details: Record<string, unknown>; createdAt: string;
};

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

export const adminApi = {
    overview: () => api<{ openReports: number; activeJobs: number; failedJobs: number; role: string }>('/api/admin/overview'),
    reports: () => api<Reported[]>('/api/admin/reports'),
    decide: (target: Target, id: number, action: 'hide' | 'dismiss', reason?: string) =>
        api<void>(`/api/admin/reports/${target}/${id}`, json('POST', { action, reason: reason ?? null })),
    hidden: () => api<Hidden[]>('/api/admin/hidden'),
    hide: (target: Target, id: number, reason: string) => api<void>(`/api/admin/hidden/${target}/${id}`, json('POST', { reason })),
    restore: (target: Target, id: number) => api<void>(`/api/admin/hidden/${target}/${id}/restore`, json('POST', {})),
    users: (q: string) => api<Person[]>(`/api/admin/users?q=${encodeURIComponent(q)}`),
    setRole: (nick: string, role: Person['role']) => api<void>(`/api/admin/users/${encodeURIComponent(nick)}/role`, json('PUT', { role })),
    settings: () => api<SiteSettingsView>('/api/admin/settings'),
    saveSettings: (settings: SiteSettingsView) => api<SiteSettingsView>('/api/admin/settings', json('PUT', settings)),
    audit: (before?: number) => api<AuditEntry[]>(`/api/admin/audit${before ? `?before=${before}` : ''}`),
};

export const TARGET_LABELS: Record<Target, string> = {
    comment: 'Коментар', chat: 'Чат', message: 'Особисте повідомлення', image: 'Картинка', edition: 'Переклад',
};

export const ROLE_LABELS: Record<Person['role'], string> = {
    reader: 'Читач', moderator: 'Модератор', admin: 'Адміністратор', owner: 'Власник сайту',
};
