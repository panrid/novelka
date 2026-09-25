import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from '@tanstack/react-router';
import { useState } from 'react';
import { useMe } from '../../auth/me';
import { messagingApi } from '../../inbox/api';
import { Avatar } from '../../ui/Avatar';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import { Toggle } from '../../ui/Toggle';
import styles from './inbox.module.css';

/** Members, the group's name and picture, «Без звуку», leaving and blocking. */
export function ConversationAboutPage() {
    const { id } = useParams({ strict: false }) as { id: string };
    const conversationId = Number(id);
    const me = useMe();
    const navigate = useNavigate();
    const client = useQueryClient();
    const about = useQuery({ queryKey: ['conversation-about', conversationId], queryFn: () => messagingApi.open(conversationId) });
    const [title, setTitle] = useState<string | null>(null);
    const [nick, setNick] = useState('');
    const [notice, setNotice] = useState<string | null>(null);
    const refresh = () => {
        void client.invalidateQueries({ queryKey: ['conversation-about', conversationId] });
        void client.invalidateQueries({ queryKey: ['conversation', conversationId] });
        void client.invalidateQueries({ queryKey: ['conversations'] });
    };
    const act = useMutation({
        mutationFn: (action: () => Promise<unknown>) => action(),
        onSuccess: refresh,
        onError: (error: Error) => setNotice(error.message),
    });

    if (about.isError) return <section className={styles.page}><Notice tone="error">{about.error.message}</Notice></section>;
    if (!about.data || !me) return <p className={styles.muted} style={{ paddingTop: 24 }}>Завантажуємо…</p>;
    const c = about.data;
    const group = c.kind === 'group';
    const other = c.kind === 'direct' ? c.members.find((m) => m.nick !== me.nick)?.nick : undefined;

    return (
        <section className={styles.page}>
            <Link to="/inbox/messages/$id" params={{ id }} className={styles.muted}>‹ До розмови</Link>
            <h1 className={styles.title} style={{ margin: '10px 0' }}>{c.kind === 'team' ? `Чат команди $${c.teamHandle}` : c.title}</h1>
            {notice && <Notice tone="error">{notice}</Notice>}
            <Toggle label="Без звуку: не рахувати в лічильнику" isSelected={c.muted}
                onChange={(muted) => act.mutate(() => messagingApi.mute(conversationId, muted))} />

            {group && c.admin && (
                <form className={styles.form} style={{ marginTop: 16 }} onSubmit={(event) => {
                    event.preventDefault();
                    if (title !== null) act.mutate(() => messagingApi.change(conversationId, { title }));
                }}>
                    <TextInput label="Назва групи" value={title ?? c.title} onChange={setTitle} />
                    <label className={styles.muted}>
                        Картинка групи{' '}
                        <input type="file" accept="image/*" onChange={(event) => {
                            const file = event.target.files?.[0];
                            if (file) act.mutate(async () => {
                                const stored = await messagingApi.uploadPicture(file, 'group_avatar');
                                await messagingApi.change(conversationId, { avatarImageId: stored.id, changeAvatar: true });
                            });
                        }} />
                    </label>
                    {title !== null && title !== c.title && <Button type="submit">Зберегти назву</Button>}
                </form>
            )}

            {c.kind === 'team' && (
                <p className={styles.muted} style={{ marginTop: 16 }}>
                    Учасники — ті, хто в команді. Вийти з чату можна лише разом із командою: <Link to="/team/$handle" params={{ handle: c.teamHandle! }}>сторінка команди</Link>.
                </p>
            )}

            {c.members.length > 0 && <h2 className={styles.title} style={{ margin: '20px 0 6px', fontSize: 16 }}>Учасники</h2>}
            {c.members.map((member) => (
                <div key={member.nick} className={styles.member}>
                    <Avatar nick={member.nick} url={member.avatarUrl} size={32} />
                    <Link to="/u/$nick" params={{ nick: member.nick }} className={styles.grow}>{member.nick}</Link>
                    <span className={styles.muted}>{member.nick === me.nick ? 'це ви' : member.role === 'admin' ? 'адмін' : ''}</span>
                    {group && c.admin && member.nick !== me.nick && (
                        <>
                            <Button variant="secondary" onPress={() => act.mutate(() => messagingApi.setRole(conversationId, member.nick, member.role === 'admin' ? 'member' : 'admin'))}>
                                {member.role === 'admin' ? 'Зняти адміна' : 'Зробити адміном'}
                            </Button>
                            <Button variant="secondary" onPress={() => act.mutate(() => messagingApi.removeMember(conversationId, member.nick))}>Видалити</Button>
                        </>
                    )}
                </div>
            ))}

            {group && c.admin && (
                <form className={styles.form} style={{ marginTop: 12 }} onSubmit={(event) => {
                    event.preventDefault();
                    act.mutate(async () => { await messagingApi.addMember(conversationId, nick.trim()); setNick(''); });
                }}>
                    <TextInput label="Додати за ніком" value={nick} onChange={setNick}
                        hint="Додати можна тих, хто дозволив їм писати і не заблокував вас." />
                    <Button type="submit" variant="secondary" isDisabled={!nick.trim()}>Додати</Button>
                </form>
            )}

            <div className={styles.actions} style={{ marginTop: 24, flexWrap: 'wrap' }}>
                {group && (
                    <Button variant="danger" onPress={() => act.mutate(async () => {
                        await messagingApi.removeMember(conversationId, me.nick);
                        void navigate({ to: '/inbox/messages' });
                    })}>Вийти з групи</Button>
                )}
                {other && (
                    <Button variant="danger" onPress={() => act.mutate(async () => {
                        await messagingApi.block(other);
                        setNotice(`${other} заблоковано. Розблокувати можна в налаштуваннях приватності.`);
                    })}>Заблокувати {other}</Button>
                )}
            </div>
        </section>
    );
}
