import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from '@tanstack/react-router';
import { useState, type FormEvent } from 'react';
import { useMe } from '../../auth/me';
import { Cover } from '../../reading/Cover';
import { chaptersWord } from '../../reading/api';
import { ROLE_LABELS, teamApi, type TeamPage as Team, type TeamRole } from '../../studio/api';
import { Avatar } from '../../ui/Avatar';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import styles from '../studio/studio.module.css';
import { askConfirm } from '../../ui/ask';

export function TeamPage() {
    const { handle } = useParams({ strict: false }) as { handle: string };
    const team = useQuery({ queryKey: ['team', handle.toLowerCase()], queryFn: () => teamApi.page(handle) });

    if (team.isError) return <section className={styles.page}><Notice tone="error">{team.error.message}</Notice></section>;
    if (!team.data) return <p className={styles.muted} style={{ paddingTop: 24 }}>Завантажуємо…</p>;
    const data = team.data;
    return (
        <section className={styles.page}>
            <h1 className={styles.title}>{data.name}</h1>
            <p className={styles.muted}>${data.handle} · тегайте команду в коментарях як ${data.handle}</p>

            <h2 className={styles.sectionTitle}>Учасники</h2>
            {data.members.map((member) => (
                <div key={member.nick} className={styles.row}>
                    <Avatar nick={member.nick} url={member.avatarUrl} size={36} />
                    <Link to="/u/$nick" params={{ nick: member.nick }} className={styles.grow} style={{ color: 'var(--text)', textDecoration: 'none' }}>
                        {member.nick}
                    </Link>
                    {data.viewerRole === 'owner' && member.role !== 'owner'
                        ? <MemberControls team={data.handle} nick={member.nick} role={member.role} />
                        : <span className={styles.muted}>{ROLE_LABELS[member.role]}</span>}
                </div>
            ))}
            {data.viewerRole && data.viewerRole !== 'owner' && <LeaveTeam team={data} />}
            {data.viewerRole === 'owner' && <OwnerTools team={data} />}

            <h2 className={styles.sectionTitle}>Переклади й твори</h2>
            {data.editions.length === 0 && <p className={styles.muted}>Поки нічого не опубліковано.</p>}
            {data.editions.map((edition) => (
                <Link key={edition.novelSlug} to="/n/$slug" params={{ slug: edition.novelSlug }} search={{ t: data.handle }} className={styles.row}>
                    <Cover url={edition.coverUrl} title={edition.title} seed={edition.novelSlug} width={40} />
                    <div className={styles.grow}>
                        <div className={styles.ellipsis}>{edition.title}</div>
                        <div className={styles.muted}>{edition.chapterCount} {chaptersWord(edition.chapterCount)}</div>
                    </div>
                </Link>
            ))}
        </section>
    );
}

function useRefresh(handle: string) {
    const client = useQueryClient();
    return () => void client.invalidateQueries({ queryKey: ['team', handle.toLowerCase()] });
}

function MemberControls({ team, nick, role }: { team: string; nick: string; role: TeamRole }) {
    const refresh = useRefresh(team);
    const change = useMutation({ mutationFn: (next: TeamRole) => teamApi.setRole(team, nick, next), onSuccess: refresh });
    const remove = useMutation({ mutationFn: () => teamApi.remove(team, nick), onSuccess: refresh });
    return (
        <span style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
            <select className={styles.select} style={{ minHeight: 36, width: 'auto', fontSize: 14 }} aria-label={`Роль ${nick}`}
                value={role} onChange={(event) => change.mutate(event.target.value as TeamRole)}>
                <option value="translator">перекладач</option>
                <option value="editor">редактор</option>
            </select>
            <Button variant="quiet" aria-label={`Прибрати ${nick}`} onPress={() => void askConfirm({ title: `Прибрати ${nick} з команди?`, confirmLabel: 'Прибрати', danger: true })
                .then((yes) => { if (yes) remove.mutate(); })}>✕</Button>
        </span>
    );
}

function OwnerTools({ team }: { team: Team }) {
    const refresh = useRefresh(team.handle);
    const navigate = useNavigate();
    const [nick, setNick] = useState('');
    const [role, setRole] = useState<TeamRole>('translator');
    const [name, setName] = useState(team.personal && team.name === team.handle ? '' : team.name);
    const [handle, setHandle] = useState(team.handle);
    const add = useMutation({
        mutationFn: () => teamApi.addMember(team.handle, nick.trim().replace(/^@/, ''), role),
        onSuccess: () => { setNick(''); refresh(); },
    });
    const rename = useMutation({
        mutationFn: () => teamApi.rename(team.handle, name.trim(), handle.trim()),
        onSuccess: (renamed) => void navigate({ to: '/team/$handle', params: { handle: renamed.handle }, replace: true }),
    });

    return (
        <>
            <form className={styles.form} style={{ marginTop: 14 }} onSubmit={(event: FormEvent) => { event.preventDefault(); add.mutate(); }}>
                <TextInput label="Додати людину за ніком" value={nick} onChange={setNick} placeholder="@нік" />
                <select className={styles.select} aria-label="Роль" value={role} onChange={(event) => setRole(event.target.value as TeamRole)}>
                    <option value="translator">Перекладач — додає глави, картинки, запускає автопереклад</option>
                    <option value="editor">Редактор — править текст і погоджує правки</option>
                </select>
                {add.isError && <Notice tone="error">{add.error.message}</Notice>}
                <Button type="submit" variant="secondary" pending={add.isPending} isDisabled={!nick.trim()}>Додати</Button>
            </form>
            <form className={styles.form} style={{ marginTop: 20 }} onSubmit={(event: FormEvent) => { event.preventDefault(); rename.mutate(); }}>
                <h2 className={styles.sectionTitle} style={{ margin: 0 }}>Назва й адреса</h2>
                <TextInput label="Назва" value={name} onChange={setName} hint={team.personal ? 'Порожньо — показується ваш нік.' : undefined} />
                <TextInput label="Адреса ($тег)" value={handle} onChange={setHandle} hint="Латиниця або кирилиця, цифри, «_» і «-»." />
                {rename.isError && <Notice tone="error">{rename.error.message}</Notice>}
                <Button type="submit" variant="secondary" pending={rename.isPending}>Зберегти</Button>
            </form>
        </>
    );
}

function LeaveTeam({ team }: { team: Team }) {
    const me = useMe();
    const refresh = useRefresh(team.handle);
    const leave = useMutation({ mutationFn: () => teamApi.remove(team.handle, me!.nick), onSuccess: refresh });
    return (
        <div className={styles.actions}>
            <Button variant="danger" onPress={() => void askConfirm({ title: 'Вийти з команди?', confirmLabel: 'Вийти', danger: true })
                .then((yes) => { if (yes) leave.mutate(); })}>Вийти з команди</Button>
        </div>
    );
}
