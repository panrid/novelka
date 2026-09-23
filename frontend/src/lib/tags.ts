/** Same normalization as the server's TagNames: NFKC, trimmed, single spaces, Ukrainian lower case. */
export function tagName(value: string) {
    return value.normalize('NFKC').trim().replace(/\s+/g, ' ');
}

export function tagSlug(value: string) {
    return tagName(value).toLocaleLowerCase('uk');
}

export const MACHINE_TRANSLATION = 'Машинний переклад';
