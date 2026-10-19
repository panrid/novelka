import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { notificationApi, type NotificationChoice, type NotificationTopic } from '../../inbox/api';
import { Loading } from '../../ui/Loading';
import { Notice } from '../../ui/Notice';
import { SettingsFrame } from './SettingsFrame';
import { TelegramSection, useTelegram } from './TelegramSection';
import styles from './settings.module.css';

/** The rows of the table, in the mockup's order; «Повідомлення» are always on the site. */
const TOPICS: readonly { topic: NotificationTopic; label: string }[] = [
    { topic: 'chapters', label: 'Нова глава в моїй бібліотеці' },
    { topic: 'replies', label: 'Відповіді й згадки' },
    { topic: 'messages', label: 'Повідомлення' },
    { topic: 'edits', label: 'Правки до моїх перекладів і рішення щодо моїх правок' },
    { topic: 'source', label: 'Нові глави в оригіналі' },
    { topic: 'other', label: 'Студія, шаги й сайт' },
];

/**
 * «Сповіщення» (етап 20, крок 6): what goes where — a table of topics against «На сайті» (the
 * «Вхідні») and «Telegram». Each box saves at once.
 */
export function NotificationSettingsPage() {
    const client = useQueryClient();
    const choices = useQuery({ queryKey: ['notification-choices'], queryFn: notificationApi.choices });
    const telegram = useTelegram();
    const linked = Boolean(telegram.data?.available && telegram.data.linked);
    const choose = useMutation({
        mutationFn: ({ topic, change }: { topic: NotificationTopic; change: { site?: boolean; telegram?: boolean } }) =>
            notificationApi.choose(topic, change),
        onMutate: ({ topic, change }) => {
            client.setQueryData<NotificationChoice[]>(['notification-choices'],
                (old) => old?.map((row) => (row.topic === topic ? { ...row, ...change } : row)));
        },
        onSuccess: (data) => client.setQueryData(['notification-choices'], data),
        onError: () => void client.invalidateQueries({ queryKey: ['notification-choices'] }),
    });
    const row = (topic: NotificationTopic) => choices.data?.find((choice) => choice.topic === topic);
    return (
        <SettingsFrame title="Сповіщення">
            <p className={styles.lead}>
                На сайті все збирається у <Link to="/inbox">«Вхідних»</Link>, повідомлення — у «Повідомленнях».
                {linked ? ' Telegram повторює те, що позначено.' : ' Прив’яжіть Telegram, щоб новини приходили й туди.'}
            </p>
            {choices.isError && <Notice tone="error">{choices.error.message}</Notice>}
            {choose.isError && <Notice tone="error">{choose.error.message}</Notice>}
            {choices.isPending && <Loading shape="lines" onRetry={() => void choices.refetch()} />}
            {choices.data && (
                <div className={styles.tableBox}>
                    <table className={styles.table}>
                        <thead>
                            <tr>
                                <th scope="col">Подія</th>
                                <th scope="col" className={styles.mark}>На сайті</th>
                                {linked && <th scope="col" className={styles.mark}>Telegram</th>}
                            </tr>
                        </thead>
                        <tbody>
                            {TOPICS.map(({ topic, label }) => {
                                const choice = row(topic);
                                return (
                                    <tr key={topic}>
                                        <td>{label}</td>
                                        <td className={styles.mark}>
                                            <input type="checkbox" aria-label={`${label}: на сайті`} checked={choice?.site ?? true}
                                                disabled={topic === 'messages'} title={topic === 'messages' ? 'Повідомлення завжди на сайті' : undefined}
                                                onChange={(event) => choose.mutate({ topic, change: { site: event.target.checked } })} />
                                        </td>
                                        {linked && (
                                            <td className={styles.mark}>
                                                <input type="checkbox" aria-label={`${label}: Telegram`} checked={choice?.telegram ?? true}
                                                    onChange={(event) => choose.mutate({ topic, change: { telegram: event.target.checked } })} />
                                            </td>
                                        )}
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}
            <p className={styles.note}>Зміну ніка, пошти й пароля бот у Telegram повідомляє завжди. Листів зі сповіщеннями сайт поки не надсилає.</p>
            <TelegramSection />
        </SettingsFrame>
    );
}
