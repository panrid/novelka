import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { useMe } from '../auth/me';
import { messagingApi, notificationApi } from './api';

/**
 * One event stream per tab while signed in. Events only say what changed; the pages
 * refetch it. The browser reconnects by itself after a network hiccup.
 */
export function useLiveEvents() {
    const me = useMe();
    const client = useQueryClient();
    useEffect(() => {
        if (!me || typeof EventSource === 'undefined') return undefined;
        const source = new EventSource('/api/events');
        const refresh = (...keys: string[]) => keys.forEach((key) => void client.invalidateQueries({ queryKey: [key] }));
        // A notification may be about suggestions: the Studio counts them too.
        source.addEventListener('notifications', () => refresh('notifications', 'inbox-counts', 'studio', 'suggestion-queue'));
        source.addEventListener('message', (event) => {
            const id = (JSON.parse((event as MessageEvent<string>).data) as { conversationId?: number }).conversationId;
            refresh('conversations', 'inbox-counts');
            if (id) void client.invalidateQueries({ queryKey: ['conversation', id] });
        });
        source.addEventListener('messages', () => refresh('conversations', 'inbox-counts'));
        source.addEventListener('chat', () => refresh('chat'));
        // Autotranslation moved a step: its page refreshes without polling.
        source.addEventListener('job', () => refresh('autotranslate', 'studio-chapters'));
        return () => source.close();
    }, [me, client]);
}

/** Unread notifications and messages, for the badge on «Вхідні». */
export function useInboxCounts() {
    const me = useMe();
    return useQuery({
        queryKey: ['inbox-counts'],
        queryFn: async () => {
            const [notifications, messages] = await Promise.all([notificationApi.unread(), messagingApi.list()]);
            return { notifications: notifications.unread, messages: messages.unread };
        },
        enabled: Boolean(me),
        staleTime: 30_000,
    });
}
