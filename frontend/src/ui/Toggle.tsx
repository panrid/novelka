import { Switch } from 'react-aria-components';
import styles from './ui.module.css';

export function Toggle({ label, isSelected, onChange }: { label: string; isSelected: boolean; onChange: (value: boolean) => void }) {
    return (
        <Switch className={styles.switchRow} isSelected={isSelected} onChange={onChange}>
            <span>{label}</span>
            <span className={styles.track} aria-hidden>
                <span className={styles.thumb} />
            </span>
        </Switch>
    );
}
