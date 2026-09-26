import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Blocks } from './Blocks';

const paragraph = (id: string, text: string) => ({ id, type: 'paragraph' as const, content: [{ text, marks: [] }] });

describe('a word shown from the glossary', () => {
    it('is marked in its other forms and as a phrase', () => {
        const { container } = render(<Blocks highlight="Молодіжне відділення" blocks={[
            paragraph('a', 'Рьо прийшов до Молодіжного відділення вранці.'),
            paragraph('b', 'Про Молодіжне відділенням ніхто не питав.'),
            paragraph('c', 'Тут його немає.'),
        ]} />);
        const marks = [...container.querySelectorAll('mark')].map((mark) => mark.textContent);
        expect(marks).toEqual(['Молодіжного відділення', 'Молодіжне відділенням']);
    });

    it('leaves the text alone without a word', () => {
        const { container } = render(<Blocks blocks={[paragraph('a', 'Рьо-сан')]} />);
        expect(container.querySelector('mark')).toBeNull();
        expect(container.textContent).toBe('Рьо-сан');
    });
});
