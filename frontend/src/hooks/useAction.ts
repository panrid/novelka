import { useCallback, useEffect, useState } from 'react';

/** Success notices fade after this delay; errors stay until the next action so they are not missed. */
const SUCCESS_VISIBLE_MS = 6000;

export function useAction() {
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');
    useEffect(() => {
        if (!message) return;
        const timer = window.setTimeout(() => setMessage(''), SUCCESS_VISIBLE_MS);
        return () => window.clearTimeout(timer);
    }, [message]);
    async function run(action: () => Promise<unknown>, success = 'Збережено.') {
        setBusy(true); setMessage(''); setError('');
        try { await action(); setMessage(success); }
        catch (failure) { setError(failure instanceof Error ? failure.message : 'Не вдалося виконати дію.'); }
        finally { setBusy(false); }
    }
    const clear = useCallback(() => { setMessage(''); setError(''); }, []);
    return { busy, message, error, run, clear };
}
