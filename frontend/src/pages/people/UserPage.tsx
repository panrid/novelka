import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, Navigate, useNavigate, useParams } from '@tanstack/react-router';
import { peopleApi } from '../../auth/api';
import { useMe } from '../../auth/me';
import { messagingApi } from '../../inbox/api';
import { Cover } from '../../reading/Cover';
import { chaptersWord, readingApi } from '../../reading/api';
import { Avatar } from '../../ui/Avatar';
import { Button } from '../../ui/Button';
import { monthYearGenitive } from '../../lib/dates';
import { plural } from '../../lib/plural';
import { Notice } from '../../ui/Notice';
import styles from '../pages.module.css';


export function UserPage() {
    const { nick } = useParams({ strict: false }) as { nick: string };
    const me = useMe();
    const profile = useQuery({ queryKey: ['user', nick.toLowerCase()], queryFn: () => peopleApi.profile(nick) });

    if (profile.isPending) {
        return <p className={styles.muted} style={{ paddingTop: 24 }}>Завантажуємо…</p>;
    }
    if (profile.isError) {
        return (
            <section className={styles.narrow}>
                <Notice tone="error">{profile.error.message}</Notice>
            </section>
        );
    }
    const person = profile.data;
    if (person.nick !== nick) {
        // An old nick or different letter case: show the current address.
        return <Navigate to="/u/$nick" params={{ nick: person.nick }} replace />;
    }
    return (
        <section className={styles.narrow}>
            <div className={styles.row}>
                <Avatar nick={person.nick} url={person.avatarUrl} size={80} />
                <div>
                    <h1 className={styles.title} style={{ marginBottom: 2 }}>{person.nick}</h1>
                    <p className={styles.muted}>{`на Новелці з ${monthYearGenitive(new Date(person.memberSince))}`}</p>
                </div>
            </div>
            {person.bio && <p className={styles.bio} style={{ marginTop: 18 }}>{person.bio}</p>}
            {me?.nick === person.nick && (
                <div className={styles.links}>
                    <Link to="/me/settings">Редагувати профіль</Link>
                </div>
            )}
            {me && me.nick !== person.nick && <WriteButton nick={person.nick} />}
            <Works nick={person.nick} />
            <Activity nick={person.nick} />
        </section>
    );
}

/** «Написати» opens the conversation with this person, starting it if needed; «Заблокувати» stops them writing. */
function WriteButton({ nick }: { nick: string }) {
    const navigate = useNavigate();
    const client = useQueryClient();
    const blocked = useQuery({ queryKey: ['blocked'], queryFn: messagingApi.blocked });
    const isBlocked = blocked.data?.some((name) => name.toLowerCase() === nick.toLowerCase()) ?? false;
    const start = useMutation({
        mutationFn: () => messagingApi.direct(nick),
        onSuccess: ({ id }) => void navigate({ to: '/inbox/messages/$id', params: { id: String(id) } }),
    });
    const block = useMutation({
        mutationFn: () => (isBlocked ? messagingApi.unblock(nick) : messagingApi.block(nick)),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['blocked'] }),
    });
    return (
        <div style={{ marginTop: 18 }}>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {!isBlocked && <Button onPress={() => start.mutate()} pending={start.isPending} pendingLabel="Відкриваємо…">Написати</Button>}
                {blocked.isSuccess && (
                    <Button variant={isBlocked ? 'secondary' : 'quiet'} onPress={() => block.mutate()} pending={block.isPending}>
                        {isBlocked ? 'Розблокувати' : 'Заблокувати'}
                    </Button>
                )}
            </div>
            {isBlocked && <p className={styles.muted}>{nick} не може писати вам і додавати вас у групи.</p>}
            {(start.error ?? block.error) && <Notice tone="error">{(start.error ?? block.error)!.message}</Notice>}
        </div>
    );
}

/** Accepted suggestions and «Читає зараз», when the person shows it. */
function Activity({ nick }: { nick: string }) {
    const activity = useQuery({ queryKey: ['activity', nick.toLowerCase()], queryFn: () => readingApi.activity(nick) });
    if (!activity.data) return null;
    const { reading, acceptedSuggestions } = activity.data;
    return (
        <>
            {acceptedSuggestions > 0 && (
                <p className={styles.muted} style={{ marginTop: 18 }}>
                    {plural(acceptedSuggestions, 'правку', 'правки', 'правок')} прийнято
                </p>
            )}
            {reading.length > 0 && (
                <div className={styles.section} style={{ marginTop: 24 }}>
                    <h2 className={styles.sectionTitle}>Читає зараз</h2>
                    <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 4 }}>
                        {reading.map((card) => (
                            <Link key={card.editionId} to="/n/$slug" params={{ slug: card.novelSlug }} search={{ t: card.teamHandle }}
                                style={{ width: 72, flex: 'none', color: 'var(--text)', textDecoration: 'none', fontSize: 13, lineHeight: 1.3 }}>
                                <Cover url={card.coverUrl} title={card.title} seed={card.novelSlug} width={72} />
                                <span style={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', marginTop: 6 }}>
                                    {card.title}
                                </span>
                            </Link>
                        ))}
                    </div>
                </div>
            )}
        </>
    );
}

/** Translations and works of the teams the person owns or belongs to. */
function Works({ nick }: { nick: string }) {
    const works = useQuery({ queryKey: ['works', nick.toLowerCase()], queryFn: () => readingApi.works(nick) });
    if (!works.data?.length) return null;
    return (
        <div className={styles.section} style={{ marginTop: 24 }}>
            <h2 className={styles.sectionTitle}>Переклади й твори</h2>
            {works.data.map((card) => (
                <Link key={card.editionId} to="/n/$slug" params={{ slug: card.novelSlug }} search={{ t: card.teamHandle }} className={styles.row}
                    style={{ padding: '8px 0', color: 'var(--text)', textDecoration: 'none' }}>
                    <Cover url={card.coverUrl} title={card.title} seed={card.novelSlug} width={40} />
                    <div>
                        <div>{card.title}</div>
                        <div className={styles.muted}>{card.chapterCount} {chaptersWord(card.chapterCount)} · ${card.teamHandle}</div>
                    </div>
                </Link>
            ))}
        </div>
    );
}
