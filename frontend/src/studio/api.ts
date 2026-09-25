import { api } from '../api/client';
import type { Span } from '../reading/api';

export type TeamRole = 'owner' | 'translator' | 'editor';

/** A block as the Studio exchanges it: pictures by id, with a URL for drawing. */
export type StudioBlock = {
    id: string;
    type: 'heading' | 'paragraph' | 'preface' | 'afterword' | 'separator' | 'image';
    content: Span[];
    imageId?: number | null;
    imageUrl?: string | null;
};

export type MyEdition = {
    editionId: number; novelSlug: string; title: string; coverUrl: string | null; kind: string; status: string;
    chapterCount: number; teamHandle: string; teamName: string; role: TeamRole; drafts: number;
};

export type Overview = {
    editionId: number; novelSlug: string; title: string; author: string; description: StudioBlock[]; tags: string[];
    kind: string; status: string; adult: boolean; coverUrl: string | null; chapterCount: number; ownNovel: boolean;
    teamHandle: string; teamName: string; role: TeamRole;
};

export type StudioChapter = { number: number; title: string; published: boolean; hasMyDraft: boolean; updatedAt: string; label: string | null };

export type EditorView = {
    number: number; title: string; blocks: StudioBlock[]; revisionId: number | null; published: boolean;
    draft: { title: string; blocks: StudioBlock[]; baseRevisionId: number | null; updatedAt: string } | null;
    role: TeamRole; mayAddPictures: boolean; previous: number | null; next: number | null; label: string | null;
};

export type RevisionInfo = {
    id: number; authorNick: string | null; origin: string; createdAt: string; blocksChanged: number; charsChanged: number;
    published: boolean;
};

export type RevisionView = { id: number; title: string; blocks: StudioBlock[]; parentTitle: string | null; parentBlocks: StudioBlock[] };

export type ImportPreview = {
    chapters: { title: string; paragraphs: number; characters: number; pictures: number }[];
    simplified: string[];
    firstNumber: number;
};

export type MyTeam = { handle: string; name: string; role: TeamRole };

export type TeamPage = {
    handle: string; name: string; personal: boolean;
    members: { nick: string; avatarUrl: string | null; role: TeamRole }[];
    editions: { novelSlug: string; title: string; coverUrl: string | null; chapterCount: number; kind: string; status: string }[];
    viewerRole: TeamRole | null;
};

export type TakeoverRequest = {
    id: number; teamHandle: string; teamName: string; requestedBy: string; message: string | null;
    state: 'open' | 'declined' | 'granted' | 'withdrawn'; createdAt: string;
};

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });
const edition = (id: number) => `/api/studio/editions/${id}`;
const chapter = (id: number, number: number) => `${edition(id)}/chapters/${number}`;

function upload(path: string, file: Blob, name: string, extra: Record<string, string> = {}) {
    const form = new FormData();
    form.append('file', file, name);
    Object.entries(extra).forEach(([key, value]) => form.append(key, value));
    return { path, init: { method: 'POST', body: form } satisfies RequestInit };
}

export const studioApi = {
    mine: () => api<MyEdition[]>('/api/studio'),
    create: (body: { kind: 'human' | 'original'; title: string; author: string; description: StudioBlock[]; tags: string[]; adult: boolean; team: string }) =>
        api<{ editionId: number; novelSlug: string }>('/api/studio/editions', json('POST', body)),
    overview: (id: number) => api<Overview>(edition(id)),
    update: (id: number, patch: Partial<{ title: string; author: string; description: StudioBlock[]; tags: string[]; status: string; adult: boolean }>) =>
        api<Overview>(edition(id), json('PATCH', patch)),
    setCover: (id: number, imageId: number | null) => api<Overview>(`${edition(id)}/cover`, json('PUT', { imageId })),
    chapters: (id: number, page = 1) => api<StudioChapter[]>(`${edition(id)}/chapters?page=${page}`),
    deleteChapter: (id: number, number: number) => api<void>(chapter(id, number), { method: 'DELETE' }),
    newChapter: (id: number) => api<{ number: number }>(`${edition(id)}/chapters`, json('POST', {})),
    editor: (id: number, number: number) => api<EditorView>(chapter(id, number)),
    saveDraft: (id: number, number: number, body: { title: string; blocks: StudioBlock[]; baseRevisionId: number | null }) =>
        api<void>(`${chapter(id, number)}/draft`, json('PUT', body)),
    discardDraft: (id: number, number: number) => api<void>(`${chapter(id, number)}/draft`, { method: 'DELETE' }),
    publish: (id: number, number: number, body: { title: string; blocks: StudioBlock[]; baseRevisionId: number | null }) =>
        api<{ revisionId: number }>(`${chapter(id, number)}/publish`, json('POST', body)),
    setLabel: (id: number, number: number, label: string | null) => api<void>(`${chapter(id, number)}/label`, json('PUT', { label })),
    revisions: (id: number, number: number) => api<RevisionInfo[]>(`${chapter(id, number)}/revisions`),
    revision: (id: number, number: number, revisionId: number) => api<RevisionView>(`${chapter(id, number)}/revisions/${revisionId}`),
    previewImport: (id: number, file: File) => {
        const request = upload(`${edition(id)}/import/preview`, file, file.name);
        return api<ImportPreview>(request.path, request.init);
    },
    importFile: (id: number, file: File) => {
        const request = upload(`${edition(id)}/import`, file, file.name);
        return api<{ numbers: number[] }>(request.path, request.init);
    },
    contributions: (id: number) => api<{ nick: string; revisions: number; blocksChanged: number; charsChanged: number }[]>(`${edition(id)}/contributions`),
    uploadImage: (file: Blob, kind: 'illustration' | 'cover') => {
        const request = upload('/api/media/images', file, kind === 'cover' ? 'cover.jpg' : 'picture.jpg', { kind });
        return api<{ id: number; url: string }>(request.path, request.init);
    },
    imageFromUrl: (url: string) => api<{ id: number; url: string }>('/api/media/images/from-url', json('POST', { url, kind: 'illustration' })),
    takeoverRequests: (id: number) => api<TakeoverRequest[]>(`${edition(id)}/takeover-requests`),
    answerTakeover: (id: number, requestId: number, grant: boolean) =>
        api<void>(`${edition(id)}/takeover-requests/${requestId}`, json('POST', { grant })),
};

export const teamApi = {
    mine: () => api<MyTeam[]>('/api/me/teams'),
    page: (handle: string) => api<TeamPage>(`/api/teams/${encodeURIComponent(handle)}`),
    create: (name: string, handle: string) => api<MyTeam>('/api/teams', json('POST', { name, handle })),
    rename: (team: string, name: string, handle: string) => api<MyTeam>(`/api/teams/${encodeURIComponent(team)}`, json('PATCH', { name, handle })),
    addMember: (team: string, nick: string, role: TeamRole) =>
        api<void>(`/api/teams/${encodeURIComponent(team)}/members`, json('POST', { nick, role })),
    setRole: (team: string, nick: string, role: TeamRole) =>
        api<void>(`/api/teams/${encodeURIComponent(team)}/members/${encodeURIComponent(nick)}`, json('PATCH', { role })),
    remove: (team: string, nick: string) =>
        api<void>(`/api/teams/${encodeURIComponent(team)}/members/${encodeURIComponent(nick)}`, { method: 'DELETE' }),
};

export const relayApi = {
    ask: (editionId: number, team: string, message: string) =>
        api<void>(`/api/editions/${editionId}/takeover-requests`, json('POST', { team, message })),
    start: (editionId: number, team: string, kind: 'human' | 'machine') =>
        api<{ editionId: number; novelSlug: string }>(`/api/editions/${editionId}/continue`, json('POST', { team, kind })),
};

export const ROLE_LABELS: Record<TeamRole, string> = { owner: 'власник', translator: 'перекладач', editor: 'редактор' };
