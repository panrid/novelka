import { useResource } from '../hooks/useResource';

interface Credits { configured: boolean; totalCredits: number | null; totalUsage: number | null; remainingUsd: number | null }

export function OpenRouterBalance() {
    const { data, error, retry } = useResource<Credits>('/settings/openrouter-credits');
    const money = (value: number) => new Intl.NumberFormat('uk-UA', {
        style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 4,
    }).format(value);
    return <section className="balance-panel" aria-labelledby="openrouter-balance-title">
        <div className="balance-heading"><div><h2 id="openrouter-balance-title">Баланс OpenRouter</h2>
            <p className="muted">Дані акаунта OpenRouter, оновлюються вручну.</p></div>
            <button type="button" onClick={retry}>Оновити баланс</button></div>
        {error ? <p role="alert">{error}</p>
            : !data ? <p className="muted">Отримуємо баланс…</p>
                : !data.configured ? <p>Щоб бачити баланс тут, створіть <a href="https://openrouter.ai/settings/management-keys" target="_blank" rel="noreferrer">management key в OpenRouter</a>, додайте <code>OPENROUTER_MANAGEMENT_KEY</code> у налаштування сервера та перезапустіть сайт. Ключ перекладу не дає доступу до балансу всього акаунта.</p>
                    : <div className="balance-values"><div><span>Залишилось</span><strong>{money(data.remainingUsd!)}</strong></div>
                        <div><span>Поповнено</span><strong>{money(data.totalCredits!)}</strong></div>
                        <div><span>Витрачено</span><strong>{money(data.totalUsage!)}</strong></div></div>}
    </section>;
}
