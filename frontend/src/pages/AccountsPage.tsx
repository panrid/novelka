import { useState } from 'react';
import { useAuth, roleNames, type User, type Role } from '../auth/AuthContext';
import { useResource } from '../hooks/useResource';
import { mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';
import { ErrorState, Loading } from '../components/Status';
import { SelectField } from '../components/SelectField';
import { ListEmpty, ListFilter, ListPages, ListSearch, TableHeader, listParams, useListState, type PageData } from '../components/ListTools';

function AccountRow({ account, refresh }: { account: User; refresh: () => void }) {
    const { user } = useAuth();
    const [role, setRole] = useState<Role>(account.role);
    const action = useAction();
    const canEdit = account.id !== user?.id && account.role !== 'OWNER'
        && (user?.role === 'OWNER' || account.role !== 'ADMIN');
    return <tr><td>{account.username}</td><td>{roleNames[account.role]}</td><td>{canEdit && <div className="button-row">
        <SelectField hideLabel label={'Роль ' + account.username} value={role} onChange={value => setRole(value as Role)}
            options={[{ value: 'READER', label: 'Читач' }, { value: 'EDITOR', label: 'Редактор' }, ...(user?.role === 'OWNER' ? [{ value: 'ADMIN', label: 'Адміністратор' }] : [])]} />
        <button disabled={action.busy || role === account.role} onClick={() => { void action.run(async () => { await mutate('/accounts/' + account.id + '/role', { role }); refresh(); }); }}>Змінити роль</button>
    </div>}<ActionNotice {...action} /></td></tr>;
}
export function AccountsPage() {
    const list = useListState('', 'created', ['role']);
    const resource = useResource<PageData<User>>('/accounts?' + listParams(list.state), true);
    const data = resource.data;
    return <div className="page workspace"><p className="eyebrow">Команда Новелки</p><h1>Користувачі та ролі</h1>
        <p className="muted">Адміністратор призначає редакторів. Власник також призначає адміністраторів. Роль власника захищена від зміни через сайт.</p>
        <div className="list-toolbar"><ListSearch label="Знайти користувача" value={list.state.q} onChange={q => list.update({ q, page: 1 })} />
            <ListFilter label="Роль" value={list.state.filters.role} onChange={value => list.setFilter('role', value)} options={[
                { value: '', label: 'Усі ролі' }, ...Object.entries(roleNames).map(([value, label]) => ({ value, label }))]} />
            {list.state.filters.role && <button onClick={list.clearFilters}>Очистити фільтр</button>}</div>
        {resource.error ? <ErrorState message={resource.error} retry={resource.retry} /> : !data ? <Loading /> : <>
            {resource.loading && <p role="status">Оновлюємо список…</p>}
            {data.items.length ? <div className="table-scroll"><table><thead><tr>
                <TableHeader label="Логін" help="Ім’я облікового запису." sortKey="username" state={list.state} onSort={list.setSort} />
                <TableHeader label="Поточна роль" help="Права користувача на сайті." sortKey="role" state={list.state} onSort={list.setSort} />
                <TableHeader label="Керування" help="Зміна ролі відповідно до ваших прав." />
            </tr></thead><tbody>{data.items.map(account => <AccountRow key={account.id + account.role} account={account} refresh={resource.retry} />)}</tbody></table></div>
                : <ListEmpty filtered={!!(list.state.q || list.state.filters.role)} noun="Користувачів" />}
            <ListPages data={data} onPage={list.setPage} /></>}
    </div>;
}
