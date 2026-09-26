import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { useState, type FormEvent } from 'react';
import { ROLE_LABELS, teamApi } from '../../studio/api';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import styles from '../studio/studio.module.css';

export function MyTeamsPage() {
    const navigate = useNavigate();
    const teams = useQuery({ queryKey: ['my-teams'], queryFn: teamApi.mine });
    const [name, setName] = useState('');
    const [handle, setHandle] = useState('');
    const create = useMutation({
        mutationFn: () => teamApi.create(name.trim(), handle.trim()),
        onSuccess: (team) => void navigate({ to: '/team/$handle', params: { handle: team.handle } }),
    });

    return (
        <section className={styles.page}>
            <Link to="/studio" className={styles.muted}>‹ До Студії</Link>
            <h1 className={styles.title}>Мої команди</h1>
            {teams.data?.map((team) => (
                <Link key={team.handle} to="/team/$handle" params={{ handle: team.handle }} className={styles.row}>
                    <div className={styles.grow}>
                        <div>{team.name}</div>
                        <div className={styles.muted}>${team.handle}</div>
                    </div>
                    <span className={styles.muted}>{ROLE_LABELS[team.role]}</span>
                </Link>
            ))}
            <form className={styles.form} style={{ marginTop: 20 }} onSubmit={(event: FormEvent) => { event.preventDefault(); create.mutate(); }}>
                <h2 className={styles.sectionTitle} style={{ margin: 0 }}>Нова команда</h2>
                <TextInput label="Назва" value={name} onChange={setName} placeholder="Кіцуне" />
                <TextInput label="Адреса ($тег)" value={handle} onChange={setHandle} placeholder="kitsune" hint="Так команду тегатимуть: $kitsune" />
                {create.isError && <Notice tone="error">{create.error.message}</Notice>}
                <Button type="submit" pending={create.isPending} isDisabled={!name.trim() || !handle.trim()}>Створити команду</Button>
            </form>
        </section>
    );
}
