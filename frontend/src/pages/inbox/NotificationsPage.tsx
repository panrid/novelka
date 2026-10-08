import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useEffect } from 'react';
import { chaptersWord } from '../../reading/api';
import { Cover } from '../../reading/Cover';
import { notificationApi, type Notification } from '../../inbox/api';
import { relativeTime } from '../../lib/dates';
import { plural } from '../../lib/plural';
import { shahWord } from '../../studio/autotranslate';
import { Notice } from '../../ui/Notice';
import { Pager } from '../../ui/Pager';
import { PAGE_SIZE, usePage } from '../../lib/usePage';
import { InboxNav } from './InboxNav';
import styles from './inbox.module.css';

export function NotificationsPage() {
    const client = useQueryClient();
    const [page, setPage] = usePage();
    const list = useQuery({
        queryKey: ['notifications', page],
        queryFn: () => notificationApi.page(page),
        placeholderData: (previous) => previous,
    });
    const items = list.data?.items ?? [];
    const newest = list.data?.newest ?? undefined;
    const unread = list.data?.unread ?? 0;

    // Opening the list is seeing it: the dots stay for this visit, the badge goes away.
    useEffect(() => {
        if (newest && unread > 0) {
            void notificationApi.read(newest).then(() => client.invalidateQueries({ queryKey: ['inbox-counts'] }));
        }
    }, [newest, unread, client]);

    return (
        <section className={styles.page}>
            <InboxNav />
            {list.isError && <Notice tone="error">{list.error.message}</Notice>}
            {list.isSuccess && list.data.total === 0 && (
                <p className={styles.muted}>Поки тихо. Тут зʼявляться відповіді, згадки, нові глави з вашої бібліотеки, правки до ваших перекладів і рішення щодо ваших правок.</p>
            )}
            {items.map((item) => <Row key={item.id} item={item} />)}
            {list.data && <Pager page={page} total={list.data.total} size={PAGE_SIZE} onPage={setPage} />}
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
        case 'achievement':
            title = `Нове досягнення: «${p.title ?? ''}»`;
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
            <div className={item.kind === 'new_chapters' ? styles.strong : undefined}>{title}</div>
            {chapters && <div className={styles.line}>{chapters}</div>}
            {excerpt && <div className={`${styles.line} ${styles.muted}`}>«{excerpt}»</div>}
            <div className={styles.muted}>{item.kind === 'new_chapters' ? '' : where && `${where} · `}{relativeTime(new Date(item.createdAt))}</div>
        </div>
    );
    const className = `${styles.item} ${item.read ? '' : styles.unread}`;
    if (item.kind === 'takeover_request' && p.editionId) {
        return <Link to="/studio/$editionId/relay" params={{ editionId: String(p.editionId) }} className={className}>{body}</Link>;
    }
    if (item.kind === 'achievement' && p.nick) {
        return <Link to="/u/$nick" params={{ nick: p.nick }} hash="achievements" className={className}>{body}</Link>;
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
