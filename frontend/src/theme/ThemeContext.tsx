import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { readPreference, savePreference } from '../lib/preferences';

type Theme = 'dark' | 'light';

interface ThemeContextValue {
    theme: Theme;
    toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
    const [theme, setTheme] = useState<Theme>(() => readPreference('theme') === 'light' ? 'light' : 'dark');
    useEffect(() => {
        document.documentElement.dataset.theme = theme;
        savePreference('theme', theme);
    }, [theme]);
    return <ThemeContext value={{ theme, toggleTheme: () => setTheme(value => value === 'dark' ? 'light' : 'dark') }}>
        {children}
    </ThemeContext>;
}

export function useTheme() {
    const context = useContext(ThemeContext);
    if (context === null) throw new Error('ThemeProvider is required.');
    return context;
}
