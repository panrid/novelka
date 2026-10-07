import { useQuery } from '@tanstack/react-query';
import { Link, useRouterState } from '@tanstack/react-router';
import { Cover } from '../reading/Cover';
import { studioApi } from '../studio/api';
import styles from './StudioRail.module.css';

/**
 * The Studio's side menu on a wide screen: every translation of the person, so moving between them
 * takes one click. The sections of the open one are its tabs.
 */
export function StudioRail() {
    const path = useRouterState({ select: (state) => state.location.pathname });
    const mine = useQuery({ queryKey: ['studio'], queryFn: studioApi.mine });
    const openId = Number(/^\/studio\/(\d+)/.exec(path)?.[1] ?? 0);
    const at = (to: string) => (path === to ? styles.on : undefined);
    return (
        <nav className={styles.rail} aria-label="Студія">
            <div className={styles.label}>Мої переклади</div>
            {mine.data?.map((item) => (
                <Link key={item.editionId} to="/studio/$editionId" params={{ editionId: String(item.editionId) }}
                    className={`${styles.item} ${item.editionId === openId ? styles.on : ''}`}>
                    <Cover url={item.coverUrl} title={item.title} seed={item.novelSlug} width={24} />
                    <span className={styles.name}>{item.title}</span>
                    {item.jobState === 'running' || item.jobState === 'queued'
                        ? <span className={styles.dot} title="Іде автопереклад" />
                        : item.pendingSuggestions > 0 && <span className={styles.badge} title="Правки на перевірку">{item.pendingSuggestions}</span>}
                </Link>
            ))}
            <Link to="/studio/new" className={`${styles.item} ${at('/studio/new') ?? ''}`}>＋ Нова публікація</Link>
            <Link to="/studio/processes" className={`${styles.item} ${at('/studio/processes') ?? ''}`}>Процеси</Link>
            <Link to="/studio/teams" className={`${styles.item} ${at('/studio/teams') ?? ''}`}>Мої команди</Link>

        </nav>
    );
}
