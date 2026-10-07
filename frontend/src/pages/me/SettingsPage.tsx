import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useSearch } from '@tanstack/react-router';
import { authApi, googleUrl, meApi } from '../../auth/api';
import { GoogleMark, useProviders } from '../../auth/GoogleButton';
import { useMe, useSetMe, type Me } from '../../auth/me';
import { Avatar } from '../../ui/Avatar';
import { AvatarPicker } from '../../ui/AvatarPicker';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import { Toggle } from '../../ui/Toggle';
import ui from '../../ui/ui.module.css';
import styles from '../pages.module.css';

export function SettingsPage() {
    const me = useMe();
    if (!me) {
        return null;
    }
    return (
        <section className={styles.narrow}>
            <h1 className={styles.title}>Налаштування</h1>
            <ProfileSection me={me} />
            <NickSection me={me} />
            <EmailSection me={me} />
            <PasswordSection me={me} />
            <GoogleSection me={me} />
            <MenuSection me={me} />
        </section>
    );
}

/** «Студія» among the main tabs, for people who translate every day. */
function MenuSection({ me }: { me: Me }) {
    const setMe = useSetMe();
    const save = useMutation({ mutationFn: (studioInMenu: boolean) => meApi.update({ studioInMenu }), onSuccess: setMe });
    return (
        <div className={styles.section}>
            <h2 className={styles.sectionTitle}>Меню</h2>
            <Toggle label="Студія в головному меню" isSelected={Boolean(me.studioInMenu)} onChange={(value) => save.mutate(value)} />
            <p className={styles.muted}>Вкладка «Студія» поруч із «Бібліотекою», а не лише в «Я».</p>
            {save.isError && <Notice tone="error">{save.error.message}</Notice>}
        </div>
    );
}

function ProfileSection({ me }: { me: Me }) {
    const setMe = useSetMe();
    const [bio, setBio] = useState(me.bio);
    const upload = useMutation({ mutationFn: meApi.uploadAvatar, onSuccess: setMe });
    const remove = useMutation({ mutationFn: meApi.removeAvatar, onSuccess: setMe });
    const save = useMutation({ mutationFn: () => meApi.update({ bio }), onSuccess: setMe });
    const error = upload.error ?? remove.error ?? save.error;

    return (
        <div className={styles.section}>
            <h2 className={styles.sectionTitle}>Профіль</h2>
            <div className={styles.form}>
                <div className={styles.row}>
                    <Avatar nick={me.nick} url={me.avatarUrl} size={72} />
                    <div className={styles.buttons}>
                        <AvatarPicker onCropped={(blob) => upload.mutate(blob)} pending={upload.isPending}
                            label={me.avatarUrl ? 'Змінити фото' : 'Додати фото'} />
                        {me.avatarUrl && (
                            <Button variant="quiet" onPress={() => remove.mutate()} pending={remove.isPending}>Прибрати</Button>
                        )}
                    </div>
                </div>
                <TextInput label="Про себе" multiline value={bio} onChange={setBio} maxLength={500} hint={`${bio.length} / 500`} />
                {error && <Notice tone="error">{error.message}</Notice>}
                {save.isSuccess && bio === me.bio && <Notice tone="success">Збережено.</Notice>}
                <Button onPress={() => save.mutate()} pending={save.isPending} pendingLabel="Зберігаємо…" isDisabled={bio === me.bio}>
                    Зберегти
                </Button>
            </div>
        </div>
    );
}

function NickSection({ me }: { me: Me }) {
    const setMe = useSetMe();
    const [nick, setNick] = useState(me.nick);
    const change = useMutation({ mutationFn: () => meApi.changeNick(nick.trim()), onSuccess: setMe });

    function submit(event: FormEvent) {
        event.preventDefault();
        change.mutate();
    }

    return (
        <form className={styles.section} onSubmit={submit}>
            <h2 className={styles.sectionTitle}>Нік</h2>
            <div className={styles.form}>
                <TextInput label="Нік" value={nick} onChange={setNick} hint="Змінювати можна раз на 30 днів. Старі посилання на профіль вестимуть на новий нік." />
                {change.isError && <Notice tone="error">{change.error.message}</Notice>}
                {change.isSuccess && <Notice tone="success">Нік змінено.</Notice>}
                <Button type="submit" pending={change.isPending} pendingLabel="Змінюємо…" isDisabled={nick.trim() === me.nick}>
                    Змінити нік
                </Button>
            </div>
        </form>
    );
}

function EmailSection({ me }: { me: Me }) {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const change = useMutation({ mutationFn: () => meApi.changeEmail(email.trim(), password) });

    function submit(event: FormEvent) {
        event.preventDefault();
        change.mutate();
    }

    return (
        <form className={styles.section} onSubmit={submit}>
            <h2 className={styles.sectionTitle}>Пошта</h2>
            <p className={styles.muted} style={{ marginBottom: 12 }}>Зараз: {me.email}</p>
            {me.hasPassword === false ? (
                <p className={styles.muted}>Щоб змінити пошту, спершу задайте пароль — нижче.</p>
            ) : change.isSuccess ? (
                <Notice tone="success">
                    Надіслали лист на {email.trim()}. Відкрийте посилання з нього — доти лишається чинною стара пошта.
                </Notice>
            ) : (
                <div className={styles.form}>
                    <TextInput label="Нова пошта" type="email" value={email} onChange={setEmail} autoComplete="email" isRequired />
                    <TextInput label="Поточний пароль" type="password" value={password} onChange={setPassword} autoComplete="current-password" isRequired />
                    {change.isError && <Notice tone="error">{change.error.message}</Notice>}
                    <Button type="submit" pending={change.isPending} pendingLabel="Надсилаємо…">Змінити пошту</Button>
                </div>
            )}
        </form>
    );
}

function PasswordSection({ me }: { me: Me }) {
    const setMe = useSetMe();
    const [current, setCurrent] = useState('');
    const [next, setNext] = useState('');
    const change = useMutation({
        mutationFn: () => meApi.changePassword(current, next),
        onSuccess: (me) => {
            setMe(me);
            setCurrent('');
            setNext('');
        },
    });

    function submit(event: FormEvent) {
        event.preventDefault();
        change.mutate();
    }

    if (me.hasPassword === false) {
        return <FirstPassword email={me.email} />;
    }
    return (
        <form className={styles.section} onSubmit={submit}>
            <h2 className={styles.sectionTitle}>Пароль</h2>
            <div className={styles.form}>
                <TextInput label="Поточний пароль" type="password" value={current} onChange={setCurrent} autoComplete="current-password" isRequired />
                <TextInput label="Новий пароль" type="password" value={next} onChange={setNext} autoComplete="new-password" isRequired hint="Щонайменше 10 символів." />
                {change.isError && <Notice tone="error">{change.error.message}</Notice>}
                {change.isSuccess && <Notice tone="success">Пароль змінено. Інші пристрої вийшли з акаунта.</Notice>}
                <Button type="submit" pending={change.isPending} pendingLabel="Змінюємо…">Змінити пароль</Button>
            </div>
        </form>
    );
}

/** An account made through Google has no password; one comes from the same letter as a reset. */
function FirstPassword({ email }: { email: string }) {
    const send = useMutation({ mutationFn: () => authApi.requestReset(email) });
    return (
        <div className={styles.section}>
            <h2 className={styles.sectionTitle}>Пароль</h2>
            <p className={styles.muted} style={{ marginBottom: 12 }}>
                Пароля ще немає: ви входите через Google. Пароль знадобиться, щоб входити без Google чи змінити пошту.
            </p>
            {send.isSuccess ? (
                <Notice tone="success">Надіслали лист на {email}. Відкрийте посилання з нього й задайте пароль.</Notice>
            ) : (
                <>
                    {send.isError && <Notice tone="error">{send.error.message}</Notice>}
                    <Button variant="secondary" onPress={() => send.mutate()} pending={send.isPending} pendingLabel="Надсилаємо…">
                        Задати пароль через лист
                    </Button>
                </>
            )}
        </div>
    );
}

/** Signing in with Google: tie it here, untie it while a password remains to get in. */
function GoogleSection({ me }: { me: Me }) {
    const providers = useProviders();
    const search: { google?: string; google_error?: string } = useSearch({ strict: false });
    const setMe = useSetMe();
    const unlink = useMutation({ mutationFn: meApi.unlinkGoogle, onSuccess: setMe });
    if (!providers.data?.google && !me.google) {
        return null;
    }
    return (
        <div className={styles.section}>
            <h2 className={styles.sectionTitle}>Вхід через Google</h2>
            {search.google_error && <Notice tone="error">{search.google_error}</Notice>}
            {search.google === 'linked' && me.google && <Notice tone="success">Google прив’язано: тепер можна входити й через нього.</Notice>}
            {me.google ? (
                <div className={styles.form}>
                    <p className={styles.muted}>Прив’язано. Можна входити кнопкою «Увійти через Google».</p>
                    {unlink.isError && <Notice tone="error">{unlink.error.message}</Notice>}
                    {me.hasPassword === false ? (
                        <p className={styles.muted}>Відв’язати можна, коли задасте пароль: інакше в акаунт не буде як увійти.</p>
                    ) : (
                        <Button variant="secondary" onPress={() => unlink.mutate()} pending={unlink.isPending} pendingLabel="Відв’язуємо…">
                            Відв’язати Google
                        </Button>
                    )}
                </div>
            ) : (
                <div className={styles.form}>
                    <p className={styles.muted}>Входьте одним натиском, без пароля. Пошта в Google може бути й іншою.</p>
                    <a href={googleUrl('/me/settings', true)} className={[ui.button, ui.secondary].join(' ')}>
                        <GoogleMark /> Прив’язати Google
                    </a>
                </div>
            )}
        </div>
    );
}
