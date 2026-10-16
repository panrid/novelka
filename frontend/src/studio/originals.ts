import { api } from '../api/client';
import type { StudioBlock } from './api';

/** «Оригінал» of a translation (етап 19): the text a translator pastes or uploads to translate. */

export type OriginalLanguage = 'ja' | 'en' | 'zh' | 'ko';

export const LANGUAGES: { value: OriginalLanguage; label: string }[] = [
    { value: 'ja', label: 'Японська' },
    { value: 'en', label: 'Англійська' },
    { value: 'zh', label: 'Китайська' },
    { value: 'ko', label: 'Корейська' },
];

export const LANGUAGE_SHORT: Record<OriginalLanguage, string> = { ja: 'яп.', en: 'англ.', zh: 'кит.', ko: 'кор.' };

export type PlannedOriginal = {
    number: number; title: string; chars: number; paragraphs: number; pictures: number; start: string;
    /** The translation has an original with this number: it will be replaced. */
    replaces: boolean;
    /** The chapter is published already. */
    translated: boolean;
};

export type OriginalPreview = { chapters: PlannedOriginal[]; firstNumber: number; nextNumber: number };

export type OriginalRow = { number: number; title: string; chars: number; language: OriginalLanguage; translated: boolean; updatedAt: string };

export type OriginalList = {
    items: OriginalRow[]; total: number; page: number; nextNumber: number;
    /** How many chapters come from each language. */
    languages: Partial<Record<OriginalLanguage, number>>;
};

export type OriginalChapter = { number: number; title: string; language: OriginalLanguage; blocks: StudioBlock[] };

/** What the person gives: pasted text or a file. */
export type OriginalInput = { text: string } | { file: File };

const base = (id: number) => `/api/studio/editions/${id}/originals`;

function form(input: OriginalInput, fields: Record<string, string | number | undefined>): FormData {
    const data = new FormData();
    if ('file' in input) data.append('file', input.file, input.file.name);
    else data.append('text', input.text);
    Object.entries(fields).forEach(([key, value]) => { if (value !== undefined) data.append(key, String(value)); });
    return data;
}

export const originalsApi = {
    list: (id: number, page: number) => api<OriginalList>(`${base(id)}?page=${page}`),
    one: (id: number, number: number) => api<OriginalChapter>(`${base(id)}/${number}`),
    preview: (id: number, input: OriginalInput, first?: number) =>
        api<OriginalPreview>(`${base(id)}/preview`, { method: 'POST', body: form(input, { first }) }),
    save: (id: number, input: OriginalInput, language: OriginalLanguage, first?: number) =>
        api<{ saved: number; from: number; to: number }>(base(id), { method: 'POST', body: form(input, { first, language }) }),
    changeLanguage: (id: number, from: number, to: number, language: OriginalLanguage) =>
        api<OriginalList>(`${base(id)}/language`, { method: 'PATCH', body: JSON.stringify({ from, to, language }) }),
    remove: (id: number, number: number) => api<void>(`${base(id)}/${number}`, { method: 'DELETE' }),
    removeAll: (id: number) => api<void>(base(id), { method: 'DELETE' }),
};
