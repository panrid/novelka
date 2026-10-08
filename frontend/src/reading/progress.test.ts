import { describe, expect, it } from 'vitest';
import { movesPlace } from './progress';

describe('the place of reading', () => {
    it('moves to a later chapter only once it is really begun', () => {
        expect(movesPlace(null, 40, 0)).toBe(true);
        expect(movesPlace({ number: 20 }, 40, 0)).toBe(false);
        expect(movesPlace({ number: 20 }, 40, 0.1)).toBe(false);
        expect(movesPlace({ number: 20 }, 21, 0.2)).toBe(true);
    });

    it('keeps the place on a look back, unless the earlier chapter is read to its end', () => {
        expect(movesPlace({ number: 20 }, 17, 0.5)).toBe(false);
        expect(movesPlace({ number: 20 }, 17, 0.95)).toBe(true);
        expect(movesPlace({ number: 20 }, 20, 0.3)).toBe(true);
    });
});
