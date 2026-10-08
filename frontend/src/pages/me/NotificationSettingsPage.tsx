import { Link } from '@tanstack/react-router';
import { TelegramSection } from './TelegramSection';
import styles from '../pages.module.css';

/** «Сповіщення»: where news reaches the person besides «Вхідні» on the site. */
export function NotificationSettingsPage() {
    return (
        <section className={styles.narrow}>
            <Link to="/me" className={styles.muted}>‹ Я</Link>
            <h1 className={styles.title}>Сповіщення</h1>
            <p className={styles.lead}>
                На сайті відповіді, згадки, правки, нові глави з підписок і повідомлення збираються у <Link to="/inbox">«Вхідних»</Link>.
            </p>
            <TelegramSection />
        </section>
    );
}
