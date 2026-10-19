import { useEffect, useState } from 'react';
import styles from './Loading.module.css';

/** The shape of what is coming, so nothing jumps when it arrives. */
type Shape = 'page' | 'list' | 'lines' | 'inline';

const QUIET_MS = 300;
const SLOW_MS = 8_000;

/**
 * Waiting without «Завантажуємо…» (етап 20, рішення 16): nothing for the first 0.3 s, then grey
 * shapes of the coming content that pulse quietly (still, when motion is turned off), and from
 * 8 s «Довше, ніж зазвичай» with «Спробувати ще раз»; without the internet it says so.
 */
export function Loading({ shape = 'page', onRetry }: { shape?: Shape; onRetry?: (() => void) | undefined }) {
    const [stage, setStage] = useState<'quiet' | 'shapes' | 'slow'>('quiet');
    useEffect(() => {
        const shown = setTimeout(() => setStage('shapes'), QUIET_MS);
        const slow = setTimeout(() => setStage('slow'), SLOW_MS);
        return () => {
            clearTimeout(shown);
            clearTimeout(slow);
        };
    }, []);
    const offline = typeof navigator !== 'undefined' && navigator.onLine === false;
    return (
        <div className={shape === 'inline' ? styles.inline : styles.wrap} role="status" aria-busy="true">
            <span className="visually-hidden">Завантажуємо</span>
            {stage !== 'quiet' && <Shapes shape={shape} />}
            {stage === 'slow' && (
                <div className={styles.slow}>
                    <p>{offline ? 'Немає інтернету. Збережене в браузері можна читати далі.' : 'Довше, ніж зазвичай.'}</p>
                    <button type="button" className={styles.retry} onClick={() => (onRetry ? onRetry() : window.location.reload())}>
                        Спробувати ще раз
                    </button>
                </div>
            )}
        </div>
    );
}

function Shapes({ shape }: { shape: Shape }) {
    if (shape === 'inline') return <span className={`${styles.bar} ${styles.short}`} />;
    if (shape === 'lines') {
        return <>{[90, 100, 70].map((width) => <span key={width} className={styles.bar} style={{ width: `${width}%` }} />)}</>;
    }
    if (shape === 'list') {
        return <>{[0, 1, 2, 3, 4].map((row) => (
            <span key={row} className={styles.row}>
                <span className={styles.thumb} />
                <span className={styles.rowText}>
                    <span className={styles.bar} style={{ width: '60%' }} />
                    <span className={`${styles.bar} ${styles.thin}`} style={{ width: '40%' }} />
                </span>
            </span>
        ))}</>;
    }
    return (
        <>
            <span className={`${styles.bar} ${styles.title}`} />
            {[100, 95, 100, 80].map((width, index) => <span key={index} className={styles.bar} style={{ width: `${width}%` }} />)}
            <span className={styles.block} />
        </>
    );
}
