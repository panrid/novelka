import { Link, useRouter } from '@tanstack/react-router';
import styles from './Placeholder.module.css';

export function NotFoundPage() {
    return (
        <section className={styles.page}>
            <h1 className={styles.title}>Такої сторінки немає</h1>
            <p className={styles.text}>Можливо, посилання застаріло або в адресі помилка.</p>
            <p className={styles.actions}>
                <Link to="/" className={styles.button}>На головну</Link>
            </p>
        </section>
    );
}

export function ErrorPage() {
    const router = useRouter();
    return (
        <section className={styles.page} role="alert">
            <h1 className={styles.title}>Щось пішло не так</h1>
            <p className={styles.text}>Сторінка не завантажилась. Спробуйте ще раз — зазвичай це допомагає.</p>
            <p className={styles.actions}>
                <button type="button" className={styles.button} onClick={() => void router.invalidate()}>
                    Спробувати ще раз
                </button>
            </p>
        </section>
    );
}

/** While a page waits for its data (slow network, the server restarting): never a blank screen. */
export function PendingPage() {
    return (
        <section className={styles.page} aria-busy="true">
            <p className={styles.text}>Завантажуємо…</p>
        </section>
    );
}
