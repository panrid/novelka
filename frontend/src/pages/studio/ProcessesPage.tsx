import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { autotranslateApi } from '../../studio/autotranslate';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { JobCard } from './AutotranslatePage';
import styles from './studio.module.css';

/** Every autotranslation run in one place: running and stopped ones first. */
export function ProcessesPage() {
    const client = useQueryClient();
    const [page, setPage] = useState(1);
    const processes = useQuery({ queryKey: ['autotranslate', 'processes', page], queryFn: () => autotranslateApi.processes(page) });
    const wallet = useQuery({ queryKey: ['wallet', '30'], queryFn: () => autotranslateApi.wallet(30) });
    const act = useMutation({
        mutationFn: (action: () => Promise<unknown>) => action(),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['autotranslate'] }),
    });
    const show = wallet.data?.showShah ?? true;
    const usdPerShah = wallet.data?.usdPerShah ?? 0.036;
    return (
        <section className={styles.page}>
            <Link to="/studio" className={styles.muted}>‹ Студія</Link>
            <h1 className={styles.title}>Процеси</h1>
            <p className={styles.muted}>Аналізи й переклади, які ви запускали. Незавершені — вгорі.</p>
            {processes.isError && <Notice tone="error">{processes.error.message}</Notice>}
            {act.isError && <Notice tone="error">{act.error.message}</Notice>}
            {processes.data?.length === 0 && <p className={styles.muted}>Ще нічого не запускали.</p>}
            {processes.data?.map((process) => (
                <JobCard key={process.job.id} job={process.job} showShah={show} usdPerShah={usdPerShah} pending={act.isPending}
                    title={<Link to="/studio/$editionId/translate" params={{ editionId: String(process.editionId) }} className={styles.processTitle}>
                        {process.title}
                    </Link>}
                    onCancel={() => act.mutate(() => autotranslateApi.cancel(process.editionId, process.job.id))}
                    onResume={() => act.mutate(() => autotranslateApi.resume(process.editionId, process.job.id))} />
            ))}
            <div className={styles.actions}>
                {page > 1 && <Button variant="secondary" onPress={() => setPage(page - 1)}>← Новіші</Button>}
                {processes.data?.length === 30 && <Button variant="secondary" onPress={() => setPage(page + 1)}>Давніші →</Button>}
            </div>
        </section>
    );
}
