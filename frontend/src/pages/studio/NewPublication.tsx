import { TagPicker } from '../../studio/TagPicker';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { useRef, useState, type FormEvent } from 'react';
import { adminApi } from '../../admin/api';
import { meQuery, useMe } from '../../auth/me';
import { studioApi, teamApi, type StudioBlock } from '../../studio/api';
import { autotranslateApi } from '../../studio/autotranslate';
import { TextEditor, type EditorHandle } from '../../studio/TextEditor';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import { Toggle } from '../../ui/Toggle';
import styles from './studio.module.css';
import { useCanRun, useMaySite } from '../../ledger/api';
import { ArrowRightLeft, Languages, PenLine, RefreshCw, Sparkles } from '../../ui/icons';

/** {@code pasted}: a translation by hand whose originals the team pastes for «Перекласти з ШІ» (етап 19). */
type Kind = 'human' | 'pasted' | 'original' | 'syosetu';

export function NewPublication() {
    const canRun = useCanRun();
    const maySite = useMaySite();
    const me = useMe();
    const client = useQueryClient();
    const navigate = useNavigate();
    const teams = useQuery({ queryKey: ['my-teams'], queryFn: teamApi.mine });
    const [kind, setKind] = useState<Kind>('human');
    const [title, setTitle] = useState('');
    const [author, setAuthor] = useState('');
    const [description, setDescription] = useState<StudioBlock[]>([]);
    const [tags, setTags] = useState<string[]>([]);
    const [adult, setAdult] = useState(false);
    const [team, setTeam] = useState('');
    // «Маю право перекладати цей текст» (етап 19): every translation; an own work needs none.
    const [rights, setRights] = useState(false);
    const needsRights = kind !== 'original';
    const publishing = (teams.data ?? []).filter((t) => t.role !== 'editor');

    const [link, setLink] = useState('');
    const chosenTeam = team || publishing[0]?.handle || '';
    const prepare = useMutation({
        mutationFn: () => autotranslateApi.prepare(link.trim(), chosenTeam),
        onSuccess: ({ editionId }) => void navigate({ to: '/studio/$editionId/translate', params: { editionId: String(editionId) } }),
    });
    const editor = useRef<EditorHandle>(null);
    const create = useMutation({
        mutationFn: () => studioApi.create({
            kind: kind === 'original' ? 'original' : 'human', title: title.trim(), author: author.trim(),
            description: editor.current?.read() ?? description,
            tags, adult, team: chosenTeam, rightsConfirmed: needsRights && rights,
        }),
        onSuccess: ({ editionId }) => void navigate(kind === 'pasted'
            ? { to: '/studio/$editionId/originals', params: { editionId: String(editionId) } }
            : { to: '/studio/$editionId', params: { editionId: String(editionId) } }),
    });
    const ask = useMutation({
        mutationFn: adminApi.requestAccess,
        onSuccess: () => void client.invalidateQueries({ queryKey: meQuery.queryKey }),
    });
    const locked = kind === 'syosetu' && !maySite;

    function submit(event: FormEvent) {
        event.preventDefault();
        if (kind === 'syosetu') prepare.mutate();
        else create.mutate();
    }

    return (
        <section className={styles.page}>
            <h1 className={styles.title}>Нова публікація</h1>
            <div role="group" aria-label="Що публікуєте" style={{ margin: '14px 0' }}>
                <Choice on={kind === 'human'} onPick={() => setKind('human')} icon={<PenLine size={22} />} title="Свій переклад"
                    text="Ви перекладаєте самі. Далі — глави в редакторі або з файлів .txt і .md." />
                <Choice on={kind === 'original'} onPick={() => setKind('original')} icon={<Sparkles size={22} />} title="Свій твір"
                    text="Ваша власна історія українською. Ви — автор." />
                {canRun && (
                    <Choice on={kind === 'pasted'} onPick={() => setKind('pasted')} icon={<Languages size={22} />}
                        title="Вставлю оригінал — перекладе ШІ"
                        text="Ви вставляєте глави оригіналу, ШІ перекладає їх зі словником за ваші шаги." />
                )}
                {canRun && (
                    <Choice on={kind === 'syosetu'} onPick={() => setKind('syosetu')} icon={<RefreshCw size={22} />}
                        title="Автопереклад із сайту"
                        text={maySite ? 'Вставте посилання на Syosetu — назву й опис перекладемо одразу, далі глави «до N».'
                            : 'Глави беруться самі з сайту оригіналу. З дозволу власника сайту.'} />
                )}
                <Choice on={false} disabled icon={<ArrowRightLeft size={22} />} title="Продовжити покинутий"
                    text="Відкрийте новелу — якщо переклад вільний, там буде кнопка «Продовжити переклад»." />
            </div>
            {locked ? (
                <div className={styles.form}>
                    <Notice tone="info">
                        Автопереклад із сайту відкриває власник сайту окремим людям. Без нього можна вставляти оригінал
                        самому — «Вставлю оригінал — перекладе ШІ».
                    </Notice>
                    {ask.isError && <Notice tone="error">{ask.error.message}</Notice>}
                    {me?.autotranslateRequested ? (
                        <Notice tone="success">Запит надіслано. Відповідь власника сайту прийде у «Вхідні».</Notice>
                    ) : (
                        <Button wide pending={ask.isPending} pendingLabel="Надсилаємо…" onPress={() => ask.mutate()}>Попросити доступ</Button>
                    )}
                </div>
            ) : (
            <form className={styles.form} onSubmit={submit}>
                {kind === 'syosetu' ? (
                    <TextInput label="Посилання на новелу" value={link} onChange={setLink} isRequired inputMode="url"
                        placeholder="https://ncode.syosetu.com/…" hint="Сторінка новели на Syosetu." />
                ) : <>
                <TextInput label="Назва" value={title} onChange={setTitle} isRequired />
                {kind !== 'original'
                    ? <TextInput label="Автор оригіналу" value={author} onChange={setAuthor} hint="Українською, як читачі шукатимуть." />
                    : <p className={styles.muted}>Автор: {me?.nick}</p>}
                <div>
                    <div className={styles.label}>Опис</div>
                    <TextEditor handle={editor} mode="description" blocks={description} onChange={setDescription} label="Опис" placeholder="Про що історія?" />
                </div>
                <TagPicker value={tags} onChange={setTags} />
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
                {needsRights && (
                    <div>
                        <Toggle label="Маю право перекладати цей текст" isSelected={rights} onChange={setRights} />
                        <p className={styles.muted}>Автор чи видавець дозволив, або твір вільний для перекладу. <Link to="/rules">Правила сайту</Link></p>
                    </div>
                )}
                {create.isError && <Notice tone="error">{create.error.message}</Notice>}
                {prepare.isError && <Notice tone="error">{prepare.error.message}</Notice>}
                {kind === 'syosetu' ? (
                    <Button type="submit" wide pending={prepare.isPending} pendingLabel="Готуємо: читаємо й перекладаємо опис…" isDisabled={!link.trim() || !rights}>
                        Підготувати
                    </Button>
                ) : (
                    <Button type="submit" wide pending={create.isPending} pendingLabel="Створюємо…" isDisabled={!title.trim() || needsRights && !rights}>
                        Створити
                    </Button>
                )}
            </form>
            )}
        </section>
    );
}

function Choice({ on, onPick, icon, title, text, disabled = false }: {
    on: boolean; onPick?: () => void; icon: React.ReactNode; title: string; text: string; disabled?: boolean;
}) {
    return (
        <button type="button" className={styles.choice} aria-pressed={on} disabled={disabled} onClick={onPick}>
            <span aria-hidden style={{ flex: 'none', display: 'inline-flex', justifyContent: 'center', width: 28, paddingTop: 2 }}>{icon}</span>
            <span>
                <b style={{ display: 'block', marginBottom: 2 }}>{title}</b>
                <span className={styles.muted}>{text}</span>
            </span>
        </button>
    );
}
