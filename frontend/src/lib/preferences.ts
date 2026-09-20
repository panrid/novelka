export function readPreference(key: string): string | null {
    try { return localStorage.getItem('novelka:' + key); } catch { return null; }
}

export function savePreference(key: string, value: string) {
    try { localStorage.setItem('novelka:' + key, value); } catch { /* Reading works without storage. */ }
}
