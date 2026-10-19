import { LinkButton } from '../../ui/LinkButton';
import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { readingApi, chaptersWord, type Card, resumeLine } from '../../reading/api';
import { Cover } from '../../reading/Cover';
import { Notice } from '../../ui/Notice';
import { relativeTime } from '../../lib/dates';
import styles from './reading.module.css';
import home from './home.module.css';
import { Loading } from '../../ui/Loading';

/**
 * Головна (етап 20, `doc/redesign/mockup/Home.dc.html` і `PhoneHome.dc.html`): «Продовжити читання»,
 * «Нові глави» і «Читають цього тижня»; нижче — теги й «Що перекласти далі». На телефоні — одна
 * картка продовження (решта — у «Бібліотеці») і три обкладинки.
 */
export function HomePage() {
    const data = useQuery({ queryKey: ['home'], queryFn: readingApi.home });
    const tags = useQuery({ queryKey: ['tags'], queryFn: readingApi.tags, staleTime: 5 * 60_000 });

    if (data.isPending) {
        return <Loading shape="list" />;
    }
    if (data.isError) {
        return <Notice tone="error">{data.error.message}</Notice>;
    }
    const { continueReading, popular, newChapters } = data.data;
    if (popular.length === 0 && newChapters.length === 0) {
        return (
            <section className={styles.page}>
                <p className={styles.empty}>Тут скоро зʼявляться новели. Загляньте трохи пізніше.</p>
            </section>
        );
    }
    return (
        <section className={home.page}>
            <h1 className="visually-hidden">Що почитати</h1>
            {continueReading.length > 0 && (
                <section aria-labelledby="home-continue">
                    <h2 id="home-continue" className={`${home.title} ${home.wideOnly}`}>Продовжити читання</h2>
                    <div className={home.continues}>
                        {continueReading.map(({ card, chapterNumber, position, chapterLabel }) => {
                            const share = Math.round(position * 100);
                            return (
                                <Link key={card.editionId} className={home.continue} to="/n/$slug/$number"
                                    params={{ slug: card.novelSlug, number: String(chapterNumber) }} search={teamSearch(card)}>
                                    <span className={home.continueCover}>
                                        <Cover url={card.coverUrl} title={card.title} seed={card.novelSlug} width={72} />
                                    </span>
                                    <span className={home.continueText}>
                                        <span className={`${home.kicker} ${home.phoneOnly}`}>Продовжити читання</span>
                                        <span className={home.novelTitle}>{card.title}</span>
                                        <span className={home.muted}>{resumeLine(chapterNumber, chapterLabel, card.chapterCount)} · {share}%</span>
                                        <span className={home.bar}><span style={{ width: `${share}%` }} /></span>
                                    </span>
                                </Link>
                            );
                        })}
                    </div>
                </section>
            )}

            <div className={home.columns}>
                {newChapters.length > 0 && (
                    <section aria-labelledby="home-fresh" className={home.fresh}>
                        <h2 id="home-fresh" className={home.title}>
                            Нові глави <Link to="/catalog" search={{ sort: 'updated' }} className={home.titleLink}>усі</Link>
                        </h2>
                        <div className={home.panel}>
                            {newChapters.map(({ card, firstNumber, lastNumber, publishedAt, firstLabel, lastLabel }) => {
                                const when = relativeTime(new Date(publishedAt));
                                return (
                                    <Link key={card.editionId} className={home.freshRow} to="/n/$slug/$number"
                                        params={{ slug: card.novelSlug, number: String(firstNumber) }} search={teamSearch(card)}>
                                        <Cover url={card.coverUrl} title={card.title} seed={card.novelSlug} width={40} />
                                        <span className={home.freshText}>
                                            <span className={home.freshTitle}>{card.title}</span>
                                            <span className={home.muted}>
                                                {chapterRange(firstNumber, lastNumber, firstLabel, lastLabel)}
                                                {card.kind === 'machine' || card.kind === 'mixed' ? <span title="Машинний переклад"> · ШІ</span> : null}
                                            </span>
                                        </span>
                                        <span className={`${home.small} ${home.wideOnly}`}>${card.teamHandle}</span>
                                        <span className={`${home.small} ${home.when}`}>{when}</span>
                                    </Link>
                                );
                            })}
                        </div>
                    </section>
                )}

                <div className={home.side}>
                    {popular.length > 0 && (
                        <section aria-labelledby="home-popular">
                            <h2 id="home-popular" className={home.title}>
                                Читають цього тижня <Link to="/catalog" className={home.titleLink}>Каталог</Link>
                            </h2>
                            <div className={home.popular}>
                                {popular.slice(0, 6).map((card) => (
                                    <Link key={card.editionId} className={home.popularItem} to="/n/$slug"
                                        params={{ slug: card.novelSlug }} search={teamSearch(card)}>
                                        <span className={home.fluidCover}>
                                            <Cover url={card.coverUrl} title={card.title} seed={card.novelSlug} width={104} />
                                        </span>
                                        <span className={home.popularTitle}>{card.title}</span>
                                    </Link>
                                ))}
                            </div>
                        </section>
                    )}
                    {(tags.data?.length ?? 0) > 0 && (
                        <section aria-labelledby="home-tags">
                            <h2 id="home-tags" className={home.title}>
                                Популярні теги <Link to="/catalog" className={home.titleLink}>каталог</Link>
                            </h2>
                            <div className={styles.chips}>
                                {tags.data?.slice(0, 16).map((tag) => (
                                    <Link key={tag.slug} className={styles.chip} to="/catalog" search={{ tags: [tag.slug] }}>
                                        {tag.name} <span className={styles.chipCount}>{tag.novels}</span>
                                    </Link>
                                ))}
                            </div>
                        </section>
                    )}
                    <section aria-labelledby="home-propose">
                        <h2 id="home-propose" className={home.title}>Що перекласти далі</h2>
                        <p className={`${home.muted} ${home.note}`}>
                            Запропонуйте новелу посиланням або проголосуйте за ті, які хочете читати.
                        </p>
                        <LinkButton to="/proposals" variant="secondary">Що перекласти</LinkButton>
                    </section>
                </div>
            </div>
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
                    {card.sourceChapters ? `${card.chapterCount} / ${card.sourceChapters} ${chaptersWord(card.sourceChapters)}` : `${card.chapterCount} ${chaptersWord(card.chapterCount)}`}
                    {card.kind === 'machine' || card.kind === 'mixed' ? ' · ШІ' : card.kind === 'original' ? ' · оригінальний твір' : ''}
                    {card.adult ? ' · 18+' : ''}
                    {card.rating && card.ratings ? <span title={`Середня оцінка з ${card.ratings}`}> · ★ {card.rating.toFixed(1)}</span> : null}
                </div>
                {card.read ? <ReadBar read={card.read} total={card.chapterCount} /> : null}
            </div>
        </Link>
    );
}

/** How much of the translation the viewer has read: a thin bar under the card. */
function ReadBar({ read, total }: { read: number; total: number }) {
    const share = Math.min(100, Math.round((read / Math.max(1, total)) * 100));
    return (
        <div className={styles.readBar} role="progressbar" aria-label={`Прочитано ${read} з ${total}`} aria-valuemin={0} aria-valuemax={100}
            aria-valuenow={share} title={`Прочитано ${read} з ${total}`}>
            <span style={{ width: `${share}%` }} />
        </div>
    );
}

/** «Глави 0–1» with the numbers readers see, not the positions. */
function chapterRange(first: number, last: number, firstLabel?: string | null, lastLabel?: string | null) {
    const a = firstLabel || (firstLabel === '' ? null : String(first));
    const b = lastLabel || (lastLabel === '' ? null : String(last));
    if (first === last) return a ? `Глава ${a}` : 'Нова глава';
    // A prologue or side story has no number: name the range by the numbered end.
    if (a && b) return `Глави ${a}–${b}`;
    if (b) return `Глави до ${b}`;
    if (a) return `Глави від ${a}`;
    return `Нових глав: ${last - first + 1}`;
}
