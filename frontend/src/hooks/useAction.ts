import { useState } from 'react';

export function useAction() {
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');
    async function run(action: () => Promise<unknown>, success = 'Збережено.') {
        setBusy(true); setMessage(''); setError('');
        try { await action(); setMessage(success); }
        catch (failure) { setError(failure instanceof Error ? failure.message : 'Не вдалося виконати дію.'); }
        finally { setBusy(false); }
    }
    return { busy, message, error, run };
}
