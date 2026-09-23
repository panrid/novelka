import { useId } from 'react';
import { useTheme } from './ThemeContext';

const themes = [
    { value: 'light', label: 'Світла', icon: '☀' },
    { value: 'system', label: 'Системна', icon: '◐' },
    { value: 'dark', label: 'Темна', icon: '☾' },
    { value: 'black', label: 'Чорна', icon: '●' },
] as const;

/** Segmented radio group: the active theme is always visible and arrow keys move between options. */
export function ThemePicker({ compact = false }: { compact?: boolean }) {
    const { theme, setTheme } = useTheme();
    const name = useId();
    return <fieldset className={'theme-picker' + (compact ? ' compact' : '')}>
        <legend className="sr-only">Тема</legend>
        {themes.map(option => <label key={option.value} className="theme-option" title={option.label}>
            <input type="radio" name={name} value={option.value} checked={theme === option.value}
                onChange={() => setTheme(option.value)} />
            <span aria-hidden="true" className="theme-icon">{option.icon}</span>
            <span className={compact ? 'sr-only' : 'theme-text'}>{option.label}</span>
        </label>)}
    </fieldset>;
}
