import { useState, type ReactNode } from 'react';
import styles from './collapsible.module.css';

function remembered(key: string, fallback: boolean): boolean {
    try {
        const saved = localStorage.getItem(`novelka:open:${key}`);
        return saved === null ? fallback : saved === '1';
    } catch {
        return fallback;
    }
}

/**
 * A section that folds to its title: «▸ Глави · 12». The reader's choice stays in this browser
 * (key), so a list someone keeps open opens again; the default is the page's own (closed for
 * long lists one rarely needs, such as the latest chapters in the studio).
 */
export function Collapsible({ id, title, count, defaultOpen = false, children }: {
    id: string; title: string; count?: number | undefined; defaultOpen?: boolean; children: ReactNode;
}) {
    const [open, setOpen] = useState(() => remembered(id, defaultOpen));
    const toggle = () => {
        setOpen(!open);
        try {
            localStorage.setItem(`novelka:open:${id}`, open ? '0' : '1');
        } catch {
            // Private mode: the section still folds, it just is not remembered.
        }
    };
    return (
        <section className={styles.section}>
            <h2 className={styles.heading}>
                <button type="button" className={styles.toggle} aria-expanded={open} onClick={toggle}>
                    <span aria-hidden className={open ? styles.arrowOpen : styles.arrow}>▸</span>
                    {title}
                    {count !== undefined && <span className={styles.count}>{count}</span>}
                </button>
            </h2>
            {open && <div className={styles.body}>{children}</div>}
        </section>
    );
}
