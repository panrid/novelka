import { api } from '../api/client';

export type Kind = 'human' | 'machine' | 'mixed' | 'original';
export type Status = 'ongoing' | 'completed' | 'paused' | 'abandoned';
export type ListName = 'reading' | 'planned' | 'done' | 'paused' | 'dropped';

export type Card = {
    editionId: number;
    novelSlug: string;
    teamHandle: string;
    teamName: string;
    title: string;
    author: string;
    coverUrl: string | null;
    kind: Kind;
    status: Status;
    adult: boolean;
    chapterCount: number;
    tags: string[];
    lastPublishedAt: string | null;
};

export type Span = { text: string; marks: ('bold' | 'italic' | 'underline' | 'strike')[] };
export type TextBlock = {
    id: string;
    type: 'heading' | 'paragraph' | 'preface' | 'afterword' | 'separator' | 'image';
    content: Span[];
    imageUrl: string | null;
};

export type Home = {
    continueReading: { card: Card; chapterNumber: number; position: number; chapterLabel: string | null }[];
    popular: Card[];
    newChapters: { card: Card; firstNumber: number; lastNumber: number; publishedAt: string; firstLabel?: string | null; lastLabel?: string | null }[];
};

export type Page<T> = { items: T[]; page: number; hasMore: boolean };

export type EditionSummary = {
    editionId: number;
    teamHandle: string;
    teamName: string;
    kind: Kind;
    status: Status;
    chapterCount: number;
    coverUrl: string | null;
    /** Average stars, null until someone rates. */
    rating: number | null;
    ratings: number;
};

export type NovelPage = {
    slug: string;
    title: string;
    author: string;
    origin: 'translation' | 'original';
    description: TextBlock[];
    tags: string[];
    edition: EditionSummary;
    editions: EditionSummary[];
    adult: boolean;
    lastPublishedAt: string | null;
    viewer: { list: ListName | null; chapterNumber: number | null; position: number | null; teamRole: 'owner' | 'translator' | 'editor' | null; myRating: number | null; chapterLabel?: string | null; relayAsked?: boolean } | null;
    relay: { free: boolean; reason: 'abandoned' | 'inactive' | 'unanswered' | null; lastNumber: number; continuations: Continuation[] };
};

export type Continuation = { teamHandle: string; teamName: string; firstNumber: number };

/** `label`: the number readers see («0», «31.1»); null means the position, '' means no number. */
export type ChapterRow = { number: number; title: string; publishedAt: string; label: string | null };

/** «31.1. Ніч», «Пролог» (no number), «Глава 12» (a number without a title). */
export function chapterHeading(chapter: { number: number; label?: string | null; title: string }): string {
    const shown = chapter.label ?? String(chapter.number);
    if (!chapter.title) return shown ? `Глава ${shown}` : 'Без назви';
    return shown ? `${shown}. ${chapter.title}` : chapter.title;
}

export type ReaderChapter = {
    novelSlug: string;
    novelTitle: string;
    edition: EditionSummary;
    number: number;
    title: string;
    blocks: TextBlock[];
    previous: number | null;
    next: number | null;
    savedPosition: number | null;
    continuation: Continuation | null;
    teamRole: 'owner' | 'translator' | 'editor' | null;
    label: string | null;
};

export type CatalogQuery = { q?: string; tags?: string[]; kind?: string; machine?: string; sort?: string; page?: number };

function query(params: Record<string, string | number | string[] | undefined>) {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
        if (Array.isArray(value)) {
            value.forEach((item) => search.append(key, item));
        } else if (value !== undefined && value !== '') {
            search.set(key, String(value));
        }
    }
    const text = search.toString();
    return text ? `?${text}` : '';
}

const novelPath = (slug: string) => `/api/novels/${encodeURIComponent(slug)}`;

export const readingApi = {
    works: (nick: string) => api<Card[]>(`/api/users/${encodeURIComponent(nick)}/works`),
    activity: (nick: string) => api<{ reading: Card[]; acceptedSuggestions: number }>(`/api/users/${encodeURIComponent(nick)}/activity`),
    home: () => api<Home>('/api/home'),
    catalog: ({ q, tags, kind, machine, sort, page }: CatalogQuery) =>
        api<Page<Card>>(`/api/catalog${query({ q, tag: tags, kind, machine, sort, page })}`),
    tags: () => api<{ name: string; slug: string; novels: number }[]>('/api/tags'),
    novel: (slug: string, team?: string) => api<NovelPage>(`${novelPath(slug)}${query({ t: team })}`),
    chapters: (slug: string, team: string | undefined, order: 'asc' | 'desc', page: number) =>
        api<Page<ChapterRow>>(`${novelPath(slug)}/chapters${query({ t: team, order, page })}`),
    chapter: (slug: string, number: number, team?: string) =>
        api<ReaderChapter>(`${novelPath(slug)}/chapters/${number}${query({ t: team })}`),
    saveProgress: (editionId: number, chapterNumber: number, position: number) =>
        api<void>(`/api/progress/${editionId}`, { method: 'PUT', body: JSON.stringify({ chapterNumber, position }) }),
    library: (list: ListName) =>
        api<{ items: { card: Card; list: ListName; chapterNumber: number | null; chapterLabel: string | null }[]; counts: Record<ListName, number> }>(
            `/api/library?list=${list}`),
    setList: (editionId: number, list: ListName | null) =>
        api<void>(`/api/library/${editionId}`, { method: 'PUT', body: JSON.stringify({ list }) }),
};

export const LIST_LABELS: Record<ListName, string> = {
    reading: 'Читаю',
    planned: 'В планах',
    done: 'Прочитано',
    paused: 'Відкладено',
    dropped: 'Кинуто',
};

export const STATUS_LABELS: Record<Status, string> = {
    ongoing: 'триває',
    completed: 'завершено',
    paused: 'пауза',
    abandoned: 'покинуто',
};

/** Numbers of chapters in Ukrainian: 1 глава, 2 глави, 5 глав. */
export function chaptersWord(count: number): string {
    const tens = count % 100;
    const ones = count % 10;
    if (tens >= 11 && tens <= 14) return 'глав';
    if (ones === 1) return 'глава';
    if (ones >= 2 && ones <= 4) return 'глави';
    return 'глав';
}

/**
 * «Глава 12 з 44» where the reader's number is the chapter's place; «Глава 0» or «Глава 31.1»
 * where it is not, since «з 44» would then count something else.
 */
export function resumeLine(number: number, label: string | null | undefined, count: number): string {
    const shown = label || String(number);
    return shown === String(number) ? `Глава ${shown} з ${count}` : `Глава ${shown}`;
}
