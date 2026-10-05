import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { ChevronUp } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useMe } from '../../auth/me';
import { plural } from '../../lib/plural';
import { proposalApi, type Proposal, type ProposalSort } from '../../proposals/api';
import { teamApi } from '../../studio/api';
import { askConfirm } from '../../ui/ask';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Pager } from '../../ui/Pager';
import { Segmented } from '../../ui/Segmented';
import { Sheet } from '../../ui/Sheet';
import { TextInput } from '../../ui/TextInput';
import styles from './proposals.module.css';

const PAGE = 30;

/**
 * «Що перекласти» (рішення 32): a novel proposed by its link, its page translated by the site,
 * votes, and «Беру перекладати» for whoever wants to translate it.
 */
export function ProposalsPage() {
    const me = useMe();
    const client = useQueryClient();
    const navigate = useNavigate();
    const [sort, setSort] = useState<ProposalSort>('votes');
    const [page, setPage] = useState(1);
    const [link, setLink] = useState('');
    const [notice, setNotice] = useState<string | null>(null);
    const list = useQuery({ queryKey: ['proposals', sort, page], queryFn: () => proposalApi.list(sort, page), placeholderData: (previous) => previous });
    const refresh = () => void client.invalidateQueries({ queryKey: ['proposals'] });
    const propose = useMutation({
        mutationFn: () => proposalApi.propose(link.trim()),
        onSuccess: ({ created }) => {
            setLink('');
            setNotice(created ? 'Новелу додано, ваш голос уже за неї.' : 'Цю новелу вже пропонували — ваш голос додано до неї.');
            setSort('new');
            setPage(1);
            refresh();
        },
    });
    const submit = (event: FormEvent) => {
        event.preventDefault();
        setNotice(null);
        if (link.trim()) propose.mutate();
    };
    const [taking, setTaking] = useState<Proposal | null>(null);
    const [editing, setEditing] = useState<Proposal | null>(null);

    return (
        <section className={styles.page}>
            <h1 className={styles.title}>Що перекласти</h1>
            <p className={styles.lead}>
                Запропонуйте новелу посиланням — сайт сам перекладе назву й опис. Голосуйте за ті, які хочете читати:
                перекладачі бачать, чого чекають найбільше.
            </p>

            {me ? (
                <form className={styles.propose} onSubmit={submit}>
                    <TextInput label="Посилання на новелу" value={link} onChange={setLink} placeholder="https://ncode.syosetu.com/…"
                        hint="Поки що з Syosetu." error={propose.isError ? propose.error.message : undefined} />
                    <Button type="submit" pending={propose.isPending} pendingLabel="Перекладаємо назву…" isDisabled={!link.trim()}>
                        Запропонувати
                    </Button>
                </form>
            ) : (
                <p className={styles.muted} style={{ marginBottom: 16 }}>
                    <Link to="/login">Увійдіть</Link>, щоб пропонувати новели й голосувати.
                </p>
            )}
            {notice && <div className={styles.notice}><Notice tone="success">{notice}</Notice></div>}

            <Segmented label="Порядок" value={sort} onChange={(next) => { setSort(next); setPage(1); }} options={[
                { value: 'votes', label: 'Найбажаніші' },
                { value: 'new', label: 'Нові' },
                { value: 'taken', label: 'Уже перекладають' },
            ]} />

            {list.isError && <Notice tone="error">{list.error.message}</Notice>}
            {list.data && list.data.items.length === 0 && (
                <p className={styles.muted} style={{ marginTop: 16 }}>
                    {sort === 'taken' ? 'Поки жодну пропозицію не взяли перекладати.' : 'Пропозицій ще немає. Будьте першими!'}
                </p>
            )}
            <div className={styles.list}>
                {list.data?.items.map((item) => (
                    <ProposalCard key={item.id} item={item} signedIn={Boolean(me)} staff={Boolean(me && me.role !== 'reader')}
                        onChanged={refresh} onTake={() => setTaking(item)} onEdit={() => setEditing(item)} />
                ))}
            </div>
            {list.data && <Pager page={page} total={list.data.total} size={PAGE} onPage={setPage} />}

            {taking && (
                <TakeSheet proposal={taking} onClose={() => setTaking(null)}
                    onTaken={(editionId) => {
                        refresh();
                        void navigate({ to: '/studio/$editionId/translate', params: { editionId: String(editionId) } });
                    }} />
            )}
            {editing && <EditSheet proposal={editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); refresh(); }} />}
        </section>
    );
}

function ProposalCard({ item, signedIn, staff, onChanged, onTake, onEdit }: {
    item: Proposal; signedIn: boolean; staff: boolean; onChanged: () => void; onTake: () => void; onEdit: () => void;
}) {
    const [expanded, setExpanded] = useState(false);
    const vote = useMutation({ meta: { errorToast: true },
        mutationFn: () => (item.voted ? proposalApi.unvote(item.id) : proposalApi.vote(item.id)),
        onSuccess: onChanged,
    });
    const remove = useMutation({ meta: { errorToast: true }, mutationFn: () => proposalApi.remove(item.id), onSuccess: onChanged });
    const open = item.state === 'open';
    const long = item.description.join(' ').length > 200 || item.description.length > 2;
    return (
        <article className={styles.item}>
            <button type="button" className={styles.vote} aria-pressed={item.voted} disabled={!signedIn || !open || vote.isPending}
                aria-label={item.voted ? `Забрати голос за «${item.title}»` : `Голосувати за «${item.title}»`}
                title={signedIn ? undefined : 'Увійдіть, щоб голосувати'} onClick={() => vote.mutate()}>
                <ChevronUp size={20} aria-hidden />
                <b>{item.votes}</b>
            </button>
            <div>
                <h2 className={styles.name}>{item.title}</h2>
                <div className={styles.muted}>
                    {[item.author, `${item.site} · ${plural(item.chapters, 'глава', 'глави', 'глав')}`, item.adult ? '18+' : null]
                        .filter(Boolean).join(' · ')}
                </div>
                {item.description.length > 0 && (
                    <div className={`${styles.description} ${expanded ? '' : styles.clamped}`}>
                        {item.description.map((paragraph, index) => <p key={index}>{paragraph}</p>)}
                    </div>
                )}
                <div className={styles.actions}>
                    {long && (
                        <button type="button" className={styles.link} onClick={() => setExpanded(!expanded)}>
                            {expanded ? 'згорнути' : 'читати повністю'}
                        </button>
                    )}
                    <span className={styles.muted}>пропонує <Link to="/u/$nick" params={{ nick: item.proposedBy }}>{item.proposedBy}</Link></span>
                    {item.taken && (
                        <Link to="/n/$slug" params={{ slug: item.taken.novelSlug }} search={{ t: item.taken.teamHandle }}>
                            Перекладає ${item.taken.teamHandle} ›
                        </Link>
                    )}
                    {open && (item.mine || staff) && <button type="button" className={styles.link} onClick={onEdit}>виправити</button>}
                    {open && (item.mine || staff) && (
                        <button type="button" className={styles.link} onClick={() => void askConfirm({
                            title: 'Прибрати пропозицію?', text: 'Голоси за неї теж зникнуть.', confirmLabel: 'Прибрати', danger: true,
                        }).then((yes) => { if (yes) remove.mutate(); })}>прибрати</button>
                    )}
                </div>
                {open && signedIn && (
                    <div style={{ marginTop: 12 }}>
                        <Button variant="secondary" onPress={onTake}>Беру перекладати</Button>
                    </div>
                )}
            </div>
        </article>
    );
}

/** The team that takes it; the personal one when the person has no other. */
function TakeSheet({ proposal, onClose, onTaken }: { proposal: Proposal; onClose: () => void; onTaken: (editionId: number) => void }) {
    const teams = useQuery({ queryKey: ['my-teams'], queryFn: teamApi.mine });
    const translating = (teams.data ?? []).filter((team) => team.role !== 'editor');
    const [team, setTeam] = useState('');
    const chosen = team || translating[0]?.handle || '';
    const take = useMutation({ mutationFn: () => proposalApi.take(proposal.id, chosen), onSuccess: ({ editionId }) => onTaken(editionId) });
    return (
        <Sheet open onClose={onClose} title={`Перекладати «${proposal.title}»`}>
            <div className={styles.sheetForm}>
                <p className={styles.muted}>
                    У Студії з'явиться переклад із назвою й описом, як тут. Глави можна перекладати вручну або автоперекладом.
                    Ті, хто голосував, отримають сповіщення.
                </p>
                {translating.length > 1 && (
                    <label className={styles.sheetForm} style={{ gap: 6 }}>
                        <span>Команда</span>
                        <select className={styles.select} value={chosen} onChange={(event) => setTeam(event.target.value)}>
                            {translating.map((option) => <option key={option.handle} value={option.handle}>{option.name}</option>)}
                        </select>
                    </label>
                )}
                {take.isError && <Notice tone="error">{take.error.message}</Notice>}
                <Button onPress={() => take.mutate()} pending={take.isPending} pendingLabel="Готуємо переклад…" isDisabled={teams.isPending}>
                    Беру перекладати
                </Button>
                <Button variant="secondary" onPress={onClose}>Скасувати</Button>
            </div>
        </Sheet>
    );
}

/** The proposer corrects what the model wrote. */
function EditSheet({ proposal, onClose, onSaved }: { proposal: Proposal; onClose: () => void; onSaved: () => void }) {
    const [title, setTitle] = useState(proposal.title);
    const [author, setAuthor] = useState(proposal.author);
    const [description, setDescription] = useState(proposal.description.join('\n\n'));
    const save = useMutation({ mutationFn: () => proposalApi.update(proposal.id, { title, author, description }), onSuccess: onSaved });
    return (
        <Sheet open onClose={onClose} title="Виправити пропозицію" tall>
            <form className={styles.sheetForm} onSubmit={(event) => { event.preventDefault(); save.mutate(); }}>
                <TextInput label="Назва" value={title} onChange={setTitle} />
                <TextInput label="Автор" value={author} onChange={setAuthor} />
                <TextInput label="Опис" value={description} onChange={setDescription} multiline hint="Абзаци — з нового рядка." />
                {save.isError && <Notice tone="error">{save.error.message}</Notice>}
                <Button type="submit" pending={save.isPending} pendingLabel="Зберігаємо…" isDisabled={!title.trim()}>Зберегти</Button>
                <Button variant="secondary" onPress={onClose}>Скасувати</Button>
            </form>
        </Sheet>
    );
}

