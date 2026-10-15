import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useRef, useState } from 'react';
import { relativeTime } from '../../lib/dates';
import { plural } from '../../lib/plural';
import { usePage } from '../../lib/usePage';
import { studioApi } from '../../studio/api';
import { PICTURE_PAGE, picturesApi, type LibraryPicture, type PictureFilter, type PicturePlace } from '../../studio/pictures';
import { askConfirm } from '../../ui/ask';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Pager } from '../../ui/Pager';
import { Segmented } from '../../ui/Segmented';
import { Sheet } from '../../ui/Sheet';
import studio from '../studio/studio.module.css';
import styles from '../../studio/pictures.module.css';
import { PictureCard } from '../../studio/PicturePicker';

const FILTERS: { value: PictureFilter; label: string }[] = [
    { value: 'all', label: 'Усі' },
    { value: 'drawn', label: 'Намальовані' },
    { value: 'uploaded', label: 'Завантажені' },
    { value: 'unused', label: 'Ще не вставлені' },
];

/**
 * «Мої картинки»: everything the person drew on the site or uploaded stays here until they
 * put it away. Several files go up at once; pictures named «27-5.png» can be placed into a
 * novel in one go — chapter 27, after the fifth paragraph.
 */
export function PicturesPage() {
    const client = useQueryClient();
    const [filter, setFilter] = useState<PictureFilter>('all');
    const [page, setPage] = usePage();
    const [selecting, setSelecting] = useState(false);
    const [selected, setSelected] = useState<number[]>([]);
    const [open, setOpen] = useState<LibraryPicture | null>(null);
    const [placing, setPlacing] = useState(false);
    const pictures = useQuery({
        queryKey: ['my-pictures', filter, page],
        queryFn: () => picturesApi.page(filter, page),
        placeholderData: (previous) => previous,
    });
    const refresh = () => void client.invalidateQueries({ queryKey: ['my-pictures'] });

    const toggle = (id: number) => setSelected((now) => now.includes(id) ? now.filter((one) => one !== id) : [...now, id]);
    const stopSelecting = () => { setSelecting(false); setSelected([]); };

    return (
        <section className={studio.page}>
            <h1 className={studio.title}>Мої картинки</h1>
            <p className={studio.muted}>
                Тут усе, що ви намалювали на сайті або завантажили. Звідси картинку можна вставити в главу. Файли з назвою
                на зразок «27-5.png» сайт сам розставить у новелі: глава 27, після 5-го абзацу.
            </p>
            <Uploader onDone={(ids) => { refresh(); if (ids.length > 0) { setSelecting(true); setSelected(ids); } }} />
            <div className={studio.filters} style={{ marginTop: 12 }}>
                <Segmented label="Показати" value={filter} options={FILTERS} onChange={(next) => { setFilter(next); setPage(1); }} />
                <button type="button" className={`${studio.chip} ${selecting ? studio.chipOn : ''}`} aria-pressed={selecting}
                    onClick={() => selecting ? stopSelecting() : setSelecting(true)}>Вибрати</button>
            </div>
            {pictures.isError && <Notice tone="error">{pictures.error.message}</Notice>}
            {pictures.data?.total === 0 && (
                <p className={studio.muted}>{filter === 'all' ? 'Картинок поки немає. Додайте файли або намалюйте сцену в редакторі глави.' : 'Таких картинок немає.'}</p>
            )}
            <div className={styles.grid}>
                {pictures.data?.items.map((picture) => (
                    <PictureCard key={picture.id} picture={picture} selecting={selecting} selected={selected.includes(picture.id)}
                        onPress={() => selecting ? toggle(picture.id) : setOpen(picture)} />
                ))}
            </div>
            {pictures.data && <Pager page={page} total={pictures.data.total} size={PICTURE_PAGE} onPage={setPage} />}
            {selecting && (
                <div className={studio.selectionBar} role="toolbar" aria-label="Вибрані картинки">
                    <span className={studio.muted} style={{ alignSelf: 'center' }}>Вибрано: {selected.length}</span>
                    <Button variant="secondary" onPress={() => setSelected((now) => [...new Set([...now, ...(pictures.data?.items ?? []).map((one) => one.id)])])}>
                        Усі на сторінці
                    </Button>
                    <Button onPress={() => setPlacing(true)} isDisabled={selected.length === 0}>Розставити в новелі…</Button>
                    <Button variant="quiet" onPress={stopSelecting}>Скасувати</Button>
                </div>
            )}
            {open && <PictureSheet picture={open} onClose={() => setOpen(null)} onRemoved={() => { setOpen(null); refresh(); }} />}
            {placing && <PlaceSheet imageIds={selected} onClose={() => setPlacing(false)} onPlaced={() => { refresh(); stopSelecting(); }} />}
        </section>
    );
}

/** Several files at once, one after another, with «12 з 40»; a file the site refuses does not stop the rest. */
function Uploader({ onDone }: { onDone: (ids: number[]) => void }) {
    const input = useRef<HTMLInputElement>(null);
    const [over, setOver] = useState(false);
    const [progress, setProgress] = useState<{ done: number; total: number } | null>(null);
    const [failed, setFailed] = useState<string[]>([]);

    async function send(files: File[]) {
        const pictures = files.filter((file) => /^image\/(jpeg|png|webp)$/.test(file.type));
        if (pictures.length === 0) return;
        setFailed([]);
        const ids: number[] = [];
        const errors: string[] = [];
        for (let index = 0; index < pictures.length; index++) {
            setProgress({ done: index, total: pictures.length });
            try {
                ids.push((await studioApi.uploadImage(pictures[index]!, 'illustration')).id);
            } catch (failure) {
                errors.push(`${pictures[index]!.name}: ${(failure as Error).message}`);
            }
        }
        setProgress(null);
        setFailed(errors);
        if (input.current) input.current.value = '';
        onDone(ids);
    }

    return (
        <div className={`${styles.drop} ${over ? styles.dropOn : ''}`} style={{ marginTop: 12 }}
            onDragOver={(event) => { event.preventDefault(); setOver(true); }} onDragLeave={() => setOver(false)}
            onDrop={(event) => { event.preventDefault(); setOver(false); void send([...event.dataTransfer.files]); }}>
            <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" multiple hidden aria-label="Файли картинок"
                onChange={(event) => void send([...(event.target.files ?? [])])} />
            {progress
                ? <span>Завантажуємо… {progress.done} з {progress.total}</span>
                : <>
                    <Button onPress={() => input.current?.click()}>Додати картинки</Button>
                    <div style={{ marginTop: 6 }}>або перетягніть файли сюди · JPEG, PNG, WebP, можна кілька</div>
                </>}
            {failed.length > 0 && <Notice tone="error">Не завантажилось: {failed.join('; ')}</Notice>}
        </div>
    );
}

function PictureSheet({ picture, onClose, onRemoved }: { picture: LibraryPicture; onClose: () => void; onRemoved: () => void }) {
    const remove = useMutation({ meta: { errorToast: true }, mutationFn: () => picturesApi.remove(picture.id), onSuccess: onRemoved });
    return (
        <Sheet open onClose={onClose} title={picture.fileName ?? (picture.drawn ? 'Намальована картинка' : 'Картинка')} tall>
            <a href={picture.fullUrl} target="_blank" rel="noreferrer"><img className={styles.big} src={picture.url} alt="" /></a>
            <p className={studio.muted}>
                {picture.drawn ? 'Намальовано на сайті' : picture.sourceUrl ? 'Додано за посиланням' : 'Завантажено з файлу'} ·{' '}
                {relativeTime(new Date(picture.createdAt))} · {picture.width}×{picture.height}
            </p>
            {picture.fragment && <p className={studio.quote}>{picture.fragment}</p>}
            {picture.prompt && <details><summary className={studio.muted}>Опис для малювання</summary><p className={studio.muted}>{picture.prompt}</p></details>}
            <h2 className={studio.sectionTitle}>Де стоїть</h2>
            {picture.uses.length === 0 && <p className={studio.muted}>Ще в жодній главі.</p>}
            {picture.uses.map((use) => (
                <div key={`${use.editionId}-${use.number}`}>
                    <Link to="/studio/$editionId/chapters/$number" params={{ editionId: String(use.editionId), number: String(use.number) }}>
                        {use.title} · глава {use.number}
                    </Link>
                </div>
            ))}
            <div className={studio.actions}>
                <Button variant="danger" pending={remove.isPending} onPress={() => void askConfirm({
                    title: 'Прибрати з бібліотеки?',
                    text: picture.uses.length > 0 ? 'У главах вона лишиться, зникне лише звідси.' : 'Картинка зникне з «Моїх картинок».',
                    confirmLabel: 'Прибрати', danger: true,
                }).then((yes) => { if (yes) remove.mutate(); })}>Прибрати</Button>
            </div>
        </Sheet>
    );
}

/** Choose a novel, see where each picture would go, then put them all in. */
function PlaceSheet({ imageIds, onClose, onPlaced }: { imageIds: number[]; onClose: () => void; onPlaced: () => void }) {
    const mine = useQuery({ queryKey: ['studio'], queryFn: studioApi.mine });
    const editions = (mine.data ?? []).filter((edition) => edition.role === 'owner' || edition.role === 'translator');
    const [editionId, setEditionId] = useState<number | null>(null);
    const plan = useQuery({
        queryKey: ['picture-plan', editionId, imageIds],
        queryFn: () => picturesApi.preview(editionId!, imageIds),
        enabled: editionId !== null,
    });
    const place = useMutation({ meta: { errorToast: true }, mutationFn: () => picturesApi.place(editionId!, imageIds), onSuccess: onPlaced });
    const ready = (plan.data ?? []).filter((one) => one.problem === null);
    const chapters = new Set(ready.map((one) => one.chapter)).size;

    return (
        <Sheet open onClose={onClose} title="Розставити в новелі" tall>
            {place.data ? (
                <>
                    <Notice tone="success">Вставлено {plural(place.data.pictures, 'картинку', 'картинки', 'картинок')} у {plural(place.data.chapters, 'главу', 'глави', 'глав')}. Читачі вже бачать.</Notice>
                    {place.data.skipped.length > 0 && <Notice tone="info">Пропущено: {place.data.skipped.join('; ')}</Notice>}
                    <div className={studio.actions}><Button onPress={onClose}>Готово</Button></div>
                </>
            ) : (
                <>
                    <label className={studio.label} htmlFor="place-edition">Новела</label>
                    <select id="place-edition" className={studio.select} value={editionId ?? ''}
                        onChange={(event) => setEditionId(event.target.value ? Number(event.target.value) : null)}>
                        <option value="">Виберіть…</option>
                        {editions.map((edition) => <option key={edition.editionId} value={edition.editionId}>{edition.title}</option>)}
                    </select>
                    {mine.isSuccess && editions.length === 0 && <p className={studio.muted}>Вставляти картинки можна в новели, де ви власник або перекладач.</p>}
                    {plan.isError && <Notice tone="error">{plan.error.message}</Notice>}
                    {plan.data && (
                        <>
                            <p className={studio.muted}>
                                Кожна картинка стане після свого абзацу, і глава одразу опублікується — як після «Опублікувати» в редакторі.
                            </p>
                            <div className={styles.places}>
                                {plan.data.map((one) => <PlaceRow key={one.imageId} place={one} />)}
                            </div>
                            <div className={studio.actions}>
                                <Button onPress={() => place.mutate()} pending={place.isPending} pendingLabel="Вставляємо…" isDisabled={ready.length === 0}>
                                    Вставити {ready.length} у {plural(chapters, 'главу', 'глави', 'глав')}
                                </Button>
                                <Button variant="secondary" onPress={onClose}>Скасувати</Button>
                            </div>
                        </>
                    )}
                </>
            )}
        </Sheet>
    );
}

function PlaceRow({ place }: { place: PicturePlace }) {
    return (
        <div className={styles.place}>
            {place.url && <img src={place.url} alt="" loading="lazy" />}
            <div className={studio.grow}>
                <div>{place.fileName ?? `Картинка ${place.imageId}`}</div>
                {place.problem
                    ? <div className={styles.problem}>{place.problem}</div>
                    : <div className={studio.muted}>
                        Глава {place.chapter}, {place.paragraph === 0 ? 'на початку' : `після ${place.paragraph}-го абзацу: «${place.quote}»`}
                    </div>}
            </div>
        </div>
    );
}
