import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { getJson, mutate } from '../api/client';

export type Role = 'READER' | 'MODERATOR' | 'ADMIN' | 'OWNER';
export interface User { id: string; username: string; role: Role }
export const roleNames: Record<Role, string> = { READER: 'Користувач', MODERATOR: 'Модератор', ADMIN: 'Адміністратор', OWNER: 'Власник' };
const roleOrder: Role[] = ['READER', 'MODERATOR', 'ADMIN', 'OWNER'];
export function permits(user: User | null, minimum: Role) {
    return !!user && roleOrder.indexOf(user.role) >= roleOrder.indexOf(minimum);
}
const AuthContext = createContext<{
    user: User | null; loading: boolean; registrationOpen: boolean;
    /** The account translates, edits or reviews at least one novel, so the correction queue is available. */
    canReview: boolean; refresh: () => Promise<void>; logout: () => Promise<void>;
}>({ user: null, loading: true, registrationOpen: false, canReview: false, refresh: async () => {}, logout: async () => {} });

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<User | null>(null);
    const [loading, setLoading] = useState(true);
    const [registrationOpen, setRegistrationOpen] = useState(false);
    const [canReview, setCanReview] = useState(false);
    async function refresh() {
        try {
            const session = await getJson<{ user: User | null; registrationOpen: boolean; canReview?: boolean }>('/auth/me');
            setUser(session.user); setRegistrationOpen(session.registrationOpen); setCanReview(!!session.canReview);
        } finally { setLoading(false); }
    }
    async function logout() { await mutate('/auth/logout'); setUser(null); setCanReview(false); window.location.hash = '/'; }
    useEffect(() => {
        void refresh().catch(() => { setUser(null); });
        const focus = () => { void refresh().catch(() => {}); };
        window.addEventListener('focus', focus);
        return () => window.removeEventListener('focus', focus);
    }, []);
    return <AuthContext.Provider value={{ user, loading, registrationOpen, canReview, refresh, logout }}>{children}</AuthContext.Provider>;
}
export function useAuth() { return useContext(AuthContext); }
