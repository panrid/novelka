export interface TagView { name: string; slug: string }

export interface NovelCard {
    id: string;
    title: string;
    author: string;
    description: string;
    chapterCount: number;
    readyChapters: number;
    aliases: string[];
    tags: TagView[];
    score: number;
}

export interface ChapterSummary {
    number: number;
    title: string;
    revision: number;
}

export interface NovelDetail {
    id: string;
    title: string;
    author: string;
    description: string;
    chapterCount: number;
    readyChapters: number;
    firstChapter: number | null;
    resumeChapter: number | null;
    tags: TagView[];
    rating: { score: number; mine: number };
    libraryStatus?: string | null;
}

export interface Block {
    id: string;
    kind: string;
    text: string;
}

export interface ReaderChapter {
    jobId: string;
    personalReplacements: Record<number, string>;
    personalStates?: Record<number, 'draft' | 'pending'>;
    personalIds?: Record<number, string>;
    draftCount?: number;
    novelId: string;
    number: number;
    revision: number;
    title: string;
    blocks: Block[];
    previousNumber: number | null;
    nextNumber: number | null;
}
