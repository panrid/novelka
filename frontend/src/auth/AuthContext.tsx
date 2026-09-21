import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { getJson, mutate } from '../api/client';

export type Role = 'READER' | 'EDITOR' | 'ADMIN' | 'OWNER';
export interface User { id: string; username: string; role: Role }
export const roleNames: Record<Role, string> = { READER: 'Читач', EDITOR: 'Редактор', ADMIN: 'Адміністратор', OWNER: 'Власник' };
export function permits(user: User | null, minimum: Role) {
    return !!user && ['READER', 'EDITOR', 'ADMIN', 'OWNER'].indexOf(user.role) >= ['READER', 'EDITOR', 'ADMIN', 'OWNER'].indexOf(minimum);
}
const AuthContext = createContext<{
    user: User | null; loading: boolean; registrationOpen: boolean; refresh: () => Promise<void>; logout: () => Promise<void>;
}>({ user: null, loading: true, registrationOpen: false, refresh: async () => {}, logout: async () => {} });

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<User | null>(null);
    const [loading, setLoading] = useState(true);
    const [registrationOpen, setRegistrationOpen] = useState(false);
    async function refresh() {
        try {
            const session = await getJson<{ user: User | null; registrationOpen: boolean }>('/auth/me');
            setUser(session.user); setRegistrationOpen(session.registrationOpen);
        } finally { setLoading(false); }
    }
    async function logout() { await mutate('/auth/logout'); setUser(null); window.location.hash = '/'; }
    useEffect(() => {
        void refresh().catch(() => { setUser(null); });
        const focus = () => { void refresh().catch(() => {}); };
        window.addEventListener('focus', focus);
        return () => window.removeEventListener('focus', focus);
    }, []);
    return <AuthContext.Provider value={{ user, loading, registrationOpen, refresh, logout }}>{children}</AuthContext.Provider>;
}
export function useAuth() { return useContext(AuthContext); }
