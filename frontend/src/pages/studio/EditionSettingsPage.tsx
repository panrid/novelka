import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { useState } from 'react';
import { changes, characters, paragraphs } from '../../lib/plural';
import { STATUS_LABELS, type Status } from '../../reading/api';
import { studioApi } from '../../studio/api';
import { Button } from '../../ui/Button';
import { Collapsible } from '../../ui/Collapsible';
import { Notice } from '../../ui/Notice';
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
