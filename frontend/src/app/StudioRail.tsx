import { useQuery } from '@tanstack/react-query';
import { Link, useRouterState } from '@tanstack/react-router';
import { useCanRun } from '../ledger/api';
import { Cover } from '../reading/Cover';
import { studioApi } from '../studio/api';
import styles from './StudioRail.module.css';

/**
 * The Studio's side menu on a wide screen (етап 15): every translation of the person and the
 * sections of the open one, so moving between them takes one click. Phones keep the page menus.
 */
export function StudioRail() {
    const path = useRouterState({ select: (state) => state.location.pathname });
    const mine = useQuery({ queryKey: ['studio'], queryFn: studioApi.mine });
    const openId = Number(/^\/studio\/(\d+)/.exec(path)?.[1] ?? 0);
    const open = mine.data?.find((item) => item.editionId === openId);
    const at = (to: string) => (path === to ? styles.on : undefined);
    const base = `/studio/${openId}`;
    const params = { editionId: String(openId) };
    // Autotranslation and its glossary show only to those who can run it (шаги or the site owner).
    const machine = useCanRun() && (open?.kind === 'machine' || open?.kind === 'mixed');
    const translator = open !== undefined && open.role !== 'editor';
    return (
        <nav className={styles.rail} aria-label="Студія">
            <div className={styles.label}>Мої переклади</div>
            {mine.data?.map((item) => (
                <Link key={item.editionId} to="/studio/$editionId" params={{ editionId: String(item.editionId) }}
                    className={`${styles.item} ${item.editionId === openId ? styles.on : ''}`}>
                    <Cover url={item.coverUrl} title={item.title} seed={item.novelSlug} width={24} />
                    <span className={styles.name}>{item.title}</span>
                    {item.pendingSuggestions > 0 && <span className={styles.badge} title="Правки на перевірку">{item.pendingSuggestions}</span>}
                </Link>
            ))}
            <Link to="/studio/new" className={`${styles.item} ${at('/studio/new') ?? ''}`}>＋ Нова публікація</Link>
            <Link to="/studio/processes" className={`${styles.item} ${at('/studio/processes') ?? ''}`}>Процеси</Link>
            <Link to="/studio/teams" className={`${styles.item} ${at('/studio/teams') ?? ''}`}>Мої команди</Link>

            {open && (
                <>
                    <div className={styles.label}>{open.title}</div>
                    <Link to="/studio/$editionId" params={params} className={`${styles.item} ${at(base) ?? ''}`}>Глави</Link>
                    {machine && translator && <Link to="/studio/$editionId/translate" params={params} className={`${styles.item} ${at(`${base}/translate`) ?? ''}`}>Автопереклад</Link>}
                    {translator && <Link to="/studio/$editionId/structure" params={params} className={`${styles.item} ${at(`${base}/structure`) ?? ''}`}>Структура й томи</Link>}
                    {machine && <Link to="/studio/$editionId/glossary" params={params} className={`${styles.item} ${at(`${base}/glossary`) ?? ''}`}>Словник</Link>}
                    {machine && <Link to="/studio/$editionId/titles" params={params} className={`${styles.item} ${at(`${base}/titles`) ?? ''}`}>Назви глав</Link>}
                    {translator && <Link to="/studio/$editionId/import" params={params} className={`${styles.item} ${at(`${base}/import`) ?? ''}`}>Глави з файлу</Link>}
                    {open.role === 'owner' && <Link to="/studio/$editionId/about" params={params} className={`${styles.item} ${at(`${base}/about`) ?? ''}`}>Дані й обкладинка</Link>}
                    {open.role === 'owner' && open.kind !== 'original' && <Link to="/studio/$editionId/relay" params={params} className={`${styles.item} ${at(`${base}/relay`) ?? ''}`}>Естафета</Link>}
                    <Link to="/team/$handle" params={{ handle: open.teamHandle }} className={styles.item}>Команда ${open.teamHandle}</Link>
                    <Link to="/n/$slug" params={{ slug: open.novelSlug }} search={{ t: open.teamHandle }} className={styles.item}>Як бачать читачі ›</Link>
                </>
            )}
        </nav>
    );
}
