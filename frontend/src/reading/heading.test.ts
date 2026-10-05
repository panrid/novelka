import { describe, expect, it } from 'vitest';
import { chapterHeading, volumeTitle } from './api';

describe('chapter heading', () => {
    it('shows the number readers expect, not the position', () => {
        expect(chapterHeading({ number: 1, label: '0', title: 'Пролог' })).toBe('0. Пролог');
        expect(chapterHeading({ number: 33, label: '31.1', title: 'Продовження' })).toBe('31.1. Продовження');
        expect(chapterHeading({ number: 40, label: '', title: 'Інтерлюдія' })).toBe('Інтерлюдія');
        expect(chapterHeading({ number: 5, label: null, title: 'Світло' })).toBe('5. Світло');
        expect(chapterHeading({ number: 12, label: '12', title: '' })).toBe('Глава 12');
    });
});

describe('volume title', () => {
    it('numbers ordinary volumes and names the others by kind', () => {
        expect(volumeTitle({ firstNumber: 2, title: 'Подорож удвох', kind: 'volume', index: 2 })).toBe('Том 2. Подорож удвох');
        expect(volumeTitle({ firstNumber: 2, title: '', kind: 'volume', index: 1 })).toBe('Том 1');
        expect(volumeTitle({ firstNumber: 1, title: '', kind: 'prologue', index: null })).toBe('Пролог');
        expect(volumeTitle({ firstNumber: 9, title: 'Інтерлюдії', kind: 'side', index: null })).toBe('Інтерлюдії');
    });
});
