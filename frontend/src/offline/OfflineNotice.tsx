import { useOffline } from './status';
import styles from './offline.module.css';

/** A quiet line at the top while the site shows what the phone kept. */
export function OfflineNotice() {
    const offline = useOffline();
    if (!offline) return null;
    return (
        <div className={styles.notice} role="status">
            Ви офлайн — показуємо збережені глави
        </div>
    );
}
