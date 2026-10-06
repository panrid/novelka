import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';
import { DiffModeSwitch, WordDiff, wordDiff } from './WordDiff';

const BEFORE = 'корисна. Цьому старому я при наступній зустрічі обовʼязково пожаліюся.';
const AFTER = 'корисна. При наступній зустрічі обовʼязково пожаліюся цьому старому.';

afterEach(() => localStorage.clear());

describe('a change word by word', () => {
    it('crosses out whole Ukrainian words, never letters', () => {
        expect(wordDiff(BEFORE, AFTER)).toEqual([
            { kind: 'same', value: 'корисна. ' },
            { kind: 'removed', value: 'Цьому старому я при' },
            { kind: 'added', value: 'При' },
            { kind: 'same', value: ' наступній зустрічі обовʼязково ' },
            { kind: 'removed', value: 'пожаліюся.' },
            { kind: 'added', value: 'пожаліюся цьому старому.' },
        ]);
    });

    it('keeps two words changed apart by a space as one change', () => {
        expect(wordDiff('сказав Рьо тихо', 'мовив Абель тихо')).toEqual([
            { kind: 'removed', value: 'сказав Рьо' },
            { kind: 'added', value: 'мовив Абель' },
            { kind: 'same', value: ' тихо' },
        ]);
    });

    it('shows it as one paragraph or as «було» and «стало», remembering the choice', async () => {
        const { container } = render(<><DiffModeSwitch /><WordDiff before={BEFORE} after={AFTER} /></>);
        expect(container.querySelectorAll('del')).toHaveLength(2);
        await userEvent.click(screen.getByRole('button', { name: 'Було і стало' }));
        expect(screen.getByText('Було')).toBeInTheDocument();
        expect(screen.getByText('Стало').nextElementSibling).toHaveTextContent(AFTER);
        expect(localStorage.getItem('novelka:diff-mode')).toBe('split');
    });
});
