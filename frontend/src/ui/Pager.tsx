import styles from './ui.module.css';

/**
 * Pages of a long list: «‹ 1 … 5 6 7 … 42 ›», and for many pages a list to jump straight to any
 * of them. `range` names what a page holds («глави 41–60»), so the jump says where it leads.
 */
export function Pager({ page, total, size, onPage, range }: {
    page: number; total: number; size: number; onPage: (page: number) => void;
    range?: (page: number) => string;
}) {
    const pages = Math.max(1, Math.ceil(total / size));
    if (pages <= 1) return null;
    const shown = numbers(page, pages);
    return (
        <nav className={styles.pager} aria-label="Сторінки">
            <div className={styles.pagerRow}>
                <button type="button" disabled={page <= 1} onClick={() => onPage(page - 1)} aria-label="Попередня сторінка">‹</button>
                {shown.map((item, index) => item === null
                    ? <span key={`gap-${index}`} className={styles.pagerGap} aria-hidden>…</span>
                    : (
                        <button key={item} type="button" aria-current={item === page ? 'page' : undefined}
                            aria-label={`Сторінка ${item}${range ? `, ${range(item)}` : ''}`} onClick={() => onPage(item)}>
                            {item}
                        </button>
                    ))}
                <button type="button" disabled={page >= pages} onClick={() => onPage(page + 1)} aria-label="Наступна сторінка">›</button>
            </div>
            {pages > 7 && (
                <select className={styles.pagerJump} aria-label="Перейти до сторінки" value={page}
                    onChange={(event) => onPage(Number(event.target.value))}>
                    {Array.from({ length: pages }, (_, index) => index + 1).map((item) => (
                        <option key={item} value={item}>сторінка {item} з {pages}{range ? ` · ${range(item)}` : ''}</option>
                    ))}
                </select>
            )}
        </nav>
    );
}

/** All pages up to seven; otherwise the first, the last and the ones around the current, gaps as null. */
function numbers(page: number, pages: number): (number | null)[] {
    if (pages <= 7) return Array.from({ length: pages }, (_, index) => index + 1);
    const from = Math.max(2, Math.min(page - 1, pages - 4));
    const to = Math.min(pages - 1, Math.max(page + 1, 5));
    const middle = Array.from({ length: to - from + 1 }, (_, index) => from + index);
    return [1, ...(from > 2 ? [null] : []), ...middle, ...(to < pages - 1 ? [null] : []), pages];
}
