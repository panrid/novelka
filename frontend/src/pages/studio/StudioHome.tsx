import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { Cover } from '../../reading/Cover';
import { chaptersWord } from '../../reading/api';
import { ROLE_LABELS, studioApi } from '../../studio/api';
import { LinkButton } from '../../ui/LinkButton';
import { Notice } from '../../ui/Notice';
import styles from './studio.module.css';

export function StudioHome() {
    const mine = useQuery({ queryKey: ['studio'], queryFn: studioApi.mine });
    return (
        <section className={styles.page}>
            <h1 className={styles.title}>Студія</h1>
            <p className={styles.muted}>Ваші переклади й твори: власні та ті, де ви в команді.</p>
            <div className={styles.actions}>
                <LinkButton to="/studio/new">Нова публікація</LinkButton>
                <LinkButton to="/studio/teams" variant="secondary">Мої команди</LinkButton>
            </div>
            {mine.isError && <Notice tone="error">{mine.error.message}</Notice>}
            {mine.isSuccess && mine.data.length === 0 && (
                <p className={styles.muted}>Поки порожньо. Почніть із «Нова публікація»: свій переклад або свій твір.</p>
            )}
            {mine.data?.map((item) => (
                <Link key={item.editionId} to="/studio/$editionId" params={{ editionId: String(item.editionId) }} className={styles.row}>
                    <Cover url={item.coverUrl} title={item.title} seed={item.novelSlug} width={44} />
                    <div className={styles.grow}>
                        <div className={styles.ellipsis} style={{ fontWeight: 500 }}>{item.title}</div>
                        <div className={styles.muted}>
                            {item.chapterCount} {chaptersWord(item.chapterCount)} · ${item.teamHandle} · {ROLE_LABELS[item.role]}
                        </div>
                    </div>
                    {item.drafts > 0 && <span className={`${styles.badge} ${styles.badgeOn}`}>чернеток: {item.drafts}</span>}
                </Link>
            ))}
        </section>
    );
}
