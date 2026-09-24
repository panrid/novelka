import { describe, expect, it } from 'vitest';
import { monthYearGenitive } from './dates';

describe('monthYearGenitive', () => {
    it('puts the month in the genitive', () => {
        expect(monthYearGenitive(new Date('2026-03-01'))).toBe('березня 2026');
        expect(monthYearGenitive(new Date('2026-09-24'))).toBe('вересня 2026');
    });
});
