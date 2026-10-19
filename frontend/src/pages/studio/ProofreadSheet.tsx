import { useQuery } from '@tanstack/react-query';
import { autotranslateApi } from '../../studio/autotranslate';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Sheet } from '../../ui/Sheet';
import { WordDiff } from '../../ui/WordDiff';
import styles from './studio.module.css';

/**
 * «Вичитати абзац» (етап 19): AI reads the paragraph with the cursor against its original; the
 * change is shown word by word and goes into the text only on «Прийняти».
 */
export function ProofreadSheet({ editionId, chapter, pick, onAccept, onClose }: {
    editionId: number; chapter: number; pick: { blockId: string | null; paragraph: string };
    onAccept: (text: string) => void; onClose: () => void;
}) {
    const proofread = useQuery({
        queryKey: ['paragraph-proofread', editionId, chapter, pick.blockId, pick.paragraph],
        queryFn: () => autotranslateApi.proofreadParagraph(editionId, chapter, pick.blockId, pick.paragraph),
        retry: false,
        staleTime: Infinity,
    });
    const fixed = proofread.data?.text;
    return (
        <Sheet open onClose={onClose} title="Вичитка ШІ">
            {proofread.isPending && <p className={styles.muted} aria-live="polite">ШІ вичитує абзац…</p>}
            {proofread.isError && <Notice tone="error">{proofread.error.message}</Notice>}
            {proofread.data && !fixed && <Notice tone="info">ШІ не знайшов, що виправити в цьому абзаці.</Notice>}
            {fixed && (
                <div className={styles.form}>
                    <p className={styles.muted}>Вичитка ШІ пропонує:</p>
                    <WordDiff before={pick.paragraph} after={fixed} />
                    <Button onPress={() => onAccept(fixed)}>Прийняти</Button>
                    <Button variant="secondary" onPress={onClose}>Лишити як є</Button>
                </div>
            )}
        </Sheet>
    );
}
