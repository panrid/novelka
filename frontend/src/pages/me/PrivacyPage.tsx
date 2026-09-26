import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { meApi, type SettingsPatch } from '../../auth/api';
import { useMe, useSetMe } from '../../auth/me';
import { messagingApi } from '../../inbox/api';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
import { Toggle } from '../../ui/Toggle';
import styles from '../pages.module.css';

/** Each switch saves at once; there is no «Зберегти» to forget. */
export function PrivacyPage() {
    const me = useMe();
    const setMe = useSetMe();
    const save = useMutation({ mutationFn: (patch: SettingsPatch) => meApi.update(patch), onSuccess: setMe });

    if (!me) {
        return null;
    }
    return (
        <section className={styles.narrow}>
            <h1 className={styles.title}>Приватність</h1>
            <div className={styles.form} style={{ marginTop: 16 }}>
                <Segmented label="Хто може мені писати й додавати в групи" value={me.dmPolicy}
                    options={[{ value: 'everyone', label: 'Усі' }, { value: 'nobody', label: 'Ніхто' }]}
                    onChange={(dmPolicy) => save.mutate({ dmPolicy })} />
                <div>
                    <Toggle label="Показувати «Читає зараз» у профілі" isSelected={me.showReading}
                        onChange={(showReading) => save.mutate({ showReading })} />
                    <Toggle label="Мені є 18 років" isSelected={me.adultConfirmed}
                        onChange={(adultConfirmed) => save.mutate({ adultConfirmed })} />
                    <p className={styles.muted}>Відкриває новели з позначкою «18+». Без цього вони приховані.</p>
                </div>
                {me.role === 'owner' && (
                    <div>
                        <Toggle label="Показувати мені суми в шагах" isSelected={me.showShah}
                            onChange={(showShah) => save.mutate({ showShah })} />
                        <p className={styles.muted}>Вимкніть, щоб бачити баланс і витрати на переклад у доларах. Діє лише для вас.</p>
                    </div>
                )}
                {save.isError && <Notice tone="error">{save.error.message}</Notice>}
            </div>
            <BlockedPeople />
        </section>
    );
}

/** People I blocked: they cannot write to me or add me to groups. */
function BlockedPeople() {
    const client = useQueryClient();
    const blocked = useQuery({ queryKey: ['blocked'], queryFn: messagingApi.blocked });
    const unblock = useMutation({ meta: { errorToast: true },
        mutationFn: (nick: string) => messagingApi.unblock(nick),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['blocked'] }),
    });
    if (!blocked.data?.length) return null;
    return (
        <div className={styles.section} style={{ marginTop: 24 }}>
            <h2 className={styles.sectionTitle}>Заблоковані</h2>
            {blocked.data.map((nick) => (
                <div key={nick} className={styles.row} style={{ justifyContent: 'space-between', padding: '6px 0' }}>
                    <span>{nick}</span>
                    <Button variant="secondary" onPress={() => unblock.mutate(nick)}>Розблокувати</Button>
                </div>
            ))}
        </div>
    );
}
