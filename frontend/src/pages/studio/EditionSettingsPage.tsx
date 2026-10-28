import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { useState } from 'react';
import { relativeTime } from '../../lib/dates';
import { changes, characters, paragraphs } from '../../lib/plural';
import { STATUS_LABELS, type Status } from '../../reading/api';
import { studioApi, type Overview } from '../../studio/api';
import { Avatar } from '../../ui/Avatar';
import { Button } from '../../ui/Button';
import { Collapsible } from '../../ui/Collapsible';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
import { Sheet } from '../../ui/Sheet';
import { TextInput } from '../../ui/TextInput';
import { useEditionId } from './EditionPage';
import { EditionShell, useEditionOverview } from './EditionShell';
import styles from './studio.module.css';

/** «Налаштування»: the translation's data, its people and where the original is. */
export function EditionSettingsPage() {
    return (
        <EditionShell tab="settings">
            <SettingsTab />
        </EditionShell>
    );
}

function SettingsTab() {
    const id = useEditionId();
    const edition = useEditionOverview(id).data!;
    const contributions = useQuery({ queryKey: ['studio-contributions', id], queryFn: () => studioApi.contributions(id) });
    const owner = edition.role === 'owner';
    const translator = edition.role !== 'editor';
    const params = { editionId: String(id) };
    return (
        <>
            <h2 className={styles.sectionTitle}>Публікація</h2>
            <nav className={styles.menu} aria-label="Публікація" style={{ marginTop: 0 }}>
                {owner && (
                    <Link to="/studio/$editionId/about" params={params} className={styles.menuItem}>
                        <span>Дані й обкладинка<span className={styles.menuHint}>назва, опис, теги, стан: {STATUS_LABELS[edition.status as Status]}</span></span>
                    </Link>
                )}
                {translator && (
                    <Link to="/studio/$editionId/structure" params={params} className={styles.menuItem}>
                        <span>Томи й нумерація<span className={styles.menuHint}>пролог, екстра, номери глав</span></span>
                    </Link>
                )}
                {edition.chapterCount > 0 && (
                    <Link to="/n/$slug" params={{ slug: edition.novelSlug }} search={{ t: edition.teamHandle }} className={styles.menuItem}>
                        <span>Як бачать читачі<span className={styles.menuHint}>/n/{edition.novelSlug}</span></span>
                    </Link>
                )}
                {edition.originalUrl && (
                    <a href={edition.originalUrl} target="_blank" rel="noopener noreferrer nofollow" className={styles.menuItem}>
                        <span>Оригінал ↗<span className={styles.menuHint}>{new URL(edition.originalUrl).host}</span></span>
                    </a>
                )}
            </nav>

            <h2 className={styles.sectionTitle}>Люди</h2>
            <nav className={styles.menu} aria-label="Люди" style={{ marginTop: 0 }}>
                <Link to="/team/$handle" params={{ handle: edition.teamHandle }} className={styles.menuItem}>
                    <span>Команда ${edition.teamHandle}<span className={styles.menuHint}>хто перекладає й редагує</span></span>
                </Link>
                {owner && edition.kind !== 'original' && (
                    <Link to="/studio/$editionId/relay" params={params} className={styles.menuItem}>
                        <span>Естафета<span className={styles.menuHint}>хто може продовжити переклад</span></span>
                    </Link>
                )}
            </nav>

            {owner && <VisibilityChoice edition={edition} />}
            {owner && <InvitedReaders editionId={id} />}

            {(contributions.data?.length ?? 0) > 0 && (
                <Collapsible id="studio-contributions" title="Внесок" count={contributions.data!.length}>
                    <table className={styles.table}>
                        <tbody>
                            {contributions.data!.map((row) => (
                                <tr key={row.nick}>
                                    <td>{row.nick}</td>
                                    <td>{changes(row.revisions)} · {paragraphs(row.blocksChanged)} · {characters(row.charsChanged)}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </Collapsible>
            )}

            {owner && <DeleteEdition editionId={id} title={edition.title} />}
        </>
    );
}

/**
 * «Видалити переклад»: a code comes to the email and Telegram; with it the translation goes to the
 * bin for 30 days (Студія → «Кошик» brings it back).
 */
function DeleteEdition({ editionId, title }: { editionId: number; title: string }) {
    const client = useQueryClient();
    const navigate = useNavigate();
    const [open, setOpen] = useState(false);
    const [code, setCode] = useState('');
    const send = useMutation({ mutationFn: () => studioApi.deleteCode(editionId) });
    const remove = useMutation({
        mutationFn: () => studioApi.deleteEdition(editionId, code),
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: ['studio'] });
            void navigate({ to: '/studio/trash' });
        },
    });
    const close = () => {
        setOpen(false);
        setCode('');
        send.reset();
        remove.reset();
    };
    return (
        <>
            <h2 className={styles.sectionTitle}>Видалення</h2>
            <p className={styles.muted}>Переклад піде в кошик: читачі його не побачать, а 30 днів його можна буде відновити.</p>
            <Button variant="secondary" onPress={() => setOpen(true)}>Видалити переклад</Button>
            {open && (
                <Sheet open onClose={close} title={`Видалити «${title}»?`}>
                    <div style={{ display: 'grid', gap: 12 }}>
                        <p className={styles.muted}>
                            Читачі більше не побачать переклад, запуски автоперекладу зупиняться. Він полежить у кошику 30 днів —
                            звідти його можна відновити, — а потім глави зітруться назавжди.
                        </p>
                        {!send.isSuccess ? (
                            <Button onPress={() => send.mutate()} pending={send.isPending} pendingLabel="Надсилаємо…">
                                Надіслати код на пошту й у Telegram
                            </Button>
                        ) : (
                            <>
                                <p role="status">
                                    Код надіслано{send.data.email ? ` на ${send.data.email}` : ''} і в Telegram, якщо його прив’язано.
                                    Він діє {send.data.minutes} хвилин.
                                </p>
                                <TextInput label="Код" value={code} onChange={setCode} inputMode="numeric" autoComplete="one-time-code" />
                                <Button onPress={() => remove.mutate()} pending={remove.isPending} pendingLabel="Видаляємо…"
                                    isDisabled={code.trim().length !== 6}>
                                    Видалити переклад
                                </Button>
                                <button type="button" className={styles.plainButton} onClick={() => send.mutate()} disabled={send.isPending}>
                                    Надіслати код ще раз
                                </button>
                            </>
                        )}
                        {(send.error ?? remove.error) && <Notice tone="error">{(send.error ?? remove.error)!.message}</Notice>}
                    </div>
                </Sheet>
            )}
        </>
    );
}

function VisibilityChoice({ edition }: { edition: Overview }) {
    const client = useQueryClient();
    const mutation = useMutation({
        mutationFn: (visibility: 'public' | 'private') => studioApi.setVisibility(edition.editionId, visibility),
        onSuccess: (updated) => {
            client.setQueryData(['studio-edition', edition.editionId], updated);
            void client.invalidateQueries({ queryKey: ['studio'] });
        },
    });
    const isPrivate = edition.visibility === 'private';
    return (
        <div style={{ marginTop: 20 }}>
            <h2 className={styles.sectionTitle} style={{ margin: '8px 0' }}>Видимість</h2>
            <div style={{ display: 'grid', gap: 8 }}>
                <Segmented
                    label="Видимість"
                    value={isPrivate ? 'private' : 'public'}
                    onChange={(val) => mutation.mutate(val as 'public' | 'private')}
                    options={[
                        { value: 'public', label: 'Публічна' },
                        { value: 'private', label: 'Приватна' },
                    ]}
                />
                <p className={styles.muted} style={{ marginTop: 0 }}>
                    {isPrivate
                        ? 'Приватна: бачать лише команда твору та запрошені читачі. Сторонні читачі та пошуковики її не бачать.'
                        : 'Публічна: відкрита всім читачам у каталозі й пошуку.'}
                </p>
                {mutation.isError && <Notice tone="error">{mutation.error.message}</Notice>}
            </div>
        </div>
    );
}

function InvitedReaders({ editionId }: { editionId: number }) {
    const client = useQueryClient();
    const [nick, setNick] = useState('');
    const readers = useQuery({
        queryKey: ['studio-readers', editionId],
        queryFn: () => studioApi.readers(editionId),
    });
    const add = useMutation({
        mutationFn: () => studioApi.addReader(editionId, nick.trim()),
        onSuccess: () => {
            setNick('');
            void client.invalidateQueries({ queryKey: ['studio-readers', editionId] });
        },
    });
    const remove = useMutation({
        mutationFn: (readerNick: string) => studioApi.removeReader(editionId, readerNick),
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: ['studio-readers', editionId] });
        },
    });

    return (
        <div style={{ marginTop: 20 }}>
            <h2 className={styles.sectionTitle} style={{ margin: '8px 0' }}>Запрошені читачі</h2>
            <p className={styles.muted} style={{ marginTop: 0 }}>
                Читають опубліковані глави приватної публікації. Робочі матеріали й канон їм недоступні.
            </p>
            <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', marginBottom: 12 }}>
                <div style={{ flex: 1 }}>
                    <TextInput
                        label="Нік читача"
                        value={nick}
                        onChange={setNick}
                        placeholder="reader_nick"
                    />
                </div>
                <Button onPress={() => add.mutate()} pending={add.isPending} isDisabled={!nick.trim()}>
                    Запросити
                </Button>
            </div>
            {(add.error ?? remove.error) && (
                <Notice tone="error">{(add.error ?? remove.error)!.message}</Notice>
            )}

            {readers.isLoading && <p className={styles.muted}>Завантажуємо читачів…</p>}
            {readers.data && readers.data.length === 0 && (
                <p className={styles.muted}>Запрошених читачів поки немає.</p>
            )}
            {readers.data && readers.data.length > 0 && (
                <div style={{ display: 'grid', gap: 8 }}>
                    {readers.data.map((r) => (
                        <div
                            key={r.nick}
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                padding: '8px 12px',
                                border: '1px solid var(--color-border)',
                                borderRadius: 'var(--radius-m)',
                            }}
                        >
                            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                <Avatar url={r.avatarUrl} nick={r.nick} size={32} />
                                <div>
                                    <strong>{r.nick}</strong>
                                    <div className={styles.muted} style={{ fontSize: '0.85em' }}>
                                        {relativeTime(new Date(r.grantedAt))}
                                    </div>
                                </div>
                            </div>
                            <Button
                                variant="secondary"
                                onPress={() => remove.mutate(r.nick)}
                                pending={remove.isPending && remove.variables === r.nick}
                            >
                                Відкликати
                            </Button>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
