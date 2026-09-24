import { Link, type LinkComponentProps } from '@tanstack/react-router';
import styles from './ui.module.css';

/** A link that looks like a button (navigation, not an action). */
export function LinkButton({ variant = 'primary', wide = false, ...props }: LinkComponentProps & {
    variant?: 'primary' | 'secondary';
    wide?: boolean;
}) {
    return <Link {...props} className={[styles.button, styles[variant], wide ? styles.wide : ''].join(' ')} />;
}
