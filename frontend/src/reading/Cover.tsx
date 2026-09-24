import styles from './Cover.module.css';

const PALETTES = [
    ['#3c5a73', '#1d2b38'], ['#7a4b5c', '#2d1a22'], ['#5c6e3c', '#1f2915'],
    ['#8a6a3a', '#2e2213'], ['#4b4d7a', '#1a1b2f'], ['#3f6b63', '#15261f'],
];

/** The team's cover, or a calm placeholder with the title until they upload one. */
export function Cover({ url, title, seed, width }: { url: string | null; title: string; seed: string; width: number }) {
    if (url) {
        return <img className={styles.cover} src={url} alt="" width={width} height={Math.round(width * 1.5)} loading="lazy" />;
    }
    const [from, to] = PALETTES[[...seed].reduce((sum, char) => sum + char.codePointAt(0)!, 0) % PALETTES.length];
    return (
        <span className={styles.placeholder} style={{ width, background: `linear-gradient(160deg, ${from}, ${to})` }} aria-hidden>
            {width >= 90 && <span className={styles.title}>{title}</span>}
        </span>
    );
}
