import { useQuery } from '@tanstack/react-query';
import { readingApi } from '../reading/api';
import styles from './TagPicker.module.css';

const MAX = 12;

/**
 * Tags are picked from the site's list by groups, as filters in a shop — never typed, so
 * «фентезі» and «фэнтези» do not become two tags. Old tags outside the list can only be removed.
 */
export function TagPicker({ value, onChange }: { value: string[]; onChange: (tags: string[]) => void }) {
    const groups = useQuery({ queryKey: ['tag-groups'], queryFn: readingApi.tagGroups, staleTime: 10 * 60_000 });
    const chosen = new Set(value.map((tag) => tag.toLowerCase()));
    const known = new Set((groups.data ?? []).flatMap((group) => group.tags.map((tag) => tag.slug)));
    const outside = groups.data ? value.filter((tag) => !known.has(tag.toLowerCase())) : [];
    const toggle = (name: string) => {
        if (chosen.has(name.toLowerCase())) onChange(value.filter((tag) => tag.toLowerCase() !== name.toLowerCase()));
        else if (value.length < MAX) onChange([...value, name]);
    };

    return (
        <fieldset className={styles.picker}>
            <legend className={styles.legend}>Теги <span className={styles.muted}>· обрано {value.length} з {MAX}</span></legend>
            {groups.isError && <p className={styles.muted}>Не вдалося завантажити список тегів.</p>}
            {groups.data?.map((group) => (
                <div key={group.name} className={styles.group}>
                    <div className={styles.muted}>{group.name}</div>
                    <div className={styles.chips} role="group" aria-label={group.name}>
                        {group.tags.map((tag) => {
                            const on = chosen.has(tag.slug);
                            return (
                                <button key={tag.slug} type="button" aria-pressed={on} disabled={!on && value.length >= MAX}
                                    className={`${styles.chip} ${on ? styles.on : ''}`} onClick={() => toggle(tag.name)}>
                                    {tag.name}
                                </button>
                            );
                        })}
                    </div>
                </div>
            ))}
            {outside.length > 0 && (
                <div className={styles.group}>
                    <div className={styles.muted}>Поза списком — лишаються, доки не приберете</div>
                    <div className={styles.chips}>
                        {outside.map((tag) => (
                            <button key={tag} type="button" aria-pressed className={`${styles.chip} ${styles.on}`} onClick={() => toggle(tag)}>
                                {tag} ×
                            </button>
                        ))}
                    </div>
                </div>
            )}
        </fieldset>
    );
}
