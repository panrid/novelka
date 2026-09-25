import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useMe, type Me } from '../auth/me';
import { messagingApi } from '../inbox/api';
import { relativeTime } from '../lib/dates';
import { Avatar } from '../ui/Avatar';
import { Segmented } from '../ui/Segmented';
import { commentApi, type Comment } from './api';
import { Composer, type ReplyTarget } from './Composer';
import { Markup } from './Markup';
import styles from './discussion.module.css';
import { askText } from '../ui/ask';

const REPORT = { title: 'Поскаржитися на коментар', label: 'Що не так?', hint: 'Причину побачать лише модератори.', confirmLabel: 'Надіслати скаргу' };

const MODERATORS = new Set(['moderator', 'admin', 'owner']);

export const threadKey = (editionId: number, chapter?: number) => ['comments', editionId, chapter ?? null];

/** How many comments there are, for the button that opens the discussion. */
export function useCommentCount(editionId: number, chapter?: number) {
    return useQuery({
        queryKey: [...threadKey(editionId, chapter), 'count'],
        queryFn: async () => (await commentApi.thread(editionId, chapter, 'new')).total,
        staleTime: 60_000,
    }).data ?? 0;
}

/**
 * Talk about a translation or one of its chapters: newest or best first, one level of
 * replies, votes. {@code focus} scrolls to one comment (a link from the inbox).
 */
export function Discussion({ editionId, chapter, focus }: { editionId: number; chapter?: number; focus?: number | undefined }) {
    const me = useMe();
    const client = useQueryClient();
    const [sort, setSort] = useState<'new' | 'top'>('new');
    const [reply, setReply] = useState<(ReplyTarget & { root: number }) | null>(null);
    const thread = useInfiniteQuery({
        queryKey: [...threadKey(editionId, chapter), sort],
        queryFn: ({ pageParam }) => commentApi.thread(editionId, chapter, sort, pageParam),
        initialPageParam: 1,
        getNextPageParam: (last) => (last.hasMore ? last.page + 1 : undefined),
    });
    const items = thread.data?.pages.flatMap((page) => page.items) ?? [];
    const refresh = () => client.invalidateQueries({ queryKey: threadKey(editionId, chapter) });

    useEffect(() => {
        if (focus && items.length > 0) document.getElementById(`c${focus}`)?.scrollIntoView?.({ block: 'center' });
    }, [focus, items.length]);

    return (
        <div className={styles.discussion}>
            <Segmented label="Порядок" value={sort} onChange={setSort}
                options={[{ value: 'new', label: 'Нові' }, { value: 'top', label: 'Кращі' }]} />
            {me ? (
                <Composer label={chapter ? 'Коментар до глави' : 'Коментар до перекладу'} reply={reply} onCancelReply={() => setReply(null)}
                    onSend={async (text) => {
                        await commentApi.post(editionId, chapter, reply ? `@${reply.who} ${text}` : text, reply?.root ?? null);
                        setReply(null);
                        await refresh();
                    }} />
            ) : (
                <p className={styles.muted}>
                    <Link to="/login" search={{ next: typeof window === 'undefined' ? '/' : window.location.pathname }}>Увійдіть</Link>, щоб коментувати.
                </p>
            )}
            {thread.isSuccess && items.length === 0 && <p className={styles.muted}>Ще ніхто не писав. Будьте першим!</p>}
            {items.map((comment) => (
                <div key={comment.id}>
                    <Item comment={comment} focus={focus} me={me} onChanged={refresh}
                        onReply={() => setReply({ id: comment.id, root: comment.id, who: comment.authorNick ?? '', excerpt: comment.body.slice(0, 80) })} />
                    <div className={styles.replies}>
                        {comment.replies.map((answer) => (
                            <Item key={answer.id} comment={answer} focus={focus} me={me} onChanged={refresh}
                                onReply={() => setReply({ id: answer.id, root: comment.id, who: answer.authorNick ?? '', excerpt: answer.body.slice(0, 80) })} />
                        ))}
                    </div>
                </div>
            ))}
            {thread.hasNextPage && (
                <button type="button" className={styles.more} onClick={() => void thread.fetchNextPage()}>Показати ще</button>
            )}
        </div>
    );
}

function Item({ comment, onReply, onChanged, focus: focused, me }: {
    comment: Comment; onReply: () => void; onChanged: () => void; focus: number | undefined; me: Me | null;
}) {
    const [editing, setEditing] = useState(false);
    const [note, setNote] = useState<string | null>(null);
    if (comment.removed) {
        return <div id={`c${comment.id}`} className={styles.removed}>{comment.removed === 'hidden' ? 'Коментар приховано модератором.' : 'Коментар видалено.'}</div>;
    }
    const act = (action: Promise<unknown>) => void action.then(onChanged, (error: Error) => setNote(error.message));
    return (
        <article id={`c${comment.id}`} className={`${styles.comment} ${focused === comment.id ? styles.focused : ''}`}>
            <Avatar nick={comment.authorNick!} url={comment.authorAvatarUrl} size={32} />
            <div className={styles.body}>
                <div className={styles.meta}>
                    <Link to="/u/$nick" params={{ nick: comment.authorNick! }} className={styles.author}>{comment.authorNick}</Link>
                    {' · '}{relativeTime(new Date(comment.createdAt))}{comment.editedAt ? ' · змінено' : ''}
                </div>
                {editing ? (
                    <Composer label="Змінити коментар" initial={comment.body} submitLabel="Зберегти"
                        onSend={async (text) => { await commentApi.edit(comment.id, text); setEditing(false); onChanged(); }} />
                ) : <Markup text={comment.body} />}
                <div className={styles.actions}>
                    <span className={styles.votes}>
                        <button type="button" aria-label="Подобається" aria-pressed={comment.myVote === 1} disabled={!me || comment.mine}
                            onClick={() => act(commentApi.vote(comment.id, comment.myVote === 1 ? 0 : 1))}><ChevronUp size={18} aria-hidden /></button>
                        <span aria-label="Оцінка">{comment.score}</span>
                        <button type="button" aria-label="Не подобається" aria-pressed={comment.myVote === -1} disabled={!me || comment.mine}
                            onClick={() => act(commentApi.vote(comment.id, comment.myVote === -1 ? 0 : -1))}><ChevronDown size={18} aria-hidden /></button>
                    </span>
                    {me && <button type="button" onClick={onReply}>Відповісти</button>}
                    {comment.mine && <button type="button" onClick={() => setEditing(!editing)}>{editing ? 'Скасувати' : 'Змінити'}</button>}
                    {me && (comment.mine || MODERATORS.has(me.role)) && (
                        <button type="button" onClick={() => act(commentApi.remove(comment.id))}>{comment.mine ? 'Видалити' : 'Приховати'}</button>
                    )}
                    {me && !comment.mine && (
                        <button type="button" onClick={() => void askText(REPORT).then((reason) => {
                            if (reason) void messagingApi.report('comment', comment.id, reason).then(() => setNote('Скаргу надіслано.'), (e: Error) => setNote(e.message));
                        })}>Поскаржитися</button>
                    )}
                </div>
                {note && <div className={styles.muted}>{note}</div>}
            </div>
        </article>
    );
}
