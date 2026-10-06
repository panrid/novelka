import { render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useKeyboardInset } from './keyboard';

function Probe() {
    useKeyboardInset();
    return <textarea aria-label="Правка" />;
}

/** A visualViewport that is `offsetTop` px down and `cover` px short of the window, as iOS reports it. */
function stubViewport(offsetTop: number, cover: number) {
    const listeners: Record<string, (() => void)[]> = {};
    const viewport = {
        offsetTop, scale: 1, height: window.innerHeight - cover - offsetTop,
        addEventListener: (name: string, fn: () => void) => { (listeners[name] ??= []).push(fn); },
        removeEventListener: () => {},
    };
    vi.stubGlobal('visualViewport', viewport);
    return { viewport, fire: (name: string) => listeners[name]?.forEach((fn) => fn()) };
}

afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
    document.documentElement.style.removeProperty('--keyboard-inset');
});

describe('keyboard inset', () => {
    it('lifts sheets only while a field has the keyboard', () => {
        const { fire } = stubViewport(0, 300);
        const { getByLabelText } = render(<Probe />);
        expect(document.documentElement.style.getPropertyValue('--keyboard-inset')).toBe('0px');
        (getByLabelText('Правка') as HTMLTextAreaElement).focus();
        fire('resize');
        expect(document.documentElement.style.getPropertyValue('--keyboard-inset')).toBe('300px');
    });

    it('puts Safari\'s viewport back when it stays shifted after the keyboard closed', () => {
        vi.useFakeTimers();
        const scrollTo = vi.fn();
        vi.stubGlobal('scrollTo', scrollTo);
        const { viewport } = stubViewport(0, 0);
        const { getByLabelText } = render(<Probe />);
        const field = getByLabelText('Правка') as HTMLTextAreaElement;
        field.focus();
        viewport.offsetTop = 24; // what iOS 26 leaves behind (WebKit bug 297779)
        field.blur();
        vi.advanceTimersByTime(300);
        expect(scrollTo).toHaveBeenCalledTimes(1);
        expect(document.documentElement.style.getPropertyValue('--keyboard-inset')).toBe('0px');
    });
});
