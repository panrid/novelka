import { useEffect } from 'react';

/** Replaces an outdated hash route without adding a history entry. */
export function Redirect({ to }: { to: string }) {
    useEffect(() => { window.location.replace('#' + to); }, [to]);
    return null;
}
