import { useState } from 'react';
import { Label, Radio, RadioGroup } from 'react-aria-components';
import { dollars, type JobKind, type Preset } from './autotranslate';
import styles from './presetPicker.module.css';

/** «★★★½☆» for 3.5: whole stars, a half, then empty ones up to five. */
export function stars(rating: number): string {
    const full = Math.floor(rating);
    const half = rating - full >= 0.5 ? 1 : 0;
    return '★'.repeat(full) + (half ? '½' : '') + '☆'.repeat(Math.max(0, 5 - full - half));
}

const short = (model: string) => model.slice(model.indexOf('/') + 1);

/** What a preset costs here: its analysis for an analysis run, the whole chapter for a translation. */
function price(preset: Preset, kind: JobKind): string {
    return kind === 'analyze' ? `≈ ${dollars(preset.analysisUsd, 3)} за аналіз глави` : `≈ ${dollars(preset.chapterUsd, 3)} за главу`;
}

function steps(preset: Preset, kind: JobKind): string {
    if (kind === 'analyze') return `Аналіз: ${short(preset.analyze)}`;
    return `Аналіз: ${short(preset.analyze)} · переклад: ${short(preset.translate)} · `
        + (preset.proofread ? `вичитка: ${short(preset.proofread)}` : 'без вичитки');
}

/**
 * A ready set of models instead of three pickers. The chosen one shows its name, judged result
 * and price; the list of all of them opens on demand, so a phone screen is not one long menu.
 */
export function PresetPicker({ presets, value, onChange, kind, siteModels }: {
    presets: Preset[]; value: number | null; onChange: (preset: number | null) => void; kind: JobKind; siteModels: string;
}) {
    const [open, setOpen] = useState(false);
    const chosen = presets.find((preset) => preset.id === value);
    return (
        <div className={styles.picker}>
            <div className={styles.current}>
                <div className={styles.grow}>
                    <div className={styles.label}>Набір моделей</div>
                    {chosen ? (
                        <div>
                            <b>{chosen.name}</b>{' '}
                            <span className={styles.stars} aria-label={`оцінка ${String(chosen.rating).replace('.', ',')} з 5`}>{stars(chosen.rating)}</span>
                            <span className={styles.muted}> · {price(chosen, kind)}</span>
                        </div>
                    ) : <div>Як у налаштуваннях <span className={styles.muted}>· {siteModels}</span></div>}
                </div>
                <button type="button" className={styles.change} aria-expanded={open} onClick={() => setOpen(!open)}>
                    {open ? 'Згорнути' : 'Змінити'}
                </button>
            </div>
            {open && (
                <RadioGroup className={styles.list} value={value === null ? 'site' : String(value)}
                    onChange={(next) => { onChange(next === 'site' ? null : Number(next)); setOpen(false); }}>
                    <Label className={styles.hidden}>Набір моделей</Label>
                    <Radio value="site" className={styles.option}>
                        <div className={styles.head}><b>Як у налаштуваннях</b></div>
                        <div className={styles.muted}>{siteModels}</div>
                    </Radio>
                    {presets.map((preset) => (
                        <Radio key={preset.id} value={String(preset.id)} className={styles.option}>
                            <div className={styles.head}>
                                <b>{preset.name}</b>
                                <span className={styles.stars} aria-label={`оцінка ${String(preset.rating).replace('.', ',')} з 5`}>{stars(preset.rating)}</span>
                                <span className={styles.price}>{price(preset, kind)}</span>
                            </div>
                            <div>{preset.summary}</div>
                            <div className={styles.muted}>{steps(preset, kind)}</div>
                        </Radio>
                    ))}
                </RadioGroup>
            )}
        </div>
    );
}
