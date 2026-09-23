import { useState } from 'react';
import { mutate } from '../api/client';
import type { TagView } from '../api/types';
import { ActionNotice } from '../components/ActionNotice';
import { HelpTip } from '../components/HelpTip';
import { TagInput } from '../components/TagInput';
import { useAction } from '../hooks/useAction';
import { MACHINE_TRANSLATION, tagSlug } from '../lib/tags';

/** Tag set of one novel. Changes are local until saved, so several tags can be edited in one request. */
export function NovelTags({ path, initial, aiTranslated }: { path: string; initial: TagView[]; aiTranslated: boolean }) {
    const [tags, setTags] = useState(initial.map(tag => tag.name));
    const [saved, setSaved] = useState(initial.map(tag => tag.name));
    const action = useAction();
    const has = (name: string) => tags.some(tag => tagSlug(tag) === tagSlug(name));
    const add = (name: string) => { if (!has(name) && tags.length < 12) setTags([...tags, name]); };
    const changed = tags.join('\n') !== saved.join('\n');
    return <section className="novel-tags-editor" aria-labelledby="novel-tags-title">
        <h3 id="novel-tags-title">Теги<HelpTip label="Теги новели">Теги допомагають знайти новелу в каталозі. Регістр і пробіли не мають значення: «Фентезі» й «фентезі» — один тег. До 12 тегів на новелу.</HelpTip></h3>
        {tags.length ? <div className="tag-editor-list">{tags.map(tag => <button type="button" key={tagSlug(tag)} className="tag-chip removable"
            aria-label={'Прибрати тег ' + tag} onClick={() => setTags(tags.filter(item => item !== tag))}>{tag} <span aria-hidden="true">×</span></button>)}</div>
            : <p className="muted">Тегів поки немає.</p>}
        {aiTranslated && !has(MACHINE_TRANSLATION) && <p className="tag-suggestion notice">Цю новелу перекладала Novelka.
            <button type="button" onClick={() => add(MACHINE_TRANSLATION)}>Додати тег «{MACHINE_TRANSLATION}»</button></p>}
        <TagInput label="Новий тег" placeholder="Наприклад, фентезі" onAdd={add} />
        <div className="button-row"><button type="button" className="button" disabled={action.busy || !changed} onClick={() => {
            void action.run(async () => {
                const result = await mutate<{ tags: TagView[] }>(path + '/tags', { tags });
                setTags(result.tags.map(tag => tag.name)); setSaved(result.tags.map(tag => tag.name));
            }, 'Теги збережено.');
        }}>Зберегти теги</button>{changed && <span className="muted">Є незбережені зміни.</span>}</div>
        <ActionNotice {...action} />
    </section>;
}
