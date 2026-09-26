import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Markup, QuoteTarget } from './Markup';
import { parse } from './parseMarkup';

describe('a quote from the chapter in a comment', () => {
    it('remembers the paragraph it came from', () => {
        expect(parse('>#s12 Рьо зліз з ліжка\nЦе сильно!')).toEqual([
            { quote: true, source: 's12', lines: [[{ kind: 'text', text: 'Рьо зліз з ліжка' }]] },
            { quote: false, lines: [[{ kind: 'text', text: 'Це сильно!' }]] },
        ]);
    });

    it('is folded like a spoiler and leads to its place', async () => {
        const goTo = vi.fn();
        render(
            <QuoteTarget.Provider value={goTo}>
                <Markup text={'>#s12 Рьо зліз з ліжка\nЦе сильно!'} />
            </QuoteTarget.Provider>,
        );
        expect(screen.queryByText('Рьо зліз з ліжка')).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: /Цитата з глави/ }));
        expect(screen.getByText('Рьо зліз з ліжка')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'До місця в главі ›' }));
        expect(goTo).toHaveBeenCalledWith('s12');
    });
});
