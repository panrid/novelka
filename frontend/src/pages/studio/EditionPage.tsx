import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from '@tanstack/react-router';
import { Cover } from '../../reading/Cover';
import { STATUS_LABELS, chaptersWord, type Status } from '../../reading/api';
import { ROLE_LABELS, studioApi } from '../../studio/api';
import { suggestionApi } from '../../reading/suggestions';
import { Button } from '../../ui/Button';
import { LinkButton } from '../../ui/LinkButton';
import { Notice } from '../../ui/Notice';
import { relativeTime } from '../../lib/dates';
import styles from './studio.module.css';

export function useEditionId() {
    const { editionId } = useParams({ strict: false }) as { editionId: string };
    return Number(editionId);
}

export function EditionPage() {
    const id = useEditionId();
    const navigate = useNavigate();
    const client = useQueryClient();
    const overview = useQuery({ queryKey: ['studio-edition', id], queryFn: () => studioApi.overview(id) });
    const chapters = useQuery({ queryKey: ['studio-chapters', id], queryFn: () => studioApi.chapters(id) });
    const contributions = useQuery({ queryKey: ['studio-contributions', id], queryFn: () => studioApi.contributions(id) });
    const queue = useQuery({ queryKey: ['suggestion-queue', id], queryFn: () => suggestionApi.queue(id) });
    const add = useMutation({
        mutationFn: () => studioApi.newChapter(id),
        onSuccess: ({ number }) => {
            void client.invalidateQueries({ queryKey: ['studio-chapters', id] });
            void navigate({ to: '/studio/$editionId/chapters/$number', params: { editionId: String(id), number: String(number) } });
        },
    });

    if (overview.isError) return <Notice tone="error">{overview.error.message}</Notice>;
    if (!overview.data) return <p className={styles.muted} style={{ paddingTop: 24 }}>Завантажуємо…</p>;
    const edition = overview.data;
    const translator = edition.role !== 'editor';
    const owner = edition.role === 'owner';
    const params = { editionId: String(id) };

    return (
        <section className={styles.page}>
            <div className={styles.head}>
                <Cover url={edition.coverUrl} title={edition.title} seed={edition.novelSlug} width={72} />
                <div className={styles.grow}>
                    <h1 className={styles.title}>{edition.title}</h1>
                    <p className={styles.muted}>
                        ${edition.teamHandle} · ви {ROLE_LABELS[edition.role]} · {STATUS_LABELS[edition.status as Status]}
                    </p>
                    <p className={styles.muted}>{edition.chapterCount} {chaptersWord(edition.chapterCount)} опубліковано</p>
                </div>
            </div>

            <div className={styles.actions}>
                {translator && <Button onPress={() => add.mutate()} pending={add.isPending} pendingLabel="Створюємо…">Нова глава</Button>}
                {translator && <LinkButton to="/studio/$editionId/import" params={params} variant="secondary">З файлу</LinkButton>}
                {edition.chapterCount > 0 && <LinkButton to="/n/$slug" params={{ slug: edition.novelSlug }} search={{ t: edition.teamHandle }} variant="secondary">Як бачать читачі</LinkButton>}
            </div>
            {add.isError && <Notice tone="error">{add.error.message}</Notice>}

            <nav className={styles.menu} aria-label="Керування">
                {owner && <Link to="/studio/$editionId/about" params={params} className={styles.menuItem}>Дані й обкладинка</Link>}
                <Link to="/team/$handle" params={{ handle: edition.teamHandle }} className={styles.menuItem}>Команда ${edition.teamHandle}</Link>
                {owner && edition.kind !== 'original' && <Link to="/studio/$editionId/relay" params={params} className={styles.menuItem}>Естафета</Link>}
            </nav>

            {(queue.data?.length ?? 0) > 0 && (
                <>
                    <h2 className={styles.sectionTitle}>Правки на перевірку</h2>
                    {queue.data!.map((row) => (
                        <Link key={row.number} className={styles.row} to="/n/$slug/$number"
                            params={{ slug: edition.novelSlug, number: String(row.number) }} search={{ t: edition.teamHandle }}>
                            <div className={styles.grow}>{row.number}. {row.title}</div>
                            <span className={`${styles.badge} ${styles.badgeOn}`}>{row.pending}</span>
                        </Link>
                    ))}
                </>
            )}

            <h2 className={styles.sectionTitle}>Глави</h2>
            {chapters.data?.length === 0 && <p className={styles.muted}>Глав ще немає.</p>}
            {chapters.data?.map((chapter) => (
                <Link key={chapter.number} className={styles.row}
                    to="/studio/$editionId/chapters/$number" params={{ editionId: String(id), number: String(chapter.number) }}>
                    <div className={styles.grow}>
                        <div className={styles.ellipsis}>{chapter.number}. {chapter.title || 'Без назви'}</div>
                        <div className={styles.muted}>{relativeTime(new Date(chapter.updatedAt))}</div>
                    </div>
                    {!chapter.published && <span className={styles.badge}>не опубліковано</span>}
                    {chapter.hasMyDraft && <span className={`${styles.badge} ${styles.badgeOn}`}>чернетка</span>}
                </Link>
            ))}

            {(contributions.data?.length ?? 0) > 0 && (
                <>
                    <h2 className={styles.sectionTitle}>Внесок</h2>
                    <table className={styles.table}>
                        <tbody>
                            {contributions.data!.map((row) => (
                                <tr key={row.nick}>
                                    <td>{row.nick}</td>
                                    <td>{row.revisions} змін · {row.blocksChanged} абзаців · {row.charsChanged} знаків</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </>
            )}
        </section>
    );
}
