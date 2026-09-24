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
