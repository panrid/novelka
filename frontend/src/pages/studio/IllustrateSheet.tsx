import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { illustrationApi, type Aspect, type Drawn } from '../../studio/illustrations';
import { dollars, money } from '../../studio/autotranslate';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
import { Sheet } from '../../ui/Sheet';
import { TextInput } from '../../ui/TextInput';
import styles from './studio.module.css';

/**
 * «Намалювати сцену»: the fragment becomes a description for the artist (editable), the
 * price is shown before anything is paid, and a drawn picture goes into the chapter only
 * when the owner likes it.
 */
export function IllustrateSheet({ editionId, chapter, fragment: initial, onInsert, onClose }: {
    editionId: number; chapter: number; fragment: string; onInsert: (picture: { id: number; url: string }) => void; onClose: () => void;
}) {
    const [fragment, setFragment] = useState(initial);
    const [prompt, setPrompt] = useState('');
    const [aspect, setAspect] = useState<Aspect>('3:4');
    const [drawn, setDrawn] = useState<Drawn | null>(null);
    const price = useQuery({ queryKey: ['illustration-price', editionId], queryFn: () => illustrationApi.price(editionId) });
    const describe = useMutation({ mutationFn: () => illustrationApi.describe(editionId, fragment), onSuccess: (result) => setPrompt(result.prompt) });
    const draw = useMutation({ mutationFn: () => illustrationApi.draw(editionId, { prompt, fragment, aspect, chapter }), onSuccess: setDrawn });
    const cost = price.data ? money(price.data.shah, price.data.usd, price.data.showShah) : '…';

    return (
        <Sheet open onClose={onClose} title="Намалювати сцену" tall>
            <TextInput label="Фрагмент глави" value={fragment} onChange={setFragment} multiline />
            <div className={styles.actions}>
                <Button variant="secondary" onPress={() => describe.mutate()} pending={describe.isPending} pendingLabel="Описуємо сцену…"
                    isDisabled={!fragment.trim()}>
                    {prompt ? 'Описати ще раз' : 'Скласти опис для художника'}
                </Button>
            </div>
            {describe.isError && <Notice tone="error">{describe.error.message}</Notice>}
            {prompt && (
                <>
                    <TextInput label="Опис для художника" value={prompt} onChange={setPrompt} multiline
                        hint="Англійською: так моделі малюють точніше. Можна поправити." />
                    <Segmented label="Форма" value={aspect} onChange={setAspect}
                        options={[{ value: '3:4', label: 'Вертикальна' }, { value: '16:9', label: 'Широка' }, { value: '1:1', label: 'Квадрат' }]} />
                    <Button onPress={() => draw.mutate()} pending={draw.isPending} pendingLabel="Малюємо, до хвилини…" isDisabled={!prompt.trim()}>
                        {drawn ? `Ще варіант · ${cost}` : `Намалювати · ${cost}`}
                    </Button>
                </>
            )}
            {draw.isError && <Notice tone="error">{draw.error.message}</Notice>}
            {drawn && (
                <figure className={styles.drawn}>
                    <img src={drawn.url} alt="Намальована ілюстрація" />
                    <figcaption className={styles.muted}>
                        Коштувало {price.data?.showShah === false ? dollars(drawn.costUsd, 3) : `${money(drawn.costShah, drawn.costUsd, true)} (${dollars(drawn.costUsd, 3)})`}
                    </figcaption>
                    <div className={styles.actions}>
                        <Button onPress={() => { onInsert({ id: drawn.imageId, url: drawn.url }); onClose(); }}>Вставити в главу</Button>
                        <Button variant="secondary" onPress={onClose}>Не треба</Button>
                    </div>
                </figure>
            )}
        </Sheet>
    );
}
