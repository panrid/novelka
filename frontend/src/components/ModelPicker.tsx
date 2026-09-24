import { useId } from 'react';
import { Autocomplete } from './Autocomplete';

export interface ModelInfo {
    id: string; name: string; contextLength: number | null; inputUsdM: number | null; outputUsdM: number | null;
    suitable: boolean; limitation: string | null;
}
export interface ModelCatalog { provider: string; refreshedAt: string | null; stale: boolean; error: string | null; items: ModelInfo[] }

const number = new Intl.NumberFormat('uk-UA', { maximumFractionDigits: 4 });

export function modelSummary(model: ModelInfo) {
    return [model.name, model.contextLength && `контекст ${number.format(model.contextLength)}`,
        model.inputUsdM != null && model.outputUsdM != null && `$${number.format(model.inputUsdM)} / $${number.format(model.outputUsdM)} за млн токенів`]
        .filter(Boolean).join(' · ');
}

/**
 * Free-text model id with suggestions from the provider catalog. Typing an id that is missing from the catalog still works,
 * so a provider outage never blocks settings. An empty value means "use the default model" when {@code defaultModel} is set.
 */
export function ModelPicker({ label, value, onChange, catalog, defaultModel, required = false }: {
    label: string; value: string; onChange: (value: string) => void; catalog?: ModelCatalog; defaultModel?: string; required?: boolean;
}) {
    const id = useId();
    const model = catalog?.items.find(item => item.id === value);
    const info = !value && defaultModel ? { text: `Буде використано модель за замовчуванням: ${defaultModel}.`, warning: false }
        : model ? model.suitable ? { text: modelSummary(model), warning: false }
            : { text: `Не підходить для перекладу: ${model.limitation}`, warning: true }
            : value && catalog?.items.length ? { text: 'Моделі немає в каталозі провайдера — перевірте назву.', warning: true } : null;
    return <div className="model-picker">
        <label htmlFor={id}>{label}</label>
        <Autocomplete id={id} value={value} required={required} placeholder={defaultModel ? 'За замовчуванням: ' + defaultModel : 'provider/model'}
            describedBy={info ? id + '-info' : undefined} onChange={next => onChange(next.trim())} onPick={suggestion => onChange(suggestion.value)}
            suggestions={(catalog?.items ?? []).filter(item => item.suitable && (item.id + ' ' + item.name).toLowerCase().includes(value.toLowerCase()) && item.id !== value)
                .slice(0, 30).map(item => ({ value: item.id, label: item.id, hint: modelSummary(item) }))} />
        {info && <small id={id + '-info'} className={info.warning ? 'model-info warning' : 'model-info'}>{info.text}</small>}
    </div>;
}
