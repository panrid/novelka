import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from '@tanstack/react-router';
import { ArrowLeft, Info } from 'lucide-react';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Composer, type ReplyTarget } from '../../community/Composer';
import { Markup } from '../../community/Markup';
import { messagingApi, type MessageLine } from '../../inbox/api';
import { Avatar } from '../../ui/Avatar';
import { Notice } from '../../ui/Notice';
import styles from './inbox.module.css';

export function ConversationPage() {
    const { id } = useParams({ strict: false }) as { id: string };
    const conversationId = Number(id);
    const client = useQueryClient();
    const pages = useInfiniteQuery({
        queryKey: ['conversation', conversationId],
        queryFn: ({ pageParam }) => messagingApi.open(conversationId, pageParam),
        initialPageParam: undefined as number | undefined,
        getNextPageParam: (last) => (last.hasOlder ? last.lines[0]?.id : undefined),
    });
    const [reply, setReply] = useState<ReplyTarget | null>(null);
    const end = useRef<HTMLDivElement>(null);
    const first = pages.data?.pages[0];
    const lines = (pages.data?.pages ?? []).slice().reverse().flatMap((page) => page.lines);
    const newest = lines[lines.length - 1]?.id;

    // Seeing the newest line marks the conversation read.
    useEffect(() => {
        if (newest) {
            void messagingApi.read(conversationId, newest).then(() => {
                void client.invalidateQueries({ queryKey: ['inbox-counts'] });
                void client.invalidateQueries({ queryKey: ['conversations'] });
            });
        }
    }, [newest, conversationId, client]);
    useLayoutEffect(() => {
        // Braces matter: newer browsers return a promise from scrollIntoView, and React must get nothing back.
        end.current?.scrollIntoView?.({ block: 'end' });
    }, [newest]);

    if (pages.isError) return <section className={styles.page}><Notice tone="error">{pages.error.message}</Notice></section>;
    if (!first) return <p className={styles.muted} style={{ paddingTop: 24 }}>Відкриваємо розмову…</p>;
    const refresh = () => client.invalidateQueries({ queryKey: ['conversation', conversationId] });

    return (
        <section className={styles.page}>
            <header className={styles.head}>
                <Link to="/inbox/messages" aria-label="До розмов"><ArrowLeft size={22} aria-hidden /></Link>
                {first.kind !== 'team' && <Avatar nick={first.title} url={first.avatarUrl} size={32} />}
                <div className={styles.grow}>
                    <h1 className={styles.title}>{first.kind === 'team' ? `$${first.teamHandle}` : first.title}</h1>
                    {first.kind === 'group' && <div className={styles.muted}>{first.members.length} учасників</div>}
                    {first.kind === 'team' && <div className={styles.muted}>чат команди</div>}
                </div>
                {first.kind === 'direct' && first.title && (
                    <Link to="/u/$nick" params={{ nick: first.title }} className={styles.muted}>профіль</Link>
                )}
                <Link to="/inbox/messages/$id/about" params={{ id }} aria-label="Про розмову"><Info size={22} aria-hidden /></Link>
            </header>
            <div className={styles.lines}>
                {pages.hasNextPage && (
                    <button type="button" className={styles.older} onClick={() => void pages.fetchNextPage()}>Давніші повідомлення</button>
                )}
                {lines.map((line) => <Bubble key={line.id} line={line} showAuthor={first.kind !== 'direct'}
                    onReply={() => setReply({ id: line.id, who: line.authorNick ?? '', excerpt: line.body.slice(0, 80) })}
                    onChanged={() => void refresh()} />)}
                <div ref={end} />
            </div>
            <div className={styles.sticky}>
                {first.canWrite ? (
                    <Composer label="Повідомлення" reply={reply} onCancelReply={() => setReply(null)}
                        uploadPicture={(file) => messagingApi.uploadPicture(file)}
                        onSend={async (text, pictures) => {
                            await messagingApi.send(conversationId, text, reply?.id ?? null, pictures);
                            setReply(null);
                            await refresh();
                        }} />
                ) : <Notice tone="info">{first.cannotWrite}</Notice>}
            </div>
        </section>
    );
}

function Bubble({ line, showAuthor, onReply, onChanged }: { line: MessageLine; showAuthor: boolean; onReply: () => void; onChanged: () => void }) {
    const [error, setError] = useState<string | null>(null);
    if (line.kind === 'system') return <div className={styles.system}>{line.body}</div>;
    const time = new Date(line.createdAt).toLocaleTimeString('uk-UA', { hour: '2-digit', minute: '2-digit' });
    return (
        <div className={`${styles.bubbleRow} ${line.mine ? styles.mineRow : ''}`}>
            {showAuthor && !line.mine && line.authorNick && <Avatar nick={line.authorNick} url={line.authorAvatarUrl} size={28} />}
            <div className={`${styles.bubble} ${line.mine ? styles.mine : ''}`}>
                {showAuthor && !line.mine && <div className={styles.author}>{line.authorNick}</div>}
                {line.replyExcerpt && <div className={styles.quoted}>{line.replyExcerpt}</div>}
                {line.deleted ? <span className={styles.muted}>Повідомлення видалено</span> : (
                    <>
                        {line.pictures.length > 0 && (
                            <div className={styles.gallery}>
                                {line.pictures.map((picture) => (
                                    <a key={picture.id} href={picture.url} target="_blank" rel="noreferrer"><img src={picture.thumbUrl} alt="" loading="lazy" /></a>
                                ))}
                            </div>
                        )}
                        {line.body && <Markup text={line.body} />}
                    </>
                )}
                <div className={styles.meta}>{time}{line.editedAt ? ' · змінено' : ''}</div>
                {!line.deleted && (
                    <div className={styles.actions}>
                        <button type="button" onClick={onReply}>Відповісти</button>
                        {line.mine ? (
                            <button type="button" onClick={() => void messagingApi.remove(line.id).then(onChanged, (e: Error) => setError(e.message))}>Видалити</button>
                        ) : (
                            <button type="button" onClick={() => {
                                const reason = window.prompt('Що не так із цим повідомленням?');
                                if (reason) void messagingApi.report('message', line.id, reason).then(() => setError('Скаргу надіслано.'), (e: Error) => setError(e.message));
                            }}>Поскаржитися</button>
                        )}
                    </div>
                )}
                {error && <div className={styles.muted}>{error}</div>}
            </div>
        </div>
    );
}
