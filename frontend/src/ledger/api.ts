import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { useMe } from '../auth/me';

export type ShahEntry = { kind: 'grant' | 'charge'; amount: number; what: string | null; createdAt: string };
export type ShahHold = { amount: number; what: string; createdAt: string };
export type MyShahs = {
    available: number; reserved: number; usdPerShah: number; running: ShahHold[]; history: ShahEntry[]; hasMore: boolean;
};

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

/** Шаги без оплати (рішення 29): the site owner grants them, people spend them on runs. */
export const shahApi = {
    mine: (page = 1) => api<MyShahs>(`/api/me/shahs?page=${page}`),
    grant: (nick: string, shah: number, note: string) =>
        api<{ available: number }>(`/api/admin/users/${encodeURIComponent(nick)}/shahs`, json('POST', { shah, note })),
    price: () => api<{ usdPerShah: number }>('/api/admin/shahs/price'),
    savePrice: (usdPerShah: number) => api<void>('/api/admin/shahs/price', json('PUT', { usdPerShah })),
};

/** The signed-in person's шаги; nothing for guests. */
export function useMyShahs() {
    const me = useMe();
    return useQuery({ queryKey: ['shahs', 1], queryFn: () => shahApi.mine(1), enabled: me !== null });
}

/** Whether the person may start runs paid from their шаги (the site owner always may). */
export function useCanRun(): boolean {
    const me = useMe();
    const shahs = useMyShahs();
    return me?.role === 'owner' || (shahs.data?.available ?? 0) > 0;
}
