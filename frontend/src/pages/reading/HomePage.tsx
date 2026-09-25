import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { readingApi, chaptersWord, type Card } from '../../reading/api';
import { Cover } from '../../reading/Cover';
import { Notice } from '../../ui/Notice';
import { relativeTime } from '../../lib/dates';
import styles from './reading.module.css';

export function HomePage() {
    const home = useQuery({ queryKey: ['home'], queryFn: readingApi.home });

    if (home.isPending) {
        return <p className={styles.empty}>Завантажуємо…</p>;
    }
    if (home.isError) {
        return <Notice tone="error">{home.error.message}</Notice>;
    }
    const { continueReading, popular, newChapters } = home.data;
    if (popular.length === 0 && newChapters.length === 0) {
        return (
            <section className={styles.page}>
                <p className={styles.empty}>Тут скоро зʼявляться новели. Загляньте трохи пізніше.</p>
            </section>
        );
    }
    return (
        <section className={styles.page}>
            <h1 className="visually-hidden">Що почитати</h1>
            {continueReading.map(({ card, chapterNumber, position }) => (
                <Link key={card.editionId} className={styles.continue} to="/n/$slug/$number"
                    params={{ slug: card.novelSlug, number: String(chapterNumber) }} search={teamSearch(card)}>
                    <Cover url={card.coverUrl} title={card.title} seed={card.novelSlug} width={44} />
                    <div className={styles.grow}>
                        <div className={styles.small}>Продовжити</div>
                        <div className={styles.ellipsis} style={{ fontWeight: 500 }}>{card.title}</div>
                        <div className={styles.small}>Глава {chapterNumber} з {card.chapterCount}</div>
                        <div className={styles.bar}><span style={{ width: `${Math.round(position * 100)}%` }} /></div>
                    </div>
                </Link>
            ))}

            {popular.length > 0 && (
                <>
                    <h2 className={styles.sectionTitle}>Популярне <Link to="/catalog">усе →</Link></h2>
                    <div className={styles.shelf}>
                        {popular.map((card) => (
                            <Link key={card.editionId} className={styles.shelfItem} to="/n/$slug"
                                params={{ slug: card.novelSlug }} search={teamSearch(card)}>
                                <Cover url={card.coverUrl} title={card.title} seed={card.novelSlug} width={104} />
                                <div className={styles.shelfTitle}>{card.title}</div>
                            </Link>
                        ))}
                    </div>
                </>
            )}

            {newChapters.length > 0 && (
                <>
                    <h2 className={styles.sectionTitle}>Нові глави <Link to="/catalog" search={{ sort: 'updated' }}>усе →</Link></h2>
                    {newChapters.map(({ card, firstNumber, lastNumber, publishedAt, firstLabel, lastLabel }) => (
                        <Link key={card.editionId} className={styles.row} to="/n/$slug/$number"
                            params={{ slug: card.novelSlug, number: String(firstNumber) }} search={teamSearch(card)}>
                            <Cover url={card.coverUrl} title={card.title} seed={card.novelSlug} width={40} />
                            <div className={styles.grow}>
                                <div className={styles.ellipsis} style={{ fontWeight: 500 }}>{card.title}</div>
                                <div className={styles.small}>
                                    {chapterRange(firstNumber, lastNumber, firstLabel, lastLabel)}
                                    {' · '}{relativeTime(new Date(publishedAt))}
                                </div>
                            </div>
                            {card.kind === 'machine' || card.kind === 'mixed' ? <span className={`${styles.chip} ${styles.chipGhost}`} title="Машинний переклад">ШІ</span> : null}
                        </Link>
                    ))}
                </>
            )}
        </section>
    );
}

/** Links carry the team only when it matters: a novel with one translation needs no ?t=. */
export function teamSearch(card: Pick<Card, 'teamHandle'>): { t?: string } {
    return { t: card.teamHandle };
}

export function CardRow({ card }: { card: Card }) {
    return (
        <Link className={styles.row} to="/n/$slug" params={{ slug: card.novelSlug }} search={teamSearch(card)}>
            <Cover url={card.coverUrl} title={card.title} seed={card.novelSlug} width={56} />
            <div className={styles.grow}>
                <div className={styles.ellipsis} style={{ fontWeight: 500 }}>{card.title}</div>
                <div className={`${styles.small} ${styles.ellipsis}`}>{[...new Set([card.author, card.teamName].filter(Boolean))].join(' · ')}</div>
                <div className={styles.small}>
                    {card.chapterCount} {chaptersWord(card.chapterCount)}
                    {card.kind === 'machine' || card.kind === 'mixed' ? ' · ШІ' : card.kind === 'original' ? ' · оригінальний твір' : ''}
                    {card.adult ? ' · 18+' : ''}
                </div>
            </div>
        </Link>
    );
}

/** «Глави 0–1» with the numbers readers see, not the positions. */
function chapterRange(first: number, last: number, firstLabel?: string | null, lastLabel?: string | null) {
    const a = firstLabel || (firstLabel === '' ? null : String(first));
    const b = lastLabel || (lastLabel === '' ? null : String(last));
    if (first === last) return a ? `Глава ${a}` : 'Нова глава';
    return a && b ? `Глави ${a}–${b}` : `Нових глав: ${last - first + 1}`;
}
