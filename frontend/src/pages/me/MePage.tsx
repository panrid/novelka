import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { LIST_LABELS, chaptersWord, readingApi, resumeLine, type ListName } from '../../reading/api';
import { Cover } from '../../reading/Cover';
import { studioApi } from '../../studio/api';
import { useWide } from '../../lib/useWide';
import { Achievements } from '../people/Achievements';
import me$ from './me.module.css';
import { authApi } from '../../auth/api';
import { meQuery, useMe } from '../../auth/me';
import { Avatar } from '../../ui/Avatar';
import { Button } from '../../ui/Button';
import { LinkButton } from '../../ui/LinkButton';
import styles from '../pages.module.css';
import { useMyShahs } from '../../ledger/api';
import { shahWord } from '../../studio/autotranslate';

/**
 * The «Я» tab: for a guest — sign in; for a member — profile and everything personal. A wide
 * screen shows it as a desk: the menu on the left, what the person reads and translates beside it.
 */
export function MePage() {
    const me = useMe();
    const wide = useWide();
    const shahs = useMyShahs();
    // Readers who never got шаги do not need the page.
    const hasShahs = Boolean(shahs.data && (shahs.data.available > 0 || shahs.data.reserved > 0 || shahs.data.history.length > 0));
    const client = useQueryClient();
    const navigate = useNavigate();
    const logout = useMutation({ meta: { errorToast: true },
        mutationFn: authApi.logout,
        onSuccess: () => {
            client.setQueryData(meQuery.queryKey, null);
            client.removeQueries({ predicate: (query) => query.queryKey[0] !== 'me' });
            void navigate({ to: '/' });
        },
    });

    if (!me) {
        return (
            <section className={styles.narrow}>
                <h1 className={styles.title}>Я</h1>
                <p className={styles.lead}>Увійдіть, щоб мати бібліотеку на всіх пристроях, коментувати й пропонувати правки.</p>
                <div className={styles.form}>
                    <LinkButton to="/login" wide>Увійти</LinkButton>
                    <LinkButton to="/register" wide variant="secondary">Зареєструватися</LinkButton>
                </div>
            </section>
        );
    }
    const menu = (
        <>
            <Link to="/u/$nick" params={{ nick: me.nick }} className={styles.card}>
                <Avatar nick={me.nick} url={me.avatarUrl} size={56} />
                <div>
                    <div style={{ font: '600 18px var(--font-reading)' }}>{me.nick}</div>
                    <div className={styles.muted}>Переглянути профіль</div>
                </div>
            </Link>
            <nav className={styles.menu} aria-label="Особисте">
                <Link to="/studio" className={styles.menuItem}>Студія — мої переклади й твори</Link>
                {me.role === 'owner' && <Link to="/me/wallet" className={styles.menuItem}>Шаги й автопереклад</Link>}
                {me.role !== 'owner' && hasShahs && (
                    <Link to="/me/shahs" className={styles.menuItem}>Шаги · {shahs.data!.available} {shahWord(shahs.data!.available)}</Link>
                )}
                {me.role !== 'reader' && <Link to="/admin" className={styles.menuItem}>Адміністрування</Link>}
                <Link to="/me/suggestions" className={styles.menuItem}>Мої правки</Link>
                <Link to="/me/settings" className={styles.menuItem}>Налаштування</Link>
                <Link to="/me/settings/privacy" className={styles.menuItem}>Приватність</Link>
            </nav>
            <div style={{ marginTop: 24 }}>
                <Button variant="danger" wide onPress={() => logout.mutate()} pending={logout.isPending} pendingLabel="Виходимо…">
                    Вийти
                </Button>
            </div>
        </>
    );
    if (!wide) {
        return <section className={styles.narrow}>{menu}</section>;
    }
    return (
        <section className={me$.desk}>
            <h1 className="visually-hidden">Я</h1>
            <aside className={me$.side}>{menu}</aside>
            <div className={me$.main}>
                <Reading />
                <Studio />
                {me.role !== 'owner' && hasShahs && (
                    <div className={me$.panel}>
                        <h2 className={me$.heading}>Шаги <Link to="/me/shahs">історія →</Link></h2>
                        <p className={me$.big}>{shahs.data!.available} {shahWord(shahs.data!.available)}</p>
                        {shahs.data!.reserved > 0 && <p className={styles.muted}>Ще {shahs.data!.reserved} зарезервовано для запусків, що йдуть.</p>}
                    </div>
                )}
                <div className={me$.panel}><Achievements nick={me.nick} /></div>
            </div>
        </section>
    );
}

const LISTS: ListName[] = ['reading', 'planned', 'done', 'paused', 'dropped'];

/** What the person reads now, and how many novels each list of the library holds. */
function Reading() {
    const home = useQuery({ queryKey: ['home'], queryFn: readingApi.home });
    const library = useQuery({ queryKey: ['library', 'reading'], queryFn: () => readingApi.library('reading') });
    const continues = home.data?.continueReading ?? [];
    const counts = library.data?.counts;
    return (
        <div className={me$.panel}>
            <h2 className={me$.heading}>Читаю <Link to="/library">бібліотека →</Link></h2>
            {counts && (
                <div className={me$.counts}>
                    {LISTS.map((list) => (
                        <Link key={list} to="/library" search={list === 'reading' ? {} : { list }} className={me$.count}>
                            <b>{counts[list] ?? 0}</b> {LIST_LABELS[list].toLowerCase()}
                        </Link>
                    ))}
                </div>
            )}
            {home.isSuccess && continues.length === 0 && (
                <p className={me$.empty}>Поки нічого не читаєте. <Link to="/catalog">Відкрийте каталог</Link>.</p>
            )}
            <div className={me$.tiles}>
                {continues.map(({ card, chapterNumber, position, chapterLabel }) => (
                    <Link key={card.editionId} className={me$.tile} to="/n/$slug/$number"
                        params={{ slug: card.novelSlug, number: String(chapterNumber) }} search={{ t: card.teamHandle }}>
                        <Cover url={card.coverUrl} title={card.title} seed={card.novelSlug} width={44} />
                        <span className={me$.grow}>
                            <span className={me$.name}>{card.title}</span>
                            <span className={me$.small}>{resumeLine(chapterNumber, chapterLabel, card.chapterCount)}</span>
                            <span className={me$.bar}><span style={{ width: `${Math.round(position * 100)}%` }} /></span>
                        </span>
                    </Link>
                ))}
            </div>
        </div>
    );
}

/** The person's translations and works with what waits for them; nothing when there are none. */
function Studio() {
    const mine = useQuery({ queryKey: ['studio'], queryFn: studioApi.mine });
    const editions = mine.data ?? [];
    if (editions.length === 0) return null;
    return (
        <div className={me$.panel}>
            <h2 className={me$.heading}>Студія <Link to="/studio">усе →</Link></h2>
            <div className={me$.tiles}>
                {editions.slice(0, 6).map((edition) => (
                    <Link key={edition.editionId} className={me$.tile} to="/studio/$editionId" params={{ editionId: String(edition.editionId) }}>
                        <Cover url={edition.coverUrl} title={edition.title} seed={edition.novelSlug} width={44} />
                        <span className={me$.grow}>
                            <span className={me$.name}>{edition.title}</span>
                            <span className={me$.small}>{edition.chapterCount} {chaptersWord(edition.chapterCount)} · {edition.teamName}</span>
                            {(edition.pendingSuggestions > 0 || edition.drafts > 0) && (
                                <span className={me$.small}>
                                    {[edition.pendingSuggestions > 0 && `правок чекає: ${edition.pendingSuggestions}`,
                                        edition.drafts > 0 && `чернеток: ${edition.drafts}`].filter(Boolean).join(' · ')}
                                </span>
                            )}
                        </span>
                    </Link>
                ))}
            </div>
        </div>
    );
}
