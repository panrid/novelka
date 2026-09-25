import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useEffect } from 'react';
import { chaptersWord } from '../../reading/api';
import { notificationApi, type Notification } from '../../inbox/api';
import { relativeTime } from '../../lib/dates';
import { plural } from '../../lib/plural';
import { Notice } from '../../ui/Notice';
import { InboxNav } from './InboxNav';
import styles from './inbox.module.css';

export function NotificationsPage() {
    const client = useQueryClient();
    const pages = useInfiniteQuery({
        queryKey: ['notifications'],
        queryFn: ({ pageParam }) => notificationApi.page(pageParam),
        initialPageParam: undefined as number | undefined,
        getNextPageParam: (last) => (last.hasMore ? last.items[last.items.length - 1]?.id : undefined),
    });
    const items = pages.data?.pages.flatMap((page) => page.items) ?? [];
    const newest = items[0]?.id;
    const unread = pages.data?.pages[0]?.unread ?? 0;

    // Opening the list is seeing it: the dots stay for this visit, the badge goes away.
    useEffect(() => {
        if (newest && unread > 0) {
            void notificationApi.read(newest).then(() => client.invalidateQueries({ queryKey: ['inbox-counts'] }));
        }
    }, [newest, unread, client]);

    return (
        <section className={styles.page}>
            <InboxNav />
            {pages.isError && <Notice tone="error">{pages.error.message}</Notice>}
            {pages.isSuccess && items.length === 0 && (
                <p className={styles.muted}>Поки тихо. Тут зʼявляться відповіді, згадки, нові глави з вашої бібліотеки, правки до ваших перекладів і рішення щодо ваших правок.</p>
            )}
            {items.map((item) => <Row key={item.id} item={item} />)}
            {pages.hasNextPage && (
                <button type="button" className={styles.older} onClick={() => void pages.fetchNextPage()}>Показати давніші</button>
            )}
        </section>
    );
}

function Row({ item }: { item: Notification }) {
    const p = item.payload;
    const where = p.novelTitle ? `${p.novelTitle}${p.chapterLabel !== undefined ? ` · глава ${p.chapterLabel || p.chapterNumber}` : ''}` : '';
    let title: string;
    let excerpt: string | undefined = p.excerpt;
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
            title = count > 1 ? `${p.novelTitle}: ${count} ${chaptersWord(count)} нових` : `${p.novelTitle}: нова глава`;
            excerpt = undefined;
            break;
        }
        case 'suggestions_submitted':
            // Batches from different people add up, so only a single one names its author.
            title = (p.count ?? 1) === 1 ? `${p.actorNick} пропонує правку` : `${p.count} ${plural(p.count ?? 0, 'нова правка', 'нові правки', 'нових правок')}`;
            break;
        case 'suggestions_reviewed':
            title = `Ваші правки перевірено: прийнято ${p.accepted}, відхилено ${p.rejected}`;
            break;
        default:
            title = 'Сповіщення';
    }
    const body = (
        <div className={styles.grow}>
            <div>{title}</div>
            {excerpt && <div className={`${styles.line} ${styles.muted}`}>«{excerpt}»</div>}
            <div className={styles.muted}>{item.kind === 'new_chapters' ? '' : where && `${where} · `}{relativeTime(new Date(item.createdAt))}</div>
        </div>
    );
    const className = `${styles.item} ${item.read ? '' : styles.unread}`;
    if (item.kind === 'suggestions_submitted' && p.editionId) {
        return <Link to="/studio/$editionId" params={{ editionId: String(p.editionId) }} className={className}>{body}</Link>;
    }
    if (p.where === 'chat') {
        return <Link to="/inbox/chat" className={className}>{body}</Link>;
    }
    if (p.slug && item.kind === 'new_chapters' && p.first) {
        return <Link to="/n/$slug/$number" params={{ slug: p.slug, number: String(p.first) }} search={{ t: p.teamHandle }} className={className}>{body}</Link>;
    }
    if (p.slug && p.chapterNumber) {
        return (
            <Link to="/n/$slug/$number" params={{ slug: p.slug, number: String(p.chapterNumber) }} search={{ t: p.teamHandle }}
                {...(p.commentId ? { hash: `c${p.commentId}` } : {})} className={className}>{body}</Link>
        );
    }
    if (p.slug) {
        return <Link to="/n/$slug" params={{ slug: p.slug }} search={{ t: p.teamHandle }} {...(p.commentId ? { hash: `c${p.commentId}` } : {})} className={className}>{body}</Link>;
    }
    return <div className={className}>{body}</div>;
}
