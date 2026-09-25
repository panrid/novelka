import { api } from '../api/client';
import type { Me } from './me';

const post = <T>(path: string, body: unknown) => api<T>(path, { method: 'POST', body: JSON.stringify(body) });

export const authApi = {
    register: (nick: string, email: string, password: string) => post<void>('/api/auth/register', { nick, email, password }),
    login: (login: string, password: string) => post<Me>('/api/auth/login', { login, password }),
    logout: () => post<void>('/api/auth/logout', {}),
    verify: (token: string) => post<Me>('/api/auth/verify', { token }),
    resendVerification: (email: string) => post<void>('/api/auth/verify/resend', { email }),
    requestReset: (email: string) => post<void>('/api/auth/password-reset', { email }),
    confirmReset: (token: string, password: string) => post<Me>('/api/auth/password-reset/confirm', { token, password }),
    confirmEmail: (token: string) => post<Me>('/api/auth/confirm-email', { token }),
};

export type SettingsPatch = Partial<Pick<Me, 'bio' | 'dmPolicy' | 'showReading' | 'showShah' | 'studioInMenu'>> & { adultConfirmed?: boolean };

export const meApi = {
    update: (patch: SettingsPatch) => api<Me>('/api/me', { method: 'PATCH', body: JSON.stringify(patch) }),
    changeNick: (nick: string) => post<Me>('/api/me/nick', { nick }),
    changeEmail: (email: string, password: string) => post<void>('/api/me/email', { email, password }),
    changePassword: (currentPassword: string, newPassword: string) =>
        post<Me>('/api/me/password', { currentPassword, newPassword }),
    uploadAvatar: (file: Blob) => {
        const form = new FormData();
        form.append('file', file, 'avatar.jpg');
        return api<Me>('/api/me/avatar', { method: 'POST', body: form });
    },
    removeAvatar: () => api<Me>('/api/me/avatar', { method: 'DELETE' }),
};

export type PublicProfile = { nick: string; avatarUrl: string | null; bio: string; memberSince: string };

export const peopleApi = {
    profile: (nick: string) => api<PublicProfile>(`/api/users/${encodeURIComponent(nick)}`),
};
