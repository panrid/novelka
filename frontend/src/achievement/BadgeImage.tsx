import { useState } from 'react';
import { Award } from '../ui/icons';

/**
 * A badge's medal (public/badges/<code>.svg); a badge drawn later than its code falls back to
 * the award icon. Badges still ahead are shown grey.
 */
export function BadgeImage({ code, size, earned = true, className }: { code: string; size: number; earned?: boolean; className?: string }) {
    const [missing, setMissing] = useState(false);
    if (missing || !/^[a-z0-9_]+$/.test(code)) return <Award size={size * 0.6} aria-hidden {...(className ? { className } : {})} />;
    return (
        <img src={`/badges/${code}.svg`} width={size} height={size} alt="" className={className} onError={() => setMissing(true)}
            style={earned ? undefined : { filter: 'grayscale(1)', opacity: 0.6 }} />
    );
}
