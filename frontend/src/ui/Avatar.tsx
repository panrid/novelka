import styles from './ui.module.css';

const COLORS = ['#5c6e3c', '#3c5a73', '#7a4b5c', '#8a6a3a', '#4b4d7a', '#3f6b63'];

/** A person's picture, or the first letter of the nick on a colour picked from the nick. */
export function Avatar({ nick, url, size = 40 }: { nick: string; url: string | null | undefined; size?: number }) {
    const color = COLORS[[...nick].reduce((sum, char) => sum + char.codePointAt(0)!, 0) % COLORS.length];
    return (
        <span
            className={styles.avatar}
            style={{ width: size, height: size, fontSize: size * 0.42, ['--avatar-bg' as string]: color }}
            aria-hidden
        >
            {url ? <img src={url} alt="" width={size} height={size} /> : [...nick][0]}
        </span>
    );
}
