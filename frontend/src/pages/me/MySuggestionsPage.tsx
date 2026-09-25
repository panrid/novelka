import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { SUGGESTION_STATES, suggestionApi } from '../../reading/suggestions';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { relativeTime } from '../../lib/dates';
import styles from '../studio/studio.module.css';

/** Everything the person suggested: what is waiting, accepted, rejected — with the team's note. */
export function MySuggestionsPage() {
    const client = useQueryClient();
    const history = useQuery({ queryKey: ['my-suggestions'], queryFn: suggestionApi.history });
    const withdraw = useMutation({
        mutationFn: suggestionApi.withdraw,
        onSuccess: () => void client.invalidateQueries({ queryKey: ['my-suggestions'] }),
    });
    return (
        <section className={styles.page}>
            <h1 className={styles.title}>Мої правки</h1>
            {history.isError && <Notice tone="error">{history.error.message}</Notice>}
            {history.data?.length === 0 && <p className={styles.muted}>Ви ще нічого не пропонували. Виділіть текст у читалці — зʼявиться «Виправити».</p>}
            {history.data?.map((item) => (
                <div key={item.id} className={styles.row} style={{ alignItems: 'flex-start' }}>
                    <div className={styles.grow}>
                        <Link to="/n/$slug/$number" params={{ slug: item.novelSlug, number: String(item.chapter) }} search={{ t: item.teamHandle }}
                            style={{ color: 'var(--text)', textDecoration: 'none' }}>
                            {item.novelTitle} · глава {item.chapter}
                        </Link>
                        <p style={{ margin: '4px 0', fontFamily: 'var(--font-reading)' }}>{item.preview}</p>
                        <div className={styles.muted}>
                            {SUGGESTION_STATES[item.state]} · {relativeTime(new Date(item.updatedAt))}
                            {item.reviewNote ? ` · команда: «${item.reviewNote}»` : ''}
                        </div>
                    </div>
                    {(item.state === 'draft' || item.state === 'pending') && (
                        <Button variant="quiet" onPress={() => withdraw.mutate(item.id)}>Відкликати</Button>
                    )}
                </div>
            ))}
        </section>
    );
}
