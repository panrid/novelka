/**
 * A glossary word in the Ukrainian text in any of its forms — the same rule as the server's
 * WordForms: «Орест» finds «Оресте», «Ореста», «Орестові»; «Крижаний спис» finds «Крижаного списа».
 */
const LETTER = "[\\p{L}ʼ'’]";
const VOWELS = 'аеєиіїоуюяйь';

function stem(word: string): string {
    const lower = word.toLowerCase();
    if (word.length >= 5 && (lower.endsWith('ий') || lower.endsWith('ій') || lower.endsWith('ей'))) return word.slice(0, -2);
    return word.length >= 4 && VOWELS.includes(lower.at(-1)!) ? word.slice(0, -1) : word;
}

const escape = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** Any case of a letter: [аА]. */
const either = (letter: string) => (letter.toLowerCase() === letter.toUpperCase() ? escape(letter) : `[${letter.toLowerCase()}${letter.toUpperCase()}]`);

/**
 * A word with a capital is found only with that capital («То», a country, is not «тож»); one in
 * lower case also at the start of a sentence. A short word (up to three letters) only as it is.
 */
export function wordPattern(form: string): RegExp {
    const words = form.trim().split(/\s+/).map((word) => {
        const [first = '', ...rest] = [...stem(word)];
        const start = first === first.toLowerCase() ? either(first) : escape(first);
        const extra = [...word].length <= 3 ? '' : `${LETTER}{0,3}`;
        return `${start}${rest.map(either).join('')}${extra}`;
    });
    return new RegExp(`(?<!${LETTER})${words.join('\\s+')}(?!${LETTER})`, 'gu');
}
