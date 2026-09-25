/** «1 зміна», «2 зміни», «5 змін»: the noun after a number, by Ukrainian rules. */
export function plural(count: number, one: string, few: string, many: string): string {
    const tens = Math.abs(count) % 100;
    const ones = Math.abs(count) % 10;
    const word = tens >= 11 && tens <= 14 ? many : ones === 1 ? one : ones >= 2 && ones <= 4 ? few : many;
    return `${count.toLocaleString('uk-UA')} ${word}`;
}

export const changes = (n: number) => plural(n, 'зміна', 'зміни', 'змін');
export const paragraphs = (n: number) => plural(n, 'абзац', 'абзаци', 'абзаців');
export const characters = (n: number) => plural(n, 'знак', 'знаки', 'знаків');
export const members = (n: number) => plural(n, 'учасник', 'учасники', 'учасників');
