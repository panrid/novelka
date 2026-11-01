/**
 * Безшовне читання (етап 20, рішення 7): what can be decided without a screen, and how a reader hands its
 * place over to the chapter it moves to. Measuring stays in ReaderPage; the numbers come here.
 */

export type Place = { anchor: string | null; offset: number };
export type Arrival = Place & { end?: boolean };

/** Where a chapter opened on purpose stands: its start, not the place remembered in it. */
export const CHAPTER_START: Arrival = { anchor: null, offset: 0 };

/** The first paragraph still on screen, and how far from the top it stands. Past them all: the last one. */
export function pickAnchor(blocks: { id: string | null; top: number; bottom: number }[]): Place {
    const block = blocks.find((candidate) => candidate.bottom > 0) ?? blocks[blocks.length - 1];
    return block ? { anchor: block.id, offset: block.top } : { anchor: null, offset: 0 };
}

/** The next chapter's divider has passed the top of the screen. */
export function reachedNext(dividerTop: number): boolean {
    return dividerTop <= 0;
}

/**
 * The screen is wholly within the previous chapter: the end of its text is below the screen's bottom.
 * No text to measure, or the top of the page: nothing else is left to see.
 */
export function reachedPrevious(lastBottom: number | null, screenHeight: number, scrollY: number): boolean {
    if (scrollY <= 0) return true;
    return lastBottom !== null && lastBottom >= screenHeight;
}

/** How far the page must move for the text on screen to stay put after something of this height went in above it. */
export function shiftFor(height: number, alreadyShifted: number): number {
    return height - alreadyShifted;
}

type Expected = Arrival & { number: number; bars: boolean | undefined; live?: Place };

/**
 * The move under way. A history entry keeps its state for good, while a place in it is true only at the moment
 * of that move: «Назад» or a reload must go by the saved place instead of being sent to an old paragraph.
 */
let expected: Expected | null = null;

/** Says a move to this chapter is coming, right before it is asked of the router. */
export function announceArrival(number: number, state: Arrival, bars?: boolean) {
    expected = { ...state, number, bars };
}

/** What a chapter opened with: the state of its history entry, if that entry was only just made. */
export function claimArrival(number: number, state: Arrival | undefined): (Arrival & { bars?: boolean }) | undefined {
    if (!state || !expected || expected.number !== number || expected.anchor !== state.anchor
        || expected.offset !== state.offset || Boolean(expected.end) !== Boolean(state.end)) return undefined;
    return expected.bars === undefined ? state : { ...state, bars: expected.bars };
}

/** The place at the very moment the chapter behind leaves: the reader keeps scrolling while the move is made. */
export function updateArrival(place: Place) {
    if (expected) expected.live = place;
}

/** The place to stand on arriving; the move is over from here on. */
export function takeLanding(): Place | undefined {
    const live = expected?.live;
    expected = null;
    return live;
}
