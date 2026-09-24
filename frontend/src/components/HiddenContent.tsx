import { useState, type ReactNode } from 'react';

/** A message a moderator hid: collapsed by default, but any reader may reveal it for themselves. */
export function HiddenContent({ hidden, reason, children }: { hidden: boolean; reason?: string; children: ReactNode }) {
    const [revealed, setRevealed] = useState(false);
    if (!hidden) return <>{children}</>;
    return <div className={'hidden-content' + (revealed ? ' revealed' : '')}>
        <p className="hidden-note"><span>Приховано модератором{reason ? ': ' + reason : ''}</span>
            <button type="button" className="plain-button" aria-expanded={revealed} onClick={() => setRevealed(value => !value)}>
                {revealed ? 'Згорнути' : 'Показати'}</button></p>
        {revealed && children}
    </div>;
}
