import { useResource } from '../hooks/useResource';

export interface Balance { available: number; toppedUp: number; reserved: number; spent: number; unlimited: boolean }

export const usd = new Intl.NumberFormat('uk-UA', { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 4 });

/** The viewer's translation balance. Everyone except the site owner pays AI tasks from it; only the owner tops it up. */
export function BalanceSummary() {
    const { data, error } = useResource<Balance>('/balance');
    if (error) return <p className="muted" role="status">Баланс недоступний: {error}</p>;
    if (!data) return null;
    if (data.unlimited) return <p className="balance-summary muted">Ваші завдання оплачуються з бюджету сайту.</p>;
    return <p className="balance-summary" role="status">Ваш баланс: <strong>{usd.format(data.available)}</strong>
        {data.reserved > 0 && <span className="muted"> · зарезервовано для активних завдань {usd.format(data.reserved)}</span>}
        {Number(data.available) <= 0 && <span className="muted"> · баланс поповнює власник сайту</span>}</p>;
}
