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

export function wordPattern(form: string): RegExp {
    const words = form.trim().split(/\s+/).map((word) => `${escape(stem(word))}${LETTER}{0,3}`);
    return new RegExp(`(?<!${LETTER})${words.join('\\s+')}(?!${LETTER})`, 'giu');
}
