import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { ROLE_LABELS, TARGET_LABELS, adminApi, type AuditEntry, type Person, type Preview, type SiteSettingsView, type Target } from '../../admin/api';
import { useMe } from '../../auth/me';
import { Markup } from '../../community/Markup';
import { relativeTime } from '../../lib/dates';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
import { Sheet } from '../../ui/Sheet';
import { shahApi } from '../../ledger/api';
import { dollars, shahWord } from '../../studio/autotranslate';
import { TextInput } from '../../ui/TextInput';
import { Toggle } from '../../ui/Toggle';
import styles from './admin.module.css';
import { askText } from '../../ui/ask';

const RANK = { reader: 0, moderator: 1, admin: 2, owner: 3 } as const;

function useRank() {
    const me = useMe();
    return me ? RANK[me.role] : 0;
}

// ---- /admin --------------------------------------------------------------------------------------

export function AdminHome() {
    const rank = useRank();
    const overview = useQuery({ queryKey: ['admin-overview'], queryFn: adminApi.overview });
    if (overview.isError) return <section className={styles.page}><Notice tone="error">{overview.error.message}</Notice></section>;
    const data = overview.data;
    return (
        <section className={styles.page}>
            <h1 className={styles.title}>Адміністрування</h1>
            {data && (
                <div className={styles.cards}>
                    <Link to="/admin/moderation" className={styles.card}><b>{data.openReports}</b>скарг на розгляді</Link>
                    <div className={styles.card}><b>{data.activeJobs}</b>автоперекладів у роботі</div>
                    <div className={styles.card}><b>{data.failedJobs}</b>зупинених з помилкою</div>
                </div>
            )}
            <nav className={styles.menu} aria-label="Розділи адміністрування">
                <Link to="/admin/moderation" className={styles.menuItem}>Скарги й приховане</Link>
                {rank >= RANK.admin && <Link to="/admin/users" className={styles.menuItem}>Користувачі й ролі</Link>}
                {rank >= RANK.owner && <Link to="/admin/settings" className={styles.menuItem}>Налаштування сайту</Link>}
                {rank >= RANK.owner && <Link to="/me/wallet" className={styles.menuItem}>Моделі, ціни й собівартість</Link>}
                {rank >= RANK.owner && <Link to="/admin/audit" className={styles.menuItem}>Журнал дій</Link>}
            </nav>
        </section>
    );
}

// ---- /admin/moderation ---------------------------------------------------------------------------

function PreviewBox({ target, preview }: { target: Target; preview: Preview }) {
    return (
        <>
            <div className={styles.muted}>
                {TARGET_LABELS[target]}
                {preview.author && <> · {target === 'edition' ? `$${preview.author}` : <Link to="/u/$nick" params={{ nick: preview.author }}>{preview.author}</Link>}</>}
                {preview.where && <> · {preview.slug
                    ? <Link to={preview.chapter ? '/n/$slug/$number' : '/n/$slug'} params={{ slug: preview.slug, number: String(preview.chapter ?? '') }}
                        search={preview.team ? { t: preview.team } : {}}>{preview.where}</Link>
                    : preview.where}</>}
            </div>
            {preview.text && target !== 'edition' && <div className={styles.quote}><Markup text={preview.text} /></div>}
            {preview.imageUrl && <img className={styles.picture} src={preview.imageUrl} alt="Картинка, на яку поскаржились" />}
        </>
    );
}

export function ModerationPage() {
    const client = useQueryClient();
    const [view, setView] = useState<'reports' | 'hidden'>('reports');
    const reports = useQuery({ queryKey: ['admin-reports'], queryFn: adminApi.reports, enabled: view === 'reports' });
    const hidden = useQuery({ queryKey: ['admin-hidden'], queryFn: adminApi.hidden, enabled: view === 'hidden' });
    const refresh = () => ['admin-reports', 'admin-hidden', 'admin-overview'].forEach((key) => void client.invalidateQueries({ queryKey: [key] }));
    const act = useMutation({ mutationFn: (action: () => Promise<unknown>) => action(), onSuccess: refresh });

    return (
        <section className={styles.page}>
            <Link to="/admin" className={styles.muted}>‹ Адміністрування</Link>
            <h1 className={styles.title}>Модерація</h1>
            <Segmented label="Що показати" value={view} onChange={setView}
                options={[{ value: 'reports', label: 'Скарги' }, { value: 'hidden', label: 'Приховане' }]} />
            {act.isError && <Notice tone="error">{act.error.message}</Notice>}
            {view === 'reports' && reports.data?.length === 0 && <p className={styles.muted}>Скарг немає.</p>}
            {view === 'reports' && reports.data?.map((item) => (
                <article key={`${item.target}-${item.targetId}`} className={styles.item}>
                    <PreviewBox target={item.target} preview={item.preview} />
                    <div className={styles.muted}>
                        Скарг: {item.reports} · перша {relativeTime(new Date(item.firstAt))} · «{item.reasons.join('», «')}»
                    </div>
                    <div className={styles.actions}>
                        <Button onPress={() => void askText({
                            title: 'Приховати', label: 'Причина', hint: 'Необовʼязково. Її побачать інші модератори.', optional: true,
                            confirmLabel: 'Приховати', danger: true,
                        }).then((reason) => {
                            if (reason !== null) act.mutate(() => adminApi.decide(item.target, item.targetId, 'hide', reason || undefined));
                        })}>Приховати</Button>
                        <Button variant="secondary" onPress={() => act.mutate(() => adminApi.decide(item.target, item.targetId, 'dismiss'))}>
                            Відхилити скаргу
                        </Button>
                    </div>
                </article>
            ))}
            {view === 'hidden' && hidden.data?.length === 0 && <p className={styles.muted}>Нічого не приховано.</p>}
            {view === 'hidden' && hidden.data?.map((item) => (
                <article key={`${item.target}-${item.targetId}`} className={styles.item}>
                    <PreviewBox target={item.target} preview={item.preview} />
                    {item.target === 'edition' && item.preview.text && <div className={styles.quote}>{item.preview.text}</div>}
                    <div className={styles.muted}>
                        Приховано {relativeTime(new Date(item.hiddenAt))}{item.hiddenBy ? ` · ${item.hiddenBy}` : ''}{item.reason ? ` · «${item.reason}»` : ''}
                    </div>
                    <div className={styles.actions}>
                        <Button variant="secondary" onPress={() => act.mutate(() => adminApi.restore(item.target, item.targetId))}>Повернути</Button>
                    </div>
                </article>
            ))}
        </section>
    );
}

// ---- /admin/users --------------------------------------------------------------------------------

export function UsersPage() {
    const rank = useRank();
    const me = useMe();
    const client = useQueryClient();
    const [q, setQ] = useState('');
    const people = useQuery({ queryKey: ['admin-users', q], queryFn: () => adminApi.users(q), placeholderData: (previous) => previous });
    const setRole = useMutation({
        mutationFn: ({ nick, role }: { nick: string; role: Person['role'] }) => adminApi.setRole(nick, role),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['admin-users'] }),
    });
    // An administrator gives up to moderator; the owner gives up to administrator.
    const grantable: Person['role'][] = rank >= RANK.owner ? ['reader', 'moderator', 'admin'] : ['reader', 'moderator'];
    const [granting, setGranting] = useState<string | null>(null);
    const [granted, setGranted] = useState<string | null>(null);
    return (
        <section className={styles.page}>
            <Link to="/admin" className={styles.muted}>‹ Адміністрування</Link>
            <h1 className={styles.title}>Користувачі й ролі</h1>
            <TextInput label="Пошук" value={q} onChange={setQ} placeholder={rank >= RANK.owner ? 'нік або пошта' : 'нік'} />
            {setRole.isError && <Notice tone="error">{setRole.error.message}</Notice>}
            {granted && <Notice tone="success">{granted}</Notice>}
            {granting && (
                <GrantSheet nick={granting} onClose={() => setGranting(null)}
                    onDone={(message) => { setGranting(null); setGranted(message); void client.invalidateQueries({ queryKey: ['admin-users'] }); }} />
            )}
            {people.data?.map((person) => {
                const editable = person.nick !== me?.nick && person.role !== 'owner' && (rank >= RANK.owner || RANK[person.role] < RANK.admin);
                return (
                    <div key={person.nick} className={styles.row}>
                        <div className={styles.grow}>
                            <Link to="/u/$nick" params={{ nick: person.nick }}>{person.nick}</Link>
                            <div className={styles.muted}>
                                {person.email ? `${person.email} · ` : ''}
                                {person.lastSeenAt ? `останній візит ${relativeTime(new Date(person.lastSeenAt))}` : 'ще не було на сайті'}
                            </div>
                            {rank >= RANK.owner && person.nick !== me?.nick && (
                                <div className={styles.muted}>
                                    {person.shahs ?? 0} {shahWord(person.shahs ?? 0)}{' · '}
                                    <button type="button" className={styles.linkButton} aria-label={`Нарахувати шаги ${person.nick}`}
                                        onClick={() => { setGranted(null); setGranting(person.nick); }}>нарахувати</button>
                                </div>
                            )}
                        </div>
                        {editable ? (
                            <select className={styles.select} aria-label={`Роль ${person.nick}`} value={person.role}
                                onChange={(event) => setRole.mutate({ nick: person.nick, role: event.target.value as Person['role'] })}>
                                {grantable.map((role) => <option key={role} value={role}>{ROLE_LABELS[role]}</option>)}
                            </select>
                        ) : <span className={styles.muted}>{ROLE_LABELS[person.role]}</span>}
                    </div>
                );
            })}
        </section>
    );
}

/** The site owner gives шаги (рішення 29): whole шаги, never taken back. */
function GrantSheet({ nick, onClose, onDone }: { nick: string; onClose: () => void; onDone: (message: string) => void }) {
    const [amount, setAmount] = useState('');
    const [note, setNote] = useState('');
    const shah = Number(amount);
    const valid = Number.isInteger(shah) && shah >= 1;
    const grant = useMutation({
        mutationFn: () => shahApi.grant(nick, shah, note),
        onSuccess: (result) => onDone(`${nick}: нараховано ${shah} ${shahWord(shah)}, тепер ${result.available} ${shahWord(result.available)}.`),
    });
    return (
        <Sheet open onClose={onClose} title={`Нарахувати шаги ${nick}`}>
            <form style={{ display: 'grid', gap: 14 }} onSubmit={(event) => { event.preventDefault(); if (valid) grant.mutate(); }}>
                <TextInput label="Скільки шагів" value={amount} onChange={setAmount} inputMode="numeric" autoFocus
                    hint="Цілі шаги. Назад їх не забрати." />
                <TextInput label="Примітка" value={note} onChange={setNote} hint="Необовʼязково. Людина побачить її в історії." />
                {grant.isError && <Notice tone="error">{grant.error.message}</Notice>}
                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                    <Button variant="secondary" onPress={onClose}>Скасувати</Button>
                    <Button type="submit" isDisabled={!valid} pending={grant.isPending} pendingLabel="Нараховуємо…">Нарахувати</Button>
                </div>
            </form>
        </Sheet>
    );
}

// ---- /admin/settings -----------------------------------------------------------------------------

export function SiteSettingsPage() {
    const settings = useQuery({ queryKey: ['admin-settings'], queryFn: adminApi.settings });
    if (settings.isError) return <section className={styles.page}><Notice tone="error">{settings.error.message}</Notice></section>;
    if (!settings.data) return null;
    return (
        <section className={styles.page}>
            <Link to="/admin" className={styles.muted}>‹ Адміністрування</Link>
            <h1 className={styles.title}>Налаштування сайту</h1>
            <SettingsForm initial={settings.data} />
            <p className={styles.muted} style={{ marginTop: 16 }}>
                Моделі, ціни й собівартість шагу — на сторінці <Link to="/me/wallet">«Шаги»</Link>.
            </p>
        </section>
    );
}

function SettingsForm({ initial }: { initial: SiteSettingsView }) {
    const client = useQueryClient();
    const [draft, setDraft] = useState(initial);
    const [months, setMonths] = useState(String(initial.relayInactiveMonths));
    const save = useMutation({
        mutationFn: () => adminApi.saveSettings({ ...draft, relayInactiveMonths: Number(months) }),
        onSuccess: (saved) => client.setQueryData(['admin-settings'], saved),
    });
    return (
        <form className={styles.form} onSubmit={(event) => { event.preventDefault(); save.mutate(); }}>
            <Toggle label="Реєстрація відкрита" isSelected={draft.registrationOpen} onChange={(registrationOpen) => setDraft({ ...draft, registrationOpen })} />
            <Toggle label="Новели 18+ на сайті" isSelected={draft.adultEnabled} onChange={(adultEnabled) => setDraft({ ...draft, adultEnabled })} />
            <p className={styles.muted}>Вимкнено — дорослих новел не бачить ніхто, навіть ті, хто підтвердив вік.</p>
            <TextInput label="Строк естафети, місяців" value={months} onChange={setMonths} inputMode="numeric"
                hint="Переклад стає вільним, якщо власник команди стільки не заходив." />
            {save.isError && <Notice tone="error">{save.error.message}</Notice>}
            {save.isSuccess && <Notice tone="success">Збережено.</Notice>}
            <Button type="submit" pending={save.isPending} pendingLabel="Зберігаємо…">Зберегти</Button>
        </form>
    );
}

// ---- /admin/audit --------------------------------------------------------------------------------

const ACTIONS: Record<string, string> = {
    hide: 'приховує', restore: 'повертає', dismiss: 'відхиляє скаргу на', role: 'змінює роль', settings: 'змінює налаштування сайту',
};

function describe(entry: AuditEntry): string {
    if (entry.action === 'role') {
        const details = entry.details as { nick?: string; from?: keyof typeof ROLE_LABELS; to?: keyof typeof ROLE_LABELS };
        return `змінює роль ${details.nick}: ${details.from ? ROLE_LABELS[details.from] : ''} → ${details.to ? ROLE_LABELS[details.to] : ''}`;
    }
    if (entry.action === 'settings') return ACTIONS.settings!;
    if (entry.action === 'shahs_granted') {
        const details = entry.details as { shah?: number; nick?: string; note?: string };
        return `нараховує ${details.shah ?? 0} ${shahWord(details.shah ?? 0)} ${details.nick ?? ''}${details.note ? ` · «${details.note}»` : ''}`;
    }
    if (entry.action === 'shah_price') {
        const micro = (entry.details as { microUsdPerShah?: number }).microUsdPerShah ?? 0;
        return `змінює ціну шагу для людей: ${dollars(micro / 1_000_000)}`;
    }
    if (entry.action === 'autotranslate_settings' || entry.action === 'illustration_settings') {
        return `${entry.action === 'autotranslate_settings' ? 'змінює моделі й ціни автоперекладу' : 'змінює налаштування ілюстрацій'}${modelChanges(entry.details)}`;
    }
    const what = TARGET_LABELS[entry.targetType as Target]?.toLowerCase() ?? entry.targetType;
    const reason = typeof entry.details.reason === 'string' ? ` · «${entry.details.reason}»` : '';
    return `${ACTIONS[entry.action] ?? entry.action} ${what}${reason}`;
}

/** «переклад: a → b» for every model the change replaced. */
function modelChanges(details: Record<string, unknown>): string {
    const before = (details.before ?? {}) as Record<string, unknown>;
    const after = (details.after ?? {}) as Record<string, unknown>;
    const names: Record<string, string> = { analyze: 'аналіз', translate: 'переклад', proofread: 'вичитка', model: 'малює', promptModel: 'опис' };
    const modelOf = (value: unknown) => (typeof value === 'string' ? value : (value as { model?: string } | undefined)?.model);
    const changed = Object.keys(names).flatMap((key) => {
        const from = modelOf(before[key]);
        const to = modelOf(after[key]);
        return from && to && from !== to ? [`${names[key]}: ${from} → ${to}`] : [];
    });
    return changed.length ? ` · ${changed.join(', ')}` : '';
}

export function AuditPage() {
    const pages = useInfiniteQuery({
        queryKey: ['admin-audit'],
        queryFn: ({ pageParam }) => adminApi.audit(pageParam),
        initialPageParam: undefined as number | undefined,
        getNextPageParam: (last) => (last.length === 50 ? last[last.length - 1]?.id : undefined),
    });
    const entries = pages.data?.pages.flat() ?? [];
    return (
        <section className={styles.page}>
            <Link to="/admin" className={styles.muted}>‹ Адміністрування</Link>
            <h1 className={styles.title}>Журнал дій</h1>
            {pages.isError && <Notice tone="error">{pages.error.message}</Notice>}
            {entries.length === 0 && pages.isSuccess && <p className={styles.muted}>Поки порожньо.</p>}
            {entries.map((entry) => (
                <div key={entry.id} className={styles.row}>
                    <div className={styles.grow}><b>{entry.actor}</b> {describe(entry)}</div>
                    <span className={styles.muted}>{relativeTime(new Date(entry.createdAt))}</span>
                </div>
            ))}
            {pages.hasNextPage && <Button variant="secondary" onPress={() => void pages.fetchNextPage()}>Давніші</Button>}
        </section>
    );
}
