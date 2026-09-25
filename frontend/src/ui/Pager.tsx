import styles from './ui.module.css';

/** «← Попередня · сторінка N з M · Наступна →» for long lists. */
export function Pager({ page, total, size, onPage }: { page: number; total: number; size: number; onPage: (page: number) => void }) {
    const pages = Math.max(1, Math.ceil(total / size));
    if (pages <= 1) return null;
    return (
        <nav className={styles.pager} aria-label="Сторінки">
            <button type="button" disabled={page <= 1} onClick={() => onPage(page - 1)}>← Попередня</button>
            <span>сторінка {page} з {pages}</span>
            <button type="button" disabled={page >= pages} onClick={() => onPage(page + 1)}>Наступна →</button>
        </nav>
    );
}
