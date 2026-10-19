import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { useEffect, useState, type ReactNode } from 'react';
import { chaptersWord } from '../../reading/api';
import { BadgeImage } from '../../achievement/BadgeImage';
import { Cover } from '../../reading/Cover';
import { NOTIFICATION_GROUPS, notificationApi, type Notification, type NotificationGroup } from '../../inbox/api';
import { relativeTime } from '../../lib/dates';
import { plural } from '../../lib/plural';
import { shahWord } from '../../studio/autotranslate';
import { askConfirm } from '../../ui/ask';
import { Button } from '../../ui/Button';
import { Archive, ArchiveRestore, Trash2 } from '../../ui/icons';
import { Loading } from '../../ui/Loading';
import { Notice } from '../../ui/Notice';
import { showError } from '../../ui/toast';
import { Pager } from '../../ui/Pager';
import { PAGE_SIZE, usePage } from '../../lib/usePage';
import { InboxNav } from './InboxNav';
import styles from './inbox.module.css';

/** The filters on the left (etap 20, `Inbox.dc.html`), in the mockup's order. */
const FILTERS: readonly { key: NotificationGroup | 'all'; label: string }[] = [
    { key: 'all', label: 'Усі' },
    { key: 'chapters', label: 'Нові глави' },
    { key: 'edits', label: 'Правки' },
    { key: 'replies', label: 'Відповіді' },
    { key: 'studio', label: 'Студія' },
    { key: 'system', label: 'Шаги й сайт' },
];

const isGroup = (value: unknown): value is NotificationGroup => NOTIFICATION_GROUPS.includes(value as NotificationGroup);

/** «Вхідні»: what happened, by kind; new rows carry a dot, the archive keeps what was put away. */
export function NotificationsPage() {
    const client = useQueryClient();
    const navigate = useNavigate();
    const search = useSearch({ strict: false }) as Record<string, unknown>;
    const archived = Boolean(search.archive);
    const group = isGroup(search.group) ? search.group : undefined;
    const [page, setPage] = usePage();
    const [selecting, setSelecting] = useState(false);
    const [selected, setSelected] = useState<Set<number>>(new Set());
    const list = useQuery({
        queryKey: ['notifications', archived ? 'archive' : 'list', group ?? 'all', page],
        queryFn: () => notificationApi.page(page, archived, group),
        placeholderData: (previous) => previous,
    });
    const items = list.data?.items ?? [];
    const newest = list.data?.newest ?? undefined;
    const unread = list.data?.unread ?? 0;
    const total = list.data?.total ?? 0;
    const groups = list.data?.groups;

    // Opening the list is seeing it: the dots stay for this visit, the badge goes away.
    useEffect(() => {
        if (!archived && newest && unread > 0) {
            void notificationApi.read(newest).then(() => client.invalidateQueries({ queryKey: ['inbox-counts'] }));
        }
    }, [archived, newest, unread, client]);

    // The last rows of a far page went away: show the page before it.
    useEffect(() => {
        if (list.isSuccess && !list.isPlaceholderData && items.length === 0 && page > 1) setPage(Math.max(1, Math.ceil(total / PAGE_SIZE)));
    }, [list.isSuccess, list.isPlaceholderData, items.length, page, total, setPage]);

    const refresh = () => {
        setSelected(new Set());
        setSelecting(false);
        void client.invalidateQueries({ queryKey: ['notifications'] });
        void client.invalidateQueries({ queryKey: ['inbox-counts'] });
    };
    const act = useMutation({
        mutationFn: ({ action, ids }: { action: 'archive' | 'restore' | 'remove'; ids?: number[] }) =>
            action === 'remove' ? notificationApi.remove(ids, archived) : notificationApi[action](ids),
        onSuccess: refresh,
        onError: (error) => showError(error.message),
    });
    // «Усе прочитано»: the dots go too, not only the badge.
    const allRead = useMutation({ mutationFn: () => notificationApi.read(), onSuccess: refresh, onError: (error) => showError(error.message) });
    const removeAll = async () => {
        const yes = await askConfirm({
            title: archived ? 'Очистити архів?' : 'Видалити всі сповіщення?',
            text: archived ? 'Усі сповіщення в архіві зникнуть назавжди.' : 'Усі сповіщення зі списку зникнуть назавжди. Архів лишиться.',
            confirmLabel: 'Видалити', danger: true,
        });
        if (yes) act.mutate({ action: 'remove' });
    };
    const removeChosen = async () => {
        const yes = await askConfirm({ title: `Видалити ${plural(selected.size, 'сповіщення', 'сповіщення', 'сповіщень')}?`,
            text: 'Їх не буде і в архіві.', confirmLabel: 'Видалити', danger: true });
        if (yes) act.mutate({ action: 'remove', ids: [...selected] });
    };
    const toggle = (id: number) => setSelected((previous) => {
        const next = new Set(previous);
        if (next.has(id)) next.delete(id);
        else next.add(id);
        return next;
    });
    const go = (next: { archive?: boolean; group?: NotificationGroup | undefined }) => {
        setSelecting(false);
        setSelected(new Set());
        const nextArchive = next.archive ?? archived;
        const nextGroup = 'group' in next ? next.group : group;
        void navigate({ to: '/inbox', search: { ...(nextArchive ? { archive: 1 } : {}), ...(nextGroup ? { group: nextGroup } : {}) } } as never);
    };
    const hasDots = !archived && items.some((item) => !item.read);
    const countOf = (key: NotificationGroup | 'all') => {
        const count = groups?.[key];
        if (!count) return '';
        if (key === 'all' && !archived && count.unread > 0) return `${count.unread} ${plural(count.unread, 'нове', 'нові', 'нових')}`;
        return count.total > 0 ? String(count.total) : '';
    };

    return (
        <section className={styles.desk}>
            <InboxNav />
            <div className={styles.split}>
                <nav className={styles.filters} aria-label="Що показувати">
                    {FILTERS.map((filter) => {
                        const on = (group ?? 'all') === filter.key;
                        return (
                            <button key={filter.key} type="button" aria-pressed={on} className={`${styles.filter} ${on ? styles.filterOn : ''}`}
                                onClick={() => go({ group: filter.key === 'all' ? undefined : filter.key })}>
                                <span>{filter.label}</span><span className={styles.filterCount}>{countOf(filter.key)}</span>
                            </button>
                        );
                    })}
                    <Link to="/me/settings/notifications" className={styles.filtersLink}>Що й куди надсилати</Link>
                </nav>

                <div className={styles.feed}>
                    <div className={styles.feedHead}>
                        <h1 className={styles.pageTitle}>{archived ? 'Архів' : 'Вхідні'}</h1>
                        <div className={styles.headButtons}>
                            {!archived && (
                                <button type="button" className={styles.softButton} disabled={!hasDots || allRead.isPending}
                                    onClick={() => allRead.mutate()}>Усе прочитано</button>
                            )}
                            <button type="button" className={styles.lineButton} aria-pressed={archived} onClick={() => go({ archive: !archived })}>
                                {archived ? 'До вхідних' : 'Архів'}
                                {!archived && list.data && list.data.archived > 0 && <span className={styles.count}>{list.data.archived}</span>}
                            </button>
                        </div>
                    </div>
                    {total > 0 && (
                        <div className={styles.toolbar}>
                            {!selecting ? (
                                <>
                                    <Button variant="quiet" onPress={() => setSelecting(true)}>Вибрати</Button>
                                    {archived
                                        ? <Button variant="quiet" onPress={() => act.mutate({ action: 'restore' })} isDisabled={act.isPending}>Повернути всі</Button>
                                        : <Button variant="quiet" onPress={() => act.mutate({ action: 'archive' })} isDisabled={act.isPending}>Усі в архів</Button>}
                                    <Button variant="quiet" onPress={() => void removeAll()} isDisabled={act.isPending}>{archived ? 'Очистити архів' : 'Видалити всі'}</Button>
                                </>
                            ) : (
                                <>
                                    <Button variant="quiet" onPress={() => setSelected(selected.size === items.length ? new Set() : new Set(items.map((item) => item.id)))}>
                                        {selected.size === items.length ? 'Зняти вибір' : 'Усі на сторінці'}
                                    </Button>
                                    <Button variant="secondary" isDisabled={selected.size === 0 || act.isPending}
                                        onPress={() => act.mutate({ action: archived ? 'restore' : 'archive', ids: [...selected] })}>
                                        {archived ? 'Повернути' : 'В архів'}{selected.size > 0 ? ` (${selected.size})` : ''}
                                    </Button>
                                    <Button variant="danger" isDisabled={selected.size === 0 || act.isPending} onPress={() => void removeChosen()}>
                                        Видалити{selected.size > 0 ? ` (${selected.size})` : ''}
                                    </Button>
                                    <Button variant="quiet" onPress={() => { setSelecting(false); setSelected(new Set()); }}>Готово</Button>
                                </>
                            )}
                        </div>
                    )}
                    {list.isError && <Notice tone="error">{list.error.message}</Notice>}
                    {list.isPending && <Loading shape="list" onRetry={() => void list.refetch()} />}
                    {list.isSuccess && total === 0 && (archived
                        ? <p className={styles.muted}>В архіві порожньо. Сюди потрапляють сповіщення, які ви прибрали зі списку, — їх можна повернути.</p>
                        : group
                            ? <p className={styles.muted}>Тут поки нічого. Решта — у «Усі».</p>
                            : <p className={styles.muted}>Поки тихо. Тут зʼявляться відповіді, згадки, нові глави з вашої бібліотеки, правки до ваших перекладів і рішення щодо ваших правок.</p>)}
                    {items.length > 0 && (
                        <div className={styles.panel}>
                            {items.map((item) => (
                                <div key={item.id} className={`${styles.note} ${item.read || archived ? '' : styles.noteUnread}`}
                                    // While choosing, a press anywhere on the row picks it instead of opening it.
                                    onClickCapture={selecting ? (event) => {
                                        if ((event.target as HTMLElement).closest('input')) return;
                                        event.preventDefault();
                                        event.stopPropagation();
                                        toggle(item.id);
                                    } : undefined}>
                                    {selecting && (
                                        <input type="checkbox" className={styles.check} checked={selected.has(item.id)} onChange={() => toggle(item.id)}
                                            aria-label="Вибрати сповіщення" />
                                    )}
                                    <Row item={item} unread={!item.read && !archived} />
                                    {!selecting && (
                                        <div className={styles.rowActions}>
                                            {archived ? (
                                                <button type="button" className={styles.iconButton} aria-label="Повернути до списку" title="Повернути"
                                                    onClick={() => act.mutate({ action: 'restore', ids: [item.id] })}><ArchiveRestore size={18} aria-hidden /></button>
                                            ) : (
                                                <button type="button" className={styles.iconButton} aria-label="В архів" title="В архів"
                                                    onClick={() => act.mutate({ action: 'archive', ids: [item.id] })}><Archive size={18} aria-hidden /></button>
                                            )}
                                            <button type="button" className={styles.iconButton} aria-label="Видалити" title="Видалити"
                                                onClick={() => act.mutate({ action: 'remove', ids: [item.id] })}><Trash2 size={18} aria-hidden /></button>
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
                    {list.data && <Pager page={page} total={total} size={PAGE_SIZE} onPage={setPage} />}
                </div>
            </div>
        </section>
    );
}

/** The kind's square on the left: its letter on its colour, as in the mockup. */
type Tone = 'chapters' | 'edits' | 'replies' | 'studio' | 'system';

function toneOf(kind: Notification['kind']): Tone {
    switch (kind) {
        case 'new_chapters':
            return 'chapters';
        case 'suggestions_submitted':
        case 'suggestions_reviewed':
            return 'edits';
        case 'reply':
        case 'mention':
        case 'team_mention':
            return 'replies';
        case 'shahs_granted':
        case 'achievement':
        case 'report':
            return 'system';
        default:
            return 'studio';
    }
}

const TONES: Record<Tone, string> = {
    chapters: styles.toneChapters!, edits: styles.toneEdits!, replies: styles.toneReplies!, studio: styles.toneStudio!, system: styles.toneSystem!,
};

function Row({ item, unread }: { item: Notification; unread: boolean }) {
    const p = item.payload;
    const where = p.novelTitle ? `${p.novelTitle}${p.chapterLabel !== undefined ? ` · глава ${p.chapterLabel || p.chapterNumber}` : ''}` : '';
    let title: string;
    let excerpt: string | undefined = p.excerpt;
    let chapters: string | undefined;
    let letter = '•';
    switch (item.kind) {
        case 'reply':
            title = `${p.actorNick} відповідає на ваш коментар`;
            letter = '@';
            break;
        case 'mention':
            title = p.where === 'chat' ? `${p.actorNick} згадує вас у чаті` : `${p.actorNick} згадує вас`;
            letter = '@';
            break;
        case 'team_mention':
            title = `${p.actorNick} згадує команду $${p.teamHandle}`;
            letter = '$';
            break;
        case 'new_chapters': {
            const count = (p.last ?? 0) - (p.first ?? 0) + 1;
            const first = p.firstLabel ?? String(p.first ?? '');
            const last = p.lastLabel ?? String(p.last ?? '');
            title = p.novelTitle ?? 'Нові глави';
            excerpt = undefined;
            chapters = count > 1
                ? `Нові глави ${first}–${last} · ${count} ${chaptersWord(count)}`
                : `Нова глава ${first}${p.chapterTitle ? `. ${p.chapterTitle}` : ''}`;
            break;
        }
        case 'source_chapters': {
            const count = (p.last ?? 0) - (p.first ?? 0) + 1;
            title = p.novelTitle ?? 'Оригінал';
            excerpt = undefined;
            letter = 'О';
            chapters = count > 1
                ? `В оригіналі нові глави ${p.first}–${p.last} · ${count} ${chaptersWord(count)} — можна перекладати`
                : `В оригіналі нова глава ${p.first} — можна перекладати`;
            break;
        }
        case 'suggestions_submitted':
            // Batches from different people add up, so only a single one names its author.
            title = (p.count ?? 1) === 1 ? `${p.actorNick} пропонує правку` : plural(p.count ?? 0, 'нова правка', 'нові правки', 'нових правок');
            letter = 'П';
            break;
        case 'shahs_granted':
            title = `Вам нараховано ${p.shah ?? 0} ${shahWord(p.shah ?? 0)}`;
            excerpt = p.note;
            letter = 'Ш';
            break;
        case 'takeover_request':
            title = `${p.actorNick ?? ''} хоче продовжити ваш переклад «${p.title ?? ''}»`;
            excerpt = p.excerpt ? `«${p.excerpt}»` : `Команда ${p.teamName ?? ''}. Відповісти можна в Студії.`;
            letter = 'С';
            break;
        case 'takeover_answered':
            title = p.granted ? `Вам дозволили продовжити «${p.title ?? ''}»` : `Власник поки не віддає «${p.title ?? ''}»`;
            excerpt = p.granted ? 'На сторінці новели тепер є кнопка «Продовжити переклад».' : undefined;
            letter = 'С';
            break;
        case 'autotranslate_request':
            title = `${p.actorNick ?? ''} просить доступ до автоперекладу із сайту`;
            excerpt = undefined;
            chapters = 'Відкрити чи відмовити — у «Користувачах і ролях».';
            letter = 'С';
            break;
        case 'autotranslate_answered':
            title = p.granted ? 'Вам відкрито автопереклад із сайту' : 'Власник сайту поки не відкриває вам автопереклад із сайту';
            chapters = p.granted ? 'Тепер у «Новому перекладі» можна вставити посилання на новелу.' : undefined;
            letter = 'С';
            break;
        case 'achievement':
            title = `Нове досягнення: «${p.title ?? ''}»`;
            chapters = p.description || undefined;
            break;
        case 'proposal_taken':
            title = `«${p.title ?? ''}» взяли перекладати`;
            excerpt = `Новела, за яку ви голосували. Перекладає $${p.teamHandle ?? ''}.`;
            letter = 'С';
            break;
        case 'suggestions_reviewed':
            title = `Ваші правки перевірено: прийнято ${p.accepted}, відхилено ${p.rejected}`;
            letter = 'П';
            break;
        case 'report':
            title = `Нова скарга: ${p.title ?? ''}`;
            letter = '!';
            break;
        default:
            title = 'Сповіщення';
    }
    const chapterRow = item.kind === 'new_chapters' || item.kind === 'source_chapters';
    const quoted = excerpt ? `«${excerpt}»` : undefined;
    let tile: ReactNode;
    if (chapterRow && (p.coverUrl || p.slug)) {
        tile = <Cover url={p.coverUrl ?? null} title={p.novelTitle ?? ''} seed={p.slug ?? String(p.editionId)} width={40} />;
    } else if (item.kind === 'achievement' && p.code) {
        tile = <BadgeImage code={p.code} size={40} />;
    } else {
        tile = <span className={`${styles.tile} ${TONES[toneOf(item.kind)]}`} aria-hidden>
            {item.kind === 'new_chapters' ? (p.novelTitle ?? 'Г').slice(0, 1).toUpperCase() : letter}
        </span>;
    }
    const body = (
        <>
            <span className={styles.dot} aria-hidden data-on={unread || undefined} />
            <span className={styles.tileBox}>{tile}</span>
            <span className={styles.noteText}>
                <span className={styles.noteTitle}>{title}{unread && <span className="visually-hidden"> (нове)</span>}</span>
                {chapters && <span className={styles.noteLine}>{chapters}</span>}
                {(quoted || (!chapterRow && where)) && (
                    <span className={styles.noteLine}>{[quoted, chapterRow ? '' : where].filter(Boolean).join(' · ')}</span>
                )}
            </span>
            <span className={styles.when}>{relativeTime(new Date(item.createdAt))}</span>
        </>
    );
    const className = styles.target;
    if (item.kind === 'takeover_request' && p.editionId) {
        return <Link to="/studio/$editionId/relay" params={{ editionId: String(p.editionId) }} className={className}>{body}</Link>;
    }
    if (item.kind === 'source_chapters' && p.editionId) {
        return <Link to="/studio/$editionId/translate" params={{ editionId: String(p.editionId) }} className={className}>{body}</Link>;
    }
    if (item.kind === 'achievement' && p.nick) {
        return <Link to="/u/$nick" params={{ nick: p.nick }} hash="achievements" className={className}>{body}</Link>;
    }
    if (item.kind === 'autotranslate_request') {
        return <Link to="/admin/users" className={className}>{body}</Link>;
    }
    if (item.kind === 'autotranslate_answered' && p.granted) {
        return <Link to="/studio/new" className={className}>{body}</Link>;
    }
    if (item.kind === 'report') {
        return <Link to="/admin/moderation" className={className}>{body}</Link>;
    }
    if (item.kind === 'shahs_granted') {
        return <Link to="/me/shahs" className={className}>{body}</Link>;
    }
    if (item.kind === 'suggestions_submitted' && p.editionId) {
        return <Link to="/studio/$editionId" params={{ editionId: String(p.editionId) }} className={className}>{body}</Link>;
    }
    if (p.where === 'chat') {
        return <Link to="/inbox/chat" search={p.channel ? { channel: p.channel } : {}} className={className}>{body}</Link>;
    }
    if (p.slug && item.kind === 'new_chapters' && p.first) {
        return (
            <Link to="/n/$slug/$number" params={{ slug: p.slug, number: String(p.first) }} search={{ t: p.teamHandle }} className={className}>{body}</Link>
        );
    }
    if (p.slug && p.chapterNumber) {
        return (
            <Link to="/n/$slug/$number" params={{ slug: p.slug, number: String(p.chapterNumber) }} search={{ t: p.teamHandle, look: true }}
                {...(p.commentId ? { hash: `c${p.commentId}` } : {})} className={className}>{body}</Link>
        );
    }
    if (p.slug) {
        return <Link to="/n/$slug" params={{ slug: p.slug }} search={{ t: p.teamHandle }} {...(p.commentId ? { hash: `c${p.commentId}` } : {})} className={className}>{body}</Link>;
    }
    return <div className={className}>{body}</div>;
}
