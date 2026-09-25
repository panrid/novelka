import { describe, expect, it } from 'vitest';
import { chapterHeading } from './api';

describe('chapter heading', () => {
    it('shows the number readers expect, not the position', () => {
        expect(chapterHeading({ number: 1, label: '0', title: 'Пролог' })).toBe('0. Пролог');
        expect(chapterHeading({ number: 33, label: '31.1', title: 'Продовження' })).toBe('31.1. Продовження');
        expect(chapterHeading({ number: 40, label: '', title: 'Інтерлюдія' })).toBe('Інтерлюдія');
        expect(chapterHeading({ number: 5, label: null, title: 'Світло' })).toBe('5. Світло');
        expect(chapterHeading({ number: 12, label: '12', title: '' })).toBe('Глава 12');
    });
});
