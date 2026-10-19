import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useSearch } from '@tanstack/react-router';
import { authApi, googleUrl, meApi, type SettingsPatch } from '../../auth/api';
import { GoogleMark, useProviders } from '../../auth/GoogleButton';
import { useMe, useSetMe, type Me } from '../../auth/me';
import { Avatar } from '../../ui/Avatar';
import { AvatarPicker } from '../../ui/AvatarPicker';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import { Loading } from '../../ui/Loading';
import { Pager } from '../../ui/Pager';
import { Segmented } from '../../ui/Segmented';
import { Toggle } from '../../ui/Toggle';
import { chatApi, messagingApi } from '../../inbox/api';
import { useMyShahs } from '../../ledger/api';
import { PAGE_SIZE, usePaged } from '../../lib/usePage';
import { SettingsFrame } from './SettingsFrame';
import frame from './settings.module.css';
import ui from '../../ui/ui.module.css';
import styles from '../pages.module.css';

/** «Профіль»: the face, the nick and a few words, and what others may see and do. */
export function ProfileSettingsPage() {
    const me = useMe();
    if (!me) {
        return null;
    }
    return (
        <SettingsFrame title="Профіль">
            <ProfileSection me={me} />
            <NickSection me={me} />
            <PrivacySection me={me} />
            <BlockedPeople />
        </SettingsFrame>
    );
}

/** «Вхід і безпека»: the email, the password (with the eye, at least 8 characters) and Google. */
export function SecuritySettingsPage() {
    const me = useMe();
    if (!me) {
        return null;
    }
    return (
        <SettingsFrame title="Вхід і безпека">
            <EmailSection me={me} />
            <PasswordSection me={me} />
            <GoogleSection me={me} />
        </SettingsFrame>
    );
}

/** «Шаги»: what is on the account and what running translations hold; the history is a click away. */
export function ShahsSettingsPage() {
    const me = useMe();
    const shahs = useMyShahs();
    const setMe = useSetMe();
    const save = useMutation({ mutationFn: (showShah: boolean) => meApi.update({ showShah }), onSuccess: setMe });
    if (!me) {
        return null;
    }
    return (
        <SettingsFrame title="Шаги">
            {shahs.isError && <Notice tone="error">{shahs.error.message}</Notice>}
            {shahs.isPending && <Loading shape="lines" onRetry={() => void shahs.refetch()} />}
            {shahs.data && (
                <div className={frame.tiles}>
                    <div className={frame.tile}><span className={frame.tileLabel}>На рахунку</span><b className={frame.tileValue}>{shahs.data.available}</b></div>
                    <div className={frame.tile}><span className={frame.tileLabel}>У резерві запусків</span><b className={frame.tileValue}>{shahs.data.reserved}</b></div>
                </div>
            )}
            <p className={styles.muted}>Шаги нараховує власник сайту. Ними платите за переклад з ШІ та ілюстрації у своїх перекладах.</p>
            <div className={styles.buttons} style={{ marginTop: 12 }}>
                <Link to="/me/shahs" className={[ui.button, ui.secondary].join(' ')}>Звідки прийшли й куди пішли</Link>
                {me.role === 'owner' && <Link to="/me/wallet" className={[ui.button, ui.secondary].join(' ')}>Шаги й автопереклад сайту</Link>}
            </div>
            {me.role === 'owner' && (
                <div className={styles.section} style={{ marginTop: 16 }}>
                    <Toggle label="Показувати мені суми в шагах" isSelected={me.showShah} onChange={(value) => save.mutate(value)} />
                    <p className={styles.muted}>Вимкніть, щоб бачити баланс і витрати на переклад у доларах. Діє лише для вас.</p>
                    {save.isError && <Notice tone="error">{save.error.message}</Notice>}
                </div>
            )}
        </SettingsFrame>
    );
}

/** Each switch saves at once; there is no «Зберегти» to forget. */
function PrivacySection({ me }: { me: Me }) {
    const setMe = useSetMe();
    const client = useQueryClient();
    const save = useMutation({ mutationFn: (patch: SettingsPatch) => meApi.update(patch), onSuccess: setMe });
    const presence = useQuery({ queryKey: ['presence'], queryFn: chatApi.presence });
    const showOnline = useMutation({
        mutationFn: chatApi.showOnline,
        onSuccess: (data) => {
            client.setQueryData(['presence'], data);
            void client.invalidateQueries({ queryKey: ['chat-online'] });
        },
    });
    const error = save.error ?? showOnline.error;
    return (
        <div className={styles.section}>
            <h2 className={styles.sectionTitle}>Приватність</h2>
            <div className={styles.form}>
                <div>
                    <Toggle label="Показувати іншим, що я читаю" isSelected={me.showReading}
                        onChange={(showReading) => save.mutate({ showReading })} />
                    <p className={styles.muted}>«Читає зараз» у профілі.</p>
                </div>
                {presence.data && (
                    <div>
                        <Toggle label="Показувати, що я на сайті" isSelected={presence.data.showOnline}
                            onChange={(value) => showOnline.mutate(value)} />
                        <p className={styles.muted}>У списку «Зараз на сайті» загального чату.</p>
                    </div>
                )}
                <Segmented label="Хто може мені писати й додавати в групи" value={me.dmPolicy}
                    options={[{ value: 'everyone', label: 'Усі' }, { value: 'nobody', label: 'Ніхто' }]}
                    onChange={(dmPolicy) => save.mutate({ dmPolicy })} />
                {error && <Notice tone="error">{error.message}</Notice>}
            </div>
        </div>
    );
}

/** People I blocked: they cannot write to me or add me to groups. */
function BlockedPeople() {
    const client = useQueryClient();
    const blocked = useQuery({ meta: { errorToast: true }, queryKey: ['blocked'], queryFn: messagingApi.blocked });
    const unblock = useMutation({ meta: { errorToast: true },
        mutationFn: (nick: string) => messagingApi.unblock(nick),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['blocked'] }),
    });
    const paged = usePaged(blocked.data);
    if (!blocked.data?.length) return null;
    return (
        <div className={styles.section}>
            <h2 className={styles.sectionTitle}>Заблоковані</h2>
            {paged.shown.map((nick) => (
                <div key={nick} className={styles.row} style={{ justifyContent: 'space-between', padding: '6px 0' }}>
                    <span>{nick}</span>
                    <Button variant="secondary" onPress={() => unblock.mutate(nick)}>Розблокувати</Button>
                </div>
            ))}
            <Pager page={paged.page} total={paged.total} size={PAGE_SIZE} onPage={paged.setPage} />
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
            <h2 className={styles.sectionTitle}>Фото й про себе</h2>
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
                <TextInput label="Новий пароль" type="password" value={next} onChange={setNext} autoComplete="new-password" isRequired hint="Щонайменше 8 символів." />
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
                    <a href={googleUrl('/me/settings/security', true)}
 className={[ui.button, ui.secondary].join(' ')}>
                        <GoogleMark /> Прив’язати Google
                    </a>
                </div>
            )}
        </div>
    );
}
