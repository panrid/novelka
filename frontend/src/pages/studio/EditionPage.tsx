import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from '@tanstack/react-router';
import { Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useMe } from '../../auth/me';
import { Cover } from '../../reading/Cover';
import { STATUS_LABELS, chapterHeading, chaptersWord, type Status } from '../../reading/api';
import { ROLE_LABELS, studioApi } from '../../studio/api';
import { suggestionApi } from '../../reading/suggestions';
import { Button } from '../../ui/Button';
import { LinkButton } from '../../ui/LinkButton';
import { Notice } from '../../ui/Notice';
import { relativeTime } from '../../lib/dates';
import { changes, characters, paragraphs } from '../../lib/plural';
import styles from './studio.module.css';
import { askConfirm } from '../../ui/ask';

export function useEditionId() {
    const { editionId } = useParams({ strict: false }) as { editionId: string };
    return Number(editionId);
}

export function EditionPage() {
    const id = useEditionId();
    const navigate = useNavigate();
    const me = useMe();
    const client = useQueryClient();
    const overview = useQuery({ queryKey: ['studio-edition', id], queryFn: () => studioApi.overview(id) });
    const [page, setPage] = useState(1);
    const chapters = useQuery({ meta: { errorToast: true }, queryKey: ['studio-chapters', id, page], queryFn: () => studioApi.chapters(id, page), placeholderData: (p) => p });
    const remove = useMutation({
        mutationFn: (number: number) => studioApi.deleteChapter(id, number),
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: ['studio-chapters', id] });
            void client.invalidateQueries({ queryKey: ['studio-edition', id] });
        },
    });
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
    const siteOwner = me?.role === 'owner';
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
                {siteOwner && translator && edition.kind === 'machine' && <Link to="/studio/$editionId/translate" params={params} className={styles.menuItem}>Автопереклад</Link>}
                {edition.kind === 'machine' && <Link to="/studio/$editionId/glossary" params={params} className={styles.menuItem}>Словник</Link>}
                {edition.kind === 'machine' && <Link to="/studio/$editionId/titles" params={params} className={styles.menuItem}>Назви глав</Link>}
                {owner && edition.kind !== 'original' && <Link to="/studio/$editionId/relay" params={params} className={styles.menuItem}>Естафета</Link>}
            </nav>

            {(queue.data?.length ?? 0) > 0 && (
                <>
                    <h2 className={styles.sectionTitle}>Правки на перевірку</h2>
                    {queue.data!.map((row) => (
                        <Link key={row.number} className={styles.row} to="/n/$slug/$number"
                            params={{ slug: edition.novelSlug, number: String(row.number) }} search={{ t: edition.teamHandle }}>
                            <div className={styles.grow}>{chapterHeading(row)}</div>
                            <span className={`${styles.badge} ${styles.badgeOn}`}>{row.pending}</span>
                        </Link>
                    ))}
                </>
            )}

            <h2 className={styles.sectionTitle}>Глави</h2>
            {chapters.data?.length === 0 && <p className={styles.muted}>Глав ще немає.</p>}
            {remove.isError && <Notice tone="error">{remove.error.message}</Notice>}
            {chapters.data?.map((chapter) => (
                <div key={chapter.number} className={styles.row}>
                    <Link className={`${styles.grow} ${styles.rowLink}`}
                        to="/studio/$editionId/chapters/$number" params={{ editionId: String(id), number: String(chapter.number) }}>
                        <div className={styles.ellipsis}>{chapterHeading(chapter)}</div>
                        <div className={styles.muted}>{relativeTime(new Date(chapter.updatedAt))}</div>
                    </Link>
                    {!chapter.published && <span className={styles.badge}>не опубліковано</span>}
                    {chapter.hasMyDraft && <span className={`${styles.badge} ${styles.badgeOn}`}>чернетка</span>}
                    {translator && !chapter.published && (
                        <button type="button" className={styles.iconButton} aria-label={`Видалити главу ${chapter.number}`}
                            onClick={() => void askConfirm({ title: 'Видалити главу?', text: 'Вона ще не опублікована.', confirmLabel: 'Видалити', danger: true })
                                .then((yes) => { if (yes) remove.mutate(chapter.number); })}>
                            <Trash2 size={18} aria-hidden />
                        </button>
                    )}
                </div>
            ))}
            {(page > 1 || chapters.data?.length === 100) && (
                <div className={styles.actions}>
                    {page > 1 && <Button variant="secondary" onPress={() => setPage(page - 1)}>← Новіші</Button>}
                    {chapters.data?.length === 100 && <Button variant="secondary" onPress={() => setPage(page + 1)}>Давніші →</Button>}
                </div>
            )}

            {(contributions.data?.length ?? 0) > 0 && (
                <>
                    <h2 className={styles.sectionTitle}>Внесок</h2>
                    <table className={styles.table}>
                        <tbody>
                            {contributions.data!.map((row) => (
                                <tr key={row.nick}>
                                    <td>{row.nick}</td>
                                    <td>{changes(row.revisions)} · {paragraphs(row.blocksChanged)} · {characters(row.charsChanged)}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </>
            )}
        </section>
    );
}
