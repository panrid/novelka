import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { useState } from 'react';
import { messagingApi } from '../../inbox/api';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import styles from './inbox.module.css';

export function NewGroupPage() {
    const navigate = useNavigate();
    const [title, setTitle] = useState('');
    const [people, setPeople] = useState('');
    const create = useMutation({
        mutationFn: () => messagingApi.group(title.trim(), people.split(/[\s,]+/).map((nick) => nick.replace(/^@/, '')).filter(Boolean)),
        onSuccess: ({ id }) => void navigate({ to: '/inbox/messages/$id', params: { id: String(id) } }),
    });
    return (
        <section className={styles.page}>
            <Link to="/inbox/messages" className={styles.muted}>‹ До розмов</Link>
            <h1 className={styles.title} style={{ margin: '10px 0 16px' }}>Нова група</h1>
            <form className={styles.form} onSubmit={(event) => { event.preventDefault(); create.mutate(); }}>
                <TextInput label="Назва" value={title} onChange={setTitle} isRequired />
                <TextInput label="Кого додати" value={people} onChange={setPeople} hint="Ніки через кому або пробіл. Можна додати й пізніше." />
                {create.isError && <Notice tone="error">{create.error.message}</Notice>}
                <Button type="submit" pending={create.isPending} pendingLabel="Створюємо…" isDisabled={!title.trim()}>Створити групу</Button>
            </form>
        </section>
    );
}
