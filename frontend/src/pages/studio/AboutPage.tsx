import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { Cover } from '../../reading/Cover';
import { studioApi, type Overview, type StudioBlock } from '../../studio/api';
import { TextEditor } from '../../studio/TextEditor';
import { ImagePicker } from '../../ui/AvatarPicker';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
import { TextInput } from '../../ui/TextInput';
import { Toggle } from '../../ui/Toggle';
import { useEditionId } from './EditionPage';
import styles from './studio.module.css';

export function AboutPage() {
    const id = useEditionId();
    const overview = useQuery({ queryKey: ['studio-edition', id], queryFn: () => studioApi.overview(id) });
    if (overview.isError) return <Notice tone="error">{overview.error.message}</Notice>;
    if (!overview.data) return null;
    return <AboutForm key={overview.dataUpdatedAt} edition={overview.data} />;
}

function AboutForm({ edition }: { edition: Overview }) {
    const client = useQueryClient();
    const [title, setTitle] = useState(edition.title);
    const [author, setAuthor] = useState(edition.author);
    const [description, setDescription] = useState<StudioBlock[]>(edition.description);
    const [tags, setTags] = useState(edition.tags.join(', '));
    const [status, setStatus] = useState(edition.status);
    const [adult, setAdult] = useState(edition.adult);
    const refresh = (updated: Overview) => client.setQueryData(['studio-edition', edition.editionId], updated);

    const save = useMutation({
        mutationFn: () => studioApi.update(edition.editionId, {
            title, author, description, status, adult, tags: tags.split(',').map((tag) => tag.trim()).filter(Boolean),
        }),
        onSuccess: refresh,
    });
    const cover = useMutation({
        mutationFn: async (image: Blob | null) => {
            const uploaded = image ? await studioApi.uploadImage(image, 'cover') : null;
            return studioApi.setCover(edition.editionId, uploaded?.id ?? null);
        },
        onSuccess: refresh,
    });

    return (
        <section className={styles.page}>
            <Link to="/studio/$editionId" params={{ editionId: String(edition.editionId) }} className={styles.muted}>‹ До публікації</Link>
            <h1 className={styles.title}>Дані й обкладинка</h1>

            <div className={styles.head} style={{ margin: '14px 0' }}>
                <Cover url={edition.coverUrl} title={edition.title} seed={edition.novelSlug} width={110} />
                <div className={styles.form} style={{ gap: 8 }}>
                    <ImagePicker onCropped={(blob) => cover.mutate(blob)} pending={cover.isPending} aspect={2 / 3} width={960}
                        label={edition.coverUrl ? 'Інша обкладинка' : 'Завантажити обкладинку'} title="Рамка 2:3 — що буде на обкладинці" />
                    {edition.coverUrl && <Button variant="quiet" onPress={() => cover.mutate(null)}>Прибрати</Button>}
                    <p className={styles.muted}>JPEG, PNG або WebP до 5 МБ.</p>
                </div>
            </div>
            {cover.isError && <Notice tone="error">{cover.error.message}</Notice>}

            <div className={styles.form}>
                <TextInput label="Назва" value={title} onChange={setTitle} />
                {edition.kind !== 'original' && edition.ownNovel && <TextInput label="Автор оригіналу" value={author} onChange={setAuthor} />}
                <div>
                    <div className={styles.label}>Опис</div>
                    <TextEditor mode="description" blocks={description} onChange={setDescription} label="Опис" />
                </div>
                {edition.ownNovel && <TextInput label="Теги" value={tags} onChange={setTags} hint="Через кому, до 12." />}
                <Segmented label="Стан" value={status} onChange={setStatus}
                    options={[{ value: 'ongoing', label: 'Триває' }, { value: 'paused', label: 'Пауза' }, { value: 'completed', label: 'Завершено' }, { value: 'abandoned', label: 'Покинуто' }]} />
                {status === 'abandoned' && edition.kind !== 'original' && (
                    <Notice tone="info">«Покинуто» відкриває естафету: інша команда зможе продовжити з наступної глави.</Notice>
                )}
                <Toggle label="Для дорослих (18+)" isSelected={adult} onChange={setAdult} />
                {save.isError && <Notice tone="error">{save.error.message}</Notice>}
                {save.isSuccess && <Notice tone="success">Збережено.</Notice>}
                <Button onPress={() => save.mutate()} pending={save.isPending} pendingLabel="Зберігаємо…">Зберегти</Button>
            </div>
        </section>
    );
}
