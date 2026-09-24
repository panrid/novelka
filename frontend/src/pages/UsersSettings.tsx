import { useEffect, useState } from 'react';
import { useAuth, roleNames, type Role, type User } from '../auth/AuthContext';
import { useResource } from '../hooks/useResource';
import { mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';
import { ErrorState, Loading } from '../components/Status';
import { usd, type Balance } from '../components/BalanceSummary';
import { ListEmpty, ListFilter, ListPages, ListSearch, TableHeader, listParams, useListState, type PageData } from '../components/ListTools';

interface AdminAccount { id: string; username: string; email: string | null; role: Role; created_at: string }

const roleOrder: Role[] = ['READER', 'MODERATOR', 'ADMIN', 'OWNER'];
const roleAbilities: Record<Role, string> = {
    READER: 'Читає, пропонує правки, коментує. Правки конкретної новели перевіряє, якщо її перекладач так вирішив.',
    MODERATOR: 'Усе, що користувач, а також модерує коментарі й чат.',
    ADMIN: 'Усе, що модератор, а також повний доступ до кожної новели: майстерня, словник, правки, витрати; призначає модераторів.',
    OWNER: 'Усе, що адміністратор, а також призначає адміністраторів, керує реєстрацією, налаштуваннями ШІ й журналом дій. Один на сайт.',
};

/** Mirrors the backend rule only to explain the UI; the server re-checks every change with the current role. */
function assignable(actor: User | null, target: AdminAccount): { roles: Role[]; reason?: string } {
    if (!actor) return { roles: [] };
    if (actor.id === target.id) return { roles: [], reason: 'Власну роль змінити не можна.' };
    if (target.role === 'OWNER') return { roles: [], reason: 'Роль власника захищена від змін через сайт.' };
    if (actor.role === 'OWNER') return { roles: ['READER', 'MODERATOR', 'ADMIN'] };
    if (actor.role === 'ADMIN' && target.role !== 'ADMIN') return { roles: ['READER', 'MODERATOR'] };
    return { roles: [], reason: 'Адміністратор не змінює роль іншого адміністратора.' };
}

export function UsersSettings() {
    const list = useListState('', 'created', ['role', 'user']);
    const selected = list.state.filters.user;
    const query = listParams({ ...list.state, filters: { role: list.state.filters.role } });
    const resource = useResource<PageData<AdminAccount>>('/accounts?' + query, true);
    const data = resource.data;
    const select = (user: string) => list.update({ filters: { ...list.state.filters, user } });
    return <div className={'users-layout' + (selected ? ' with-panel' : '')}>
        <div className="users-list">
            <div className="list-toolbar"><ListSearch label="Нік або email" value={list.state.q} onChange={q => list.update({ q, page: 1 })} />
                <ListFilter label="Роль" value={list.state.filters.role} onChange={value => list.setFilter('role', value)} options={[
                    { value: '', label: 'Усі ролі' }, ...roleOrder.map(value => ({ value, label: roleNames[value] }))]} />
                {list.state.filters.role && <button type="button" onClick={() => list.setFilter('role', '')}>Очистити фільтр</button>}</div>
            {resource.error ? <ErrorState message={resource.error} retry={resource.retry} /> : !data ? <Loading /> : <>
                {resource.loading && <p role="status">Оновлюємо список…</p>}
                {data.items.length ? <div className="table-scroll"><table className="users-table"><thead><tr>
                    <TableHeader label="Нік" help="Поточне публічне ім’я. Історію змін бачать лише адміністратори." sortKey="username" state={list.state} onSort={list.setSort} />
                    <TableHeader label="Email" help="Адреса для входу. Бачать лише адміністратори." sortKey="email" state={list.state} onSort={list.setSort} />
                    <TableHeader label="Роль" help="Що користувач може робити на сайті. Докладно — у панелі користувача." sortKey="role" state={list.state} onSort={list.setSort} />
                </tr></thead><tbody>{data.items.map(account => <tr key={account.id} aria-selected={account.id === selected}>
                    <td><button type="button" className="link-button" aria-pressed={account.id === selected}
                        onClick={() => select(account.id === selected ? '' : account.id)}>{account.username}</button></td>
                    <td className="users-email">{account.email ?? <span className="muted">—</span>}</td>
                    <td><span className={'badge role-badge ' + account.role.toLowerCase()}>{roleNames[account.role]}</span></td>
                </tr>)}</tbody></table></div>
                    : <ListEmpty filtered={!!(list.state.q || list.state.filters.role)} noun="Користувачів" />}
                <ListPages data={data} onPage={list.setPage} /></>}
        </div>
        {selected && <UserPanel key={selected} id={selected} close={() => select('')} refreshList={resource.retry} />}
    </div>;
}

function UserPanel({ id, close, refreshList }: { id: string; close: () => void; refreshList: () => void }) {
    const { user } = useAuth();
    const resource = useResource<AdminAccount>('/accounts/' + id);
    const [role, setRole] = useState<Role>();
    const action = useAction();
    useEffect(() => { if (resource.data) setRole(resource.data.role); }, [resource.data]);
    if (resource.error) return <section className="user-panel"><ErrorState message={resource.error} retry={resource.retry} /></section>;
    const account = resource.data;
    if (!account || !role) return <section className="user-panel"><Loading /></section>;
    const allowed = assignable(user, account);
    return <section className="user-panel" aria-labelledby="user-panel-title">
        <div className="user-panel-heading"><div><h3 id="user-panel-title">{account.username}</h3>
            <p className="muted">{account.email ?? 'Email не вказано'} · з {new Date(account.created_at).toLocaleDateString('uk-UA')}</p></div>
            <button type="button" onClick={close}>Закрити</button></div>
        <form onSubmit={event => {
            event.preventDefault();
            void action.run(async () => { await mutate('/accounts/' + account.id + '/role', { role }); resource.retry(); refreshList(); }, 'Роль змінено.');
        }}>
            <fieldset className="role-choices" disabled={!allowed.roles.length}><legend>Роль</legend>
                {roleOrder.map(value => <label key={value} className={'role-choice' + (value === account.role ? ' current' : '')}>
                    <input type="radio" name="role" value={value} checked={role === value}
                        disabled={value !== account.role && !allowed.roles.includes(value)} onChange={() => setRole(value)} />
                    <span><strong>{roleNames[value]}{value === account.role && <small> · поточна</small>}</strong><span>{roleAbilities[value]}</span></span>
                </label>)}
            </fieldset>
            {allowed.reason && <p className="muted">{allowed.reason}</p>}
            {!!allowed.roles.length && <button className="button" disabled={action.busy || role === account.role}>Зберегти роль</button>}
            <ActionNotice {...action} />
        </form>
        {user?.role === 'OWNER' && <BalancePanel id={account.id} />}
        <h4>Історія ніків</h4>
        <NicknameHistory id={account.id} />
    </section>;
}

/** Owner-only: the user's translation balance, top-ups and corrections of a mistaken top-up. */
function BalancePanel({ id }: { id: string }) {
    const resource = useResource<{ balance: Balance; topups: { id: number; amount_usd: number; note: string; created_at: string; actor: string }[] }>('/accounts/' + id + '/balance');
    const [amount, setAmount] = useState('');
    const [note, setNote] = useState('');
    const action = useAction();
    if (resource.error) return <ErrorState message={resource.error} retry={resource.retry} />;
    if (!resource.data) return <Loading />;
    const { balance, topups } = resource.data;
    return <section className="balance-panel" aria-labelledby={'balance-' + id}>
        <h4 id={'balance-' + id}>Баланс перекладу</h4>
        {balance.unlimited ? <p className="muted">Власник сайту використовує бюджет сайту.</p> : <>
            <p>Доступно <strong>{usd.format(balance.available)}</strong> · зарезервовано {usd.format(balance.reserved)} · витрачено {usd.format(balance.spent)}</p>
            <form className="inline-form" onSubmit={event => {
                event.preventDefault();
                void action.run(async () => {
                    await mutate('/accounts/' + id + '/balance', { amountUsd: Number(amount), note });
                    setAmount(''); setNote(''); resource.retry();
                }, Number(amount) > 0 ? 'Баланс поповнено.' : 'Суму списано.');
            }}>
                <label>Сума, $<input type="number" required step="0.01" min="-1000" max="1000" value={amount} onChange={event => setAmount(event.target.value)} /></label>
                <label>Коментар<input maxLength={500} value={note} onChange={event => setNote(event.target.value)} /></label>
                <button className="button" disabled={action.busy || !Number(amount)}>{Number(amount) < 0 ? 'Списати' : 'Поповнити'}</button>
            </form>
            <ActionNotice {...action} />
        </>}
        {topups.length > 0 && <ul className="nickname-history">{topups.map(topup => <li key={topup.id}>
            {Number(topup.amount_usd) > 0 ? '+' : ''}{usd.format(topup.amount_usd)}{topup.note && ' · ' + topup.note}
            <span className="muted"> {topup.actor}, {new Date(topup.created_at).toLocaleString('uk-UA')}</span></li>)}</ul>}
    </section>;
}

function NicknameHistory({ id }: { id: string }) {
    const resource = useResource<{ items: { previous_nickname: string; new_nickname: string; changed_at: string }[] }>('/accounts/' + id + '/nicknames');
    if (resource.error) return <ErrorState message={resource.error} retry={resource.retry} />;
    if (!resource.data) return <Loading />;
    if (!resource.data.items.length) return <p className="muted nickname-history">Нік не змінювався.</p>;
    return <ul className="nickname-history">{resource.data.items.map(item => <li key={item.changed_at + item.new_nickname}>
        {item.previous_nickname} → {item.new_nickname} <span className="muted">{new Date(item.changed_at).toLocaleString('uk-UA')}</span></li>)}</ul>;
}
