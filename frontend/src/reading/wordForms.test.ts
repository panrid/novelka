import { describe, expect, it } from 'vitest';
import { wordPattern } from './wordForms';

const all = (form: string, text: string) => [...text.matchAll(wordPattern(form))].map((match) => match[0]);

describe('a glossary word in any of its forms', () => {
    it('finds the cases of a name and of a two-word term, not a longer word', () => {
        expect(all('Орест', 'Оресте! Ореста не було, і Орестові сумно.')).toEqual(['Оресте', 'Ореста', 'Орестові']);
        expect(all('Крижаний спис', 'Він кинув Крижаного списа.')).toEqual(['Крижаного списа']);
        expect(all('Рьо', 'Рьо прийшов.')).toEqual(['Рьо']);
        expect(all('Анна', 'Ганна й Анна')).toEqual(['Анна']);
    });
});
