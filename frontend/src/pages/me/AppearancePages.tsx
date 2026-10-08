import { Link } from '@tanstack/react-router';
import { FONT_STACKS, loadFont } from '../../appearance/fonts';
import {
    ICON_SETS, READER_COLORS, READER_WIDTHS, SITE_ACCENTS, SITE_FONTS, SITE_PRESETS, SITE_TONES, inkOn, readerLook, siteLook, sitePresetLook,
    type FontId, type SiteLook,
} from '../../appearance/model';
import { ReaderLookForm } from '../../appearance/ReaderLookForm';
import { setSite, setSitePreset, useAppearance } from '../../appearance/store';
import form from '../../appearance/ReaderLookForm.module.css';
import { Toggle } from '../../ui/Toggle';
import { Bell, BookOpen, Home, Inbox, Search, User } from '../../ui/icons';
import styles from './appearance.module.css';

/**
 * «Вигляд сайту» (етап 18): a ready style, or one's own put together from the site's choices.
 * The whole site changes as soon as something is picked.
 */
export function SiteAppearancePage() {
    const look = siteLook(useAppearance());
    const base = SITE_PRESETS.find((item) => item.value === look.preset)!;
    return (
        <section className={styles.page}>
            <Link to="/me/settings" className={styles.back}>‹ Налаштування</Link>
            <h1 className={styles.title}>Вигляд сайту</h1>
            <p className={styles.lead}>Готовий стиль або свій: змініть будь-що нижче, і стиль стане вашим. Читалка налаштовується окремо — <Link to="/me/settings/reader">вигляд читалки</Link>.</p>

            <div className={styles.presets} role="radiogroup" aria-label="Готовий стиль">
                {SITE_PRESETS.map((preset) => {
                    const thumb = { ...look, ...sitePresetLook(preset.value) } as SiteLook;
                    const tone = SITE_TONES[thumb.base][thumb.bg]!;
                    return (
                        <button key={preset.value} type="button" role="radio" aria-checked={!look.custom && look.preset === preset.value}
                            className={styles.preset} onClick={() => setSitePreset(preset.value)} onFocus={() => void loadFont(thumb.head)}>
                            <span className={styles.thumb} style={{ background: tone.bg }}>
                                <i style={{ width: '62%', background: tone.text, borderRadius: thumb.radius / 3 }} />
                                <i style={{ width: '36%', background: thumb.accent, borderRadius: thumb.radius / 3 }} />
                                <span className={styles.covers}>
                                    <b style={{ background: tone.surface2, borderRadius: thumb.radius / 4 }} />
                                    <b style={{ background: thumb.accent, opacity: 0.7, borderRadius: thumb.radius / 4 }} />
                                    <b style={{ background: tone.surface2, borderRadius: thumb.radius / 4 }} />
                                </span>
                            </span>
                            <span className={styles.name} style={{ fontFamily: FONT_STACKS[thumb.head] }}>{preset.label}</span>
                        </button>
                    );
                })}
            </div>
            {look.custom && (
                <p className={form.note}>
                    Свій стиль на основі «{base.label}».{' '}
                    <button type="button" className={styles.link} onClick={() => setSitePreset(look.preset)}>Повернути «{base.label}»</button>
                </p>
            )}

            <div className={styles.group}>
                <h2 className={styles.groupTitle}>Кольори</h2>
                <Choice label="Основа" value={look.base} onPick={(value) => setSite({ base: value, bg: 0 })}
                    options={[{ value: 'light', label: 'Світла' }, { value: 'dark', label: 'Темна' }]} />
                <div className={form.row}>
                    <span className={form.label}>Тло</span>
                    <div className={form.swatches} role="radiogroup" aria-label="Тло">
                        {SITE_TONES[look.base].map((tone, index) => (
                            <button key={tone.name} type="button" role="radio" aria-checked={look.bg === index} aria-label={tone.name} title={tone.name}
                                className={form.pair} style={{ background: `linear-gradient(135deg, ${tone.bg} 50%, ${tone.surface} 50%)`, color: tone.text }}
                                onClick={() => setSite({ bg: index })}>Аа</button>
                        ))}
                    </div>
                </div>
                <div className={form.row}>
                    <span className={form.label}>Акцент</span>
                    <div className={form.swatches} role="radiogroup" aria-label="Акцент">
                        {SITE_ACCENTS.map((accent) => (
                            <button key={accent} type="button" role="radio" aria-checked={look.accent === accent} aria-label={accent} title={accent}
                                className={form.swatch} style={{ background: accent, color: inkOn(accent) }} onClick={() => setSite({ accent })} />
                        ))}
                    </div>
                </div>
            </div>

            <div className={styles.group}>
                <h2 className={styles.groupTitle}>Шрифти</h2>
                <FontSelect label="Меню, кнопки, підписи" value={look.ui} onPick={(ui) => setSite({ ui })} />
                <FontSelect label="Заголовки й назви" value={look.head} onPick={(head) => setSite({ head })} />
            </div>

            <div className={styles.group}>
                <h2 className={styles.groupTitle}>Форма</h2>
                <label className={form.row}>
                    <span className={form.label}>Кути</span>
                    <span className={form.range}>
                        <input type="range" min={0} max={24} step={2} value={look.radius} onChange={(event) => setSite({ radius: Number(event.target.value) })} />
                        <output>{look.radius} px</output>
                    </span>
                </label>
                <Choice label="Щільність" value={look.density} onPick={(density) => setSite({ density })}
                    options={[{ value: 'compact', label: 'Компактно' }, { value: 'normal', label: 'Звичайно' }, { value: 'airy', label: 'Просторо' }]} />
                <Choice label="Картки" value={look.cards} onPick={(cards) => setSite({ cards })}
                    options={[{ value: 'flat', label: 'Пласкі' }, { value: 'border', label: 'З рамкою' }, { value: 'shadow', label: 'З тінню' }]} />
                <Choice label="Анімації" value={look.anim} onPick={(anim) => setSite({ anim })}
                    options={[{ value: 'system', label: 'Як у системі' }, { value: 'full', label: 'Увімкнені' }, { value: 'light', label: 'Лише легкі' }, { value: 'off', label: 'Вимкнені' }]} />
            </div>
            <div className={styles.group}>
                <h2 className={styles.groupTitle}>Іконки</h2>
                <div className={styles.iconDemo} aria-hidden>
                    <Home size={26} /><Search size={26} /><BookOpen size={26} /><Inbox size={26} /><Bell size={26} /><User size={26} />
                </div>
                <Choice label="Набір" value={look.icons} onPick={(icons) => setSite({ icons })}
                    options={ICON_SETS.map(({ value, label }) => ({ value, label }))} />
                {look.icons === 'phosphor' ? (
                    <p className={form.note}>Phosphor має чотири товщини: найближча до вибраної.</p>
                ) : null}
                <label className={form.row}>
                    <span className={form.label}>Товщина ліній</span>
                    <span className={form.range}>
                        <input type="range" min={1} max={2.5} step={0.25} value={look.iconWeight}
                            onChange={(event) => setSite({ iconWeight: Number(event.target.value) })} />
                        <output>{look.iconWeight.toFixed(2).replace('.', ',')}</output>
                    </span>
                </label>
                <Choice label="Колір у меню й панелях" value={look.iconColor} onPick={(iconColor) => setSite({ iconColor })}
                    options={[{ value: 'text', label: 'Як текст' }, { value: 'accent', label: 'Акцент' }, { value: 'muted', label: 'Приглушений' }]} />
                {ICON_SETS.find((set) => set.value === look.icons)?.fill && (
                    <Toggle label="Заповнені" isSelected={look.iconFill} onChange={(iconFill) => setSite({ iconFill })} />
                )}
            </div>
            <div className={styles.group}>
                <h2 className={styles.groupTitle}>Верстка</h2>
                <Choice label="Меню на комп’ютері" value={look.nav} onPick={(nav) => setSite({ nav })}
                    options={[{ value: 'top', label: 'Вгорі' }, { value: 'side', label: 'Збоку' }]} />
                <Choice label="Новели в каталозі" value={look.catalog} onPick={(catalog) => setSite({ catalog })}
                    options={[{ value: 'rows', label: 'Рядками' }, { value: 'grid', label: 'Сіткою' }, { value: 'shelf', label: 'Полицею' }]} />
                <p className={form.note}>Меню збоку — лише на широкому екрані; на телефоні вкладки завжди внизу.</p>
            </div>
        </section>
    );
}

/** «Вигляд читалки»: the same form as behind «Аа», with a page of text to see it on. */
export function ReaderAppearancePage() {
    const look = readerLook(useAppearance());
    const colors = READER_COLORS.find((item) => item.value === look.colors)!;
    return (
        <section className={styles.page}>
            <Link to="/me/settings" className={styles.back}>‹ Налаштування</Link>
            <h1 className={styles.title}>Вигляд читалки</h1>
            <p className={styles.lead}>Окремо від вигляду сайту. Те саме можна змінити просто в читалці — кнопка «Аа».</p>
            <div className={styles.sample} lang="uk" data-paragraphs={look.paragraphs} style={{
                background: colors.bg, color: colors.text, fontFamily: FONT_STACKS[look.font], fontSize: look.size,
                lineHeight: look.lineHeight, textAlign: look.align, hyphens: look.align === 'justify' ? 'auto' : 'manual',
                paddingInline: look.margin,
            }}>
                <div style={{ maxWidth: READER_WIDTHS[look.width], margin: '0 auto' }}>
                    <p>Ліхтарник Орест прокинувся раніше за місто. Над дахами ще висіла сива пара, а годинник на ратуші вперто показував за п’ять шосту.</p>
                    <p>Він узяв драбину, торбу з ґнотами й вийшов на <b>Ковальську</b> вулицю. Кожен ліхтар тут мав своє ім’я, і Орест знав їх усі.</p>
                </div>
            </div>
            <ReaderLookForm />
        </section>
    );
}

function FontSelect({ label, value, onPick }: { label: string; value: FontId; onPick: (font: FontId) => void }) {
    return (
        <label className={form.row}>
            <span className={form.label}>{label}</span>
            <select className={form.select} value={value} style={{ fontFamily: FONT_STACKS[value] }}
                onChange={(event) => onPick(event.target.value as FontId)}
                onFocus={() => SITE_FONTS.forEach((font) => void loadFont(font.value))}>
                {SITE_FONTS.map((font) => <option key={font.value} value={font.value} style={{ fontFamily: FONT_STACKS[font.value] }}>{font.label}</option>)}
            </select>
        </label>
    );
}

function Choice<T extends string>({ label, value, options, onPick }: {
    label: string; value: T; options: { value: T; label: string }[]; onPick: (value: T) => void;
}) {
    return (
        <div className={form.row}>
            <span className={form.label}>{label}</span>
            <div className={form.chips} role="radiogroup" aria-label={label}>
                {options.map((option) => (
                    <button key={option.value} type="button" role="radio" aria-checked={value === option.value}
                        className={form.chip} onClick={() => onPick(option.value)}>{option.label}</button>
                ))}
            </div>
        </div>
    );
}
