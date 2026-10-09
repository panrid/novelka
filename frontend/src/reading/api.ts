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
    /** Chapters in the original, when known: «55 / 822 глав». */
    sourceChapters?: number | null;
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
/** A catalog page with how many novels match in all. */
export type Found<T> = Page<T> & { total: number };
export type TagCount = { name: string; slug: string; novels: number };
/** A group of the site's tag list: «Жанр», «Світ і сюжет», «Герой», «Настрій». */
export type TagGroup = { name: string; tags: TagCount[] };
/** What the search box offers while a person types. */
export type Hints = { novels: Card[]; tags: TagCount[] };

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
    /** The day a paused translation means to go on (ISO date). */
    pausedUntil?: string | null;
    /** The team lets readers download it as EPUB. */
    downloadAllowed?: boolean;
};

/** A volume one can download as a book. */
export type VolumeChoice = { firstNumber: number; lastNumber: number | null; title: string; chapters: number };

export type SourceStatus = 'ongoing' | 'completed' | 'paused';

/** The novel beyond its Ukrainian title: other names and how the original stands. All optional. */
export type NovelFacts = {
    titleOriginal: string | null;
    titleEnglish: string | null;
    altTitles: string[];
    sourceStatus: SourceStatus | null;
    sourceChapterCount: number | null;
};

export type NovelPage = {
    slug: string;
    title: string;
    author: string;
    origin: 'translation' | 'original';
    /** The original's language (ja, en…), null when unknown. */
    language?: string | null;
    description: TextBlock[];
    tags: string[];
    edition: EditionSummary;
    editions: EditionSummary[];
    adult: boolean;
    lastPublishedAt: string | null;
    viewer: { list: ListName | null; chapterNumber: number | null; position: number | null; teamRole: 'owner' | 'translator' | 'editor' | null; myRating: number | null; chapterLabel?: string | null; relayAsked?: boolean; subscribed?: boolean;
        /** Published chapters before the place the reader did not read, and the first of them. */
        skipped?: number; firstUnread?: number | null; firstUnreadLabel?: string | null } | null;
    relay: { free: boolean; reason: 'abandoned' | 'inactive' | 'unanswered' | null; lastNumber: number; continuations: Continuation[] };
    /** The original's page, when the site knows it. */
    originalUrl?: string | null;
    facts?: NovelFacts;
};

export type Continuation = { teamHandle: string; teamName: string; firstNumber: number };

/** `label`: the number readers see («0», «31.1»); null means the position, '' means no number. */
/** A volume as readers see it (етап 15); {@code index} is «Том 2», null for a prologue or side stories. */
export type VolumeRef = { firstNumber: number; title: string; kind: 'volume' | 'prologue' | 'side' | 'extra'; index: number | null };

/** {@code read}: whether the signed-in reader finished it (null for a guest). */
export type ChapterRow = { number: number; title: string; publishedAt: string; label: string | null; volume?: VolumeRef | null; read?: boolean | null };

/** «Том 2. Подорож удвох», «Пролог», «Побічні історії». */
export function volumeTitle(volume: VolumeRef): string {
    if (volume.kind !== 'volume') return volume.title || { prologue: 'Пролог', side: 'Побічні історії', extra: 'Екстра' }[volume.kind];
    const prefix = volume.index ? `Том ${volume.index}` : 'Том';
    return volume.title ? `${prefix}. ${volume.title}` : prefix;
}

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
    /** The volume the chapter is in, if the translation has volumes. */
    volume?: VolumeRef | null;
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
        api<Found<Card>>(`/api/catalog${query({ q, tag: tags, kind, machine, sort, page })}`),
    tags: () => api<TagCount[]>('/api/tags'),
    tagGroups: () => api<TagGroup[]>('/api/tags/groups'),
    hints: (q: string) => api<Hints>(`/api/search/hints${query({ q })}`),
    novel: (slug: string, team?: string) => api<NovelPage>(`${novelPath(slug)}${query({ t: team })}`),
    volumes: (slug: string, team?: string) => api<VolumeChoice[]>(`${novelPath(slug)}/volumes${query({ t: team })}`),
    /** The book itself is a plain link: the browser downloads it with the session's cookie. */
    epubUrl: (slug: string, team?: string, volume?: number) => `${novelPath(slug)}/epub${query({ t: team, volume })}`,
    chapters: (slug: string, team: string | undefined, order: 'asc' | 'desc', page: number) =>
        api<Page<ChapterRow>>(`${novelPath(slug)}/chapters${query({ t: team, order, page })}`),
    chapter: (slug: string, number: number, team?: string) =>
        api<ReaderChapter>(`${novelPath(slug)}/chapters/${number}${query({ t: team })}`),
    saveProgress: (editionId: number, chapterNumber: number, position: number) =>
        api<void>(`/api/progress/${editionId}`, { method: 'PUT', body: JSON.stringify({ chapterNumber, position }) }),
    library: (list: ListName, page = 1) =>
        api<{ items: { card: Card; list: ListName; chapterNumber: number | null; chapterLabel: string | null }[]; counts: Record<ListName, number>;
            total: number; page: number; hasMore: boolean }>(`/api/library?list=${list}&page=${page}`),
    setList: (editionId: number, list: ListName | null) =>
        api<void>(`/api/library/${editionId}`, { method: 'PUT', body: JSON.stringify({ list }) }),
    /** The bell: new chapters of this translation come to the inbox while it rings. */
    /** Chapters from..to marked read or not: one, «усі до цієї», or the skipped ones. */
    markRead: (editionId: number, from: number, to: number, read: boolean) =>
        api<void>(`/api/reads/${editionId}`, { method: 'PUT', body: JSON.stringify({ from, to, read }) }),
    /** No place and nothing read; the novel stays in its library list. */
    resetProgress: (editionId: number) => api<void>(`/api/progress/${editionId}`, { method: 'DELETE' }),
    subscribe: (editionId: number, on: boolean) =>
        api<void>(`/api/library/${editionId}/subscription`, { method: on ? 'PUT' : 'DELETE' }),
};

export const LIST_LABELS: Record<ListName, string> = {
    reading: 'Читаю',
    planned: 'В планах',
    done: 'Прочитано',
    paused: 'Відкладено',
    dropped: 'Кинуто',
};

/** The translation's state. */
export const STATUS_LABELS: Record<Status, string> = {
    ongoing: 'в роботі',
    completed: 'завершено',
    paused: 'призупинено',
    abandoned: 'закинуто',
};

/** The original's state. */
export const SOURCE_STATUS_LABELS: Record<SourceStatus, string> = {
    ongoing: 'виходить',
    completed: 'завершено',
    paused: 'призупинено',
};

/** «призупинено до 1 грудня» (with the year when it is not this one). */
export function translationStatus(status: Status, pausedUntil?: string | null): string {
    if (status !== 'paused' || !pausedUntil) return STATUS_LABELS[status];
    const day = new Date(`${pausedUntil}T00:00:00`);
    const sameYear = day.getFullYear() === new Date().getFullYear();
    return `призупинено до ${day.toLocaleDateString('uk-UA', sameYear ? { day: 'numeric', month: 'long' } : { day: 'numeric', month: 'long', year: 'numeric' })}`;
}

/** The novel's names besides the Ukrainian one: English, original, the rest. */
export function otherNames(title: string, facts: NovelFacts | undefined): string[] {
    if (!facts) return [];
    const names = [facts.titleEnglish, facts.titleOriginal, ...facts.altTitles].filter((name): name is string => Boolean(name?.trim()));
    return [...new Set(names)].filter((name) => name.toLowerCase() !== title.toLowerCase());
}

/** Numbers of chapters in Ukrainian: 1 глава, 2 глави, 5 глав. */
export function novelsWord(count: number): string {
    const tens = count % 100;
    const ones = count % 10;
    if (tens >= 11 && tens <= 14) return 'новел';
    if (ones === 1) return 'новела';
    if (ones >= 2 && ones <= 4) return 'новели';
    return 'новел';
}

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
