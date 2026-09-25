import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { shahApi } from '../../ledger/api';
import { relativeTime } from '../../lib/dates';
import { dollars, shahWord } from '../../studio/autotranslate';
import { Notice } from '../../ui/Notice';
import styles from '../studio/studio.module.css';

/** A person's шаги (рішення 29): what is left, what runs hold, what came and what went. */
export function ShahsPage() {
    const [page, setPage] = useState(1);
    const mine = useQuery({ queryKey: ['shahs', page], queryFn: () => shahApi.mine(page), placeholderData: keepPreviousData });
    const data = mine.data;
    return (
        <section className={styles.page}>
            <h1 className={styles.title}>Шаги</h1>
            {mine.isError && <Notice tone="error">{mine.error.message}</Notice>}
            {data && (
                <>
                    <p style={{ fontSize: 28, margin: '4px 0' }}><b>{data.available} {shahWord(data.available)}</b></p>
                    {data.reserved > 0 && <p className={styles.muted}>Ще {data.reserved} {shahWord(data.reserved)} у резерві запусків, що тривають.</p>}
                    <p className={styles.muted}>
                        Шаги нараховує власник сайту. Ними платите за автопереклад і ілюстрації у своїх перекладах: списуємо, скільки
                        насправді коштували моделі, округлюючи вгору до цілого шагу (1 шаг — до {dollars(data.usdPerShah, 2)} витрат).
                        На час запуску частину шагів блокуємо й повертаємо, що не знадобилося.
                    </p>

                    {data.running.length > 0 && (
                        <>
                            <h2 className={styles.sectionTitle}>Зараз у резерві</h2>
                            {data.running.map((hold, index) => (
                                <div key={index} className={styles.row}>
                                    <div className={styles.grow}>{hold.what}</div>
                                    <span className={styles.muted}>{hold.amount} {shahWord(hold.amount)}</span>
                                </div>
                            ))}
                        </>
                    )}

                    <h2 className={styles.sectionTitle}>Історія</h2>
                    {data.history.length === 0 && <p className={styles.muted}>Поки нічого.</p>}
                    {data.history.map((entry, index) => (
                        <div key={`${page}-${index}`} className={styles.row}>
                            <div className={styles.grow}>
                                <div>{entry.kind === 'grant' ? 'Нараховано' : entry.what ?? 'Списано'}</div>
                                <div className={styles.muted}>
                                    {entry.kind === 'grant' && entry.what ? `«${entry.what}» · ` : ''}{relativeTime(new Date(entry.createdAt))}
                                </div>
                            </div>
                            <b style={{ color: entry.kind === 'grant' ? 'var(--accent)' : undefined }}>
                                {entry.kind === 'grant' ? '+' : '−'}{entry.amount}
                            </b>
                        </div>
                    ))}
                    {(page > 1 || data.hasMore) && (
                        <div className={styles.actions} style={{ marginTop: 12 }}>
                            <button type="button" className={styles.plainButton} disabled={page <= 1} onClick={() => setPage(page - 1)}>← Новіші</button>
                            <button type="button" className={styles.plainButton} disabled={!data.hasMore} onClick={() => setPage(page + 1)}>Давніші →</button>
                        </div>
                    )}
                </>
            )}
        </section>
    );
}
