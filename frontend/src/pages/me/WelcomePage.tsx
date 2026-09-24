import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { useState } from 'react';
import { meApi } from '../../auth/api';
import { useMe, useSetMe } from '../../auth/me';
import { Avatar } from '../../ui/Avatar';
import { AvatarPicker } from '../../ui/AvatarPicker';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import styles from '../pages.module.css';

/** Right after confirming the email: an optional face and a few words. Skippable. */
export function WelcomePage() {
    const me = useMe();
    const setMe = useSetMe();
    const navigate = useNavigate();
    const [bio, setBio] = useState(me?.bio ?? '');
    const avatar = useMutation({ mutationFn: meApi.uploadAvatar, onSuccess: setMe });
    const save = useMutation({
        mutationFn: () => meApi.update({ bio }),
        onSuccess: (updated) => {
            setMe(updated);
            void navigate({ to: '/' });
        },
    });

    if (!me) {
        return null;
    }
    return (
        <section className={styles.narrow}>
            <h1 className={styles.title}>Вітаємо, {me.nick}!</h1>
            <p className={styles.lead}>Додайте фото й кілька слів про себе — так вас легше впізнати в коментарях. Це можна зробити й пізніше.</p>
            <div className={styles.form}>
                <div className={styles.row}>
                    <Avatar nick={me.nick} url={me.avatarUrl} size={72} />
                    <AvatarPicker onCropped={(blob) => avatar.mutate(blob)} pending={avatar.isPending}
                        label={me.avatarUrl ? 'Змінити фото' : 'Додати фото'} />
                </div>
                {avatar.isError && <Notice tone="error">{avatar.error.message}</Notice>}
                <TextInput label="Про себе" multiline value={bio} onChange={setBio} maxLength={500}
                    placeholder="Що любите читати, що перекладаєте…" hint={`${bio.length} / 500`} />
                {save.isError && <Notice tone="error">{save.error.message}</Notice>}
                <Button wide onPress={() => save.mutate()} pending={save.isPending} pendingLabel="Зберігаємо…">
                    Зберегти
                </Button>
                <Link to="/" className={styles.muted} style={{ textAlign: 'center' }}>Пропустити</Link>
            </div>
        </section>
    );
}
