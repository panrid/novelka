import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import { useMe } from '../../auth/me';
import { messageTime } from '../../lib/dates';
import { Button } from '../../ui/Button';
import { Loading } from '../../ui/Loading';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
import { TextInput } from '../../ui/TextInput';
import { Toggle } from '../../ui/Toggle';
import { SettingsFrame } from './SettingsFrame';
import styles from '../pages.module.css';

export type ApiToken = {
    id: number;
    name: string;
    hint: string;
    scopes: string[];
    createdAt: string;
    expiresAt: string;
    lastUsedAt: string | null;
    lastUsedIp: string | null;
    revokedAt: string | null;
    state: 'active' | 'expired' | 'revoked';
    owner: string;
};

type Tokens = { tokens: ApiToken[]; offered: string[]; days: number[] };
type NewToken = { name: string; scopes: string[]; days: number };

export const apiTokensApi = {
    mine: () => api<Tokens>('/api/me/api-tokens'),
    create: (token: NewToken) => api<{ token: ApiToken; secret: string }>('/api/me/api-tokens', { method: 'POST', body: JSON.stringify(token) }),
    revoke: (id: number) => api<void>(`/api/me/api-tokens/${id}`, { method: 'DELETE' }),
    all: () => api<ApiToken[]>('/api/admin/api-tokens'),
    revokeAny: (id: number) => api<void>(`/api/admin/api-tokens/${id}`, { method: 'DELETE' }),
};

/** What each right lets a token do, in the words of the site. */
export const SCOPES: Record<string, { label: string; hint: string }> = {
    read: { label: 'Читання', hint: 'Новели, каталог, глави, теги — те, що бачить кожен.' },
    library: { label: 'Моя бібліотека', hint: 'Списки, місце читання, прочитані глави, оцінки.' },
    suggest: { label: 'Правки', hint: 'Чернетки правок і надсилання на перевірку.' },
    studio: { label: 'Студія', hint: 'Переклади, де ви в команді: глави, оригінал, словник, публікація, картинки.' },
    translate: { label: 'Переклад з ШІ', hint: 'Запуски автоперекладу й ілюстрації — за ваші шаги.' },
    admin: { label: 'Адміністрування', hint: 'Те, що дозволяє ваша роль на сайті; кожна дія — в журнал.' },
};

const STATES: Record<ApiToken['state'], string> = { active: 'діє', expired: 'строк минув', revoked: 'відкликано' };

/** The browser's clipboard, where it gives one (not on plain HTTP); the token stays selectable anyway. */
function copy(text: string) {
    try {
        void navigator.clipboard.writeText(text).catch(() => undefined);
    } catch {
        // No clipboard here: the text is selected with one tap.
    }
}

function dayWord(days: number) {
    return days === 365 ? '1 рік' : `${days} днів`;
}

/**
 * «Налаштування → API» (етап 22.1): tokens for programs and agents. A new token is shown once —
 * the site keeps only its fingerprint; each can be revoked at any time. The site owner also sees
 * everyone's tokens and may revoke any.
 */
export function ApiSettingsPage() {
    const me = useMe();
    if (!me) {
        return null;
    }
    return (
        <SettingsFrame title="API">
            <p className={styles.muted}>
                Токен дає програмі чи агентові ШІ діяти від вашого імені — не більше, ніж можете ви самі, і лише з позначеними правами.
                Запити — з заголовком <code>Authorization: Bearer …</code>; публічне читання — <code>/api/v1</code>.
            </p>
            <NewTokenSection />
            <MyTokens />
            {me.role === 'owner' && <AllTokens />}
        </SettingsFrame>
    );
}

function NewTokenSection() {
    const client = useQueryClient();
    const tokens = useQuery({ queryKey: ['api-tokens'], queryFn: apiTokensApi.mine });
    const [name, setName] = useState('');
    const [scopes, setScopes] = useState<string[]>(['read']);
    const [days, setDays] = useState('90');
    const create = useMutation({
        mutationFn: () => apiTokensApi.create({ name: name.trim(), scopes, days: Number(days) }),
        onSuccess: () => {
            setName('');
            void client.invalidateQueries({ queryKey: ['api-tokens'] });
        },
    });

    function submit(event: FormEvent) {
        event.preventDefault();
        create.mutate();
    }

    const created = create.data;
    if (created) {
        return (
            <div className={styles.section}>
                <h2 className={styles.sectionTitle}>Новий токен «{created.token.name}»</h2>
                <Notice tone="success">Скопіюйте токен зараз: більше його не покаже ніхто, навіть сайт.</Notice>
                <code aria-label="Новий токен" style={{ display: 'block', wordBreak: 'break-all', userSelect: 'all', padding: '12px 0' }}>
                    {created.secret}
                </code>
                <div className={styles.buttons}>
                    <Button variant="secondary" onPress={() => copy(created.secret)}>Копіювати</Button>
                    <Button variant="quiet" onPress={() => create.reset()}>Готово, скопіював</Button>
                </div>
            </div>
        );
    }
    const offered = tokens.data?.offered ?? [];
    return (
        <form className={styles.section} onSubmit={submit}>
            <h2 className={styles.sectionTitle}>Новий токен</h2>
            <div className={styles.form}>
                <TextInput label="Назва" value={name} onChange={setName} maxLength={60} hint="Щоб потім знати, де він: «Читалка на телефоні», «Агент перекладу»." isRequired />
                <div>
                    <p className={styles.muted} style={{ marginBottom: 8 }}>Права</p>
                    {offered.map((scope) => (
                        <div key={scope} style={{ marginBottom: 8 }}>
                            <Toggle label={SCOPES[scope]?.label ?? scope} isSelected={scopes.includes(scope)}
                                onChange={(on) => setScopes((now) => (on ? [...now, scope] : now.filter((item) => item !== scope)))} />
                            <p className={styles.muted}>{SCOPES[scope]?.hint}</p>
                        </div>
                    ))}
                </div>
                <Segmented label="Строк дії" value={days} onChange={setDays}
                    options={(tokens.data?.days ?? [7, 30, 90, 365]).map((value) => ({ value: String(value), label: dayWord(value) }))} />
                {create.isError && <Notice tone="error">{create.error.message}</Notice>}
                <Button type="submit" pending={create.isPending} pendingLabel="Створюємо…" isDisabled={!name.trim() || scopes.length === 0}>
                    Створити токен
                </Button>
            </div>
        </form>
    );
}

function TokenRow({ token, showOwner, onRevoke, pending }: { token: ApiToken; showOwner?: boolean; onRevoke: () => void; pending: boolean }) {
    return (
        <li style={{ padding: '10px 0', borderBottom: '1px solid var(--line, rgba(127,127,127,.2))' }} aria-label={token.name}>
            <div className={styles.row} style={{ justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
                <div style={{ minWidth: 0 }}>
                    <b>{token.name}</b>{showOwner && <> · {token.owner}</>} <code>{token.hint}</code>
                    <p className={styles.muted}>
                        {token.scopes.map((scope) => SCOPES[scope]?.label ?? scope).join(', ')} · {STATES[token.state]}
                        {token.state === 'active' && <> до {messageTime(new Date(token.expiresAt))}</>}
                    </p>
                    <p className={styles.muted}>
                        {token.lastUsedAt
                            ? `Востаннє: ${messageTime(new Date(token.lastUsedAt))}${token.lastUsedIp ? ` з ${token.lastUsedIp}` : ''}`
                            : 'Ще не використовувався'}
                    </p>
                </div>
                {token.state === 'active' && (
                    <Button variant="danger" onPress={onRevoke} pending={pending} pendingLabel="Відкликаємо…">Відкликати</Button>
                )}
            </div>
        </li>
    );
}

function MyTokens() {
    const client = useQueryClient();
    const tokens = useQuery({ queryKey: ['api-tokens'], queryFn: apiTokensApi.mine });
    const revoke = useMutation({
        mutationFn: apiTokensApi.revoke,
        onSuccess: () => void client.invalidateQueries({ queryKey: ['api-tokens'] }),
    });
    return (
        <div className={styles.section}>
            <h2 className={styles.sectionTitle}>Мої токени</h2>
            {tokens.isError && <Notice tone="error">{tokens.error.message}</Notice>}
            {tokens.isPending && <Loading shape="lines" onRetry={() => void tokens.refetch()} />}
            {revoke.isError && <Notice tone="error">{revoke.error.message}</Notice>}
            {tokens.data && tokens.data.tokens.length === 0 && <p className={styles.muted}>Токенів ще немає.</p>}
            {tokens.data && tokens.data.tokens.length > 0 && (
                <ul style={{ listStyle: 'none', padding: 0, margin: 0 }} aria-label="Мої токени">
                    {tokens.data.tokens.map((token) => (
                        <TokenRow key={token.id} token={token} onRevoke={() => revoke.mutate(token.id)}
                            pending={revoke.isPending && revoke.variables === token.id} />
                    ))}
                </ul>
            )}
        </div>
    );
}

/** The site owner's view: every live token of the site, whoever made it. */
function AllTokens() {
    const client = useQueryClient();
    const tokens = useQuery({ queryKey: ['api-tokens-all'], queryFn: apiTokensApi.all });
    const revoke = useMutation({
        mutationFn: apiTokensApi.revokeAny,
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: ['api-tokens-all'] });
            void client.invalidateQueries({ queryKey: ['api-tokens'] });
        },
    });
    return (
        <div className={styles.section}>
            <h2 className={styles.sectionTitle}>Усі токени сайту</h2>
            {tokens.isError && <Notice tone="error">{tokens.error.message}</Notice>}
            {revoke.isError && <Notice tone="error">{revoke.error.message}</Notice>}
            {tokens.data && tokens.data.length === 0 && <p className={styles.muted}>Живих токенів немає.</p>}
            {tokens.data && tokens.data.length > 0 && (
                <ul style={{ listStyle: 'none', padding: 0, margin: 0 }} aria-label="Усі токени сайту">
                    {tokens.data.map((token) => (
                        <TokenRow key={token.id} token={token} showOwner onRevoke={() => revoke.mutate(token.id)}
                            pending={revoke.isPending && revoke.variables === token.id} />
                    ))}
                </ul>
            )}
        </div>
    );
}
