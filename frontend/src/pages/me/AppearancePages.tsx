import { useMutation } from '@tanstack/react-query';
import { useState, type CSSProperties } from 'react';
import { SettingsFrame } from './SettingsFrame';

import { meApi } from '../../auth/api';
import { useMe, useSetMe } from '../../auth/me';
import { FONT_STACKS } from '../../appearance/fonts';
import {
    SITE_ACCENTS, SITE_SCALES, SITE_TONES, READER_WIDTHS, inkOn, readerColors, readerLook,
    type FrameId, type SiteLook, type ThemeId,
} from '../../appearance/model';
import { ReaderLookForm } from '../../appearance/ReaderLookForm';
import { setOwnColors, setSite, useAppearance, useSite } from '../../appearance/store';
import { FRAMES, OWN_THEME_LABEL, THEMES, THEMES_AT_ONCE, themeVars, type ThemeVars } from '../../appearance/themes';
import form from '../../appearance/ReaderLookForm.module.css';
import { Notice } from '../../ui/Notice';
import { Toggle } from '../../ui/Toggle';
import styles from './appearance.module.css';

/**
 * «Вигляд сайту» (етап 20, #292; `SettingsT`, `SettingsK`, стан «вигляд сайту»): the каркас and the тема
 * apart, then light or dark, size, catalog, portraits and animations. The whole site changes as soon as
 * something is picked; a guest's choice stays in the browser.
 */
export function SiteAppearancePage() {
    const { look, light, siteDefault } = useSite();
    const colors = themeVars(look.theme, look.own, light);
    return (
        <SettingsFrame title="Вигляд сайту">
            <div className={styles.set}>
                <div className={styles.block}>
                    <span className={styles.lab}>Каркас</span>
                    <p className={styles.about}>Будова сайту: меню, шрифти, форма кнопок і карток, щільність. Вибраний — у кожного свій; гостю його пам’ятає браузер.</p>
                    <FramePicker value={look.frame} siteFrame={siteDefault.frame} colors={colors} onPick={(frame) => setSite({ frame })} />
                </div>
                <div className={styles.block}>
                    <span className={styles.lab}>Тема</span>
                    <p className={styles.about}>Лише кольори; будь-яка тема лягає на будь-який каркас. Кольори нинішніх стилів сайту — теж тут.</p>
                    <ThemePicker value={look.theme} withOwn onPick={(theme) => setSite({ theme })} />
                    {look.theme === 'own' && <OwnColorsForm look={look} light={light} />}
                </div>
                <div className={styles.rows}>
                    <SetRow label="Світла чи темна" hint={<>
                        <span className={styles.onPhone}>на телефоні сайт темний, якщо не вибрати інше</span>
                        <span className={styles.onComputer}>на комп’ютері — ще й кнопкою в шапці</span>
                    </>}>
                        <Seg label="Світла чи темна" value={look.mode} onPick={(mode) => setSite({ mode })} min={250}
                            options={[{ value: 'dark', label: 'Темна' }, { value: 'light', label: 'Світла' }, { value: 'system', label: 'Як у системі' }]} />
                    </SetRow>
                    <SetRow label="Розмір сторінок">
                        <Seg label="Розмір сторінок" value={String(look.scale)} onPick={(scale) => setSite({ scale: Number(scale) })} min={200}
                            options={SITE_SCALES.map((scale) => ({ value: String(scale), label: `${Math.round(scale * 100)}%` }))} />
                    </SetRow>
                    <SetRow label="Каталог">
                        <Seg label="Каталог" value={look.catalog} onPick={(catalog) => setSite({ catalog })} min={220}
                            options={[{ value: 'rows', label: 'Рядками' }, { value: 'grid', label: 'Сіткою' }, { value: 'shelf', label: 'Полицею' }]} />
                    </SetRow>
                    <SetRow label="Портрети персонажів">
                        <Seg label="Портрети персонажів" value={look.portraits} onPick={(portraits) => setSite({ portraits })} min={150}
                            options={[{ value: 'round', label: 'Кола' }, { value: 'square', label: 'Квадрати' }]} />
                    </SetRow>
                    <SetRow label="Анімації">
                        <Seg label="Анімації" value={look.anim} onPick={(anim) => setSite({ anim })} min={250}
                            options={[{ value: 'system', label: 'Як у системі' }, { value: 'light', label: 'Легкі' }, { value: 'off', label: 'Вимкнені' }]} />
                    </SetRow>
                    <StudioInMenu />
                </div>
            </div>
        </SettingsFrame>
    );
}

/** The four каркаси, each drawn small in the colours of the тема in use. */
export function FramePicker({ value, siteFrame, colors, onPick }: {
    value: FrameId; siteFrame?: FrameId; colors: ThemeVars; onPick: (frame: FrameId) => void;
}) {
    const paint = {
        '--mini-bg': colors.bg, '--mini-s0': colors.s0, '--mini-s1': colors.s1, '--mini-s2': colors.s2, '--mini-line': colors.line,
        '--mini-acc': colors.acc, '--mini-faint': colors.faint,
    } as CSSProperties;
    return (
        <div className={styles.kgrid} role="radiogroup" aria-label="Каркас" style={paint}>
            {FRAMES.map((frame) => (
                <button key={frame.value} type="button" role="radio" aria-checked={value === frame.value} className={styles.kc}
                    onClick={() => onPick(frame.value)}>
                    <span className={styles.mini} aria-hidden>{MINI[frame.value]}</span>
                    <b>{frame.label}</b>
                    <span>{frame.about}{frame.value === siteFrame ? ' · за замовчуванням' : ''}</span>
                </button>
            ))}
        </div>
    );
}

/** doc/redesign/mockup-src/comm.mjs, mini(): each каркас in a few strokes. */
const MINI: Record<FrameId, React.ReactNode> = {
    pult: <>
        <i style={{ left: 0, top: 0, bottom: 0, width: 14, background: 'var(--mini-s0)' }} />
        <i style={{ left: 14, right: 0, top: 0, height: 10, background: 'var(--mini-s0)' }} />
        <i className={styles.ln} style={{ left: 22, top: 20, width: '60%' }} />
        <i className={styles.ln} style={{ left: 22, top: 30, width: '50%' }} />
        <i className={styles.ln} style={{ left: 22, top: 40, width: '55%' }} />
        <i style={{ left: 22, top: 54, width: 30, height: 10, background: 'var(--mini-acc)' }} />
    </>,
    zv: <>
        <i style={{ left: 0, right: 0, top: 0, height: 14, background: 'var(--mini-s1)' }} />
        <i style={{ left: 8, top: 22, width: '40%', height: 40, borderRadius: 5, background: 'var(--mini-s1)' }} />
        <i style={{ right: 8, top: 22, width: '40%', height: 40, borderRadius: 5, background: 'var(--mini-s1)' }} />
        <i style={{ left: 8, top: 68, width: 40, height: 10, borderRadius: 4, background: 'var(--mini-acc)' }} />
    </>,
    pal: <>
        <i className={styles.ln} style={{ left: '35%', top: 8, width: '30%', background: 'var(--mini-acc)' }} />
        <i style={{ left: 8, right: 8, top: 18, height: 1, background: 'var(--mini-line)' }} />
        <i className={styles.ln} style={{ left: '20%', top: 30, width: '60%' }} />
        <i className={styles.ln} style={{ left: '20%', top: 40, width: '60%' }} />
        <i className={styles.ln} style={{ left: '20%', top: 50, width: '55%' }} />
        <i className={styles.ln} style={{ left: '20%', top: 60, width: '60%' }} />
    </>,
    obl: <>
        <i style={{ left: 6, top: 6, bottom: 6, width: 22, borderRadius: 8, background: 'var(--mini-s1)' }} />
        <i style={{ left: 34, top: 8, width: 26, height: 20, borderRadius: 4, background: 'var(--mini-acc)', opacity: 0.85 }} />
        <i style={{ left: 34, top: 36, right: 8, height: 16, borderRadius: 8, background: 'var(--mini-s1)' }} />
        <i style={{ left: 34, top: 58, width: 44, height: 14, borderRadius: 7, background: 'var(--mini-acc)' }} />
    </>,
};

/**
 * The themes as on the mockup: seven at once and «Усі теми · 16 ›» for the rest (open from the start
 * when the chosen one is among them); «Свої кольори» last, for a person, not for the site's default.
 */
export function ThemePicker({ value, withOwn, onPick }: {
    value: ThemeId | 'own'; withOwn: boolean; onPick: (theme: ThemeId | 'own') => void;
}) {
    const hidden = THEMES.findIndex((theme) => theme.value === value) >= THEMES_AT_ONCE;
    const [all, setAll] = useState(hidden);
    const shown = all || hidden ? THEMES : THEMES.slice(0, THEMES_AT_ONCE);
    const total = THEMES.length + (withOwn ? 1 : 0);
    return (
        <>
            <div className={styles.thg} role="radiogroup" aria-label="Тема">
                {shown.map((theme) => {
                    // As on the mockup: the dark variant's page, acc2 and acc, in light and dark alike.
                    const colors = themeVars(theme.value, { bg: 0, accent: '#000000' }, false);
                    return (
                        <button key={theme.value} type="button" role="radio" aria-checked={value === theme.value} className={styles.tc}
                            onClick={() => onPick(theme.value)}>
                            <span className={styles.sws} aria-hidden><i style={{ background: colors.bg }} /><i style={{ background: colors.acc2 }} /><i style={{ background: colors.acc }} /></span>
                            {theme.label}
                        </button>
                    );
                })}
                {withOwn && (
                    <button type="button" role="radio" aria-checked={value === 'own'} className={styles.tc} onClick={() => onPick('own')}>
                        <span className={styles.sws} aria-hidden><i style={{ background: '#333' }} /><i style={{ background: '#777' }} /><i style={{ background: '#bbb' }} /></span>
                        {OWN_THEME_LABEL}
                    </button>
                )}
            </div>
            {!(all || hidden) && (
                <button type="button" className={styles.more} onClick={() => setAll(true)}>Усі теми · {total} ›</button>
            )}
        </>
    );
}

/** «Свої кольори»: a tone of the site and an accent, as «Свій стиль» had them. */
function OwnColorsForm({ look, light }: { look: SiteLook; light: boolean }) {
    const tones = SITE_TONES[light ? 'light' : 'dark'];
    return (
        <div className={styles.own}>
            <div className={form.row}>
                <span className={form.label}>Тло</span>
                <div className={form.swatches} role="radiogroup" aria-label="Тло">
                    {tones.map((tone, index) => (
                        <button key={tone.name} type="button" role="radio" aria-checked={look.own.bg === index} aria-label={tone.name} title={tone.name}
                            className={form.pair} style={{ background: `linear-gradient(135deg, ${tone.bg} 50%, ${tone.surface} 50%)`, color: tone.text }}
                            onClick={() => setOwnColors({ bg: index })}>Аа</button>
                    ))}
                </div>
            </div>
            <div className={form.row}>
                <span className={form.label}>Акцент</span>
                <div className={form.swatches} role="radiogroup" aria-label="Акцент">
                    {SITE_ACCENTS.map((accent) => (
                        <button key={accent} type="button" role="radio" aria-checked={look.own.accent === accent} aria-label={accent} title={accent}
                            className={form.swatch} style={{ background: accent, color: inkOn(accent) }} onClick={() => setOwnColors({ accent })} />
                    ))}
                </div>
            </div>
        </div>
    );
}

function SetRow({ label, hint, children }: { label: string; hint?: React.ReactNode; children: React.ReactNode }) {
    return (
        <div className={styles.setRow}>
            <span>{label}{hint && <small>{hint}</small>}</span>
            {children}
        </div>
    );
}

/** A row of choices joined together (the mockup's `.seg`). */
function Seg<T extends string>({ label, value, options, onPick, min }: {
    label: string; value: T; options: { value: T; label: string }[]; onPick: (value: T) => void; min: number;
}) {
    return (
        <div className={styles.seg} role="radiogroup" aria-label={label} style={{ minWidth: min }}>
            {options.map((option) => (
                <button key={option.value} type="button" role="radio" aria-checked={value === option.value} onClick={() => onPick(option.value)}>
                    {option.label}
                </button>
            ))}
        </div>
    );
}

/** «Вигляд читалки»: the same form as behind «Аа», with a page of text to see it on. */
export function ReaderAppearancePage() {
    const appearance = useAppearance();
    const look = readerLook(appearance);
    const colors = readerColors(appearance);
    return (
        <SettingsFrame title="Читання">
        <div className={styles.page}>
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
            <AdultSwitch />
        </div>
        </SettingsFrame>
    );
}

/** Novels marked 18+ are hidden until the person says they are 18 (signed in only). */
function AdultSwitch() {
    const me = useMe();
    const setMe = useSetMe();
    const save = useMutation({ mutationFn: (adultConfirmed: boolean) => meApi.update({ adultConfirmed }), onSuccess: setMe });
    if (!me) return null;
    return (
        <div className={styles.group}>
            <Toggle label="Мені є 18 років" isSelected={me.adultConfirmed} onChange={(value) => save.mutate(value)} />
            <p className={form.note}>Відкриває новели з позначкою «18+». Без цього вони приховані.</p>
            {save.isError && <Notice tone="error">{save.error.message}</Notice>}
        </div>
    );
}

/**
 * «Студія» among the phone's bottom tabs, for people who translate every day; kept with the account.
 * On a computer it is always in the menu.
 */
function StudioInMenu() {
    const me = useMe();
    const setMe = useSetMe();
    const save = useMutation({ mutationFn: (studioInMenu: boolean) => meApi.update({ studioInMenu }), onSuccess: setMe });
    if (!me) return null;
    return (
        <div className={styles.toggleRow}>
            <Toggle label="Студія в нижньому меню на телефоні" isSelected={Boolean(me.studioInMenu)} onChange={(value) => save.mutate(value)} />
            <small>вкладка «Студія» поруч із «Бібліотекою», а не лише в «Я»; на комп'ютері вона в меню завжди</small>
            {save.isError && <Notice tone="error">{save.error.message}</Notice>}
        </div>
    );
}
