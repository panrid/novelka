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

const CLOCK = new Intl.DateTimeFormat('uk', { hour: '2-digit', minute: '2-digit' });
const DAY = new Intl.DateTimeFormat('uk', { day: 'numeric', month: 'long' });
const DAY_YEAR = new Intl.DateTimeFormat('uk', { day: 'numeric', month: 'long', year: 'numeric' });

/** When a message was written: «13:48» today, «учора, 13:48», «25 вересня, 13:48», with the year if not this one. */
export function messageTime(date: Date, now: Date = new Date()): string {
    const clock = CLOCK.format(date);
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    if (date.getTime() >= startOfToday) return clock;
    if (date.getTime() >= startOfToday - 86_400_000) return `учора, ${clock}`;
    return `${(date.getFullYear() === now.getFullYear() ? DAY : DAY_YEAR).format(date)}, ${clock}`;
}
