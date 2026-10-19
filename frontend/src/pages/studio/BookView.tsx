import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { studioApi, type ParagraphMarks, type StudioBlock } from '../../studio/api';
import { ArrowLeft } from '../../ui/icons';
import { ProofreadSheet } from './ProofreadSheet';
import { VariantsSheet } from './VariantsSheet';
import styles from './book.module.css';

/** What a paragraph is in «Як у книжці»; «машинний» and «є пропозиція» come from the server, the rest from its text. */
export type ParagraphState = 'checked' | 'offer' | 'machine' | 'unchecked';

const STATE_LABELS: Record<ParagraphState, string> = {
    checked: 'перевірено', offer: 'є пропозиція', machine: 'машинний', unchecked: 'не перевірено',
};

const TEXT_TYPES = new Set(['paragraph', 'preface', 'afterword', 'heading']);

export function plainText(block: StudioBlock): string {
    return block.content.map((span) => span.text).join('');
}

/**
 * The state of a paragraph: a reader's правка waits for it; the team checked it and it reads the same
 * since; it is still what the AI wrote; or none of that.
 */
export function paragraphState(block: StudioBlock, marks: ParagraphMarks | undefined): ParagraphState {
    if (!marks || !block.id) return 'unchecked';
    const text = plainText(block);
    if (marks.offers.includes(block.id)) return 'offer';
    if (marks.checked.some((item) => item.blockId === block.id && item.text === text)) return 'checked';
    if (marks.machine.some((item) => item.blockId === block.id && item.text === text)) return 'machine';
    return 'unchecked';
}

/**
 * «Як у книжці» (новий дизайн, WorkspaceFocus.dc.html): the translation alone, set like the book, with
 * the state of each paragraph beside its number and «Перевірено N з M абзаців» on top. A click on a
 * paragraph shows its original under it and what can be done with it.
 */
export function BookView({ editionId, number, heading, title, blocks, hasOriginal, canRun, notices, publishButton, onBack, onReplace }: {
    editionId: number; number: number; heading: string; title: string; blocks: StudioBlock[]; hasOriginal: boolean; canRun: boolean;
    notices: ReactNode; publishButton: ReactNode; onBack: () => void; onReplace: (index: number, text: string) => void;
}) {
    const client = useQueryClient();
    const marks = useQuery({
        queryKey: ['studio-paragraphs', editionId, number],
        queryFn: () => studioApi.paragraphs(editionId, number),
    });
    const original = useQuery({
        queryKey: ['studio-original', editionId, number],
        queryFn: () => studioApi.original(editionId, number),
        staleTime: Infinity,
        enabled: hasOriginal,
    });
    const [active, setActive] = useState<number | null>(null);
    const [withOriginal, setWithOriginal] = useState(true);
    const [rewording, setRewording] = useState<{ index: number; blockId: string | null; paragraph: string } | null>(null);
    const [proofing, setProofing] = useState<{ index: number; blockId: string | null; paragraph: string } | null>(null);
    const mark = useMutation({ meta: { errorToast: true },
        mutationFn: ({ blockId, text, on }: { blockId: string; text: string; on: boolean }) => on
            ? studioApi.checkParagraph(editionId, number, blockId, text) : studioApi.uncheckParagraph(editionId, number, blockId),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['studio-paragraphs', editionId, number] }),
    });

    const paragraphs = blocks.filter((block) => TEXT_TYPES.has(block.type) && plainText(block).trim());
    const checked = paragraphs.filter((block) => paragraphState(block, marks.data) === 'checked').length;
    const share = paragraphs.length > 0 ? Math.round((checked / paragraphs.length) * 100) : 0;
    const machineUsed = paragraphs.some((block) => paragraphState(block, marks.data) === 'machine');

    return (
        <div className={styles.page}>
            <header className={styles.top}>
                <button type="button" className={styles.back} aria-label="Назад до двох колонок" onClick={onBack}>
                    <ArrowLeft size={20} aria-hidden />
                </button>
                <div className={styles.where}>
                    <div className={styles.heading}>{heading}</div>
                    <div className={styles.title}>{title || 'Без назви'}</div>
                </div>
                <div className={styles.progress}>
                    <span>Перевірено {checked} з {paragraphs.length} абзаців</span>
                    <span className={styles.bar} aria-hidden><span style={{ width: `${share}%` }} /></span>
                </div>
                {hasOriginal && (
                    <label className={styles.toggle}>
                        <input type="checkbox" checked={withOriginal} onChange={(event) => setWithOriginal(event.target.checked)} />
                        Оригінал під абзацом
                    </label>
                )}
                {publishButton}
            </header>

            <main className={styles.main}>
                {notices}
                <div className={styles.legend}>
                    {machineUsed && <span><span className={`${styles.dot} ${styles.machine}`} aria-hidden />машинний</span>}
                    <span><span className={`${styles.dot} ${styles.unchecked}`} aria-hidden />не перевірено</span>
                    <span><span className={`${styles.dot} ${styles.checked}`} aria-hidden />перевірено</span>
                    <span><span className={`${styles.dot} ${styles.offer}`} aria-hidden />є пропозиція</span>
                </div>
                <h1 className={styles.chapterTitle}>{title || 'Без назви'}</h1>
                {rewording && (
                    <VariantsSheet editionId={editionId} chapter={number} pick={rewording} onClose={() => setRewording(null)}
                        onPick={(text) => { onReplace(rewording.index, text); setRewording(null); }} />
                )}
                {proofing && (
                    <ProofreadSheet editionId={editionId} chapter={number} pick={proofing} onClose={() => setProofing(null)}
                        onAccept={(text) => { onReplace(proofing.index, text); setProofing(null); }} />
                )}
                {blocks.map((block, index) => {
                    if (block.type === 'separator') return <p key={index} className={styles.separator} aria-label="Розділювач сцен">◇</p>;
                    if (block.type === 'image') return block.imageUrl ? <img key={index} className={styles.picture} src={block.imageUrl} alt="" /> : null;
                    const text = plainText(block);
                    if (!text.trim()) return null;
                    const row = paragraphs.indexOf(block) + 1;
                    const state = paragraphState(block, marks.data);
                    const lit = active === index;
                    const source = lit && withOriginal && block.id ? original.data?.blocks.find((item) => item.id === block.id) : undefined;
                    const pick = { index, blockId: block.id || null, paragraph: text };
                    return (
                        <div key={index} className={styles.row}>
                            <button type="button" className={styles.number} aria-label={`Абзац ${row}: ${STATE_LABELS[state]}`} title={STATE_LABELS[state]}
                                aria-pressed={lit} onClick={() => setActive(lit ? null : index)}>
                                {row}<span className={`${styles.dot} ${styles[state]}`} aria-hidden />
                            </button>
                            <div className={`${styles.paragraph} ${lit ? styles.paragraphLit : ''}`}>
                                <p lang="uk" className={block.type === 'heading' ? styles.subheading : styles.text} onClick={() => setActive(index)}>{text}</p>
                                {source && source.type !== 'separator' && <p lang="und" className={styles.original}>{source.text}</p>}
                                {lit && (
                                    <div className={styles.actions}>
                                        {state === 'checked' ? (
                                            <button type="button" className={styles.action} disabled={mark.isPending}
                                                onClick={() => mark.mutate({ blockId: block.id, text, on: false })}>Зняти позначку</button>
                                        ) : (
                                            <button type="button" className={styles.actionMain} disabled={!block.id || mark.isPending}
                                                title={block.id ? undefined : 'Абзац ще не збережено: опублікуйте главу'}
                                                onClick={() => mark.mutate({ blockId: block.id, text, on: true })}>Позначити перевіреним</button>
                                        )}
                                        {canRun && <button type="button" className={styles.action} onClick={() => setRewording(pick)}>Інший варіант</button>}
                                        {canRun && hasOriginal && <button type="button" className={styles.action} onClick={() => setProofing(pick)}>Вичитати абзац</button>}
                                    </div>
                                )}
                            </div>
                        </div>
                    );
                })}
            </main>
        </div>
    );
}
