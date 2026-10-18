import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { useMe } from '../auth/me';

export type ShahEntry = { kind: 'grant' | 'charge'; amount: number; what: string | null; createdAt: string };
export type ShahHold = { amount: number; what: string; createdAt: string };
export type MyShahs = {
    available: number; reserved: number; usdPerShah: number; running: ShahHold[]; history: ShahEntry[]; hasMore: boolean; total: number; page: number;
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

/**
 * Autotranslate and the AI tools of translating: the site owner's and of those the owner let in
 * (they pay with their шаги) while етап 19 is being made; nobody's from whom AI is hidden.
 */
export function useCanRun(): boolean {
    const me = useMe();
    const mayPaste = useMayPaste();
    return me?.ai === true && mayPaste;
}

/** The original pasted by hand (етап 19): not AI, so it stays for those let in even with AI hidden. */
export function useMayPaste(): boolean {
    const me = useMe();
    return me?.role === 'owner' || me?.autotranslate === true;
}

/** «Намалювати сцену»: for anyone with шаги, and the site owner; nobody's from whom AI is hidden. */
export function useCanDraw(): boolean {
    const me = useMe();
    const shahs = useMyShahs();
    return me?.ai === true && (me.role === 'owner' || (shahs.data?.available ?? 0) > 0 || (shahs.data?.reserved ?? 0) > 0);
}
