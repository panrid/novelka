import { useEffect, useState } from 'react';

export type Theme = 'dark' | 'light' | 'black';
const THEME_KEY = 'novelka:theme';
const SIZE_KEY = 'novelka:reader-size';

function read(key: string): string | null {
    try {
        return localStorage.getItem(key);
    } catch {
        return null;
    }
}

function write(key: string, value: string) {
    try {
        localStorage.setItem(key, value);
    } catch {
        // Storage may be blocked; the choice then lasts until the page is closed.
    }
}

export function applyStoredTheme() {
    const theme = read(THEME_KEY);
    document.documentElement.dataset.theme = theme === 'light' || theme === 'black' ? theme : 'dark';
}

export function useTheme(): [Theme, (theme: Theme) => void] {
    const [theme, setTheme] = useState<Theme>(() => (document.documentElement.dataset.theme as Theme) ?? 'dark');
    useEffect(() => {
        document.documentElement.dataset.theme = theme;
        write(THEME_KEY, theme);
    }, [theme]);
    return [theme, setTheme];
}

/** Reading text size in px, 15–26. */
export function useReaderSize(): [number, (size: number) => void] {
    const [size, setSize] = useState(() => {
        const stored = Number(read(SIZE_KEY));
        return stored >= 15 && stored <= 26 ? stored : 18;
    });
    return [size, (next: number) => {
        const clamped = Math.max(15, Math.min(26, next));
        setSize(clamped);
        write(SIZE_KEY, String(clamped));
    }];
}
