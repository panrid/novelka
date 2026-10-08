import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { useMe } from '../auth/me';

export type Target = 'comment' | 'chat' | 'message' | 'image' | 'edition';
export type Preview = {
    author: string | null; text: string | null; imageUrl: string | null; where: string | null;
    slug: string | null; chapter: number | null; team: string | null; hidden: boolean;
};
/** One person's report: who, when, why, and the chapter of a translation. */
export type ReportEntry = { nick: string; at: string; reason: string; chapter: number | null; chapterLabel: string | null };
export type Reported = { target: Target; targetId: number; reports: number; reasons: string[]; firstAt: string; preview: Preview; entries: ReportEntry[] };
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
    users: (q: string, page = 1) =>
        api<{ items: Person[]; total: number; page: number; hasMore: boolean }>(`/api/admin/users?q=${encodeURIComponent(q)}&page=${page}`),
    setRole: (nick: string, role: Person['role']) => api<void>(`/api/admin/users/${encodeURIComponent(nick)}/role`, json('PUT', { role })),
    settings: () => api<SiteSettingsView>('/api/admin/settings'),
    saveSettings: (settings: SiteSettingsView) => api<SiteSettingsView>('/api/admin/settings', json('PUT', settings)),
    audit: (page = 1) => api<{ items: AuditEntry[]; total: number; page: number; hasMore: boolean }>(`/api/admin/audit?page=${page}`),
};

export const TARGET_LABELS: Record<Target, string> = {
    comment: 'Коментар', chat: 'Чат', message: 'Особисте повідомлення', image: 'Картинка', edition: 'Переклад',
};

export const ROLE_LABELS: Record<Person['role'], string> = {
    reader: 'Читач', moderator: 'Модератор', admin: 'Адміністратор', owner: 'Власник сайту',
};

/** Reports waiting for a decision, for the counters next to «Адміністрування»; 0 for readers. */
export function useOpenReports(): number {
    const me = useMe();
    const staff = Boolean(me && me.role !== 'reader');
    const overview = useQuery({ queryKey: ['admin-overview'], queryFn: adminApi.overview, enabled: staff, staleTime: 60_000 });
    return staff ? overview.data?.openReports ?? 0 : 0;
}
