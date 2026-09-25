import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { chapterHeading } from '../../reading/api';
import { autotranslateApi, type ChapterAnalysis } from '../../studio/autotranslate';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Pager } from '../../ui/Pager';
import { TextInput } from '../../ui/TextInput';
import { useEditionId } from './EditionPage';
import styles from './studio.module.css';

/** Chapter titles and numbers from analysis, 50 to a page, fixed before translating. */
export function TitlesPage() {
    const id = useEditionId();
    const [page, setPage] = useState(1);
    const titles = useQuery({ queryKey: ['analysis', id, page], queryFn: () => autotranslateApi.analysis(id, page), placeholderData: (p) => p });
    return (
        <section className={styles.page}>
            <Link to="/studio/$editionId/translate" params={{ editionId: String(id) }} className={styles.muted}>‹ Автопереклад</Link>
            <h1 className={styles.title}>Назви глав</h1>
            <p className={styles.muted}>
                Номер — як на сайті: 0, 12, 31.1 або порожньо, якщо без номера (пролог, побічна історія). Для вже перекладених
                глав зміна подіє, коли главу перекладуть заново.
            </p>
            {titles.isError && <Notice tone="error">{titles.error.message}</Notice>}
            {titles.data?.total === 0 && <p className={styles.muted}>Назви з'являться після аналізу глав.</p>}
            {titles.data?.items.map((chapter) => <TitleRow key={chapter.number} editionId={id} chapter={chapter} />)}
            {titles.data && <Pager page={page} total={titles.data.total} size={50} onPage={setPage} />}
        </section>
    );
}

function TitleRow({ editionId, chapter }: { editionId: number; chapter: ChapterAnalysis }) {
    const client = useQueryClient();
    const [label, setLabel] = useState(chapter.label ?? String(chapter.number));
    const [title, setTitle] = useState(chapter.title);
    const changed = label !== (chapter.label ?? String(chapter.number)) || title !== chapter.title;
    const save = useMutation({
        mutationFn: () => autotranslateApi.editAnalysis(editionId, chapter.number,
            { title, label: label.trim() === String(chapter.number) ? null : label.trim() }),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['analysis', editionId] }),
    });
    return (
        <form className={`${styles.entry} ${styles.analysisRow}`} onSubmit={(event) => { event.preventDefault(); save.mutate(); }}
            aria-label={`Глава ${chapter.number}: ${chapterHeading({ ...chapter, title })}`}>
            <TextInput label="№" value={label} onChange={setLabel} inputMode="decimal" />
            <TextInput label={chapter.translated ? 'Назва · перекладено' : 'Назва'} value={title} onChange={setTitle} />
            {changed && <Button type="submit" pending={save.isPending}>Зберегти</Button>}
            {save.isError && <Notice tone="error">{save.error.message}</Notice>}
        </form>
    );
}
