import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { useMe } from '../../auth/me';
import { Cover } from '../../reading/Cover';
import { LIST_LABELS, readingApi, type ListName } from '../../reading/api';
import { LinkButton } from '../../ui/LinkButton';
import { Notice } from '../../ui/Notice';
import { teamSearch } from './HomePage';
import styles from './reading.module.css';

const LISTS = Object.keys(LIST_LABELS) as ListName[];

export function LibraryPage() {
    const me = useMe();
    const { list = 'reading' }: { list?: ListName } = useSearch({ strict: false });
    const navigate = useNavigate();
    const library = useQuery({ queryKey: ['library', list], queryFn: () => readingApi.library(list), enabled: me !== null });

    if (!me) {
        return (
            <section className={styles.page}>
                <h1 className={styles.sectionTitle}>Бібліотека</h1>
                <p className={styles.small} style={{ marginBottom: 16 }}>Увійдіть, щоб зберігати новели в списки й продовжувати читання на будь-якому пристрої.</p>
                <LinkButton to="/login" search={{ next: '/library' }}>Увійти</LinkButton>
            </section>
        );
    }
    return (
        <section className={styles.page}>
            <h1 className="visually-hidden">Бібліотека</h1>
            <div className={styles.shelf} role="tablist" aria-label="Списки" style={{ marginTop: 8 }}>
                {LISTS.map((name) => (
                    <button key={name} type="button" role="tab" aria-selected={name === list}
                        className={`${styles.chip} ${name === list ? styles.chipOn : styles.chipGhost}`}
                        style={{ padding: '7px 12px', fontSize: 13 }}
                        onClick={() => void navigate({ to: '/library', search: name === 'reading' ? {} : { list: name }, replace: true })}>
                        {LIST_LABELS[name]}{library.data ? ` · ${library.data.counts[name]}` : ''}
                    </button>
                ))}
            </div>
            {library.isError && <Notice tone="error">{library.error.message}</Notice>}
            {library.isSuccess && library.data.items.length === 0 && (
                <p className={styles.empty}>
                    {list === 'reading'
                        ? 'Тут зʼявиться все, що ви почнете читати.'
                        : `У списку «${LIST_LABELS[list]}» поки порожньо. Додати можна на сторінці новели.`}
                </p>
            )}
            {library.data?.items.map(({ card, chapterNumber }) => (
                <Link key={card.editionId} className={styles.row}
                    to={chapterNumber ? '/n/$slug/$number' : '/n/$slug'}
                    params={{ slug: card.novelSlug, number: String(chapterNumber ?? 1) }} search={teamSearch(card)}>
                    <Cover url={card.coverUrl} title={card.title} seed={card.novelSlug} width={48} />
                    <div className={styles.grow}>
                        <div className={styles.ellipsis} style={{ fontWeight: 500 }}>{card.title}</div>
                        <div className={styles.small}>
                            {chapterNumber ? `Глава ${chapterNumber} з ${card.chapterCount}` : `${card.chapterCount} глав`}
                        </div>
                        {chapterNumber ? <div className={styles.bar}><span style={{ width: `${Math.round((chapterNumber / Math.max(1, card.chapterCount)) * 100)}%` }} /></div> : null}
                    </div>
                </Link>
            ))}
        </section>
    );
}
