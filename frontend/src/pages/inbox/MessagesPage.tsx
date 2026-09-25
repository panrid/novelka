import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { Users } from 'lucide-react';
import { useMe } from '../../auth/me';
import { messagingApi } from '../../inbox/api';
import { relativeTime } from '../../lib/dates';
import { Avatar } from '../../ui/Avatar';
import { LinkButton } from '../../ui/LinkButton';
import { Notice } from '../../ui/Notice';
import { InboxNav } from './InboxNav';
import styles from './inbox.module.css';

export function MessagesPage() {
    const me = useMe();
    const list = useQuery({ queryKey: ['conversations'], queryFn: messagingApi.list });
    return (
        <section className={styles.page}>
            <InboxNav />
            <div className={styles.actions} style={{ marginBottom: 8 }}>
                <LinkButton to="/inbox/messages/new" variant="secondary">Нова група</LinkButton>
            </div>
            {list.isError && <Notice tone="error">{list.error.message}</Notice>}
            {list.data?.items.length === 0 && (
                <p className={styles.muted}>Розмов ще немає. Щоб написати людині, відкрийте її профіль і натисніть «Написати».</p>
            )}
            {list.data?.items.map((item) => (
                <Link key={item.id} to="/inbox/messages/$id" params={{ id: String(item.id) }} className={styles.item}>
                    {item.kind === 'team'
                        ? <span style={{ width: 44, display: 'grid', placeItems: 'center' }}><Users size={26} aria-hidden /></span>
                        : <Avatar nick={item.title} url={item.avatarUrl} size={44} />}
                    <div className={styles.grow}>
                        <div className={styles.line}>
                            <b>{item.kind === 'team' ? `$${item.teamHandle}` : item.title}</b>
                            {item.kind === 'team' && item.title !== item.teamHandle && <span className={styles.muted}> · {item.title}</span>}
                        </div>
                        <div className={`${styles.line} ${styles.muted}`}>
                            {item.lastText
                                ? `${item.lastAuthorNick ? `${item.lastAuthorNick === me?.nick ? 'Ви' : item.lastAuthorNick}: ` : ''}${item.lastText}`
                                : 'Ще нічого не написано'}
                        </div>
                    </div>
                    <div style={{ textAlign: 'right' }}>
                        <div className={styles.muted}>{relativeTime(new Date(item.lastAt))}</div>
                        {item.unread > 0 && <span className={`${styles.badge} ${item.muted ? styles.badgeMuted : ''}`}>{item.unread}</span>}
                    </div>
                </Link>
            ))}
        </section>
    );
}
