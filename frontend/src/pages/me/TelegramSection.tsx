import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Toggle } from '../../ui/Toggle';
import ui from '../../ui/ui.module.css';
import styles from '../pages.module.css';

type TelegramStatus = {
    available: boolean; linked: boolean; username: string | null; botUsername: string | null;
    notifyInbox: boolean; notifyChapters: boolean; notifyMessages: boolean;
};
type TelegramSettings = Partial<Pick<TelegramStatus, 'notifyInbox' | 'notifyChapters' | 'notifyMessages'>>;

const telegramApi = {
    status: () => api<TelegramStatus>('/api/me/telegram'),
    link: () => api<{ url: string }>('/api/me/telegram/link', { method: 'POST', body: '{}' }),
    update: (settings: TelegramSettings) => api<TelegramStatus>('/api/me/telegram', { method: 'PATCH', body: JSON.stringify(settings) }),
    unlink: () => api<TelegramStatus>('/api/me/telegram', { method: 'DELETE' }),
};

/**
 * Notifications in Telegram as well as on the site: tie the chat with a one-time link
 * (the bot gets it when «Start» is pressed) and choose what comes there.
 */
export function TelegramSection() {
    const client = useQueryClient();
    const [link, setLink] = useState<string | null>(null);
    const status = useQuery({
        queryKey: ['telegram'],
        queryFn: telegramApi.status,
        // While the person is in Telegram pressing «Start», the page waits for the tie.
        refetchInterval: (query) => (link && !query.state.data?.linked ? 3_000 : false),
    });
    const set = (data: TelegramStatus) => client.setQueryData(['telegram'], data);
    const start = useMutation({ mutationFn: telegramApi.link, onSuccess: (data) => setLink(data.url) });
    const update = useMutation({ mutationFn: telegramApi.update, onSuccess: set });
    const unlink = useMutation({ mutationFn: telegramApi.unlink, onSuccess: (data) => { set(data); setLink(null); } });
    const data = status.data;
    if (!data?.available) {
        return null;
    }
    const error = start.error ?? update.error ?? unlink.error;
    return (
        <div className={styles.section}>
            <h2 className={styles.sectionTitle}>Telegram</h2>
            {data.linked ? (
                <div className={styles.form}>
                    <p className={styles.muted}>
                        Сповіщення приходять у Telegram{data.username ? ` (@${data.username})` : ''} від @{data.botUsername}. Що надсилати:
                    </p>
                    <Toggle label="Особисті й групові повідомлення" isSelected={data.notifyMessages}
                        onChange={(value) => update.mutate({ notifyMessages: value })} />
                    <Toggle label="Нові глави з підписок" isSelected={data.notifyChapters}
                        onChange={(value) => update.mutate({ notifyChapters: value })} />
                    <Toggle label="Відповіді, згадки, правки й решта сповіщень" isSelected={data.notifyInbox}
                        onChange={(value) => update.mutate({ notifyInbox: value })} />
                    <p className={styles.muted}>Зміну ніка, пошти й пароля бот повідомляє завжди. Посилання для нового пароля теж приходить сюди.</p>
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
