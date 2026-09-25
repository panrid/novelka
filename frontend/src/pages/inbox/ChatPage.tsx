import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useLayoutEffect, useRef, useState } from 'react';
import { useMe } from '../../auth/me';
import { Composer, type ReplyTarget } from '../../community/Composer';
import { Markup } from '../../community/Markup';
import { chatApi, messagingApi } from '../../inbox/api';
import { Avatar } from '../../ui/Avatar';
import { Notice } from '../../ui/Notice';
import { InboxNav } from './InboxNav';
import styles from './inbox.module.css';

const MODERATORS = new Set(['moderator', 'admin', 'owner']);

/** The site-wide chat. Guests read it; to write, sign in. */
export function ChatPage() {
    const me = useMe();
    const client = useQueryClient();
    const pages = useInfiniteQuery({
        queryKey: ['chat'],
        queryFn: ({ pageParam }) => chatApi.lines(pageParam),
        initialPageParam: undefined as number | undefined,
        getNextPageParam: (last) => (last.length === 50 ? last[0]?.id : undefined),
        // Guests have no live stream: look for new lines now and then.
        refetchInterval: me ? false : 30_000,
    });
    const [reply, setReply] = useState<ReplyTarget | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const end = useRef<HTMLDivElement>(null);
    const lines = (pages.data?.pages ?? []).slice().reverse().flat();
    const newest = lines[lines.length - 1]?.id;
    useLayoutEffect(() => {
        // Braces matter: newer browsers return a promise from scrollIntoView, and React must get nothing back.
        end.current?.scrollIntoView?.({ block: 'end' });
    }, [newest]);
    const refresh = () => client.invalidateQueries({ queryKey: ['chat'] });

    return (
        <section className={styles.page}>
            <InboxNav />
            {pages.isError && <Notice tone="error">{pages.error.message}</Notice>}
            {notice && <Notice tone="info">{notice}</Notice>}
            <div className={styles.lines}>
                {pages.hasNextPage && <button type="button" className={styles.older} onClick={() => void pages.fetchNextPage()}>Давніші</button>}
                {pages.isSuccess && lines.length === 0 && <p className={styles.muted}>У чаті ще тихо. Напишіть першим!</p>}
                {lines.map((line) => (
                    <div key={line.id} className={`${styles.bubbleRow} ${line.mine ? styles.mineRow : ''}`}>
                        {!line.mine && <Avatar nick={line.authorNick} url={line.authorAvatarUrl} size={28} />}
                        <div className={`${styles.bubble} ${line.mine ? styles.mine : ''}`}>
                            {!line.mine && <Link to="/u/$nick" params={{ nick: line.authorNick }} className={styles.author}>{line.authorNick}</Link>}
                            {line.replyExcerpt && <div className={styles.quoted}>{line.replyExcerpt}</div>}
                            <Markup text={line.body} />
                            <div className={styles.meta}>{new Date(line.createdAt).toLocaleTimeString('uk-UA', { hour: '2-digit', minute: '2-digit' })}</div>
                            {me && (
                                <div className={styles.actions}>
                                    <button type="button" onClick={() => setReply({ id: line.id, who: line.authorNick, excerpt: line.body.slice(0, 80) })}>Відповісти</button>
                                    {(line.mine || MODERATORS.has(me.role)) && (
                                        <button type="button" onClick={() => void chatApi.remove(line.id).then(refresh, (e: Error) => setNotice(e.message))}>
                                            {line.mine ? 'Видалити' : 'Приховати'}
                                        </button>
                                    )}
                                    {!line.mine && (
                                        <button type="button" onClick={() => {
                                            const reason = window.prompt('Що не так із цим повідомленням?');
                                            if (reason) void messagingApi.report('chat', line.id, reason).then(() => setNotice('Скаргу надіслано.'), (e: Error) => setNotice(e.message));
                                        }}>Поскаржитися</button>
                                    )}
                                </div>
                            )}
                        </div>
                    </div>
                ))}
                <div ref={end} />
            </div>
            <div className={styles.sticky}>
                {me ? (
                    <Composer label="Повідомлення в чат" maxLength={2000} reply={reply} onCancelReply={() => setReply(null)}
                        onSend={async (text) => { await chatApi.say(text, reply?.id ?? null); setReply(null); await refresh(); }} />
                ) : (
                    <p className={styles.muted}><Link to="/login" search={{ next: '/inbox/chat' }}>Увійдіть</Link>, щоб писати в чат.</p>
                )}
            </div>
        </section>
    );
}
