import { beforeEach, describe, expect, it } from 'vitest';
import { announceArrival, CHAPTER_START, claimArrival, pickAnchor, reachedNext, reachedPrevious, shiftFor, takeLanding, updateArrival } from './seamless';

const block = (id: string, top: number, bottom: number) => ({ id, top, bottom });

describe('pickAnchor', () => {
    it('takes the first paragraph still on screen, with its distance from the top', () => {
        expect(pickAnchor([block('a', -300, -10), block('b', -10, 40), block('c', 40, 90)])).toEqual({ anchor: 'b', offset: -10 });
    });

    it('takes a paragraph that begins below the top', () => {
        expect(pickAnchor([block('a', 72, 200)])).toEqual({ anchor: 'a', offset: 72 });
    });

    it('does not count a paragraph ending exactly at the top', () => {
        expect(pickAnchor([block('a', -50, 0), block('b', 0, 50)])).toEqual({ anchor: 'b', offset: 0 });
    });

    it('keeps the last paragraph when a fast scroll has left them all above the screen', () => {
        expect(pickAnchor([block('a', -900, -800), block('b', -800, -700)])).toEqual({ anchor: 'b', offset: -800 });
    });

    it('has no paragraph in an empty chapter', () => {
        expect(pickAnchor([])).toEqual({ anchor: null, offset: 0 });
    });

    it('treats a picture first like any other block', () => {
        expect(pickAnchor([block('picture', 80, 500), block('p', 500, 560)])).toEqual({ anchor: 'picture', offset: 80 });
    });
});

describe('reachedNext', () => {
    it.each([[1, false], [0, true], [-1, true], [-5000, true]])('divider at %d: %s', (top, reached) => {
        expect(reachedNext(top)).toBe(reached);
    });
});

describe('reachedPrevious', () => {
    it('waits while the end of its text is on screen', () => {
        expect(reachedPrevious(799, 800, 1000)).toBe(false);
    });

    it('moves on once that end is at or below the bottom of the screen', () => {
        expect(reachedPrevious(800, 800, 1000)).toBe(true);
        expect(reachedPrevious(5000, 800, 1000)).toBe(true);
    });

    it('moves on at the very top, even with a chapter of one short paragraph', () => {
        expect(reachedPrevious(100, 800, 0)).toBe(true);
    });

    it('an empty chapter moves on only at the top', () => {
        expect(reachedPrevious(null, 800, 500)).toBe(false);
        expect(reachedPrevious(null, 800, 0)).toBe(true);
    });
});

describe('shiftFor', () => {
    it('moves by the height put in', () => {
        expect(shiftFor(1200, 0)).toBe(1200);
    });

    it('does not move twice for the same text', () => {
        expect(shiftFor(1200, 1200)).toBe(0);
    });

    it('moves only by what has grown', () => {
        expect(shiftFor(1300, 1200)).toBe(100);
    });
});

describe('a move between chapters', () => {
    beforeEach(() => {
        takeLanding();
    });

    it('is believed only for the chapter it was announced for', () => {
        announceArrival(13, { anchor: 'a', offset: 40 });
        expect(claimArrival(12, { anchor: 'a', offset: 40 })).toBeUndefined();
        expect(claimArrival(13, { anchor: 'a', offset: 40 })).toEqual({ anchor: 'a', offset: 40 });
    });

    it('does not believe a state nobody announced: a reload', () => {
        expect(claimArrival(13, { anchor: 'a', offset: 40 })).toBeUndefined();
    });

    it('does not believe the state of an older history entry', () => {
        announceArrival(13, { anchor: 'new', offset: 10 });
        expect(claimArrival(13, { anchor: 'old', offset: 40 })).toBeUndefined();
    });

    it('is over once the chapter has landed: «Назад» to it later goes by the saved place', () => {
        announceArrival(13, { anchor: 'a', offset: 40 });
        takeLanding();
        expect(claimArrival(13, { anchor: 'a', offset: 40 })).toBeUndefined();
    });

    it('lands where the reader was at the last moment, not when the move was asked for', () => {
        announceArrival(13, { anchor: 'a', offset: 40 });
        updateArrival({ anchor: 'b', offset: 12 });
        expect(takeLanding()).toEqual({ anchor: 'b', offset: 12 });
    });

    it('has nothing to land on without a newer place', () => {
        announceArrival(13, CHAPTER_START);
        expect(takeLanding()).toBeUndefined();
    });

    it('carries the controls as they were', () => {
        announceArrival(13, { anchor: 'a', offset: 40 }, false);
        expect(claimArrival(13, { anchor: 'a', offset: 40 })?.bars).toBe(false);
    });

    it('tells the last page from the first', () => {
        announceArrival(11, { ...CHAPTER_START, end: true });
        expect(claimArrival(11, CHAPTER_START)).toBeUndefined();
        expect(claimArrival(11, { ...CHAPTER_START, end: true })?.end).toBe(true);
    });
});
