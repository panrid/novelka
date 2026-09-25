import { describe, expect, it } from 'vitest';
import { changes, characters, paragraphs } from './plural';

describe('plural', () => {
    it('follows Ukrainian number agreement', () => {
        expect(changes(1)).toBe('1 зміна');
        expect(changes(3)).toBe('3 зміни');
        expect(changes(11)).toBe('11 змін');
        expect(paragraphs(21)).toBe('21 абзац');
        expect(paragraphs(0)).toBe('0 абзаців');
        expect(characters(1234)).toBe(`${(1234).toLocaleString('uk-UA')} знаки`);
    });
});
