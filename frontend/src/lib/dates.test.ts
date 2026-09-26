import { describe, expect, it } from 'vitest';
import { messageTime, monthYearGenitive } from './dates';

describe('monthYearGenitive', () => {
    it('puts the month in the genitive', () => {
        expect(monthYearGenitive(new Date('2026-03-01'))).toBe('березня 2026');
        expect(monthYearGenitive(new Date('2026-09-24'))).toBe('вересня 2026');
    });
});

describe('messageTime', () => {
    const now = new Date(2026, 8, 26, 10, 0);
    it('says only the time today, and the day before that', () => {
        expect(messageTime(new Date(2026, 8, 26, 9, 5), now)).toBe('09:05');
        expect(messageTime(new Date(2026, 8, 25, 13, 48), now)).toBe('учора, 13:48');
        expect(messageTime(new Date(2026, 8, 20, 13, 48), now)).toBe('20 вересня, 13:48');
        expect(messageTime(new Date(2025, 11, 31, 23, 0), now)).toBe('31 грудня 2025 р., 23:00');
    });
});
