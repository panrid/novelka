import { SelectField } from '../components/SelectField';
import { useTheme } from './ThemeContext';

export function ThemePicker() {
    const { theme, setTheme } = useTheme();
    return <div className="theme-picker"><SelectField label="Тема" hideLabel value={theme}
        options={[{ value: 'system', label: 'Системна' }, { value: 'light', label: 'Світла' }, { value: 'dark', label: 'Темна' }, { value: 'black', label: 'Чорна' }]}
        onChange={value => { if (value === 'system' || value === 'light' || value === 'dark' || value === 'black') setTheme(value); }} /></div>;
}
