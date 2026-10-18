import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { relativeTime } from '../../lib/dates';
import { studioApi } from '../../studio/api';
import { Notice } from '../../ui/Notice';
import styles from './studio.module.css';

/** «Кошик»: translations deleted in the last 30 days, each with «Відновити» and when it is erased for good. */
export function TrashPage() {
    const client = useQueryClient();
    const bin = useQuery({ queryKey: ['studio', 'trash'], queryFn: studioApi.trash });
    const restore = useMutation({
        mutationFn: (editionId: number) => studioApi.restoreEdition(editionId),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['studio'] }),
    });
    return (
        <section className={styles.page}>
            <h1 className={styles.title}>Кошик</h1>
            <p className={styles.muted}>Видалені переклади лежать тут 30 днів. Потім їхні глави стираються назавжди.</p>
            {bin.isError && <Notice tone="error">{bin.error.message}</Notice>}
            {restore.isError && <Notice tone="error">{restore.error.message}</Notice>}
            {bin.isSuccess && bin.data.length === 0 && <p className={styles.muted}>Кошик порожній.</p>}
            {bin.data?.map((item) => (
                <div key={item.editionId} className={styles.row}>
                    <div className={styles.grow}>
                        <div className={styles.ellipsis}>{item.title}</div>
                        <div className={styles.muted}>
                            {item.teamName} · видалено {relativeTime(new Date(item.deletedAt))} · зітреться {new Date(item.erasedAt).toLocaleDateString('uk')}
                        </div>
                    </div>
                    <button type="button" className={styles.plainButton} disabled={restore.isPending} onClick={() => restore.mutate(item.editionId)}>
                        Відновити
                    </button>
                </div>
            ))}
        </section>
    );
}
