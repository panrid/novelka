import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useCanRun, useMyShahs } from '../ledger/api';
import { autotranslateApi, shahWord } from '../studio/autotranslate';
import styles from './StudioBar.module.css';

/**
 * The Studio's own header (етап 20, рішення 18): it looks like an app of its own at the same address —
 * «СТУДІЯ», «Мої переклади · Хід перекладу · Команди · Кошик», the balance, «+ Новий переклад»,
 * «На сайт». The chapter's workspace draws its own bar instead.
 */
export function StudioBar() {
    const canRun = useCanRun();
    const shahs = useMyShahs();
    const active = useQuery({
        queryKey: ['processes', 'active-count'],
        queryFn: () => autotranslateApi.processes({ state: 'active', page: 1 }),
        enabled: canRun,
        refetchInterval: 30_000,
    });
    const running = active.data?.total ?? 0;
    const balance = canRun ? shahs.data?.available ?? null : null;
    return (
        <div className={styles.bar}>
            <Link to="/studio" className={styles.mark} aria-label="Студія, мої переклади">СТУДІЯ</Link>
            <nav className={styles.nav} aria-label="Меню Студії">
                <Link to="/studio" className={styles.item} activeProps={{ className: styles.on }} activeOptions={{ exact: true }}>
                    Мої переклади
                </Link>
                {canRun && (
                    <Link to="/studio/processes" className={styles.item} activeProps={{ className: styles.on }}>
                        Хід перекладу{running > 0 ? ` · ${running}` : ''}
                    </Link>
                )}
                <Link to="/studio/teams" className={styles.item} activeProps={{ className: styles.on }}>Команди</Link>
                <Link to="/studio/trash" className={styles.item} activeProps={{ className: styles.on }}>Кошик</Link>
            </nav>
            {balance !== null && (
                <Link to="/me/shahs" className={styles.balance}><b>{balance} {shahWord(balance)}</b> на рахунку</Link>
            )}
            <Link to="/studio/new" className={styles.create}>+ Новий переклад</Link>
            <Link to="/" className={styles.site}>На сайт</Link>
        </div>
    );
}
