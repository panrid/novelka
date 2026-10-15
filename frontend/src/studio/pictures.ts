import { api } from '../api/client';

/** Where a picture is shown: a published chapter of one of the person's novels. */
export type PictureUse = { editionId: number; novelSlug: string; title: string; number: number };

/** One of «Мої картинки»: drawn on the site or uploaded, until the person puts it away. */
export type LibraryPicture = {
    id: number; url: string; fullUrl: string; width: number; height: number;
    fileName: string | null; drawn: boolean; prompt: string | null; fragment: string | null; sourceUrl: string | null;
    createdAt: string; uses: PictureUse[];
};

export type PictureFilter = 'all' | 'drawn' | 'uploaded' | 'unused';

/** Where a picture named «27-5.png» would go; `problem` says why it will not. */
export type PicturePlace = {
    imageId: number; fileName: string | null; url: string | null;
    chapter: number | null; paragraph: number | null; quote: string | null; problem: string | null;
};

export const PICTURE_PAGE = 40;

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

export const picturesApi = {
    page: (filter: PictureFilter, page: number) =>
        api<{ items: LibraryPicture[]; total: number; page: number }>(`/api/me/pictures?filter=${filter}&page=${page}`),
    remove: (id: number) => api<void>(`/api/me/pictures/${id}`, { method: 'DELETE' }),
    preview: (editionId: number, imageIds: number[]) =>
        api<PicturePlace[]>('/api/me/pictures/placement/preview', json('POST', { editionId, imageIds })),
    place: (editionId: number, imageIds: number[]) =>
        api<{ chapters: number; pictures: number; skipped: string[] }>('/api/me/pictures/placement', json('POST', { editionId, imageIds })),
};
