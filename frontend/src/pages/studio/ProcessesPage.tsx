import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { useDebounced } from '../../lib/useDebounced';
import { PAGE_SIZE, usePage } from '../../lib/usePage';
import { autotranslateApi } from '../../studio/autotranslate';
import { Notice } from '../../ui/Notice';
import { Pager } from '../../ui/Pager';
import { TextInput } from '../../ui/TextInput';
import { JobCard } from './AutotranslatePage';
import styles from './studio.module.css';

const STATES = [['', 'Усі'], ['active', 'Ідуть'], ['failed', 'Зупинені'], ['done', 'Готові'], ['cancelled', 'Скасовані']] as const;
const KINDS = [['', 'Аналіз і переклад'], ['analyze', 'Аналіз'], ['translate', 'Переклад']] as const;

/** Every autotranslation run in one place: running and stopped ones first, 20 to a page, by state, kind and title. */
export function ProcessesPage() {
    const client = useQueryClient();
    const [page, setPage] = usePage();
    const [state, setState] = useState('');
    const [kind, setKind] = useState('');
    const [search, setSearch] = useState('');
    const q = useDebounced(search, 300);
    const processes = useQuery({
        queryKey: ['autotranslate', 'processes', state, kind, q, page],
        queryFn: () => autotranslateApi.processes({ state, kind, q, page }),
        placeholderData: (previous) => previous,
    });
    const wallet = useQuery({ queryKey: ['wallet', '30'], queryFn: () => autotranslateApi.wallet(30) });
    const act = useMutation({
        mutationFn: (action: () => Promise<unknown>) => action(),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['autotranslate'] }),
    });
    const show = wallet.data?.showShah ?? true;
    const usdPerShah = wallet.data?.usdPerShah ?? 0.036;
    const filtered = Boolean(state || kind || q.trim());
    // A new filter starts from its first page.
    const reset = (change: () => void) => {
        change();
        setPage(1);
    };
    return (
        <section className={styles.page}>
            <Link to="/studio" className={styles.muted}>‹ Студія</Link>
            <h1 className={styles.title}>Процеси</h1>
            <p className={styles.muted}>Аналізи й переклади, які ви запускали. Незавершені — вгорі.</p>
            <div className={styles.filters} role="group" aria-label="Стан">
                {STATES.map(([value, label]) => (
                    <button key={value} type="button" className={`${styles.chip} ${state === value ? styles.chipOn : ''}`}
                        aria-pressed={state === value} onClick={() => reset(() => setState(value))}>{label}</button>
                ))}
            </div>
            <div className={styles.filters} style={{ margin: '12px 0' }}>
                <label>
                    <div className={styles.label}>Що</div>
                    <select className={styles.select} value={kind} aria-label="Що" onChange={(event) => reset(() => setKind(event.target.value))}>
                        {KINDS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                    </select>
                </label>
            </div>
            <TextInput label="Пошук за назвою" value={search} onChange={(value) => { setSearch(value); setPage(1); }} />
            {processes.isError && <Notice tone="error">{processes.error.message}</Notice>}
            {act.isError && <Notice tone="error">{act.error.message}</Notice>}
            {processes.data?.total === 0 && (
                <p className={styles.muted}>{filtered ? 'Нічого не знайшли.' : 'Ще нічого не запускали.'}</p>
            )}
            {processes.data?.items.map((process) => (
                <JobCard key={process.job.id} job={process.job} editionId={process.editionId} showShah={show} usdPerShah={usdPerShah} pending={act.isPending}
                    title={<Link to="/studio/$editionId/translate" params={{ editionId: String(process.editionId) }} className={styles.processTitle}>
                        {process.title}
                    </Link>}
                    onCancel={() => act.mutate(() => autotranslateApi.cancel(process.editionId, process.job.id))}
                    onResume={() => act.mutate(() => autotranslateApi.resume(process.editionId, process.job.id))} />
            ))}
            {processes.data && <Pager page={page} total={processes.data.total} size={PAGE_SIZE} onPage={setPage} />}
        </section>
    );
}
