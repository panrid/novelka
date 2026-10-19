import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useMe } from '../../auth/me';
import { Composer, type ReplyTarget } from '../../community/Composer';
import { Markup } from '../../community/Markup';
import { GENERAL_CHANNEL, chatApi, messagingApi, type ChatLine, type ChatRole } from '../../inbox/api';
import { Avatar } from '../../ui/Avatar';
import { Loading } from '../../ui/Loading';
import { Notice } from '../../ui/Notice';
import { InboxNav } from './InboxNav';
import styles from './inbox.module.css';
import { askText } from '../../ui/ask';
import { messageTime } from '../../lib/dates';

const MODERATORS = new Set(['moderator', 'admin', 'owner']);

const ROLE_NAMES: Record<ChatRole, string> = {
    owner: 'власник сайту', admin: 'адміністратор', moderator: 'модератор', translator: 'перекладач', reader: 'читач',
};

/** Until the channels arrive (or if they fail), the chat is «загальний». */
const FALLBACK = { slug: GENERAL_CHANNEL, name: 'загальний', about: 'Про все, що стосується читання й сайту', unread: 0 };

/**
 * «Загальний чат» (етап 20, крок 6; `GlobalChat.dc.html`): channels by topic on the left, the
 * channel in the middle, «Зараз на сайті» and the rules on the right. Guests read; to write, sign in.
 */
export function ChatPage() {
    const me = useMe();
    const client = useQueryClient();
    const navigate = useNavigate();
    const search = useSearch({ strict: false }) as { channel?: string };
    const channels = useQuery({ queryKey: ['chat-channels'], queryFn: chatApi.channels, refetchInterval: me ? false : 60_000 });
    const list = channels.data?.length ? channels.data : [FALLBACK];
    const channel = list.find((item) => item.slug === search.channel) ?? list[0]!;
    const slug = channel.slug;
    const pages = useInfiniteQuery({
        queryKey: ['chat', slug],
        queryFn: ({ pageParam }) => chatApi.lines(slug, pageParam),
        initialPageParam: undefined as number | undefined,
        getNextPageParam: (last) => (last.length === 50 ? last[0]?.id : undefined),
        // Guests have no live stream: look for new lines now and then.
        refetchInterval: me ? false : 30_000,
    });
    const online = useQuery({ queryKey: ['chat-online'], queryFn: chatApi.online, refetchInterval: 60_000 });
    // A reply belongs to its channel: in another one it is simply not shown.
    const [replyTo, setReply] = useState<(ReplyTarget & { slug: string }) | null>(null);
    const reply = replyTo?.slug === slug ? replyTo : null;
    const [notice, setNotice] = useState<string | null>(null);
    const end = useRef<HTMLDivElement>(null);
    const lines = (pages.data?.pages ?? []).slice().reverse().flat();
    const newest = lines[lines.length - 1]?.id;
    useLayoutEffect(() => {
        // Braces matter: newer browsers return a promise from scrollIntoView, and React must get nothing back.
        end.current?.scrollIntoView?.({ block: 'end' });
    }, [newest]);
    // Seeing the newest line of a channel clears its number.
    useEffect(() => {
        if (me && newest) {
            void chatApi.read(slug, newest).then(() => client.invalidateQueries({ queryKey: ['chat-channels'] }), () => undefined);
        }
    }, [me, slug, newest, client]);
    const refresh = () => client.invalidateQueries({ queryKey: ['chat', slug] });
    const pick = (next: string) => void navigate({ to: '/inbox/chat', search: next === GENERAL_CHANNEL ? {} : { channel: next } });

    return (
        <section className={`${styles.desk} ${styles.frame}`}>
            <InboxNav />
            <div className={styles.talk} data-open="">
                <nav className={styles.channels} aria-label="Канали">
                    <b className={styles.channelsTitle}>Канали</b>
                    {list.map((item) => (
                        <button key={item.slug} type="button" className={`${styles.channel} ${item.slug === slug ? styles.channelOn : ''}`}
                            aria-current={item.slug === slug ? 'page' : undefined} onClick={() => pick(item.slug)}>
                            <span># {item.name}</span>
                            {item.unread > 0 && item.slug !== slug && (
                                <span className={styles.filterCount}>{item.unread > 99 ? '99+' : item.unread}<span className="visually-hidden"> нових</span></span>
                            )}
                        </button>
                    ))}
                </nav>

                <div className={styles.thread}>
                    <header className={styles.threadHead}>
                        <div className={styles.grow}>
                            <h1 className={styles.threadTitle}># {channel.name}</h1>
                            <div className={styles.muted}>{channel.about}</div>
                        </div>
                        {online.data && <span className={`${styles.muted} ${styles.onlineShort}`}>на сайті · {online.data.count}</span>}
                    </header>
                    {pages.isError && <Notice tone="error">{pages.error.message}</Notice>}
                    {notice && <Notice tone="info">{notice}</Notice>}
                    <div className={`${styles.scroll} ${styles.channelLines}`}>
                        {pages.isPending && <Loading shape="list" onRetry={() => void pages.refetch()} />}
                        {pages.hasNextPage && <button type="button" className={styles.older} onClick={() => void pages.fetchNextPage()}>Давніші</button>}
                        {pages.isSuccess && lines.length === 0 && <p className={styles.muted}>У # {channel.name} ще тихо. Напишіть першим!</p>}
                        {lines.map((line) => (
                            <Line key={line.id} line={line} canAct={Boolean(me)} moderator={Boolean(me && MODERATORS.has(me.role))}
                                onReply={() => setReply({ id: line.id, who: line.authorNick, excerpt: line.body.slice(0, 80), slug })}
                                onRemove={() => void chatApi.remove(line.id).then(refresh, (e: Error) => setNotice(e.message))}
                                onReport={() => void askText({ title: 'Поскаржитися на повідомлення', label: 'Що не так?', hint: 'Причину побачать лише модератори.', confirmLabel: 'Надіслати скаргу' }).then((reason) => {
                                    if (reason) void messagingApi.report('chat', line.id, reason).then(() => setNotice('Скаргу надіслано.'), (e: Error) => setNotice(e.message));
                                })} />
                        ))}
                        <div ref={end} />
                    </div>
                    <div className={styles.threadComposer}>
                        {me ? (
                            <Composer key={slug} label="Повідомлення в чат" placeholder={`Напишіть у # ${channel.name} · @нік — згадати людину`} maxLength={2000}
                                reply={reply} onCancelReply={() => setReply(null)}
                                onSend={async (text) => { await chatApi.say(slug, text, reply?.id ?? null); setReply(null); await refresh(); }} />
                        ) : (
                            <p className={styles.muted}><Link to="/login" search={{ next: `/inbox/chat${slug === GENERAL_CHANNEL ? '' : `?channel=${slug}`}` }}>Увійдіть</Link>, щоб писати в чат.</p>
                        )}
                    </div>
                </div>

                <aside className={styles.online} aria-label="Зараз на сайті">
                    <b className={styles.channelsTitle}>Зараз на сайті{online.data ? ` · ${online.data.count}` : ''}</b>
                    {online.data?.people.map((person) => (
                        <Link key={person.nick} to="/u/$nick" params={{ nick: person.nick }} className={styles.onlinePerson}>
                            <span className={styles.onlineFace}><Avatar nick={person.nick} url={person.avatarUrl} size={30} /></span>
                            <span>{person.nick}</span>
                        </Link>
                    ))}
                    {online.data && online.data.count > online.data.people.length && (
                        <span className={styles.muted}>і ще {online.data.count - online.data.people.length}</span>
                    )}
                    <p className={styles.rules}>
                        Правила чату: без спойлерів поза каналом обговорень, без посилань на піратські сайти. Решта — у <Link to="/rules">правилах сайту</Link>.
                    </p>
                </aside>
            </div>
        </section>
    );
}

function Line({ line, canAct, moderator, onReply, onRemove, onReport }: {
    line: ChatLine; canAct: boolean; moderator: boolean; onReply: () => void; onRemove: () => void; onReport: () => void;
}) {
    return (
        <div className={styles.chatLine}>
            <Avatar nick={line.authorNick} url={line.authorAvatarUrl} size={36} />
            <div className={styles.grow}>
                <div className={styles.chatWho}>
                    <Link to="/u/$nick" params={{ nick: line.authorNick }} className={styles.chatNick}>{line.authorNick}</Link>
                    {line.authorRole && <span className={styles.role}>{ROLE_NAMES[line.authorRole]}</span>}
                    <span className={styles.when}>{messageTime(new Date(line.createdAt))}</span>
                </div>
                <div className={`${styles.chatBody} ${line.mentionsMe ? styles.mentionsMe : ''}`}>
                    {line.replyExcerpt && <div className={styles.quoted}>{line.replyExcerpt}</div>}
                    <Markup text={line.body} />
                </div>
                {canAct && (
                    <div className={styles.actions}>
                        <button type="button" onClick={onReply}>Відповісти</button>
                        {(line.mine || moderator) && <button type="button" onClick={onRemove}>{line.mine ? 'Видалити' : 'Приховати'}</button>}
                        {!line.mine && <button type="button" onClick={onReport}>Поскаржитися</button>}
                    </div>
                )}
            </div>
        </div>
    );
}
