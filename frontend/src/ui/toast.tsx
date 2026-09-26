import { useEffect, useSyncExternalStore } from 'react';
import styles from './ui.module.css';

/*
 * A short message at the bottom of the screen for actions that have no place of their own to
 * say something went wrong (a tap on a star, «Вийти з команди»). Mutations opt in with
 * meta: { errorToast: true }; the query client shows their errors here.
 */

type Toast = { id: number; text: string };

let current: Toast | null = null;
let shown = 0;
const listeners = new Set<() => void>();

export function showError(text: string) {
    current = { id: ++shown, text };
    listeners.forEach((listener) => listener());
}

function hide(id: number) {
    if (current?.id === id) {
        current = null;
        listeners.forEach((listener) => listener());
    }
}

const subscribe = (listener: () => void) => {
    listeners.add(listener);
    return () => void listeners.delete(listener);
};

export function ToastHost() {
    const toast = useSyncExternalStore(subscribe, () => current);
    useEffect(() => {
        if (!toast) return;
        const timer = window.setTimeout(() => hide(toast.id), 6000);
        return () => window.clearTimeout(timer);
    }, [toast]);
    if (!toast) return null;
    return (
        <div className={styles.toast} role="alert">
            <span>{toast.text}</span>
            <button type="button" aria-label="Закрити" onClick={() => hide(toast.id)}>✕</button>
        </div>
    );
}
