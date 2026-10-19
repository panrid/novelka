import { useQuery } from '@tanstack/react-query';
import { autotranslateApi } from '../../studio/autotranslate';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Sheet } from '../../ui/Sheet';
import styles from './studio.module.css';

/**
 * «Інший варіант» (етап 19, п. 5): other versions of the paragraph with the cursor, from its
 * original when the chapter has one; the one picked takes the paragraph's place in the editor.
 */
export function VariantsSheet({ editionId, chapter, pick, onPick, onClose }: {
    editionId: number; chapter: number; pick: { blockId: string | null; paragraph: string };
    onPick: (text: string) => void; onClose: () => void;
}) {
    const variants = useQuery({
        queryKey: ['paragraph-variants', editionId, chapter, pick.blockId, pick.paragraph],
        queryFn: () => autotranslateApi.variants(editionId, chapter, pick.blockId, pick.paragraph),
        retry: false,
        staleTime: Infinity,
    });
    return (
        <Sheet open onClose={onClose} title="Інший варіант">
            <p className={styles.muted}>Зараз: {pick.paragraph}</p>
            {variants.isPending && <p className={styles.muted} aria-live="polite">ШІ пише варіанти…</p>}
            {variants.isError && <Notice tone="error">{variants.error.message}</Notice>}
            {variants.data?.variants.map((text) => (
                <div key={text} className={styles.form}>
                    <p>{text}</p>
                    <Button variant="secondary" onPress={() => onPick(text)}>Взяти цей</Button>
                </div>
            ))}
        </Sheet>
    );
}
