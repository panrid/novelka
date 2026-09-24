const WITH_DAY = new Intl.DateTimeFormat('uk', { day: 'numeric', month: 'long', year: 'numeric' });

/**
 * «березня 2026» — month in the genitive, as Ukrainian needs after «з».
 * Month-and-year formats give the nominative («березень»), so take the month from a full date.
 */
export function monthYearGenitive(date: Date): string {
    const parts = WITH_DAY.formatToParts(date);
    const month = parts.find((part) => part.type === 'month')?.value ?? '';
    const year = parts.find((part) => part.type === 'year')?.value ?? '';
    return `${month} ${year}`;
}

const RELATIVE = new Intl.RelativeTimeFormat('uk', { numeric: 'auto' });

/** «5 годин тому», «вчора», «3 дні тому». */
export function relativeTime(date: Date, now: Date = new Date()): string {
    const seconds = Math.round((date.getTime() - now.getTime()) / 1000);
    const minutes = Math.round(seconds / 60);
    const hours = Math.round(minutes / 60);
    const days = Math.round(hours / 24);
    if (Math.abs(seconds) < 60) return 'щойно';
    if (Math.abs(minutes) < 60) return RELATIVE.format(minutes, 'minute');
    if (Math.abs(hours) < 24) return RELATIVE.format(hours, 'hour');
    if (Math.abs(days) < 30) return RELATIVE.format(days, 'day');
    return new Intl.DateTimeFormat('uk', { day: 'numeric', month: 'long' }).format(date);
}
