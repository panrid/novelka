import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { FileUp, PenLine, Sparkles } from 'lucide-react';
import { useState } from 'react';
import { useDebounced } from '../../lib/useDebounced';
import { autotranslateApi, money } from '../../studio/autotranslate';
import { studioApi } from '../../studio/api';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Sheet } from '../../ui/Sheet';
import { TextInput } from '../../ui/TextInput';
import styles from './newChapter.module.css';

type Way = 'auto' | 'manual';

/**
 * «Нова глава» asks how first (етап 15): a translation made from an original offers the
 * autotranslation of the next chapters with their price; writing by hand or a file always.
 */
export function NewChapterSheet({ editionId, machine, onClose }: { editionId: number; machine: boolean; onClose: () => void }) {
    const navigate = useNavigate();
    const client = useQueryClient();
    const [way, setWay] = useState<Way>(machine ? 'auto' : 'manual');
    const add = useMutation({
        mutationFn: () => studioApi.newChapter(editionId),
        onSuccess: ({ number }) => {
            void client.invalidateQueries({ queryKey: ['studio-chapters', editionId] });
            void navigate({ to: '/studio/$editionId/chapters/$number', params: { editionId: String(editionId), number: String(number) } });
        },
    });
    return (
        <Sheet open onClose={onClose} title="Нова глава">
            <div className={styles.choices} role="radiogroup" aria-label="Як додати главу">
                {machine && (
                    <button type="button" role="radio" aria-checked={way === 'auto'} className={styles.choice} onClick={() => setWay('auto')}>
                        <Sparkles size={20} aria-hidden />
                        <b>Автопереклад</b>
                        <span>Перекласти наступні глави з оригіналу</span>
                    </button>
                )}
                <button type="button" role="radio" aria-checked={way === 'manual'} className={styles.choice} onClick={() => setWay('manual')}>
                    <PenLine size={20} aria-hidden />
                    <b>Вручну</b>
                    <span>Написати або вставити текст у редактор</span>
                </button>
            </div>
            {way === 'auto' && machine ? (
                <AutoNext editionId={editionId} onClose={onClose} />
            ) : (
                <div className={styles.footer}>
                    {add.isError && <Notice tone="error">{add.error.message}</Notice>}
                    <Button onPress={() => add.mutate()} pending={add.isPending} pendingLabel="Створюємо…">Відкрити редактор</Button>
                    <Link to="/studio/$editionId/import" params={{ editionId: String(editionId) }} className={styles.file}>
                        <FileUp size={16} aria-hidden /> Або завантажити з файлу (.docx, .txt, .md)
                    </Link>
                </div>
            )}
        </Sheet>
    );
}

/** The next chapters of the original, their price, and the start right here. */
function AutoNext({ editionId, onClose }: { editionId: number; onClose: () => void }) {
    const navigate = useNavigate();
    const overview = useQuery({ queryKey: ['autotranslate', editionId], queryFn: () => autotranslateApi.overview(editionId) });
    const data = overview.data;
    const [to, setTo] = useState('');
    const target = Number((to.trim() || String(data?.nextNumber ?? '')).replace(/\s/g, ''));
    const settled = useDebounced(target, 400);
    const active = data?.jobs.find((job) => job.state === 'queued' || job.state === 'running' || job.state === 'failed');
    const left = data ? data.nextNumber <= data.sourceChapters : false;
    const valid = data !== undefined && Number.isInteger(settled) && settled >= data.nextNumber && settled <= data.sourceChapters;
    const quote = useQuery({
        queryKey: ['autotranslate-quote', editionId, JSON.stringify({ kind: 'translate', to: settled })],
        queryFn: () => autotranslateApi.quote(editionId, { kind: 'translate', to: settled }),
        enabled: valid && !active,
        retry: false,
    });
    const start = useMutation({
        mutationFn: () => autotranslateApi.start(editionId, { kind: 'translate', to: settled }),
        onSuccess: () => {
            onClose();
            void navigate({ to: '/studio/$editionId/translate', params: { editionId: String(editionId) } });
        },
    });
    const goToPage = () => void navigate({ to: '/studio/$editionId/translate', params: { editionId: String(editionId) } });

    if (overview.isError) return <Notice tone="error">{overview.error.message}</Notice>;
    if (!data) return <p className={styles.muted}>Дивимося, що далі в оригіналі…</p>;
    if (active) {
        return (
            <div className={styles.footer}>
                <p className={styles.muted}>Автопереклад уже йде: глави {active.from}–{active.to}. Новий можна запустити, коли він закінчиться.</p>
                <Button onPress={goToPage}>Подивитися, як іде</Button>
            </div>
        );
    }
    if (!left) {
        return <p className={styles.muted}>Усі {data.sourceChapters} глав оригіналу вже перекладено. Нові з'являться, щойно автор їх опублікує.</p>;
    }
    const outOfRange = to.trim() !== '' && !valid && settled === target;
    return (
        <div className={styles.footer}>
            <TextInput label={`Перекласти з глави ${data.nextNumber} до глави…`} value={to} onChange={setTo} inputMode="numeric"
                placeholder={String(data.nextNumber)}
                hint={`В оригіналі ${data.sourceChapters}. Порожньо — лише глава ${data.nextNumber}.`}
                error={outOfRange ? `Від ${data.nextNumber} до ${data.sourceChapters}.` : quote.isError ? quote.error.message : undefined} />
            {quote.data && (
                <p className={styles.price} aria-live="polite">
                    {quote.data.chapters === 1 ? `Глава ${quote.data.from}` : `Глави ${quote.data.from}–${quote.data.to}`}
                    {' · '}{quote.data.estimated ? 'орієнтовно ' : ''}<b>{data.personal ? `≈ ${money(quote.data.shah, quote.data.usd, true)}` : money(quote.data.shah, quote.data.usd, data.showShah)}</b>
                </p>
            )}
            {start.isError && <Notice tone="error">{start.error.message}</Notice>}
            <Button onPress={() => start.mutate()} pending={start.isPending} pendingLabel="Запускаємо…" isDisabled={!quote.data || !data.configured}>
                {quote.data && quote.data.chapters > 1 ? `Перекласти глави ${quote.data.from}–${quote.data.to}` : 'Перекласти главу'}
            </Button>
            <button type="button" className={styles.more} onClick={goToPage}>Аналіз, моделі й інші налаштування ›</button>
        </div>
    );
}
