export interface NovelCard {
    id: string;
    title: string;
    author: string;
    chapterCount: number;
    readyChapters: number;
    aliases: string[];
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
    chapterCount: number;
    chapters: ChapterSummary[];
}

export interface Block {
    id: string;
    kind: string;
    text: string;
}

export interface ReaderChapter {
    novelId: string;
    number: number;
    revision: number;
    title: string;
    blocks: Block[];
}
