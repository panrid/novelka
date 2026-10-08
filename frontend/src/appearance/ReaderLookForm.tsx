import { FONT_STACKS, loadFont } from './fonts';
import {
    READER_ACCENTS, READER_BACKGROUNDS, READER_COLORS, READER_INKS, READER_FONTS, READER_LINE_HEIGHT, READER_MARGIN, READER_PRESETS, READER_SIZE,
    contrast, readerLook, tapZones, type ReaderLook,
} from './model';
import { setReader, setReaderPreset, useAppearance } from './store';
import { Toggle } from '../ui/Toggle';
import styles from './ReaderLookForm.module.css';

/**
 * The reader's look (етап 18): a ready style, or one's own from the site's choices. The same
 * form sits in «Налаштування» and behind «Аа» in the reader; every change shows at once.
 */
export function ReaderLookForm() {
    const look = readerLook(useAppearance());
    return (
        <div className={styles.form}>
            <Choice label="Готовий стиль" value={look.custom ? null : look.preset} onPick={setReaderPreset}
                options={READER_PRESETS.map(({ value, label }) => ({ value, label }))} />
            {look.custom && <p className={styles.note}>Свій стиль на основі «{READER_PRESETS.find((p) => p.value === look.preset)?.label}». Готовий стиль вище поверне все як було.</p>}

            <Choice label="Гортання" value={look.mode} onPick={(mode) => setReader({ mode })}
                options={[{ value: 'scroll', label: 'Прокрутка вниз' }, { value: 'pages', label: 'Сторінки' }]} />
            {look.mode === 'pages' && (
                <>
                    <Choice label="Анімація сторінок" value={look.pageAnim} onPick={(pageAnim) => setReader({ pageAnim })}
                        options={[{ value: 'none', label: 'Без анімації' }, { value: 'slide', label: 'Зсув' }, { value: 'fade', label: 'Згасання' }, { value: 'curl', label: 'Аркуш' }]} />
                    <Choice label="Куди торкатися" value={look.taps} onPick={(taps) => setReader({ taps })}
                        options={TAPS.map(({ value, label }) => ({ value, label }))} />
                    <TapMap taps={look.taps} />
                    <p className={styles.note}>Гортати можна й свайпом або стрілками. Якщо телефон не встигає — «Без анімації».</p>
                </>
            )}

            <label className={styles.row}>
                <span className={styles.label}>Шрифт</span>
                <select className={styles.select} value={look.font} style={{ fontFamily: FONT_STACKS[look.font] }}
                    onChange={(event) => setReader({ font: event.target.value as ReaderLook['font'] })}
                    onFocus={() => READER_FONTS.forEach(({ value }) => void loadFont(value))}>
                    {READER_FONTS.map(({ value, label }) => <option key={value} value={value} style={{ fontFamily: FONT_STACKS[value] }}>{label}</option>)}
                </select>
            </label>

            <div className={styles.row}>
                <span className={styles.label}>Розмір</span>
                <div className={styles.stepper}>
                    <button type="button" onClick={() => setReader({ size: look.size - 1 })} disabled={look.size <= READER_SIZE.min} aria-label="Менший текст">А−</button>
                    <output aria-live="polite">{look.size}</output>
                    <button type="button" onClick={() => setReader({ size: look.size + 1 })} disabled={look.size >= READER_SIZE.max} aria-label="Більший текст">А+</button>
                </div>
            </div>

            <Range label="Міжрядковий інтервал" value={look.lineHeight} min={READER_LINE_HEIGHT.min} max={READER_LINE_HEIGHT.max} step={0.05}
                show={(v) => v.toFixed(2).replace('.', ',')} onChange={(lineHeight) => setReader({ lineHeight })} />

            <Choice label="Вирівнювання" value={look.align} onPick={(align) => setReader({ align })}
                options={[{ value: 'left', label: 'Ліворуч' }, { value: 'justify', label: 'По ширині, з переносами' }]} />
            <Choice label="Абзаци" value={look.paragraphs} onPick={(paragraphs) => setReader({ paragraphs })}
                options={[{ value: 'gap', label: 'Пропуск між абзацами' }, { value: 'indent', label: 'Відступ першого рядка' }]} />
            <Choice label="Ширина тексту" value={look.width} onPick={(width) => setReader({ width })}
                options={[{ value: 'narrow', label: 'Вузька' }, { value: 'medium', label: 'Середня' }, { value: 'wide', label: 'Широка' }, { value: 'full', label: 'На весь екран' }]} />
            <Range label="Поля" value={look.margin} min={READER_MARGIN.min} max={READER_MARGIN.max} step={4}
                show={(v) => `${v} px`} onChange={(margin) => setReader({ margin })} />

            <div className={styles.row}>
                <span className={styles.label}>Кольори</span>
                <div className={styles.swatches} role="radiogroup" aria-label="Кольори">
                    {READER_COLORS.map((colors) => (
                        <button key={colors.value} type="button" role="radio" aria-checked={look.colors === colors.value} title={colors.label}
                            aria-label={colors.label} className={styles.pair} style={{ background: colors.bg, color: colors.text }}
                            onClick={() => setReader({ colors: colors.value })}>Аа</button>
                    ))}
                    <button type="button" role="radio" aria-checked={look.colors === 'own'} title="Свої" aria-label="Свої"
                        className={`${styles.pair} ${styles.own}`} style={{ background: look.bg, color: look.text }}
                        onClick={() => setReader({ colors: 'own' })}>Аа</button>
                </div>
            </div>
            {look.colors === 'own' && (
                <>
                    <Swatches label="Тло сторінки" values={READER_BACKGROUNDS} value={look.bg} onPick={(bg) => setReader({ bg })} />
                    <Swatches label="Колір тексту" values={READER_INKS} value={look.text} onPick={(text) => setReader({ text })} />
                    {contrast(look.bg, look.text) < 4.5 && (
                        <p className={styles.note}>Текст на такому тлі читати важко — візьміть темніший чи світліший.</p>
                    )}
                </>
            )}
            <div className={styles.row}>
                <span className={styles.label}>Позначки й посилання</span>
                <div className={styles.swatches} role="radiogroup" aria-label="Колір позначок">
                    <button type="button" role="radio" aria-checked={look.accent === null} aria-label="Як у кольорах" title="Як у кольорах"
                        className={`${styles.swatch} ${styles.auto}`} onClick={() => setReader({ accent: null })} />
                    {READER_ACCENTS.map((accent) => (
                        <button key={accent} type="button" role="radio" aria-checked={look.accent === accent} aria-label={accent} title={accent}
                            className={styles.swatch} style={{ background: accent }} onClick={() => setReader({ accent })} />
                    ))}
                </div>
            </div>

            <Toggle label="Ховати панелі, поки читаю" isSelected={look.hideBars} onChange={(hideBars) => setReader({ hideBars })} />
            <Toggle label="Відсоток прочитаного" isSelected={look.percent} onChange={(percent) => setReader({ percent })} />
            <Toggle label="Годинник" isSelected={look.clock} onChange={(clock) => setReader({ clock })} />
            <Toggle label="Не гасити екран, поки читаю" isSelected={look.awake} onChange={(awake) => setReader({ awake })} />
        </div>
    );
}

const TAPS: { value: ReaderLook['taps']; label: string; hint: string }[] = [
    { value: 'sides', label: 'Краї', hint: 'лівий край — назад, правий — далі, середина — панелі' },
    { value: 'forward', label: 'Одною рукою', hint: 'лівий край — назад, решта — далі, верх — панелі' },
    { value: 'vertical', label: 'Верх і низ', hint: 'верх — назад, низ — далі, середина — панелі' },
    { value: 'none', label: 'Лише свайп', hint: 'торкання показує панелі, гортання — свайпом' },
];

/** Where a tap goes, drawn on a small phone: back, forward and the controls. */
function TapMap({ taps }: { taps: ReaderLook['taps'] }) {
    const zones = tapZones(taps);
    return (
        <div className={styles.tapRow}>
            <div className={styles.tapMap} aria-hidden>
                {zones.map((zone) => (
                    <span key={zone.what} data-what={zone.what} style={{ left: `${zone.x[0] * 100}%`, width: `${(zone.x[1] - zone.x[0]) * 100}%`,
                        top: `${zone.y[0] * 100}%`, height: `${(zone.y[1] - zone.y[0]) * 100}%` }}>
                        {zone.what === 'back' ? '‹' : zone.what === 'next' ? '›' : '☰'}
                    </span>
                ))}
            </div>
            <p className={styles.note}>{TAPS.find((item) => item.value === taps)?.hint}</p>
        </div>
    );
}

function Swatches({ label, values, value, onPick }: { label: string; values: string[]; value: string; onPick: (value: string) => void }) {
    return (
        <div className={styles.row}>
            <span className={styles.label}>{label}</span>
            <div className={styles.swatches} role="radiogroup" aria-label={label}>
                {values.map((item) => (
                    <button key={item} type="button" role="radio" aria-checked={value === item} aria-label={item} title={item}
                        className={styles.swatch} style={{ background: item }} onClick={() => onPick(item)} />
                ))}
            </div>
        </div>
    );
}

function Choice<T extends string>({ label, value, options, onPick }: {
    label: string; value: T | null; options: { value: T; label: string }[]; onPick: (value: T) => void;
}) {
    return (
        <div className={styles.row}>
            <span className={styles.label}>{label}</span>
            <div className={styles.chips} role="radiogroup" aria-label={label}>
                {options.map((option) => (
                    <button key={option.value} type="button" role="radio" aria-checked={value === option.value}
                        className={styles.chip} onClick={() => onPick(option.value)}>{option.label}</button>
                ))}
            </div>
        </div>
    );
}

function Range({ label, value, min, max, step, show, onChange }: {
    label: string; value: number; min: number; max: number; step: number; show: (value: number) => string; onChange: (value: number) => void;
}) {
    return (
        <label className={styles.row}>
            <span className={styles.label}>{label}</span>
            <span className={styles.range}>
                <input type="range" min={min} max={max} step={step} value={value} onChange={(event) => onChange(Number(event.target.value))} />
                <output>{show(value)}</output>
            </span>
        </label>
    );
}
