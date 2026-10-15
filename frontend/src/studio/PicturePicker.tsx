import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Button } from '../ui/Button';
import { Check } from '../ui/icons';
import { Notice } from '../ui/Notice';
import { Pager } from '../ui/Pager';
import { Sheet } from '../ui/Sheet';
import { PICTURE_PAGE, picturesApi, type LibraryPicture } from './pictures';
import styles from './pictures.module.css';

export function PictureCard({ picture, selecting, selected, onPress }: {
    picture: LibraryPicture; selecting: boolean; selected: boolean; onPress: () => void;
}) {
    const where = picture.uses.length > 0
        ? `глава ${picture.uses[0]!.number}${picture.uses.length > 1 ? ` і ще ${picture.uses.length - 1}` : ''}`
        : 'ще ніде';
    return (
        <button type="button" className={`${styles.card} ${selected ? styles.selected : ''}`} onClick={onPress}
            aria-pressed={selecting ? selected : undefined} aria-label={picture.fileName ?? (picture.drawn ? 'Намальована картинка' : 'Картинка')}>
            <img src={picture.url} alt="" loading="lazy" />
            {selecting && <span className={styles.tick}>{selected && <Check size={14} aria-hidden />}</span>}
            <span className={styles.caption}>{picture.fileName ?? (picture.drawn ? 'Намальовано' : 'Завантажено')}</span>
            <span className={styles.caption}>{where}</span>
        </button>
    );
}

/** «З моїх картинок» in the editor: pick one or several; they go after the paragraph with the cursor. */
export function PicturePicker({ onPick, onClose }: { onPick: (pictures: LibraryPicture[]) => void; onClose: () => void }) {
    const [page, setPage] = useState(1);
    const [chosen, setChosen] = useState<LibraryPicture[]>([]);
    const pictures = useQuery({
        queryKey: ['my-pictures', 'all', page],
        queryFn: () => picturesApi.page('all', page),
        placeholderData: (previous) => previous,
    });
    const toggle = (picture: LibraryPicture) => setChosen((now) => now.some((one) => one.id === picture.id)
        ? now.filter((one) => one.id !== picture.id) : [...now, picture]);
    return (
        <Sheet open onClose={onClose} title="Мої картинки" tall>
            {pictures.isError && <Notice tone="error">{pictures.error.message}</Notice>}
            {pictures.data?.total === 0 && (
                <p className={styles.caption} style={{ whiteSpace: 'normal' }}>
                    Бібліотека порожня. Завантажте картинки в «Я → Мої картинки» або намалюйте сцену.
                </p>
            )}
            <div className={styles.grid}>
                {pictures.data?.items.map((picture) => (
                    <PictureCard key={picture.id} picture={picture} selecting selected={chosen.some((one) => one.id === picture.id)}
                        onPress={() => toggle(picture)} />
                ))}
            </div>
            {pictures.data && <Pager page={page} total={pictures.data.total} size={PICTURE_PAGE} onPage={setPage} />}
            <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
                <Button onPress={() => { onPick(chosen); onClose(); }} isDisabled={chosen.length === 0}>
                    {chosen.length > 1 ? `Вставити ${chosen.length}` : 'Вставити'}
                </Button>
                <Button variant="secondary" onPress={onClose}>Скасувати</Button>
            </div>
        </Sheet>
    );
}
