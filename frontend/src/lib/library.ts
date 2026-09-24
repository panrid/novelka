/** Reader shelves in display order; keys match LibraryService.STATUSES on the server. */
export const shelves = [
    { value: 'reading', label: 'Читаю' },
    { value: 'planned', label: 'В планах' },
    { value: 'completed', label: 'Завершено' },
    { value: 'on_hold', label: 'Відкладено' },
    { value: 'dropped', label: 'Кинуто' },
] as const;

export type Shelf = typeof shelves[number]['value'];

export function shelfLabel(value: string | null | undefined) {
    return shelves.find(shelf => shelf.value === value)?.label ?? '';
}
