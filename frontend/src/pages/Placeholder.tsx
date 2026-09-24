import styles from './Placeholder.module.css';

/** Temporary page body for sections that later stages fill in. */
export function Placeholder({ title, text }: { title: string; text: string }) {
    return (
        <section className={styles.page}>
            <h1 className={styles.title}>{title}</h1>
            <p className={styles.text}>{text}</p>
        </section>
    );
}
