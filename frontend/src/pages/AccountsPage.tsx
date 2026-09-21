import { useState } from 'react';
import { useAuth, roleNames, type User, type Role } from '../auth/AuthContext';
import { useResource } from '../hooks/useResource';
import { mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';
import { ErrorState, Loading } from '../components/Status';

function AccountRow({ account, refresh }: { account: User; refresh: () => void }) {
    const { user } = useAuth();
    const [role, setRole] = useState<Role>(account.role);
    const action = useAction();
    const canEdit = account.id !== user?.id && account.role !== 'OWNER'
        && (user?.role === 'OWNER' || account.role !== 'ADMIN');
    return <tr><td>{account.username}</td><td>{roleNames[account.role]}</td><td>{canEdit && <div className="button-row">
        <select aria-label={'Роль ' + account.username} value={role} onChange={event => setRole(event.target.value as Role)}>
            <option value="READER">Читач</option><option value="EDITOR">Редактор</option>{user?.role === 'OWNER' && <option value="ADMIN">Адміністратор</option>}
        </select><button disabled={action.busy || role === account.role} onClick={() => { void action.run(async () => { await mutate('/accounts/' + account.id + '/role', { role }); refresh(); }); }}>Змінити роль</button>
    </div>}<ActionNotice {...action} /></td></tr>;
}
export function AccountsPage() {
    const [offset, setOffset] = useState(0);
    const resource = useResource<User[]>('/accounts?offset=' + offset);
    return <div className="page workspace"><p className="eyebrow">Команда Новелки</p><h1>Користувачі та ролі</h1>
        <p className="muted">Адміністратор призначає редакторів. Власник також призначає адміністраторів. Роль власника захищена від зміни через сайт.</p>
        {resource.error ? <ErrorState message={resource.error} retry={resource.retry} /> : !resource.data ? <Loading /> : <div className="table-scroll"><table><thead><tr><th>Логін</th><th>Поточна роль</th><th>Керування</th></tr></thead><tbody>{resource.data.map(account => <AccountRow key={account.id + account.role} account={account} refresh={resource.retry} />)}</tbody></table></div>}
        <div className="button-row"><button disabled={!offset} onClick={() => setOffset(value => value - 50)}>Назад</button><button disabled={(resource.data?.length ?? 0) < 50} onClick={() => setOffset(value => value + 50)}>Далі</button></div>
    </div>;
}
