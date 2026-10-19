import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState, type ReactNode } from 'react';
import { useMe } from '../../auth/me';
import { messagingApi, type ConversationSummary } from '../../inbox/api';
import { relativeTime } from '../../lib/dates';
import { Avatar } from '../../ui/Avatar';
import { Users } from '../../ui/icons';
import { Loading } from '../../ui/Loading';
import { Notice } from '../../ui/Notice';
import { InboxNav } from './InboxNav';
import styles from './inbox.module.css';

/** The name a conversation goes by: a person's nick, a group's title, a team's $handle. */
export const conversationName = (item: { kind: string; title: string; teamHandle: string | null }) =>
    item.kind === 'team' ? `$${item.teamHandle}` : item.title;

/** A person's round face, or a square for a group or a team (as in `Messages.dc.html`). */
export function ConversationFace({ item, size }: { item: { kind: string; title: string; avatarUrl: string | null }; size: number }) {
    if (item.kind === 'direct') return <Avatar nick={item.title} url={item.avatarUrl} size={size} />;
    if (item.avatarUrl) return <img src={item.avatarUrl} alt="" width={size} height={size} className={styles.squareFace} />;
    return <span className={styles.squareFace} style={{ width: size, height: size }} aria-hidden><Users size={size / 2} /></span>;
}

/**
 * «Повідомлення» (етап 20, крок 6): conversations on the left, the open one on the right. A phone
 * shows one of the two: the list, or the conversation with a way back.
 */
export function MessagesFrame({ openId, children }: { openId?: number; children?: ReactNode }) {
    const me = useMe();
    const list = useQuery({ queryKey: ['conversations'], queryFn: messagingApi.list });
    const [find, setFind] = useState('');
    const words = find.trim().toLowerCase();
    const shown = (list.data?.items ?? []).filter((item) => !words || conversationName(item).toLowerCase().includes(words)
        || item.title.toLowerCase().includes(words));
    return (
        <section className={`${styles.desk} ${styles.frame}`}>
            <InboxNav />
            <div className={styles.talk} data-open={openId ? '' : undefined}>
                <aside className={styles.conversations} aria-label="Розмови">
                    <div className={styles.findRow}>
                        <label className={styles.find}>
                            <span className="visually-hidden">Пошук розмов</span>
                            <input type="search" placeholder="Пошук" value={find} onChange={(event) => setFind(event.target.value)} />
                        </label>
                        <Link to="/inbox/messages/new" className={styles.plus} aria-label="Нова група" title="Нова група">+</Link>
                    </div>
                    {list.isError && <Notice tone="error">{list.error.message}</Notice>}
                    {list.isPending && <Loading shape="list" onRetry={() => void list.refetch()} />}
                    {list.data?.items.length === 0 && (
                        <p className={styles.muted}>Розмов ще немає. Щоб написати людині, відкрийте її профіль і натисніть «Написати».</p>
                    )}
                    {list.data && list.data.items.length > 0 && shown.length === 0 && <p className={styles.muted}>Нічого не знайшлося.</p>}
                    <div className={styles.scroll}>
                        {shown.map((item) => <ConversationRow key={item.id} item={item} open={item.id === openId} myNick={me?.nick} />)}
                    </div>
                </aside>
                <div className={styles.thread}>
                    {children ?? (
                        <div className={styles.pick}>
                            <p>Виберіть розмову ліворуч.</p>
                            <p className={styles.muted}>Написати людині — кнопкою «Написати» в її профілі; зібрати кількох — «+ Нова група».</p>
                        </div>
                    )}
                </div>
            </div>
        </section>
    );
}

function ConversationRow({ item, open, myNick }: { item: ConversationSummary; open: boolean; myNick: string | undefined }) {
    const last = item.lastText
        ? `${item.lastAuthorNick && item.kind !== 'direct' ? `${item.lastAuthorNick === myNick ? 'Ви' : item.lastAuthorNick}: ` : ''}${item.lastText}`
        : 'Ще нічого не написано';
    return (
        <Link to="/inbox/messages/$id" params={{ id: String(item.id) }} className={`${styles.conversation} ${open ? styles.conversationOpen : ''}`}
            aria-current={open ? 'page' : undefined}>
            <ConversationFace item={item} size={40} />
            <span className={styles.noteText}>
                <span className={styles.noteTitle}>{conversationName(item)}</span>
                <span className={styles.noteLine}>{last}</span>
            </span>
            <span className={styles.conversationSide}>
                <span className={styles.when}>{relativeTime(new Date(item.lastAt))}</span>
                {item.unread > 0 && (
                    <span className={`${styles.badge} ${item.muted ? styles.badgeMuted : ''}`}>
                        {item.unread}<span className="visually-hidden"> непрочитаних</span>
                    </span>
                )}
            </span>
        </Link>
    );
}
