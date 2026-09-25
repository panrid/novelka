import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { studioApi } from '../../studio/api';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { relativeTime } from '../../lib/dates';
import { useEditionId } from './EditionPage';
import styles from './studio.module.css';

const STATES = { open: 'чекає відповіді', granted: 'дозволено', declined: 'відмовлено', withdrawn: 'відкликано' };

export function RelayPage() {
    const id = useEditionId();
    const client = useQueryClient();
    const requests = useQuery({ queryKey: ['takeover', id], queryFn: () => studioApi.takeoverRequests(id) });
    const answer = useMutation({
        mutationFn: ({ requestId, grant }: { requestId: number; grant: boolean }) => studioApi.answerTakeover(id, requestId, grant),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['takeover', id] }),
    });

    return (
        <section className={styles.page}>
            <p><Link to="/studio/$editionId" params={{ editionId: String(id) }}>← До публікації</Link></p>
            <h1 className={styles.title}>Естафета</h1>
            <p className={styles.muted}>
                Інші команди можуть попросити продовжити ваш переклад. Якщо ви дозволите, вони почнуть з наступної глави,
                а ваші глави лишаться за вами. Без відповіді за 14 днів і без нових глав кілька місяців переклад стане вільним сам.
            </p>
            {requests.isError && <Notice tone="error">{requests.error.message}</Notice>}
            {requests.data?.length === 0 && <p className={styles.muted} style={{ marginTop: 16 }}>Запитів немає.</p>}
            {requests.data?.map((request) => (
                <div key={request.id} className={styles.row} style={{ alignItems: 'flex-start' }}>
                    <div className={styles.grow}>
                        <div><b>${request.teamHandle}</b> · {request.requestedBy}</div>
                        {request.message && <p style={{ margin: '4px 0' }}>{request.message}</p>}
                        <div className={styles.muted}>{relativeTime(new Date(request.createdAt))} · {STATES[request.state]}</div>
                    </div>
                    {request.state === 'open' && (
                        <div className={styles.actions} style={{ margin: 0 }}>
                            <Button variant="secondary" onPress={() => answer.mutate({ requestId: request.id, grant: false })}>Ні</Button>
                            <Button onPress={() => answer.mutate({ requestId: request.id, grant: true })}>Дозволити</Button>
                        </div>
                    )}
                </div>
            ))}
            {answer.isError && <Notice tone="error">{answer.error.message}</Notice>}
        </section>
    );
}
