import { useState } from 'react';
import styles from './charts.module.css';

export type Series = { key: string; label: string; color: string };

/**
 * Columns over time, each split into coloured parts (spending by step, day by day). Plain SVG
 * that stretches to the width; a tap or hover on a column shows its numbers under the chart.
 */
export function StackedColumns({ buckets, series, format, label }: {
    buckets: { start: string; values: Record<string, number> }[];
    series: Series[];
    format: (value: number) => string;
    label: string;
}) {
    const [picked, setPicked] = useState<number | null>(null);
    const totals = buckets.map((bucket) => series.reduce((sum, s) => sum + (bucket.values[s.key] ?? 0), 0));
    const max = Math.max(...totals, 0);
    const height = 160;
    const width = buckets.length * 10;
    const step = width / Math.max(buckets.length, 1);
    const shown = picked ?? buckets.length - 1;
    if (buckets.length === 0 || max === 0) return <p className={styles.empty}>За цей період даних немає.</p>;
    return (
        <figure className={styles.figure}>
            <svg viewBox={`0 0 ${width} ${height + 18}`} preserveAspectRatio="none" className={styles.svg} role="img" aria-label={label}>
                {[0.25, 0.5, 0.75, 1].map((f) => (
                    <line key={f} x1={0} x2={width} y1={height - f * height} y2={height - f * height} className={styles.grid} />
                ))}
                {buckets.map((bucket, i) => {
                    let y = height;
                    return (
                        <g key={bucket.start} onMouseEnter={() => setPicked(i)} onClick={() => setPicked(i)} className={styles.column}>
                            <rect x={i * step} y={0} width={step} height={height} fill="transparent" />
                            {series.map((s) => {
                                const value = bucket.values[s.key] ?? 0;
                                const h = (value / max) * height;
                                y -= h;
                                return h > 0 ? <rect key={s.key} x={i * step + step * 0.15} y={y} width={step * 0.7} height={h} fill={s.color}
                                    opacity={picked === null || picked === i ? 1 : 0.55} /> : null;
                            })}
                        </g>
                    );
                })}
            </svg>
            <div className={styles.axis}>
                <span>{shortDate(buckets[0]!.start)}</span>
                <span>макс. {format(max)}</span>
                <span>{shortDate(buckets[buckets.length - 1]!.start)}</span>
            </div>
            <figcaption className={styles.caption}>
                <b>{shortDate(buckets[shown]!.start)}</b>: {format(totals[shown] ?? 0)}
                {series.map((s) => (buckets[shown]!.values[s.key] ?? 0) > 0 && (
                    <span key={s.key} className={styles.legendItem}>
                        <i style={{ background: s.color }} />{s.label} {format(buckets[shown]!.values[s.key] ?? 0)}
                    </span>
                ))}
            </figcaption>
            <Legend series={series} />
        </figure>
    );
}

export function Legend({ series }: { series: Series[] }) {
    return (
        <div className={styles.legend}>
            {series.map((s) => <span key={s.key} className={styles.legendItem}><i style={{ background: s.color }} />{s.label}</span>)}
        </div>
    );
}

/** Horizontal bars to compare things (models, novels): a label, a bar, the value. */
export function Bars({ rows, format, color = 'var(--accent)' }: {
    rows: { label: string; value: number; hint?: string | undefined; color?: string | undefined }[];
    format: (value: number) => string;
    color?: string;
}) {
    const max = Math.max(...rows.map((row) => row.value), 0);
    // Nothing to compare: the table under it says so.
    if (rows.length === 0) return null;
    return (
        <div className={styles.bars}>
            {rows.map((row) => (
                <div key={row.label} className={styles.bar} title={row.hint}>
                    <span className={styles.barLabel}>{row.label}</span>
                    <span className={styles.barTrack}>
                        <span style={{ width: `${max === 0 ? 0 : Math.max(1, (row.value / max) * 100)}%`, background: row.color ?? color }} />
                    </span>
                    <span className={styles.barValue}>{format(row.value)}</span>
                </div>
            ))}
        </div>
    );
}

/** One bar split into shares (spending by step): the parts in proportion, with a legend. */
export function ShareBar({ parts, format }: { parts: { key: string; label: string; color: string; value: number }[]; format: (value: number) => string }) {
    const total = parts.reduce((sum, part) => sum + part.value, 0);
    if (total === 0) return <p className={styles.empty}>Даних ще немає.</p>;
    return (
        <div>
            <div className={styles.share} role="img" aria-label={parts.map((p) => `${p.label} ${Math.round((p.value / total) * 100)}%`).join(', ')}>
                {parts.filter((p) => p.value > 0).map((p) => <span key={p.key} style={{ width: `${(p.value / total) * 100}%`, background: p.color }} />)}
            </div>
            <div className={styles.legend}>
                {parts.filter((p) => p.value > 0).map((p) => (
                    <span key={p.key} className={styles.legendItem}>
                        <i style={{ background: p.color }} />{p.label}: {format(p.value)} · {Math.round((p.value / total) * 100)}%
                    </span>
                ))}
            </div>
        </div>
    );
}

function shortDate(iso: string) {
    const [, month, day] = iso.split('-');
    return `${day}.${month}`;
}
