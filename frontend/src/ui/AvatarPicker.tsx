import { useRef, useState } from 'react';
import Cropper, { type Area } from 'react-easy-crop';
import { Dialog, Heading, Modal, ModalOverlay } from 'react-aria-components';
import { Button } from './Button';
import styles from './AvatarPicker.module.css';

/** A round avatar, 512×512. */
export function AvatarPicker(props: { onCropped: (image: Blob) => void; label?: string; pending?: boolean }) {
    return <ImagePicker {...props} aspect={1} round width={512} title="Оберіть, що буде в колі" />;
}

/**
 * Pick a photo and crop it before upload. The browser applies the photo's rotation when
 * drawing, and the canvas output carries no camera metadata.
 */
export function ImagePicker({ onCropped, label = 'Вибрати фото', pending = false, aspect, round = false, width, title }: {
    onCropped: (image: Blob) => void;
    label?: string;
    pending?: boolean;
    aspect: number;
    round?: boolean;
    width: number;
    title: string;
}) {
    const input = useRef<HTMLInputElement>(null);
    const [source, setSource] = useState<string | null>(null);
    const [crop, setCrop] = useState({ x: 0, y: 0 });
    const [zoom, setZoom] = useState(1);
    const [area, setArea] = useState<Area | null>(null);

    function choose(files: FileList | null) {
        const file = files?.[0];
        if (file) {
            setSource(URL.createObjectURL(file));
            setZoom(1);
            setCrop({ x: 0, y: 0 });
        }
        if (input.current) {
            input.current.value = '';
        }
    }

    function close() {
        if (source) {
            URL.revokeObjectURL(source);
        }
        setSource(null);
    }

    async function done() {
        if (source && area) {
            onCropped(await cropToBlob(source, area, width, Math.round(width / aspect)));
        }
        close();
    }

    return (
        <>
            <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={(event) => choose(event.target.files)} />
            <Button variant="secondary" onPress={() => input.current?.click()} pending={pending} pendingLabel="Завантажуємо…">
                {label}
            </Button>
            <ModalOverlay className={styles.overlay} isOpen={source !== null} onOpenChange={(open) => !open && close()} isDismissable>
                <Modal className={styles.modal}>
                    <Dialog className={styles.dialog}>
                        <Heading slot="title" className={styles.heading}>{title}</Heading>
                        <div className={styles.area}>
                            {source && (
                                <Cropper image={source} crop={crop} zoom={zoom} aspect={aspect} cropShape={round ? 'round' : 'rect'} showGrid={false}
                                    onCropChange={setCrop} onZoomChange={setZoom} onCropComplete={(_, pixels) => setArea(pixels)} />
                            )}
                        </div>
                        <label className={styles.zoom}>
                            <span>Масштаб</span>
                            <input type="range" min={1} max={3} step={0.01} value={zoom} onChange={(event) => setZoom(Number(event.target.value))} />
                        </label>
                        <div className={styles.actions}>
                            <Button variant="secondary" onPress={close}>Скасувати</Button>
                            <Button onPress={() => void done()} isDisabled={!area}>Готово</Button>
                        </div>
                    </Dialog>
                </Modal>
            </ModalOverlay>
        </>
    );
}

async function cropToBlob(source: string, area: Area, width: number, height: number): Promise<Blob> {
    const image = new Image();
    image.src = source;
    await image.decode();
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d')!;
    context.imageSmoothingQuality = 'high';
    context.drawImage(image, area.x, area.y, area.width, area.height, 0, 0, width, height);
    return new Promise((resolve, reject) =>
        canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('crop failed'))), 'image/jpeg', 0.92));
}
