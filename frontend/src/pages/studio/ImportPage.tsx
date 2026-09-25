import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { useRef, useState } from 'react';
import { studioApi } from '../../studio/api';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { useEditionId } from './EditionPage';
import styles from './studio.module.css';

/** Upload → see how the file splits into chapters and what gets simplified → publish. */
export function ImportPage() {
    const id = useEditionId();
    const navigate = useNavigate();
    const client = useQueryClient();
    const input = useRef<HTMLInputElement>(null);
    const [file, setFile] = useState<File | null>(null);
    const preview = useMutation({ mutationFn: (chosen: File) => studioApi.previewImport(id, chosen) });
    const publish = useMutation({
        mutationFn: () => studioApi.importFile(id, file!),
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: ['studio-chapters', id] });
            void client.invalidateQueries({ queryKey: ['studio-edition', id] });
            void navigate({ to: '/studio/$editionId', params: { editionId: String(id) } });
        },
    });

    function choose(chosen: File | undefined) {
        if (!chosen) return;
        setFile(chosen);
        preview.mutate(chosen);
    }

    return (
        <section className={styles.page}>
            <p><Link to="/studio/$editionId" params={{ editionId: String(id) }}>← До публікації</Link></p>
            <h1 className={styles.title}>Глави з файлу</h1>
            <p className={styles.muted}>
                .txt або .md у кодуванні UTF-8. У .md нова глава починається з «# Назва», у .txt — з рядка «Глава N».
                Жирний **так**, курсив *так*, підкреслений ++так++, закреслений ~~так~~.
            </p>
            <input ref={input} type="file" accept=".txt,.md,text/plain,text/markdown" hidden onChange={(event) => choose(event.target.files?.[0])} />
            <div className={styles.actions}>
                <Button variant="secondary" onPress={() => input.current?.click()} pending={preview.isPending} pendingLabel="Читаємо файл…">
                    {file ? 'Інший файл' : 'Вибрати файл'}
                </Button>
            </div>
            {preview.isError && <Notice tone="error">{preview.error.message}</Notice>}
            {preview.data && (
                <>
                    {preview.data.simplified.length > 0 && (
                        <Notice tone="info">{preview.data.simplified.map((line) => <p key={line}>{line}</p>)}</Notice>
                    )}
                    <h2 className={styles.sectionTitle}>Буде {preview.data.chapters.length} глав(и), починаючи з {preview.data.firstNumber}</h2>
                    <table className={styles.table}>
                        <tbody>
                            {preview.data.chapters.map((chapter, index) => (
                                <tr key={index}>
                                    <td>{preview.data.firstNumber + index}. {chapter.title}</td>
                                    <td>{chapter.characters} знаків{chapter.pictures ? ` · картинок: ${chapter.pictures}` : ''}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                    <div className={styles.actions}>
                        <Button onPress={() => publish.mutate()} pending={publish.isPending} pendingLabel="Публікуємо…">Опублікувати всі</Button>
                    </div>
                    {publish.isError && <Notice tone="error">{publish.error.message}</Notice>}
                </>
            )}
        </section>
    );
}
