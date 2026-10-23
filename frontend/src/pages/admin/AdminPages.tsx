import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { ROLE_LABELS, TARGET_LABELS, adminApi, type AuditEntry, type Person, type Preview, type SiteSettingsView, type Target } from '../../admin/api';
import { useMe } from '../../auth/me';
import { Markup } from '../../community/Markup';
import { relativeTime } from '../../lib/dates';
import { Button } from '../../ui/Button';
import { LinkButton } from '../../ui/LinkButton';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
import { Sheet } from '../../ui/Sheet';
import { shahApi } from '../../ledger/api';
import { QUALITIES, QUALITY_LABELS, autotranslateApi, dollars, shahWord, type PresetQuality, type Quality } from '../../studio/autotranslate';
import { TextInput } from '../../ui/TextInput';
import { Toggle } from '../../ui/Toggle';
import { Pager } from '../../ui/Pager';
import { PAGE_SIZE, usePage, usePaged } from '../../lib/usePage';
import styles from './admin.module.css';
import { askText } from '../../ui/ask';
import { setSiteDefault, useSite } from '../../appearance/store';
import { FRAMES, THEMES, themeVars, type FrameId, type ThemeId } from '../../appearance/themes';
import { FramePicker, ThemePicker } from '../me/AppearancePages';

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
                {rank >= RANK.owner && <Link to="/admin/analytics" className={styles.menuItem}>Аналітика: витрати й якість моделей</Link>}
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
    const rank = useRank();
    const [view, setView] = useState<'reports' | 'hidden'>('reports');
    const reports = useQuery({ meta: { errorToast: true }, queryKey: ['admin-reports'], queryFn: adminApi.reports, enabled: view === 'reports' });
    const hidden = useQuery({ meta: { errorToast: true }, queryKey: ['admin-hidden'], queryFn: adminApi.hidden, enabled: view === 'hidden' });
    const reportPage = usePaged(reports.data);
    const hiddenPage = usePaged(hidden.data);
    const refresh = () => ['admin-reports', 'admin-hidden', 'admin-overview'].forEach((key) => void client.invalidateQueries({ queryKey: [key] }));
    const act = useMutation({ mutationFn: (action: () => Promise<unknown>) => action(), onSuccess: refresh });

    return (
        <section className={styles.page}>
            <Link to="/admin" className={styles.muted}>‹ Адміністрування</Link>
            <h1 className={styles.title}>Модерація</h1>
            <Segmented label="Що показати" value={view} onChange={(next) => { setView(next); reportPage.setPage(1); }}
                options={[{ value: 'reports', label: 'Скарги' }, { value: 'hidden', label: 'Приховане' }]} />
            {act.isError && <Notice tone="error">{act.error.message}</Notice>}
            {view === 'reports' && reports.data?.length === 0 && <p className={styles.muted}>Скарг немає.</p>}
            {view === 'reports' && reportPage.shown.map((item) => (
                <article key={`${item.target}-${item.targetId}`} className={styles.item}>
                    <PreviewBox target={item.target} preview={item.preview} />
                    {item.target === 'edition' && item.preview.text && <div className={styles.quote}>{item.preview.text}</div>}
                    <ul className={styles.reports} aria-label="Скарги">
                        {(item.entries ?? []).map((entry, index) => (
                            <li key={index}>
                                <span className={styles.muted}>
                                    <Link to="/u/$nick" params={{ nick: entry.nick }}>{entry.nick}</Link> · {relativeTime(new Date(entry.at))}
                                    {entry.chapter !== null && item.preview.slug && <> · <Link to="/n/$slug/$number"
                                        params={{ slug: item.preview.slug, number: String(entry.chapter) }}
                                        search={item.preview.team ? { t: item.preview.team } : {}}>глава {entry.chapterLabel}</Link></>}
                                </span>
                                <div>«{entry.reason}»</div>
                            </li>
                        ))}
                    </ul>
                    <div className={styles.actions}>
                        {item.preview.slug && (
                            <LinkButton variant="secondary" to={item.preview.chapter ? '/n/$slug/$number' : '/n/$slug'}
                                params={{ slug: item.preview.slug, number: String(item.preview.chapter ?? '') }}
                                search={item.preview.team ? { t: item.preview.team } : {}}>Відкрити</LinkButton>
                        )}
                        {(item.target !== 'edition' || rank >= RANK.admin) && <Button onPress={() => void askText({
                            title: 'Приховати', label: 'Причина', hint: 'Необовʼязково. Її побачать інші модератори.', optional: true,
                            confirmLabel: 'Приховати', danger: true,
                        }).then((reason) => {
                            if (reason !== null) act.mutate(() => adminApi.decide(item.target, item.targetId, 'hide', reason || undefined));
                        })}>Приховати</Button>}
                        <Button variant="secondary" onPress={() => act.mutate(() => adminApi.decide(item.target, item.targetId, 'dismiss'))}>
                            Відхилити скаргу
                        </Button>
                    </div>
                </article>
            ))}
            {view === 'reports' && <Pager page={reportPage.page} total={reportPage.total} size={PAGE_SIZE} onPage={reportPage.setPage} />}
            {view === 'hidden' && hidden.data?.length === 0 && <p className={styles.muted}>Нічого не приховано.</p>}
            {view === 'hidden' && hiddenPage.shown.map((item) => (
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
            {view === 'hidden' && <Pager page={hiddenPage.page} total={hiddenPage.total} size={PAGE_SIZE} onPage={hiddenPage.setPage} />}
        </section>
    );
}

// ---- /admin/users --------------------------------------------------------------------------------

export function UsersPage() {
    const rank = useRank();
    const me = useMe();
    const client = useQueryClient();
    const [q, setQ] = useState('');
    const [page, setPage] = usePage();
    const people = useQuery({ meta: { errorToast: true }, queryKey: ['admin-users', q, page], queryFn: () => adminApi.users(q, page),
        placeholderData: (previous) => previous });
    const setRole = useMutation({
        mutationFn: ({ nick, role }: { nick: string; role: Person['role'] }) => adminApi.setRole(nick, role),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['admin-users'] }),
    });
    const setAutotranslate = useMutation({
        mutationFn: ({ person, allowed }: { person: Person; allowed: boolean }) => adminApi.setAutotranslate(person.nick, allowed),
        onSuccess: (_, { person, allowed }) => {
            setGranted(!allowed ? `${person.nick}: автопереклад закрито. Запуски, що вже йдуть, доробляться.`
                : (person.shahs ?? 0) > 0 ? `${person.nick}: автопереклад відкрито. Запуски — за шаги людини.`
                    : `${person.nick}: автопереклад відкрито. Запуски — за шаги людини, а їх поки немає: нарахуйте.`);
            void client.invalidateQueries({ queryKey: ['admin-users'] });
            void client.invalidateQueries({ queryKey: ['access-requests'] });
        },
    });
    const requests = useQuery({ queryKey: ['access-requests'], queryFn: adminApi.accessRequests, enabled: rank >= RANK.owner });
    const refuse = useMutation({
        mutationFn: (nick: string) => adminApi.refuseAccess(nick),
        onSuccess: (_, nick) => {
            setGranted(`${nick}: у доступі відмовлено. Людина побачить це у «Вхідних».`);
            void client.invalidateQueries({ queryKey: ['access-requests'] });
        },
    });
    // An administrator gives up to moderator; the owner gives up to administrator.
    const grantable: Person['role'][] = rank >= RANK.owner ? ['reader', 'moderator', 'admin'] : ['reader', 'moderator'];
    const [granting, setGranting] = useState<string | null>(null);
    const [granted, setGranted] = useState<string | null>(null);
    return (
        <section className={styles.page}>
            <Link to="/admin" className={styles.muted}>‹ Адміністрування</Link>
            <h1 className={styles.title}>Користувачі й ролі</h1>
            <TextInput label="Пошук" value={q} onChange={(value) => { setQ(value); setPage(1); }} placeholder={rank >= RANK.owner ? 'нік або пошта' : 'нік'} />
            {setRole.isError && <Notice tone="error">{setRole.error.message}</Notice>}
            {setAutotranslate.isError && <Notice tone="error">{setAutotranslate.error.message}</Notice>}
            {refuse.isError && <Notice tone="error">{refuse.error.message}</Notice>}
            {granted && <Notice tone="success">{granted}</Notice>}
            {(requests.data?.length ?? 0) > 0 && (
                <div role="group" aria-label="Просять доступ до автоперекладу із сайту">
                    <h2 className={styles.subtitle}>Просять доступ до автоперекладу із сайту</h2>
                    {requests.data!.map((request) => (
                        <div key={request.nick} className={styles.row}>
                            <div className={styles.grow}>
                                <Link to="/u/$nick" params={{ nick: request.nick }}>{request.nick}</Link>
                                <div className={styles.muted}>
                                    просить {relativeTime(new Date(request.requestedAt))} · {request.shahs ?? 0} {shahWord(request.shahs ?? 0)}
                                </div>
                            </div>
                            <Button variant="secondary" isDisabled={refuse.isPending || setAutotranslate.isPending}
                                onPress={() => { setGranted(null); refuse.mutate(request.nick); }}>Відмовити</Button>
                            <Button isDisabled={refuse.isPending || setAutotranslate.isPending}
                                onPress={() => {
                                    setGranted(null);
                                    setAutotranslate.mutate({ person: { nick: request.nick, shahs: request.shahs } as Person, allowed: true });
                                }}>Відкрити</Button>
                        </div>
                    ))}
                </div>
            )}
            {granting && (
                <GrantSheet nick={granting} onClose={() => setGranting(null)}
                    onDone={(message) => { setGranting(null); setGranted(message); void client.invalidateQueries({ queryKey: ['admin-users'] }); }} />
            )}
            {people.data?.items.map((person) => {
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
                            {rank >= RANK.owner && person.role !== 'owner' && (
                                <div className={styles.muted}>
                                    автопереклад {person.autotranslate ? 'відкрито' : 'закрито'}{' · '}
                                    <button type="button" className={styles.linkButton} disabled={setAutotranslate.isPending}
                                        aria-label={`${person.autotranslate ? 'Закрити' : 'Відкрити'} автопереклад ${person.nick}`}
                                        onClick={() => { setGranted(null); setAutotranslate.mutate({ person, allowed: !person.autotranslate }); }}>
                                        {person.autotranslate ? 'закрити' : 'відкрити'}
                                    </button>
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
            {people.data && <Pager page={page} total={people.data.total} size={PAGE_SIZE} onPage={setPage} />}
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
            <PeopleQualities />
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
        onSuccess: (saved) => {
            client.setQueryData(['admin-settings'], saved);
            setSiteDefault({ frame: saved.defaultFrame, theme: saved.defaultTheme });
        },
    });
    return (
        <form className={styles.form} onSubmit={(event) => { event.preventDefault(); save.mutate(); }}>
            <Toggle label="Реєстрація відкрита" isSelected={draft.registrationOpen} onChange={(registrationOpen) => setDraft({ ...draft, registrationOpen })} />
            <Toggle label="Новели 18+ на сайті" isSelected={draft.adultEnabled} onChange={(adultEnabled) => setDraft({ ...draft, adultEnabled })} />
            <p className={styles.muted}>Вимкнено — дорослих новел не бачить ніхто, навіть ті, хто підтвердив вік.</p>
            <Toggle label="Сховати ШІ для людей без шагів" isSelected={draft.hideAiWithoutShahs}
                onChange={(hideAiWithoutShahs) => setDraft({ ...draft, hideAiWithoutShahs })} />
            <p className={styles.muted}>
                Увімкнено — хто не має шагів, не бачить нічого з ШІ: «Перекласти з ШІ», автопереклад, вичитку, ШІ в словнику,
                «Намалювати сцену», вибір моделей; оригінал вставляють і перекладають самі. Ви й люди з шагами бачите все.
            </p>
            <TextInput label="Строк естафети, місяців" value={months} onChange={setMonths} inputMode="numeric"
                hint="Переклад стає вільним, якщо власник команди стільки не заходив." />
            <DefaultLook frame={draft.defaultFrame} theme={draft.defaultTheme}
                onPick={(look) => setDraft({ ...draft, defaultFrame: look.frame, defaultTheme: look.theme })} />
            {save.isError && <Notice tone="error">{save.error.message}</Notice>}
            {save.isSuccess && <Notice tone="success">Збережено.</Notice>}
            <Button type="submit" pending={save.isPending} pendingLabel="Зберігаємо…">Зберегти</Button>
        </form>
    );
}

/**
 * «Тема й каркас за замовчуванням» (#292; `AdminK`, «налаштування сайту»): what guests and those who never
 * chose see. The pair opens the same choice as «Вигляд сайту», without «Свої кольори».
 */
function DefaultLook({ frame, theme, onPick }: { frame: FrameId; theme: ThemeId; onPick: (look: { frame: FrameId; theme: ThemeId }) => void }) {
    const [open, setOpen] = useState(false);
    const { light } = useSite();
    const label = lookLabel(frame, theme);
    return (
        <div className={styles.setRow}>
            <span>Тема й каркас за замовчуванням<small>для нових відвідувачів</small></span>
            <button type="button" className={styles.setValue} onClick={() => setOpen(true)} aria-label={`Тема й каркас за замовчуванням: ${label}`}>{label}</button>
            <Sheet open={open} onClose={() => setOpen(false)} title="Тема й каркас за замовчуванням" tall>
                <div style={{ display: 'grid', gap: 18 }}>
                    <FramePicker value={frame} colors={themeVars(theme, { bg: 0, accent: '#000000' }, light)}
                        onPick={(next) => onPick({ frame: next, theme })} />
                    <ThemePicker value={theme} withOwn={false}
                        onPick={(next) => next !== 'own' && onPick({ frame, theme: next })} />
                    <Button onPress={() => setOpen(false)}>Готово</Button>
                </div>
            </Sheet>
        </div>
    );
}

/** «Пульт · Сланець». */
function lookLabel(frame: FrameId, theme: ThemeId) {
    return `${FRAMES.find((item) => item.value === frame)?.label ?? ''} · ${THEMES.find((item) => item.value === theme)?.label.replace(' · зараз', '') ?? ''}`;
}

/**
 * «Якість для людей» (етап 19): which ready set of models people get as «Найкраща», «Висока» and
 * «Швидка чернетка» when they translate with their шаги.
 */
function PeopleQualities() {
    const sets = useQuery({ queryKey: ['autotranslate-qualities'], queryFn: autotranslateApi.qualities });
    if (sets.isError) return <Notice tone="error">{sets.error.message}</Notice>;
    if (!sets.data) return null;
    return <QualitiesForm sets={sets.data} />;
}

function QualitiesForm({ sets }: { sets: PresetQuality[] }) {
    const client = useQueryClient();
    const [chosen, setChosen] = useState<Record<Quality, number | null>>(
        () => Object.fromEntries(QUALITIES.map((quality) => [quality, sets.find((set) => set.quality === quality)?.id ?? null])) as Record<Quality, number | null>);
    const save = useMutation({
        mutationFn: () => autotranslateApi.saveQualities(chosen),
        onSuccess: (saved) => client.setQueryData(['autotranslate-qualities'], saved),
    });
    return (
        <form className={styles.form} onSubmit={(event) => { event.preventDefault(); save.mutate(); }}>
            <h2 className={styles.subtitle}>Якість для людей</h2>
            <p className={styles.muted}>
                Хто перекладає за свої шаги, вибирає якість, а не моделі. Тут — який набір моделей стоїть за кожною; без набору якості в списку немає.
            </p>
            {QUALITIES.map((quality) => (
                <label key={quality}>
                    <div className={styles.muted}>{QUALITY_LABELS[quality]}</div>
                    <select className={styles.select} aria-label={QUALITY_LABELS[quality]} value={chosen[quality] ?? ''}
                        onChange={(event) => setChosen({ ...chosen, [quality]: event.target.value ? Number(event.target.value) : null })}>
                        <option value="">— не пропонувати —</option>
                        {sets.map((set) => <option key={set.id} value={set.id}>{set.name}</option>)}
                    </select>
                </label>
            ))}
            {save.isError && <Notice tone="error">{save.error.message}</Notice>}
            {save.isSuccess && <Notice tone="success">Збережено.</Notice>}
            <Button type="submit" pending={save.isPending} pendingLabel="Зберігаємо…">Зберегти якість</Button>
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
    if (entry.action === 'shahs_granted') {
        const details = entry.details as { shah?: number; nick?: string; note?: string };
        return `нараховує ${details.shah ?? 0} ${shahWord(details.shah ?? 0)} ${details.nick ?? ''}${details.note ? ` · «${details.note}»` : ''}`;
    }
    if (entry.action === 'autotranslate_granted' || entry.action === 'autotranslate_revoked') {
        const nick = (entry.details as { nick?: string }).nick ?? '';
        return `${entry.action === 'autotranslate_granted' ? 'відкриває' : 'закриває'} автопереклад ${nick}`;
    }
    if (entry.action === 'autotranslate_qualities') {
        return 'змінює якість автоперекладу для людей';
    }
    if (entry.action === 'autotranslate_refused') {
        return `відмовляє ${(entry.details as { nick?: string }).nick ?? ''} в автоперекладі із сайту`;
    }
    if (entry.action === 'settings') {
        const { before, after } = entry.details as { before?: Partial<SiteSettingsView>; after?: Partial<SiteSettingsView> };
        const ai = before?.hideAiWithoutShahs !== undefined && after?.hideAiWithoutShahs !== undefined
            && before.hideAiWithoutShahs !== after.hideAiWithoutShahs;
        const look = after?.defaultFrame && after.defaultTheme && before?.defaultFrame
            && (before.defaultFrame !== after.defaultFrame || before.defaultTheme !== after.defaultTheme);
        return `${ACTIONS.settings}${ai ? (after!.hideAiWithoutShahs ? ' · ховає ШІ від людей без шагів' : ' · показує ШІ й людям без шагів') : ''}`
            + (look ? ` · за замовчуванням ${lookLabel(after.defaultFrame!, after.defaultTheme!)}` : '');
    }
    if (entry.action === 'api_token_revoked') {
        const details = entry.details as { nick?: string; name?: string };
        return `відкликає токен API «${details.name ?? ''}» ${details.nick ?? ''}`;
    }
    if (entry.action === 'api_admin') {
        const details = entry.details as { method?: string; path?: string };
        return `через токен API: ${details.method ?? ''} ${details.path ?? ''}`;
    }
    if (entry.action === 'shah_price') {
        const micro = (entry.details as { microUsdPerShah?: number }).microUsdPerShah ?? 0;
        return `змінює ціну шагу для людей: ${dollars(micro / 1_000_000)}`;
    }
    if (entry.action === 'autotranslate_parallel') {
        const details = entry.details as { before?: number; after?: number };
        return `змінює, скільки глав перекладається одночасно: ${details.before ?? '?'} → ${details.after ?? '?'}`;
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
    const [page, setPage] = usePage();
    const pages = useQuery({ queryKey: ['admin-audit', page], queryFn: () => adminApi.audit(page), placeholderData: (previous) => previous });
    const entries = pages.data?.items ?? [];
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
            {pages.data && <Pager page={page} total={pages.data.total} size={PAGE_SIZE} onPage={setPage} />}
        </section>
    );
}
