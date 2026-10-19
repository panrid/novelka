import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { shahApi, useCanRun } from '../../ledger/api';
import { relativeTime } from '../../lib/dates';
import { autotranslateApi, dollars, shahWord } from '../../studio/autotranslate';
import { modelName } from '../studio/AutotranslatePage';
import { askConfirm, askText } from '../../ui/ask';
import { Notice } from '../../ui/Notice';
import { Pager } from '../../ui/Pager';
import { PAGE_SIZE, usePage } from '../../lib/usePage';
import styles from '../studio/studio.module.css';

/** A person's шаги (рішення 29): what is left, what runs hold, what came and what went. */
export function ShahsPage() {
    const [page, setPage] = usePage();
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
                    <Pager page={page} total={data.total} size={PAGE_SIZE} onPage={setPage} />
                </>
            )}
            <MySets />
        </section>
    );
}

/** «Мої набори моделей» (етап 19, п. 8): the sets a person saved, to rename or delete. */
function MySets() {
    const canRun = useCanRun();
    const client = useQueryClient();
    const sets = useQuery({ queryKey: ['my-sets'], queryFn: autotranslateApi.mySets, enabled: canRun });
    const change = useMutation({
        mutationFn: (run: () => Promise<void>) => run(),
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: ['my-sets'] });
            void client.invalidateQueries({ queryKey: ['autotranslate'] });
        },
    });
    if (!canRun || !sets.data || sets.data.length === 0) return null;
    return (
        <>
            <h2 className={styles.sectionTitle}>Мої набори моделей</h2>
            {change.isError && <Notice tone="error">{change.error.message}</Notice>}
            {sets.data.map((set) => (
                <div key={set.id} className={styles.row}>
                    <div>
                        <b>{set.name}</b>
                        <div className={styles.muted}>
                            словник {modelName(set.analyze)} · переклад {modelName(set.translate)}
                            {set.proofread ? ` · вичитка ${modelName(set.proofread)}` : ' · без вичитки'}
                        </div>
                    </div>
                    <button type="button" className={styles.linkButton} aria-label={`Перейменувати ${set.name}`} onClick={() => void askText({
                        title: 'Перейменувати набір', label: 'Нова назва', hint: `Зараз: «${set.name}»`, confirmLabel: 'Зберегти',
                    }).then((name) => { if (name) change.mutate(() => autotranslateApi.renameSet(set.id, name)); })}>перейменувати</button>
                    <button type="button" className={styles.linkButton} aria-label={`Видалити ${set.name}`} onClick={() => void askConfirm({
                        title: `Видалити набір «${set.name}»?`, text: 'Запуски, що вже йдуть, доробляться з ним.', confirmLabel: 'Видалити', danger: true,
                    }).then((yes) => { if (yes) change.mutate(() => autotranslateApi.deleteSet(set.id)); })}>видалити</button>
                </div>
            ))}
        </>
    );
}
