import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { useEffect, useState } from 'react';
import { chaptersWord } from '../../reading/api';
import { BadgeImage } from '../../achievement/BadgeImage';
import { Cover } from '../../reading/Cover';
import { notificationApi, type Notification } from '../../inbox/api';
import { relativeTime } from '../../lib/dates';
import { plural } from '../../lib/plural';
import { shahWord } from '../../studio/autotranslate';
import { askConfirm } from '../../ui/ask';
import { Button } from '../../ui/Button';
import { Archive, ArchiveRestore, Trash2 } from '../../ui/icons';
import { Notice } from '../../ui/Notice';
import { showError } from '../../ui/toast';
import { Pager } from '../../ui/Pager';
import { PAGE_SIZE, usePage } from '../../lib/usePage';
import { InboxNav } from './InboxNav';
import styles from './inbox.module.css';

export function NotificationsPage() {
    const client = useQueryClient();
    const navigate = useNavigate();
    const search = useSearch({ strict: false }) as Record<string, unknown>;
    const archived = Boolean(search.archive);
    const [page, setPage] = usePage();
    const [selecting, setSelecting] = useState(false);
    const [selected, setSelected] = useState<Set<number>>(new Set());
    const list = useQuery({
        queryKey: ['notifications', archived ? 'archive' : 'list', page],
        queryFn: () => notificationApi.page(page, archived),
        placeholderData: (previous) => previous,
    });
    const items = list.data?.items ?? [];
    const newest = list.data?.newest ?? undefined;
    const unread = list.data?.unread ?? 0;
    const total = list.data?.total ?? 0;

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

    const act = useMutation({
        mutationFn: ({ action, ids }: { action: 'archive' | 'restore' | 'remove'; ids?: number[] }) =>
            action === 'remove' ? notificationApi.remove(ids, archived) : notificationApi[action](ids),
        onSuccess: () => {
            setSelected(new Set());
            setSelecting(false);
            void client.invalidateQueries({ queryKey: ['notifications'] });
            void client.invalidateQueries({ queryKey: ['inbox-counts'] });
        },
        onError: (error) => showError(error.message),
    });
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
    const shelf = (archive: boolean) => {
        setSelecting(false);
        setSelected(new Set());
        void navigate({ to: '/inbox', search: archive ? { archive: 1 } : {} } as never);
    };

    return (
        <section className={styles.page}>
            <InboxNav />
            <div className={styles.shelves} role="tablist" aria-label="Сповіщення">
                <button type="button" role="tab" aria-selected={!archived} className={!archived ? styles.shelfActive : styles.shelf}
                    onClick={() => shelf(false)}>Нові й прочитані</button>
                <button type="button" role="tab" aria-selected={archived} className={archived ? styles.shelfActive : styles.shelf}
                    onClick={() => shelf(true)}>
                    Архів{list.data && list.data.archived > 0 && <span className={styles.count}>{list.data.archived}</span>}
                </button>
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
            {list.isSuccess && total === 0 && (archived
                ? <p className={styles.muted}>В архіві порожньо. Сюди потрапляють сповіщення, які ви прибрали зі списку, — їх можна повернути.</p>
                : <p className={styles.muted}>Поки тихо. Тут зʼявляться відповіді, згадки, нові глави з вашої бібліотеки, правки до ваших перекладів і рішення щодо ваших правок.</p>)}
            {items.map((item) => (
                <div key={item.id} className={`${styles.item} ${item.read || archived ? '' : styles.unread}`}
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
                    <Row item={item} />
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
            {list.data && <Pager page={page} total={total} size={PAGE_SIZE} onPage={setPage} />}
        </section>
    );
}

function Row({ item }: { item: Notification }) {
    const p = item.payload;
    const where = p.novelTitle ? `${p.novelTitle}${p.chapterLabel !== undefined ? ` · глава ${p.chapterLabel || p.chapterNumber}` : ''}` : '';
    let title: string;
    let excerpt: string | undefined = p.excerpt;
    let chapters: string | undefined;
    switch (item.kind) {
        case 'reply':
            title = `${p.actorNick} відповідає на ваш коментар`;
            break;
        case 'mention':
            title = p.where === 'chat' ? `${p.actorNick} згадує вас у чаті` : `${p.actorNick} згадує вас`;
            break;
        case 'team_mention':
            title = `${p.actorNick} згадує команду $${p.teamHandle}`;
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
            chapters = count > 1
                ? `В оригіналі нові глави ${p.first}–${p.last} · ${count} ${chaptersWord(count)} — можна перекладати`
                : `В оригіналі нова глава ${p.first} — можна перекладати`;
            break;
        }
        case 'suggestions_submitted':
            // Batches from different people add up, so only a single one names its author.
            title = (p.count ?? 1) === 1 ? `${p.actorNick} пропонує правку` : plural(p.count ?? 0, 'нова правка', 'нові правки', 'нових правок');
            break;
        case 'shahs_granted':
            title = `Вам нараховано ${p.shah ?? 0} ${shahWord(p.shah ?? 0)}`;
            excerpt = p.note;
            break;
        case 'takeover_request':
            title = `${p.actorNick ?? ''} хоче продовжити ваш переклад «${p.title ?? ''}»`;
            excerpt = p.excerpt ? `«${p.excerpt}»` : `Команда ${p.teamName ?? ''}. Відповісти можна в Студії.`;
            break;
        case 'takeover_answered':
            title = p.granted ? `Вам дозволили продовжити «${p.title ?? ''}»` : `Власник поки не віддає «${p.title ?? ''}»`;
            excerpt = p.granted ? 'На сторінці новели тепер є кнопка «Продовжити переклад».' : undefined;
            break;
        case 'autotranslate_request':
            title = `${p.actorNick ?? ''} просить доступ до автоперекладу із сайту`;
            excerpt = undefined;
            chapters = 'Відкрити чи відмовити — у «Користувачах і ролях».';
            break;
        case 'autotranslate_answered':
            title = p.granted ? 'Вам відкрито автопереклад із сайту' : 'Власник сайту поки не відкриває вам автопереклад із сайту';
            chapters = p.granted ? 'Тепер у «Новому перекладі» можна вставити посилання на новелу.' : undefined;
            break;
        case 'achievement':
            title = `Нове досягнення: «${p.title ?? ''}»`;
            chapters = p.description || undefined;
            break;
        case 'proposal_taken':
            title = `«${p.title ?? ''}» взяли перекладати`;
            excerpt = `Новела, за яку ви голосували. Перекладає $${p.teamHandle ?? ''}.`;
            break;
        case 'suggestions_reviewed':
            title = `Ваші правки перевірено: прийнято ${p.accepted}, відхилено ${p.rejected}`;
            break;
        case 'report':
            title = `Нова скарга: ${p.title ?? ''}`;
            break;
        default:
            title = 'Сповіщення';
    }
    const body = (
        <div className={styles.grow}>
            <div className={item.kind === 'new_chapters' || item.kind === 'source_chapters' ? styles.strong : undefined}>{title}</div>
            {chapters && <div>{chapters}</div>}
            {excerpt && <div className={`${styles.line} ${styles.muted}`}>«{excerpt}»</div>}
            <div className={styles.muted}>{item.kind === 'new_chapters' || item.kind === 'source_chapters' ? '' : where && `${where} · `}{relativeTime(new Date(item.createdAt))}</div>
        </div>
    );
    const className = styles.target;
    if (item.kind === 'takeover_request' && p.editionId) {
        return <Link to="/studio/$editionId/relay" params={{ editionId: String(p.editionId) }} className={className}>{body}</Link>;
    }
    if (item.kind === 'source_chapters' && p.editionId) {
        return (
            <Link to="/studio/$editionId/translate" params={{ editionId: String(p.editionId) }} className={className}>
                <Cover url={p.coverUrl ?? null} title={p.novelTitle ?? ''} seed={p.slug ?? String(p.editionId)} width={40} />
                {body}
            </Link>
        );
    }
    if (item.kind === 'achievement' && p.nick) {
        return (
            <Link to="/u/$nick" params={{ nick: p.nick }} hash="achievements" className={className}>
                {p.code && <BadgeImage code={p.code} size={40} />}
                {body}
            </Link>
        );
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
        return <Link to="/inbox/chat" className={className}>{body}</Link>;
    }
    if (p.slug && item.kind === 'new_chapters' && p.first) {
        return (
            <Link to="/n/$slug/$number" params={{ slug: p.slug, number: String(p.first) }} search={{ t: p.teamHandle }} className={className}>
                <Cover url={p.coverUrl ?? null} title={p.novelTitle ?? ''} seed={p.slug} width={40} />
                {body}
            </Link>
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
