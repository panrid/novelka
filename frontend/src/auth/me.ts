import { queryOptions, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';

export type SiteRole = 'reader' | 'moderator' | 'admin' | 'owner';

/** The signed-in person's own account (GET /api/me). */
export type Me = {
    id: number;
    nick: string;
    email: string;
    emailVerified: boolean;
    role: SiteRole;
    bio: string;
    avatarUrl: string | null;
    dmPolicy: 'everyone' | 'nobody';
    showReading: boolean;
    adultConfirmed: boolean;
    showShah: boolean;
};

export const meQuery = queryOptions({
    queryKey: ['me'],
    queryFn: async () => (await api<Me | undefined>('/api/me')) ?? null,
    staleTime: 60_000,
});

export function useMe() {
    return useQuery(meQuery).data ?? null;
}

/** After any call that returns the updated account, put it straight into the cache. */
export function useSetMe() {
    const client = useQueryClient();
    return (me: Me | null) => client.setQueryData(meQuery.queryKey, me);
}

/** `next` from the address bar, only if it points inside the site. */
export function safeNext(next: unknown): string {
    return typeof next === 'string' && next.startsWith('/') && !next.startsWith('//') ? next : '/';
}
