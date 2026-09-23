import { useEffect, useState } from 'react';
import { useResource } from '../hooks/useResource';
import { useAction } from '../hooks/useAction';
import { mutate } from '../api/client';
import { ActionNotice } from '../components/ActionNotice';
import { ErrorState, Loading } from '../components/Status';
import { OpenRouterBalance } from '../components/OpenRouterBalance';
import { HelpField } from '../components/HelpField';
import { HelpTip } from '../components/HelpTip';

export interface Settings {
    revision: number; registrationOpen: boolean; segmentChars: number; targetUsdPer5000: number; maxBudgetUsd: number;
    stages: { stage: string; model: string; inputUsdM: number; outputUsdM: number }[]; adminSelfApproval: boolean;
}
export function SettingsPage() {
    const resource = useResource<Settings>('/settings');
    const [settings, setSettings] = useState<Settings>();
    const action = useAction();
    useEffect(() => { if (resource.data) setSettings(resource.data); }, [resource.data]);
    if (resource.error) return <ErrorState message={resource.error} retry={resource.retry} />;
    if (!settings) return <Loading />;
    return <div className="page workspace"><p className="eyebrow">Лише для власника</p><h1>Налаштування сайту й ШІ</h1>
        <p className="muted">Нові завдання отримують знімок цих налаштувань. Ключ OpenRouter задається на сервері.</p>
        <OpenRouterBalance />
        <form className="stack-form" onSubmit={event => { event.preventDefault(); void action.run(async () => { const saved = await mutate<Settings>('/settings', settings); setSettings(saved); }); }}>
            <label className="check-label"><input type="checkbox" checked={settings.registrationOpen} onChange={event => setSettings({ ...settings, registrationOpen: event.target.checked })} />Дозволити реєстрацію</label>
            <span className="check-with-help"><label className="check-label"><input type="checkbox" checked={settings.adminSelfApproval} onChange={event => setSettings({ ...settings, adminSelfApproval: event.target.checked })} />Адміністратор може погоджувати власні правки</label>
                <HelpTip label="Власні правки адміністратора">Стосується лише адміністраторів і власника. Редактор, як і раніше, не погоджує власну правку: її перевіряє інший редактор. Вимкнення діє одразу, зокрема для правок, що вже очікують.</HelpTip></span>
            <div className="form-grid">
                <HelpField label="Максимальний бюджет одного запуску, $" help="Верхня межа, яку можна вказати для одного завдання в майстерні. Не обмежує вже запущені завдання.">{id => <input id={id} type="number" min=".01" max="1000" step=".01" required value={settings.maxBudgetUsd} onChange={event => setSettings({ ...settings, maxBudgetUsd: Number(event.target.value) })} />}</HelpField>
                <HelpField label="Цільова ціна на 5000 токенів, $" help="Орієнтир вартості перекладу 5000 токенів оригіналу. Використовується для оцінки витрат у звітах; фактичний ліміт задає бюджет запуску.">{id => <input id={id} type="number" min="0" step=".01" required value={settings.targetUsdPer5000} onChange={event => setSettings({ ...settings, targetUsdPer5000: Number(event.target.value) })} />}</HelpField>
                <HelpField label="Символів у сегменті" help="Довга глава ділиться між абзацами на сегменти приблизно такого розміру. Більший сегмент дає моделі більше контексту, але дорожчий повтор після збою.">{id => <input id={id} type="number" min="500" max="20000" required value={settings.segmentChars} onChange={event => setSettings({ ...settings, segmentChars: Number(event.target.value) })} />}</HelpField>
            </div>
            {settings.stages.map((stage, index) => <fieldset key={stage.stage}><legend>{({ analyze: 'Аналіз', translate: 'Переклад', proofread: 'Вичитка' })[stage.stage]}</legend><div className="form-grid">
                <label>Модель<input required value={stage.model} onChange={event => setSettings({ ...settings, stages: settings.stages.map((item, i) => i === index ? { ...item, model: event.target.value } : item) })} /></label>
                <label>Вхідні токени, $ / млн<input type="number" required min="0" step=".001" value={stage.inputUsdM} onChange={event => setSettings({ ...settings, stages: settings.stages.map((item, i) => i === index ? { ...item, inputUsdM: Number(event.target.value) } : item) })} /></label>
                <label>Вихідні токени, $ / млн<input type="number" required min="0" step=".001" value={stage.outputUsdM} onChange={event => setSettings({ ...settings, stages: settings.stages.map((item, i) => i === index ? { ...item, outputUsdM: Number(event.target.value) } : item) })} /></label>
            </div></fieldset>)}
            <button className="button" disabled={action.busy}>Зберегти налаштування</button><ActionNotice {...action} />
        </form>
        <a className="text-link" href="#/audit">Журнал дій →</a>
    </div>;
}
