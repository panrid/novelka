import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import ui from '../../ui/ui.module.css';
import styles from '../pages.module.css';

type TelegramStatus = { available: boolean; linked: boolean; username: string | null; botUsername: string | null };

const telegramApi = {
    status: () => api<TelegramStatus>('/api/me/telegram'),
    link: () => api<{ url: string }>('/api/me/telegram/link', { method: 'POST', body: '{}' }),
    unlink: () => api<TelegramStatus>('/api/me/telegram', { method: 'DELETE' }),
};

/** Whether the site has a bot and the person's chat is tied; while a tie is awaited, it looks again. */
export function useTelegram(waiting = false) {
    return useQuery({
        queryKey: ['telegram'],
        queryFn: telegramApi.status,
        // While the person is in Telegram pressing «Start», the page waits for the tie.
        refetchInterval: (query) => (waiting && !query.state.data?.linked ? 3_000 : false),
    });
}

/**
 * Notifications in Telegram as well as on the site: tie the chat with a one-time link (the bot
 * gets it when «Start» is pressed). What comes there is chosen in the table above.
 */
export function TelegramSection() {
    const client = useQueryClient();
    const [link, setLink] = useState<string | null>(null);
    const status = useTelegram(Boolean(link));
    const set = (data: TelegramStatus) => client.setQueryData(['telegram'], data);
    const start = useMutation({ mutationFn: telegramApi.link, onSuccess: (data) => setLink(data.url) });
    const unlink = useMutation({ mutationFn: telegramApi.unlink, onSuccess: (data) => { set(data); setLink(null); } });
    const data = status.data;
    if (!data?.available) {
        return null;
    }
    const error = start.error ?? unlink.error;
    return (
        <div className={styles.section}>
            <h2 className={styles.sectionTitle}>Telegram</h2>
            {data.linked ? (
                <div className={styles.form}>
                    <p className={styles.muted}>
                        Telegram підключено{data.username ? `: @${data.username}` : ''}. Сповіщення приходять від @{data.botUsername}; що саме — позначено в таблиці.
                    </p>
                    {error && <Notice tone="error">{error.message}</Notice>}
                    <Button variant="secondary" onPress={() => unlink.mutate()} pending={unlink.isPending} pendingLabel="Відв’язуємо…">
                        Відв’язати Telegram
                    </Button>
                </div>
            ) : (
                <div className={styles.form}>
                    <p className={styles.muted}>
                        Повідомлення, нові глави й інші сповіщення можуть приходити й у Telegram — від бота @{data.botUsername}.
                    </p>
                    {error && <Notice tone="error">{error.message}</Notice>}
                    {link ? (
                        <>
                            <a href={link} target="_blank" rel="noopener noreferrer" className={[ui.button, ui.primary].join(' ')}>
                                Відкрити Telegram
                            </a>
                            <p className={styles.muted}>У чаті з ботом натисніть «Start» (або «Почати»). Ця сторінка оновиться сама.</p>
                        </>
                    ) : (
                        <Button onPress={() => start.mutate()} pending={start.isPending} pendingLabel="Готуємо посилання…">
                            Прив’язати Telegram
                        </Button>
                    )}
                </div>
            )}
        </div>
    );
}
