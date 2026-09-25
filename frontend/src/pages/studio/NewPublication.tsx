import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { useState, type FormEvent } from 'react';
import { useMe } from '../../auth/me';
import { studioApi, teamApi, type StudioBlock } from '../../studio/api';
import { autotranslateApi } from '../../studio/autotranslate';
import { TextEditor } from '../../studio/TextEditor';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import { Toggle } from '../../ui/Toggle';
import styles from './studio.module.css';
import { useCanRun } from '../../ledger/api';

type Kind = 'human' | 'original' | 'syosetu';

export function NewPublication() {
    const canRun = useCanRun();
    const me = useMe();
    const navigate = useNavigate();
    const teams = useQuery({ queryKey: ['my-teams'], queryFn: teamApi.mine });
    const [kind, setKind] = useState<Kind>('human');
    const [title, setTitle] = useState('');
    const [author, setAuthor] = useState('');
    const [description, setDescription] = useState<StudioBlock[]>([]);
    const [tags, setTags] = useState('');
    const [adult, setAdult] = useState(false);
    const [team, setTeam] = useState('');
    const publishing = (teams.data ?? []).filter((t) => t.role !== 'editor');

    const [link, setLink] = useState('');
    const chosenTeam = team || publishing[0]?.handle || '';
    const prepare = useMutation({
        mutationFn: () => autotranslateApi.prepare(link.trim(), chosenTeam),
        onSuccess: ({ editionId }) => void navigate({ to: '/studio/$editionId/translate', params: { editionId: String(editionId) } }),
    });
    const create = useMutation({
        mutationFn: () => studioApi.create({
            kind: kind === 'original' ? 'original' : 'human', title: title.trim(), author: author.trim(), description,
            tags: tags.split(',').map((tag) => tag.trim()).filter(Boolean), adult, team: chosenTeam,
        }),
        onSuccess: ({ editionId }) => void navigate({ to: '/studio/$editionId', params: { editionId: String(editionId) } }),
    });

    function submit(event: FormEvent) {
        event.preventDefault();
        if (kind === 'syosetu') prepare.mutate();
        else create.mutate();
    }

    return (
        <section className={styles.page}>
            <h1 className={styles.title}>Нова публікація</h1>
            <div role="group" aria-label="Що публікуєте" style={{ margin: '14px 0' }}>
                <Choice on={kind === 'human'} onPick={() => setKind('human')} icon="✎" title="Свій переклад"
                    text="Ви перекладаєте самі. Далі — глави в редакторі або з файлів .txt і .md." />
                <Choice on={kind === 'original'} onPick={() => setKind('original')} icon="✦" title="Свій твір"
                    text="Ваша власна історія українською. Ви — автор." />
                <Choice on={kind === 'syosetu'} onPick={() => setKind('syosetu')} disabled={!canRun} icon="↻"
                    title="Автопереклад із Syosetu"
                    text={canRun ? 'Вставте посилання — назву й опис перекладемо одразу, далі глави «до N».'
                        : 'Запускається за шаги. Їх нараховує власник сайту.'} />
                <Choice on={false} disabled icon="⇢" title="Продовжити покинутий"
                    text="Відкрийте новелу — якщо переклад вільний, там буде кнопка «Продовжити переклад»." />
            </div>
            <form className={styles.form} onSubmit={submit}>
                {kind === 'syosetu' ? (
                    <TextInput label="Посилання на новелу" value={link} onChange={setLink} isRequired inputMode="url"
                        placeholder="https://ncode.syosetu.com/…" hint="Сторінка новели на Syosetu." />
                ) : <>
                <TextInput label="Назва" value={title} onChange={setTitle} isRequired />
                {kind === 'human'
                    ? <TextInput label="Автор оригіналу" value={author} onChange={setAuthor} hint="Українською, як читачі шукатимуть." />
                    : <p className={styles.muted}>Автор: {me?.nick}</p>}
                <div>
                    <div className={styles.label}>Опис</div>
                    <TextEditor mode="description" blocks={description} onChange={setDescription} label="Опис" placeholder="Про що історія?" />
                </div>
                <TextInput label="Теги" value={tags} onChange={setTags} hint="Через кому: фентезі, перевтілення, затишне" />
                </>}
                {publishing.length > 1 && (
                    <label>
                        <div className={styles.label}>Команда</div>
                        <select className={styles.select} value={team || publishing[0]!.handle} onChange={(event) => setTeam(event.target.value)}>
                            {publishing.map((t) => <option key={t.handle} value={t.handle}>{t.name} (${t.handle})</option>)}
                        </select>
                    </label>
                )}
                {kind !== 'syosetu' && <Toggle label="Для дорослих (18+)" isSelected={adult} onChange={setAdult} />}
                {create.isError && <Notice tone="error">{create.error.message}</Notice>}
                {prepare.isError && <Notice tone="error">{prepare.error.message}</Notice>}
                {kind === 'syosetu' ? (
                    <Button type="submit" wide pending={prepare.isPending} pendingLabel="Готуємо: читаємо й перекладаємо опис…" isDisabled={!link.trim()}>
                        Підготувати
                    </Button>
                ) : (
                    <Button type="submit" wide pending={create.isPending} pendingLabel="Створюємо…" isDisabled={!title.trim()}>
                        Створити
                    </Button>
                )}
            </form>
        </section>
    );
}

function Choice({ on, onPick, icon, title, text, disabled = false }: {
    on: boolean; onPick?: () => void; icon: string; title: string; text: string; disabled?: boolean;
}) {
    return (
        <button type="button" className={styles.choice} aria-pressed={on} disabled={disabled} onClick={onPick}>
            <span aria-hidden style={{ fontSize: 22, width: 28, textAlign: 'center' }}>{icon}</span>
            <span>
                <b style={{ display: 'block', marginBottom: 2 }}>{title}</b>
                <span className={styles.muted}>{text}</span>
            </span>
        </button>
    );
}
