import { Link } from '@tanstack/react-router';
import styles from './editionShell.module.css';

/** The two halves of «Словник»: names and terms, and chapter titles from analysis. */
export function WordsSwitch({ editionId, at }: { editionId: number; at: 'glossary' | 'titles' }) {
    const params = { editionId: String(editionId) };
    return (
        <nav className={styles.switch} aria-label="Що в словнику">
            <Link to="/studio/$editionId/glossary" params={params} className={at === 'glossary' ? styles.switchOn : styles.switchItem}
                aria-current={at === 'glossary' ? 'page' : undefined}>Імена й терміни</Link>
            <Link to="/studio/$editionId/titles" params={params} className={at === 'titles' ? styles.switchOn : styles.switchItem}
                aria-current={at === 'titles' ? 'page' : undefined}>Назви глав</Link>
        </nav>
    );
}
