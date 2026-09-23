import { useEffect, useState, type ReactNode } from 'react';
import { useResource } from '../hooks/useResource';
import { useAction } from '../hooks/useAction';
import { mutate } from '../api/client';
import { ActionNotice } from '../components/ActionNotice';
import { ErrorState, Loading } from '../components/Status';
import { OpenRouterBalance } from '../components/OpenRouterBalance';
import { HelpField } from '../components/HelpField';
import { HelpTip } from '../components/HelpTip';
import { ThemePicker } from '../theme/ThemePicker';
import { permits, useAuth, type Role } from '../auth/AuthContext';
import { UsersSettings } from './UsersSettings';
import { ModelPicker, modelSummary, type ModelCatalog } from '../components/ModelPicker';

export interface Settings {
    revision: number; registrationOpen: boolean; segmentChars: number; targetUsdPer5000: number; maxBudgetUsd: number;
    stages: { stage: string; model: string; inputUsdM: number; outputUsdM: number; catalogPricing: boolean }[]; adminSelfApproval: boolean;
}

/** Categories are listed only when they have real controls; new settings join an existing category first. */
export const settingsSections: readonly { id: string; label: string; minimum: Role }[] = [
    { id: 'general', label: 'Загальні', minimum: 'OWNER' },
    { id: 'ai', label: 'ШІ та моделі', minimum: 'OWNER' },
    { id: 'translation', label: 'Переклад', minimum: 'OWNER' },
    { id: 'users', label: 'Користувачі та ролі', minimum: 'ADMIN' },
    { id: 'editing', label: 'Редагування та погодження', minimum: 'OWNER' },
    { id: 'appearance', label: 'Вигляд', minimum: 'ADMIN' },
];
type SectionId = 'general' | 'ai' | 'translation' | 'editing';
const stageNames: Record<string, string> = { analyze: 'Аналіз', translate: 'Переклад', proofread: 'Вичитка' };

export function SettingsPage({ search = '' }: { search?: string }) {
    const { user } = useAuth();
    const visible = settingsSections.filter(item => permits(user, item.minimum));
    const requested = new URLSearchParams(search).get('section');
    const current = visible.find(item => item.id === requested) ?? visible[0];
    const owner = permits(user, 'OWNER');
    return <div className="page workspace settings-page"><p className="eyebrow">{owner ? 'Сайт і команда' : 'Адміністрування'}</p><h1>Налаштування</h1>
        <div className="settings-layout">
            <nav className="settings-nav" aria-label="Розділи налаштувань">{visible.map(item =>
                <a key={item.id} href={'#/settings?section=' + item.id} aria-current={item.id === current.id ? 'page' : undefined}>{item.label}</a>)}
                {owner && <a href="#/audit">Журнал дій</a>}
            </nav>
            <section className="settings-content" aria-labelledby="settings-section-title">
                <h2 id="settings-section-title">{current.label}</h2>
                {current.id === 'users' ? <UsersSettings /> : current.id === 'appearance' ? <Appearance /> : <SiteSettingsForm section={current.id as SectionId} />}
            </section>
        </div>
    </div>;
}

/** Owner-only site settings; stays mounted across its categories so unsaved edits survive switching. */
function SiteSettingsForm({ section }: { section: SectionId }) {
    const resource = useResource<Settings>('/settings');
    const [settings, setSettings] = useState<Settings>();
    const action = useAction();
    useEffect(() => { if (resource.data) setSettings(resource.data); }, [resource.data]);
    if (resource.error) return <ErrorState message={resource.error} retry={resource.retry} />;
    if (!settings) return <Loading />;
    return <form className="stack-form settings-form" onSubmit={event => {
        event.preventDefault();
        void action.run(async () => { setSettings(await mutate<Settings>('/settings', settings)); }, 'Налаштування збережено.');
    }}>
        <SectionFields section={section} settings={settings} update={setSettings} />
        <div className="settings-save"><button className="button" disabled={action.busy}>Зберегти налаштування</button>
            <span className="muted">Нові завдання отримують знімок цих налаштувань.</span></div>
        <ActionNotice {...action} />
    </form>;
}

function SectionFields({ section, settings, update }: { section: SectionId; settings: Settings; update: (settings: Settings) => void }): ReactNode {
    if (section === 'general') return <label className="check-label"><input type="checkbox" checked={settings.registrationOpen}
        onChange={event => update({ ...settings, registrationOpen: event.target.checked })} />Дозволити реєстрацію нових читачів</label>;
    if (section === 'editing') return <span className="check-with-help"><label className="check-label"><input type="checkbox" checked={settings.adminSelfApproval}
        onChange={event => update({ ...settings, adminSelfApproval: event.target.checked })} />Адміністратор може погоджувати власні правки</label>
        <HelpTip label="Власні правки адміністратора">Стосується лише адміністраторів і власника. Редактор, як і раніше, не погоджує власну правку: її перевіряє інший редактор. Вимкнення діє одразу, зокрема для правок, що вже очікують.</HelpTip></span>;
    if (section === 'translation') return <div className="form-grid">
        <HelpField label="Максимальний бюджет одного запуску, $" help="Верхня межа, яку можна вказати для одного завдання в майстерні. Не обмежує вже запущені завдання.">{id => <input id={id} type="number" min=".01" max="1000" step=".01" required value={settings.maxBudgetUsd} onChange={event => update({ ...settings, maxBudgetUsd: Number(event.target.value) })} />}</HelpField>
        <HelpField label="Цільова ціна на 5000 токенів, $" help="Орієнтир вартості перекладу 5000 токенів оригіналу. Використовується для оцінки витрат у звітах; фактичний ліміт задає бюджет запуску.">{id => <input id={id} type="number" min="0" step=".01" required value={settings.targetUsdPer5000} onChange={event => update({ ...settings, targetUsdPer5000: Number(event.target.value) })} />}</HelpField>
        <HelpField label="Символів у сегменті" help="Довга глава ділиться між абзацами на сегменти приблизно такого розміру. Більший сегмент дає моделі більше контексту, але дорожчий повтор після збою.">{id => <input id={id} type="number" min="500" max="20000" required value={settings.segmentChars} onChange={event => update({ ...settings, segmentChars: Number(event.target.value) })} />}</HelpField>
    </div>;
    return <AiSettings settings={settings} update={update} />;
}

function AiSettings({ settings, update }: { settings: Settings; update: (settings: Settings) => void }) {
    const catalog = useResource<ModelCatalog>('/models', true);
    const refresh = useAction();
    const [fresh, setFresh] = useState<ModelCatalog>();
    const models = fresh ?? catalog.data;
    const stage = (index: number, patch: Partial<Settings['stages'][number]>) =>
        update({ ...settings, stages: settings.stages.map((item, i) => i === index ? { ...item, ...patch } : item) });
    return <>
        <OpenRouterBalance />
        <div className="catalog-status" role="status">
            <span>{catalog.error ? 'Каталог моделей недоступний: ' + catalog.error
                : !models ? 'Отримуємо список моделей…'
                    : `Каталог OpenRouter: ${models.items.filter(item => item.suitable).length} придатних моделей` + (models.refreshedAt
                        ? `, оновлено ${new Date(models.refreshedAt).toLocaleString('uk-UA')}` : '') + (models.error ? `. Останнє оновлення не вдалося: ${models.error}` : '.')}</span>
            <button type="button" disabled={refresh.busy} onClick={() => { void refresh.run(async () => setFresh(await mutate<ModelCatalog>('/models/refresh'))); }}>Оновити список моделей</button>
        </div>
        <p className="muted">Ключ OpenRouter задається на сервері. Підказки показують лише моделі з JSON-відповідями, інструментами й фіксованою ціною.</p>
        {settings.stages.map((item, index) => {
            const known = models?.items.find(model => model.id === item.model);
            const catalogPrice = item.catalogPricing && known?.inputUsdM != null && known.outputUsdM != null;
            return <fieldset key={item.stage}><legend>{stageNames[item.stage]}</legend>
                <ModelPicker label="Модель" required value={item.model} catalog={models} onChange={model => stage(index, { model })} />
                <span className="check-with-help"><label className="check-label"><input type="checkbox" checked={item.catalogPricing}
                    onChange={event => stage(index, { catalogPricing: event.target.checked })} />Брати ціну з каталогу провайдера</label>
                    <HelpTip label="Ціна з каталогу">Нове завдання отримує актуальну ціну моделі з останнього списку OpenRouter. Якщо моделі немає в каталозі або ціна неповна, використовуються ручні ціни нижче. Каталог ніколи не перезаписує ручні ціни.</HelpTip></span>
                {catalogPrice && <p className="muted model-price">Діє ціна з каталогу: {modelSummary(known!)}.</p>}
                <div className="form-grid">
                    <label>{item.catalogPricing ? 'Резервна ціна входу' : 'Вхідні токени'}, $ / млн<input type="number" required min="0" step=".001" value={item.inputUsdM} onChange={event => stage(index, { inputUsdM: Number(event.target.value) })} /></label>
                    <label>{item.catalogPricing ? 'Резервна ціна виходу' : 'Вихідні токени'}, $ / млн<input type="number" required min="0" step=".001" value={item.outputUsdM} onChange={event => stage(index, { outputUsdM: Number(event.target.value) })} /></label>
                </div></fieldset>;
        })}
        <ActionNotice {...refresh} />
    </>;
}

function Appearance() {
    return <div className="stack-form">
        <p className="muted">Тема зберігається в цьому браузері й одразу діє на всьому сайті. «Системна» повторює тему операційної системи.</p>
        <ThemePicker />
    </div>;
}
