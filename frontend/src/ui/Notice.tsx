import type { ReactNode } from 'react';
import styles from './ui.module.css';

/** A message block. Errors are announced to screen readers immediately. */
export function Notice({ tone, children }: { tone: 'error' | 'success' | 'info'; children: ReactNode }) {
    return (
        <div className={`${styles.notice} ${styles[tone]}`} role={tone === 'error' ? 'alert' : 'status'}>
            {children}
        </div>
    );
}
