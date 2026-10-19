import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import { messageTime } from '../../lib/dates';
import { Button } from '../../ui/Button';
import { Loading } from '../../ui/Loading';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
import { TextInput } from '../../ui/TextInput';
import { Toggle } from '../../ui/Toggle';
import styles from '../pages.module.css';

export type WebhookEndpoint = {
    id: number;
    url: string;
    events: string[];
    editionIds: number[];
    enabled: boolean;
    disabledAt: string | null;
    disabledReason: string | null;
    failures: number;
    secretHint: string;
    createdAt: string;
};

export type WebhookDelivery = {
    id: number;
    deliveryId: string;
    event: string;
    state: 'pending' | 'sending' | 'delivered' | 'failed' | 'cancelled';
    attempts: number;
    status: number | null;
    error: string | null;
    durationMs: number | null;
    createdAt: string;
    lastAttemptAt: string | null;
    nextAttemptAt: string | null;
};

type TranslationOption = { editionId: number; title: string; novel: string; team: string; member: boolean };
type Overview = { endpoints: WebhookEndpoint[]; events: { code: string; team: boolean }[]; translations: TranslationOption[] };
type NewEndpoint = { url: string; events: string[]; editionIds: number[] };
type EndpointChange = { events?: string[]; editionIds?: number[]; enabled?: boolean };

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

export const webhooksApi = {
    mine: () => api<Overview>('/api/me/webhooks'),
    create: (endpoint: NewEndpoint) => api<{ endpoint: WebhookEndpoint; secret: string }>('/api/me/webhooks', json('POST', endpoint)),
    update: (id: number, change: EndpointChange) => api<WebhookEndpoint>(`/api/me/webhooks/${id}`, json('PATCH', change)),
    remove: (id: number) => api<void>(`/api/me/webhooks/${id}`, { method: 'DELETE' }),
    test: (id: number) => api<WebhookDelivery>(`/api/me/webhooks/${id}/test`, json('POST', {})),
    deliveries: (id: number) => api<WebhookDelivery[]>(`/api/me/webhooks/${id}/deliveries`),
    retry: (id: number, deliveryId: number) => api<WebhookDelivery>(`/api/me/webhooks/${id}/deliveries/${deliveryId}/retry`, json('POST', {})),
};

/** The events in the words of the site; «лише команда» — only for the translation's team. */
export const WEBHOOK_EVENTS: Record<string, { label: string; hint: string }> = {
    'chapter.published': { label: 'Нова глава опублікована', hint: 'Читачі можуть відкрити нові глави перекладу.' },
    'run.finished': { label: 'Запуск завершено', hint: 'Автопереклад переклав усі глави запуску.' },
    'run.stopped': { label: 'Запуск зупинився', hint: 'Помилка або хтось скасував запуск.' },
    'suggestion.created': { label: 'Нова правка', hint: 'Читач надіслав правки команді.' },
    'source.chapters': { label: 'Нові глави в оригіналі', hint: 'На сайті оригіналу зʼявилися глави, які можна перекладати.' },
    ping: { label: 'Пробна подія', hint: '' },
};

const DELIVERY_STATES: Record<WebhookDelivery['state'], string> = {
    pending: 'у черзі', sending: 'надсилається', delivered: 'доставлено', failed: 'не доставлено', cancelled: 'скасовано',
};

const KEY = ['webhooks'];

/** The browser's clipboard, where it gives one; the secret stays selectable anyway. */
function copy(text: string) {
    try {
        void navigator.clipboard.writeText(text).catch(() => undefined);
    } catch {
        // No clipboard here: the text is selected with one tap.
    }
}

const list = { listStyle: 'none', padding: 0, margin: 0 } as const;
const rowLine = { padding: '10px 0', borderBottom: '1px solid var(--line, rgba(127,127,127,.2))' } as const;
const wrap = { display: 'block', wordBreak: 'break-all', userSelect: 'all', padding: '12px 0' } as const;

/**
 * «Налаштування → API → Вебхуки» (етап 22.5): the site POSTs events to the person's https address,
 * signed with a secret shown once; a trial event, the journal of deliveries and «Повторити».
 */
export function WebhooksSection() {
    const overview = useQuery({ queryKey: KEY, queryFn: webhooksApi.mine });
    return (
        <div className={styles.section}>
            <h2 className={styles.sectionTitle}>Вебхуки</h2>
            <p className={styles.muted}>
                Коли щось стається, сайт надсилає <code>POST</code> із JSON на вашу адресу. Перевіряйте підпис: <code>X-Novelka-Signature</code> —
                це <code>sha256=</code> і HMAC-SHA256 секретом від <code>X-Novelka-Timestamp</code>, крапки й тіла запиту. Відповідь 2xx — доставлено;
                інакше сайт повторить із паузами протягом доби. Переадресації не виконуються.
            </p>
            {overview.isError && <Notice tone="error">{overview.error.message}</Notice>}
            {overview.isPending && <Loading shape="lines" onRetry={() => void overview.refetch()} />}
            {overview.data && (
                <>
                    <NewEndpointForm overview={overview.data} />
                    {overview.data.endpoints.length === 0 && <p className={styles.muted}>Вебхуків ще немає.</p>}
                    {overview.data.endpoints.length > 0 && (
                        <ul style={list} aria-label="Мої вебхуки">
                            {overview.data.endpoints.map((endpoint) => (
                                <EndpointRow key={endpoint.id} endpoint={endpoint} translations={overview.data.translations} />
                            ))}
                        </ul>
                    )}
                </>
            )}
        </div>
    );
}

function NewEndpointForm({ overview }: { overview: Overview }) {
    const client = useQueryClient();
    const [url, setUrl] = useState('');
    const [events, setEvents] = useState<string[]>(['chapter.published']);
    const [scope, setScope] = useState<'all' | 'chosen'>('all');
    const [chosen, setChosen] = useState<number[]>([]);
    const create = useMutation({
        mutationFn: () => webhooksApi.create({ url: url.trim(), events, editionIds: scope === 'all' ? [] : chosen }),
        onSuccess: () => {
            setUrl('');
            void client.invalidateQueries({ queryKey: KEY });
        },
    });

    function submit(event: FormEvent) {
        event.preventDefault();
        create.mutate();
    }

    const created = create.data;
    if (created) {
        return (
            <div>
                <Notice tone="success">Вебхук додано. Скопіюйте секрет зараз: більше його не покаже ніхто, навіть сайт.</Notice>
                <code aria-label="Секрет вебхука" style={wrap}>{created.secret}</code>
                <div className={styles.buttons}>
                    <Button variant="secondary" onPress={() => copy(created.secret)}>Копіювати</Button>
                    <Button variant="quiet" onPress={() => create.reset()}>Готово, скопіював</Button>
                </div>
            </div>
        );
    }
    return (
        <form onSubmit={submit} aria-label="Новий вебхук">
            <div className={styles.form}>
                <TextInput label="Адреса" value={url} onChange={setUrl} maxLength={500} placeholder="https://…" type="url"
                    hint="Лише https і публічний сайт: адреси внутрішніх мереж сайт не приймає." isRequired />
                <div>
                    <p className={styles.muted} style={{ marginBottom: 8 }}>Події</p>
                    {overview.events.map((option) => (
                        <div key={option.code} style={{ marginBottom: 8 }}>
                            <Toggle label={WEBHOOK_EVENTS[option.code]?.label ?? option.code} isSelected={events.includes(option.code)}
                                onChange={(on) => setEvents((now) => (on ? [...now, option.code] : now.filter((item) => item !== option.code)))} />
                            <p className={styles.muted}>
                                {WEBHOOK_EVENTS[option.code]?.hint}{option.team && ' Лише для команди перекладу.'}
                            </p>
                        </div>
                    ))}
                </div>
                <Segmented label="Переклади" value={scope} onChange={setScope}
                    options={[{ value: 'all', label: 'Усі доступні' }, { value: 'chosen', label: 'Вибрані' }]} />
                {scope === 'all' && (
                    <p className={styles.muted}>Усі переклади, які ви бачите; події для команди — лише з перекладів ваших команд.</p>
                )}
                {scope === 'chosen' && (
                    <div>
                        {overview.translations.length === 0 && (
                            <p className={styles.muted}>Тут будуть переклади ваших команд і ті, на які ви підписалися дзвіночком.</p>
                        )}
                        {overview.translations.map((option) => (
                            <div key={option.editionId} style={{ marginBottom: 8 }}>
                                <Toggle label={`${option.title} · $${option.team}`} isSelected={chosen.includes(option.editionId)}
                                    onChange={(on) => setChosen((now) => (on ? [...now, option.editionId] : now.filter((item) => item !== option.editionId)))} />
                            </div>
                        ))}
                    </div>
                )}
                {create.isError && <Notice tone="error">{create.error.message}</Notice>}
                <Button type="submit" pending={create.isPending} pendingLabel="Додаємо…"
                    isDisabled={!url.trim() || events.length === 0 || (scope === 'chosen' && chosen.length === 0)}>
                    Додати вебхук
                </Button>
            </div>
        </form>
    );
}

function EndpointRow({ endpoint, translations }: { endpoint: WebhookEndpoint; translations: TranslationOption[] }) {
    const client = useQueryClient();
    const [journal, setJournal] = useState(false);
    const refresh = () => {
        void client.invalidateQueries({ queryKey: KEY });
        void client.invalidateQueries({ queryKey: [...KEY, endpoint.id, 'deliveries'] });
    };
    const test = useMutation({ mutationFn: () => webhooksApi.test(endpoint.id), onSuccess: refresh });
    const toggle = useMutation({ mutationFn: (enabled: boolean) => webhooksApi.update(endpoint.id, { enabled }), onSuccess: refresh });
    const remove = useMutation({ mutationFn: () => webhooksApi.remove(endpoint.id), onSuccess: refresh });
    const names = endpoint.editionIds.map((id) => translations.find((option) => option.editionId === id)?.title ?? `переклад ${id}`);
    const tried = test.data;
    return (
        <li style={rowLine} aria-label={endpoint.url}>
            <code style={{ wordBreak: 'break-all' }}>{endpoint.url}</code>
            <p className={styles.muted}>
                {endpoint.events.map((code) => WEBHOOK_EVENTS[code]?.label ?? code).join(', ')}
                {' · '}
                {names.length === 0 ? 'усі доступні переклади' : names.join(', ')}
            </p>
            <p className={styles.muted}>
                Секрет <code>{endpoint.secretHint}</code> ·{' '}
                {endpoint.enabled
                    ? (endpoint.failures > 0 ? `працює, невдалих спроб поспіль: ${endpoint.failures}` : 'працює')
                    : `вимкнено${endpoint.disabledReason ? `: ${endpoint.disabledReason}` : ''}`}
            </p>
            {tried && (
                <Notice tone={tried.state === 'delivered' ? 'success' : 'error'}>
                    {tried.state === 'delivered'
                        ? `Пробну подію доставлено: відповідь ${tried.status ?? ''}${tried.durationMs !== null ? `, ${tried.durationMs} мс` : ''}.`
                        : `Пробну подію не доставлено: ${tried.error ?? 'невідома помилка'}`}
                </Notice>
            )}
            {test.isError && <Notice tone="error">{test.error.message}</Notice>}
            {toggle.isError && <Notice tone="error">{toggle.error.message}</Notice>}
            {remove.isError && <Notice tone="error">{remove.error.message}</Notice>}
            <div className={styles.buttons} style={{ flexWrap: 'wrap' }}>
                <Button variant="secondary" onPress={() => test.mutate()} pending={test.isPending} pendingLabel="Надсилаємо…">
                    Надіслати пробну подію
                </Button>
                <Button variant="quiet" onPress={() => setJournal((open) => !open)} aria-expanded={journal}>
                    {journal ? 'Сховати журнал' : 'Журнал доставок'}
                </Button>
                <Button variant="quiet" onPress={() => toggle.mutate(!endpoint.enabled)} pending={toggle.isPending}>
                    {endpoint.enabled ? 'Вимкнути' : 'Увімкнути'}
                </Button>
                <Button variant="danger" onPress={() => remove.mutate()} pending={remove.isPending} pendingLabel="Видаляємо…">Видалити</Button>
            </div>
            {journal && <Journal endpointId={endpoint.id} enabled={endpoint.enabled} />}
        </li>
    );
}

function Journal({ endpointId, enabled }: { endpointId: number; enabled: boolean }) {
    const client = useQueryClient();
    const deliveries = useQuery({ queryKey: [...KEY, endpointId, 'deliveries'], queryFn: () => webhooksApi.deliveries(endpointId) });
    const retry = useMutation({
        mutationFn: (deliveryId: number) => webhooksApi.retry(endpointId, deliveryId),
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: [...KEY, endpointId, 'deliveries'] });
            void client.invalidateQueries({ queryKey: KEY, exact: true });
        },
    });
    return (
        <div style={{ paddingTop: 8 }}>
            {deliveries.isError && <Notice tone="error">{deliveries.error.message}</Notice>}
            {deliveries.isPending && <Loading shape="lines" onRetry={() => void deliveries.refetch()} />}
            {retry.isError && <Notice tone="error">{retry.error.message}</Notice>}
            {deliveries.data && deliveries.data.length === 0 && <p className={styles.muted}>Доставок ще не було.</p>}
            {deliveries.data && deliveries.data.length > 0 && (
                <ul style={list} aria-label="Журнал доставок">
                    {deliveries.data.map((delivery) => (
                        <li key={delivery.id} style={rowLine} aria-label={`Доставка ${delivery.id}`}>
                            <div className={styles.row} style={{ justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
                                <div style={{ minWidth: 0 }}>
                                    <b>{WEBHOOK_EVENTS[delivery.event]?.label ?? delivery.event}</b> · {DELIVERY_STATES[delivery.state]}
                                    <p className={styles.muted}>
                                        {messageTime(new Date(delivery.lastAttemptAt ?? delivery.createdAt))}
                                        {delivery.status !== null && ` · код ${delivery.status}`}
                                        {delivery.durationMs !== null && ` · ${delivery.durationMs} мс`}
                                        {` · спроба ${delivery.attempts}`}
                                        {delivery.nextAttemptAt && ` · наступна ${messageTime(new Date(delivery.nextAttemptAt))}`}
                                    </p>
                                    {delivery.error && <p className={styles.muted}>{delivery.error}</p>}
                                </div>
                                {(delivery.state === 'failed' || delivery.state === 'cancelled' || delivery.state === 'delivered')
                                    && (enabled || delivery.event === 'ping') && (
                                    <Button variant="secondary" onPress={() => retry.mutate(delivery.id)}
                                        pending={retry.isPending && retry.variables === delivery.id} pendingLabel="Надсилаємо…">
                                        Повторити
                                    </Button>
                                )}
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}
