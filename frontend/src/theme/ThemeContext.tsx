import { createContext, useContext, useLayoutEffect, useState, type ReactNode } from 'react';
import { readPreference, savePreference } from '../lib/preferences';

type Theme = 'dark' | 'light' | 'black' | 'system';

interface ThemeContextValue {
    theme: Theme;
    setTheme: (theme: Theme) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
    const [theme, setTheme] = useState<Theme>(() => {
        const saved = readPreference('theme');
        return saved === 'light' || saved === 'black' || saved === 'system' ? saved : 'dark';
    });
    useLayoutEffect(() => {
        const media = window.matchMedia('(prefers-color-scheme: dark)');
        const apply = () => {
            const effective = theme === 'system' ? media.matches ? 'dark' : 'light' : theme;
            document.documentElement.dataset.theme = effective;
            document.querySelector('meta[name="theme-color"]')?.setAttribute('content',
                { dark: '#1c2521', light: '#f5f2ea', black: '#000000' }[effective]);
        };
        apply();
        media.addEventListener('change', apply);
        savePreference('theme', theme);
        return () => media.removeEventListener('change', apply);
    }, [theme]);
    return <ThemeContext value={{ theme, setTheme }}>
        {children}
    </ThemeContext>;
}

export function useTheme() {
    const context = useContext(ThemeContext);
    if (context === null) throw new Error('ThemeProvider is required.');
    return context;
}
