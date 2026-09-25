import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { autotranslateApi, dollars, money, type Settings, type Stage } from '../../studio/autotranslate';
import { meApi } from '../../auth/api';
import { useSetMe } from '../../auth/me';
import { illustrationApi, type IllustrationSettings } from '../../studio/illustrations';
import { ModelPicker } from '../../studio/ModelPicker';
import { shahApi } from '../../ledger/api';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { Segmented } from '../../ui/Segmented';
import { TextInput } from '../../ui/TextInput';
import { Toggle } from '../../ui/Toggle';
import { chaptersWord } from '../../reading/api';
import styles from '../studio/studio.module.css';

const PERIODS = [
    { value: '7', label: '7 днів' },
    { value: '30', label: '30 днів' },
    { value: '365', label: 'рік' },
] as const;

/**
 * At launch only the site owner has шаги (рішення 23): the balance is what is left at
 * OpenRouter, the report shows what chapters really cost, and the models live here.
 */
export function WalletPage() {
    const [days, setDays] = useState<'7' | '30' | '365'>('30');
    const wallet = useQuery({ queryKey: ['wallet', days], queryFn: () => autotranslateApi.wallet(Number(days)) });
    if (wallet.isError) return <Notice tone="error">{wallet.error.message}</Notice>;
    if (!wallet.data) return <p className={styles.muted} style={{ paddingTop: 24 }}>Завантажуємо…</p>;
    const data = wallet.data;
    const show = data.showShah;

    return (
        <section className={styles.page}>
            <h1 className={styles.title}>Шаги</h1>
            {data.balance
                ? <p style={{ fontSize: 28, margin: '8px 0' }}><b>{money(data.balance.shah, data.balance.usd, show)}</b></p>
                : <Notice tone="error">{data.configured
                    ? 'OpenRouter не відповів про залишок. Перевірте ключ керування.'
                    : 'Ключ OpenRouter не налаштовано на сервері.'}</Notice>}
            <p className={styles.muted}>
                Залишок на OpenRouter. Один шаг — до 10 000 знаків оригіналу моделями сайту, зараз {dollars(data.usdPerShah, 3)}; з дорожчою моделлю глава займає більше шагів.

            </p>

            <ShowShah value={show} />

            <h2 className={styles.sectionTitle}>Собівартість</h2>
            <Segmented label="За період" value={days} options={PERIODS} onChange={setDays} />
            {data.report.length === 0 && <p className={styles.muted}>За цей час перекладених глав немає.</p>}
            {data.report.map((row) => (
                <div key={row.model} className={styles.entry}>
                    <b>{row.model}</b> <span className={styles.muted}>· {row.chapters} {chaptersWord(row.chapters)} · разом {dollars(row.total, 3)}</span>
                    <table className={styles.table}>
                        <thead><tr><th /><th>мін.</th><th>серед.</th><th>макс.</th></tr></thead>
                        <tbody>
                            <tr><td>Глава</td><td>{dollars(row.minChapter, 3)}</td><td>{dollars(row.avgChapter, 3)}</td><td>{dollars(row.maxChapter, 3)}</td></tr>
                            <tr><td>10 000 знаків</td><td>{dollars(row.minPerShah, 3)}</td><td>{dollars(row.avgPerShah, 3)}</td><td>{dollars(row.maxPerShah, 3)}</td></tr>
                        </tbody>
                    </table>
                    {show && (
                        <p className={styles.muted}>
                            За нинішньою ціною шагу середня глава коштує {(row.avgChapter / data.usdPerShah).toFixed(1).replace('.', ',')} шага.
                        </p>
                    )}
                </div>
            ))}

            <h2 className={styles.sectionTitle}>Моделі й ціни</h2>
            {/* Initialised once: the saved values come back equal, and «Збережено» stays visible. */}
            <SettingsForm settings={data.settings} />

            <Illustrations days={Number(days)} />
            <PeoplePrice />
        </section>
    );
}

function SettingsForm({ settings }: { settings: Settings }) {
    const client = useQueryClient();
    const [draft, setDraft] = useState(settings);
    const save = useMutation({
        mutationFn: () => autotranslateApi.saveSettings(draft),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['wallet'] }),
    });
    const stage = (key: 'analyze' | 'translate' | 'proofread') => (patch: Partial<Stage>) =>
        setDraft((current) => ({ ...current, [key]: { ...current[key], ...patch } }));
    return (
        <form className={styles.form} onSubmit={(event) => { event.preventDefault(); save.mutate(); }}>
            <StageFields title="Аналіз і словник" kind="analyze" stage={draft.analyze} onChange={stage('analyze')} />
            <StageFields title="Переклад" kind="translate" stage={draft.translate} onChange={stage('translate')} />
            <Toggle label="Вичитка другою моделлю" isSelected={draft.proofread.enabled} onChange={(enabled) => stage('proofread')({ enabled })} />
            {draft.proofread.enabled && <StageFields title="Вичитка" kind="proofread" stage={draft.proofread} onChange={stage('proofread')} />}
            <div className={styles.numbers}>
                <NumberField label="Частина глави, знаків" value={draft.segmentChars} onChange={(segmentChars) => setDraft({ ...draft, segmentChars })} />
                <NumberField label="Собівартість шагу, $" value={draft.microUsdPerShah / 1_000_000} step
                    onChange={(usd) => setDraft({ ...draft, microUsdPerShah: Math.round(usd * 1_000_000) })} />
            </div>
            <NumberField label="Зупинити, якщо витрати більші за кошторис у … раз" value={draft.capFactor} step
                onChange={(capFactor) => setDraft({ ...draft, capFactor })} />
            <p className={styles.muted}>Нові значення діють для перекладів, запущених після збереження.</p>
            {save.isError && <Notice tone="error">{save.error.message}</Notice>}
            {save.isSuccess && <Notice tone="success">Збережено.</Notice>}
            <Button type="submit" pending={save.isPending} pendingLabel="Зберігаємо…">Зберегти</Button>
        </form>
    );
}

function StageFields({ title, kind, stage, onChange }: {
    title: string; kind: 'analyze' | 'translate' | 'proofread'; stage: Stage; onChange: (patch: Partial<Stage>) => void;
}) {
    return (
        <fieldset className={styles.entry} style={{ border: 0, padding: 0, margin: 0 }}>
            <legend className={styles.label}>{title}</legend>
            <ModelPicker label="Модель" stage={kind} value={stage.model}
                onChange={(model, choice) => onChange({ model, inputPerMillion: choice.inputPerMillion, outputPerMillion: choice.outputPerMillion })} />
            {/* Keyed by the model: choosing one from the list fills in its prices. */}
            <div className={styles.numbers} key={stage.model}>
                <NumberField label="Вхід, $ за 1 млн токенів" value={stage.inputPerMillion} step onChange={(inputPerMillion) => onChange({ inputPerMillion })} />
                <NumberField label="Вихід, $ за 1 млн токенів" value={stage.outputPerMillion} step onChange={(outputPerMillion) => onChange({ outputPerMillion })} />
            </div>
        </fieldset>
    );
}

/** Text while typing, a number once it parses; a comma works as the decimal point. */
function NumberField({ label, value, onChange, step = false }: { label: string; value: number; onChange: (value: number) => void; step?: boolean }) {
    const [text, setText] = useState(String(value).replace('.', ','));
    return (
        <TextInput label={label} value={text} inputMode={step ? 'decimal' : 'numeric'}
            onChange={(next) => {
                setText(next);
                const parsed = Number(next.replace(',', '.'));
                if (next.trim() && Number.isFinite(parsed)) onChange(parsed);
            }} />
    );
}

/** Шаги for other people (рішення 29): the owner grants them in «Користувачі», runs cost this much a шаг. */
function PeoplePrice() {
    const client = useQueryClient();
    const price = useQuery({ queryKey: ['shah-price'], queryFn: shahApi.price });
    const [usd, setUsd] = useState<number | null>(null);
    const save = useMutation({
        mutationFn: () => shahApi.savePrice(usd ?? price.data!.usdPerShah),
        onSuccess: () => void client.invalidateQueries({ queryKey: ['shah-price'] }),
    });
    if (!price.data) return null;
    return (
        <>
            <h2 className={styles.sectionTitle}>Шаги для інших</h2>
            <p className={styles.muted}>
                Шаги нараховуєте в <Link to="/admin/users">Адмініструванні → Користувачі</Link>. Люди витрачають їх на автопереклад
                і ілюстрації за моделями сайту: списуються фактичні витрати, округлені вгору до цілого шагу.
            </p>
            <form className={styles.form} onSubmit={(event) => { event.preventDefault(); save.mutate(); }}>
                <NumberField label="Скільки витрат на моделі покриває 1 шаг, $" value={price.data.usdPerShah} step onChange={setUsd} />
                {save.isError && <Notice tone="error">{save.error.message}</Notice>}
                {save.isSuccess && <Notice tone="success">Збережено.</Notice>}
                <Button type="submit" pending={save.isPending} pendingLabel="Зберігаємо…">Зберегти</Button>
            </form>
        </>
    );
}

/** Pictures drawn in the period, and the model, price and style of the next ones. */
function Illustrations({ days }: { days: number }) {
    const overview = useQuery({ queryKey: ['illustration-settings', days], queryFn: () => illustrationApi.settings(days) });
    if (!overview.data) return null;
    const { spent, settings } = overview.data;
    return (
        <>
            <h2 className={styles.sectionTitle}>Ілюстрації</h2>
            <p className={styles.muted}>
                {spent.pictures > 0
                    ? `Намальовано ${spent.pictures}, разом ${dollars(spent.usd, 3)}, в середньому ${dollars(spent.average, 3)} за картинку з описом.`
                    : 'За цей час нічого не малювали.'}
            </p>
            <IllustrationForm settings={settings} />
        </>
    );
}

function IllustrationForm({ settings }: { settings: IllustrationSettings }) {
    const client = useQueryClient();
    const [draft, setDraft] = useState(settings);
    const save = useMutation({
        mutationFn: () => illustrationApi.saveSettings(draft),
        onSuccess: () => {
            void client.invalidateQueries({ queryKey: ['illustration-settings'] });
            void client.invalidateQueries({ queryKey: ['illustration-price'] });
        },
    });
    return (
        <form className={styles.form} onSubmit={(event) => { event.preventDefault(); save.mutate(); }}>
            <ModelPicker label="Модель, що малює" output="image" value={draft.model}
                onChange={(model, choice) => setDraft({ ...draft, model, microUsdPerImage: Math.max(1_000, Math.round(choice.chapterUsd * 1_000_000)) })}
                hint="Лише моделі, що малюють. Ціна картинки підставиться сама, її можна поправити." />
            <NumberField key={draft.model} label="Ціна картинки, $ (для кошторису)" value={draft.microUsdPerImage / 1_000_000} step
                onChange={(usd) => setDraft({ ...draft, microUsdPerImage: Math.round(usd * 1_000_000) })} />
            <TextInput label="Стиль (додається до кожного опису)" value={draft.style} multiline
                onChange={(style) => setDraft({ ...draft, style })} />
            <ModelPicker label="Модель, що складає опис" value={draft.promptModel}
                onChange={(promptModel, choice) => setDraft({
                    ...draft, promptModel, promptInputPerMillion: choice.inputPerMillion, promptOutputPerMillion: choice.outputPerMillion,
                })} />
            {save.isError && <Notice tone="error">{save.error.message}</Notice>}
            {save.isSuccess && <Notice tone="success">Збережено.</Notice>}
            <Button type="submit" pending={save.isPending} pendingLabel="Зберігаємо…">Зберегти</Button>
        </form>
    );
}

/** Шаги or dollars in every sum the owner sees (рішення 23): the switch lives where the sums are. */
function ShowShah({ value }: { value: boolean }) {
    const client = useQueryClient();
    const setMe = useSetMe();
    const save = useMutation({
        mutationFn: (showShah: boolean) => meApi.update({ showShah }),
        onSuccess: (me) => {
            setMe(me);
            void client.invalidateQueries({ queryKey: ['wallet'] });
            void client.invalidateQueries({ queryKey: ['autotranslate'] });
        },
    });
    return <Toggle label="Показувати суми в шагах (вимкнено — у доларах)" isSelected={value} onChange={(next) => save.mutate(next)} />;
}
