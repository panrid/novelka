import { api } from '../api/client';
import type { NovelFacts, Span } from '../reading/api';

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
    /** Readers' suggestions waiting for the team. */
    pendingSuggestions: number;
    /** Glossary entries nobody checked yet. */
    newWords: number;
    /** The unfinished autotranslation run and the chapter it is at, if any. */
    jobState: 'queued' | 'running' | 'failed' | null; jobChapter: number | null;
    /** Chapters of the original the team has: pasted, or listed on the original's site. */
    originals?: number;
    /** ja, en, zh, ko — when known. */
    sourceLanguage?: string | null;
    /** The newest chapter, published or not: «Мої переклади» open it in the workspace. */
    lastChapter?: number | null;
};

export type Overview = {
    editionId: number; novelSlug: string; title: string; author: string; description: StudioBlock[]; tags: string[];
    kind: string; status: string; adult: boolean; coverUrl: string | null; chapterCount: number; ownNovel: boolean;
    teamHandle: string; teamName: string; role: TeamRole;
    /** Chapters of the original when the site reads it; null otherwise. */
    sourceChapters: number | null;
    pendingSuggestions: number; drafts: number; newWords: number; originalUrl: string | null;
    /** The day a paused translation means to go on (ISO date). */
    pausedUntil: string | null;
    /** syosetu, manual or original. */
    source: string;
    /** Chapters of the original the team pasted (етап 19). */
    originals: number;
    facts: NovelFacts;
    /** Readers may download the translation as EPUB. */
    downloadAllowed: boolean;
    /** public or private (етап 24.1). */
    visibility: 'public' | 'private';
};

export type InvitedReader = {
    nick: string;
    avatarUrl: string | null;
    grantedAt: string;
};

export interface DocumentSummary {
    id: number;
    slug: string;
    title: string;
    kind: string;
    version: number;
    updatedAt: string;
    updatedByNick: string | null;
    snippet: string;
}

export interface DocumentDetails {
    id: number;
    slug: string;
    title: string;
    kind: string;
    content: string;
    version: number;
    createdAt: string;
    updatedAt: string;
    updatedByNick: string | null;
    revisionsCount: number;
    links: string[];
    attachments: WorkspaceAttachment[];
}

export interface WorkspaceRevision {
    id: number;
    version: number;
    title: string;
    content: string;
    authorNick: string | null;
    createdAt: string;
    comment: string | null;
}

export interface WorkspaceAttachment {
    id: number;
    imageId: number;
    url: string;
    name: string;
    description: string | null;
    createdAt: string;
}

export interface WorkspaceLinkMatch {
    rawTarget: string;
    resolvedDocumentSlug: string | null;
    resolvedChapterNumber: number | null;
    resolved: boolean;
}

export interface WorkspaceImportDocResult {
    slug: string;
    title: string;
    kind: string;
    action: 'create' | 'update' | 'unchanged';
    version: number;
    links: WorkspaceLinkMatch[];
}

export interface WorkspaceImportReport {
    dryRun: boolean;
    created: number;
    updated: number;
    unchanged: number;
    attachmentsCreated: number;
    attachmentsUnchanged: number;
    documents: WorkspaceImportDocResult[];
    attachments: { name: string; imageId: number; documentSlug: string | null; action: string }[];
}

export interface WorkspaceExport {
    novel: string;
    team: string;
    exportedAt: string;
    documents: DocumentDetails[];
    attachments: WorkspaceAttachment[];
}

export type StudioChapter = {
    number: number; title: string; published: boolean; hasMyDraft: boolean; updatedAt: string; label: string | null;
    /** Readers' suggestions waiting. */
    pending: number;
};
export type ChapterFilter = '' | 'suggestions' | 'drafts' | 'unpublished';

export type EditorView = {
    number: number; title: string; blocks: StudioBlock[]; revisionId: number | null; published: boolean;
    draft: { title: string; blocks: StudioBlock[]; baseRevisionId: number | null; updatedAt: string } | null;
    role: TeamRole; mayAddPictures: boolean; previous: number | null; next: number | null; label: string | null;
    /** Translated from an original the team may look at next to the text. */
    hasOriginal: boolean;
};

/** The original a chapter was translated from; paragraph ids match the translation's. */
export type ChapterOriginal = { title: string; blocks: { id: string; type: string; text: string }[] };

/**
 * What «Як у книжці» marks beside each paragraph (етап 20, крок 3): the text it had when the team
 * checked it, the text the AI wrote, and paragraphs a reader's правка waits for.
 */
export type ParagraphMarks = {
    checked: { blockId: string; text: string }[];
    machine: { blockId: string; text: string }[];
    offers: string[];
};

export type RevisionInfo = {
    id: number; authorNick: string | null; origin: string; createdAt: string; blocksChanged: number; charsChanged: number;
    published: boolean;
    /** AI: «translate» or «proofread», the models of that stage and the run (етап 20, історія глави). */
    stage?: 'translate' | 'proofread' | null; model?: string | null; jobId?: number | null;
    /** Readers' правки: who proposed them (authorNick took them) and what they said. */
    proposers?: string[]; notes?: string[];
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

export type TrashedChapter = { number: number; label: string | null; title: string; deletedAt: string };
export type TrashedEdition = { editionId: number; title: string; teamName: string; deletedAt: string; erasedAt: string };

export const studioApi = {
    mine: () => api<MyEdition[]>('/api/studio'),
    create: (body: { kind: 'human' | 'original'; title: string; author: string; description: StudioBlock[]; tags: string[]; adult: boolean; team: string;
        /** «Маю право перекладати цей текст» was ticked (етап 19). */
        rightsConfirmed?: boolean;
        /** «Новий переклад»: the name in the original and the original's language (ja, en, zh, ko). */
        titleOriginal?: string; language?: string }) =>
        api<{ editionId: number; novelSlug: string }>('/api/studio/editions', json('POST', body)),
    overview: (id: number) => api<Overview>(edition(id)),
    update: (id: number, patch: Partial<{ title: string; author: string; description: StudioBlock[]; tags: string[]; status: string; adult: boolean; downloadAllowed: boolean;
        visibility: 'public' | 'private';
        pausedUntil: string; facts: Partial<{ titleOriginal: string; titleEnglish: string; altTitles: string[]; sourceStatus: string; sourceChapterCount: number }> }>) =>
        api<Overview>(edition(id), json('PATCH', patch)),
    /** The novel's address (/n/…); the old one keeps working. */
    setSlug: (id: number, slug: string) => api<{ slug: string }>(`${edition(id)}/slug`, json('PUT', { slug })),
    setCover: (id: number, imageId: number | null) => api<Overview>(`${edition(id)}/cover`, json('PUT', { imageId })),
    setVisibility: (id: number, visibility: 'public' | 'private') =>
        api<Overview>(`${edition(id)}/visibility`, json('PUT', { visibility })),
    readers: (id: number) => api<InvitedReader[]>(`${edition(id)}/readers`),
    addReader: (id: number, nick: string) => api<void>(`${edition(id)}/readers`, json('POST', { nick })),
    removeReader: (id: number, nick: string) =>
        api<void>(`${edition(id)}/readers/${encodeURIComponent(nick)}`, { method: 'DELETE' }),
    chapters: (id: number, filter: { filter?: ChapterFilter; order?: 'asc' | 'desc'; page: number }) =>
        api<{ items: StudioChapter[]; total: number; page: number; hasMore: boolean }>(
            `${edition(id)}/chapters?${new URLSearchParams({ page: String(filter.page), order: filter.order ?? 'desc', filter: filter.filter ?? '' })}`),
    /** To the bin for 30 days; the number stays a gap. */
    deleteChapter: (id: number, number: number) => api<void>(chapter(id, number), { method: 'DELETE' }),
    trashedChapters: (id: number) => api<TrashedChapter[]>(`${edition(id)}/chapters/trash`),
    restoreChapter: (id: number, number: number) => api<void>(`${chapter(id, number)}/restore`, json('POST', {})),
    /** «Видалити переклад»: a code to the email and Telegram first. */
    deleteCode: (id: number) => api<{ email: string | null; minutes: number }>(`${edition(id)}/delete-code`, json('POST', {})),
    deleteEdition: (id: number, code: string) => api<void>(`${edition(id)}/delete`, json('POST', { code })),
    trash: () => api<TrashedEdition[]>('/api/studio/trash'),
    restoreEdition: (id: number) => api<void>(`${edition(id)}/restore`, json('POST', {})),
    newChapter: (id: number) => api<{ number: number }>(`${edition(id)}/chapters`, json('POST', {})),
    editor: (id: number, number: number) => api<EditorView>(chapter(id, number)),
    original: (id: number, number: number) => api<ChapterOriginal>(`${chapter(id, number)}/original`),
    saveDraft: (id: number, number: number, body: { title: string; blocks: StudioBlock[]; baseRevisionId: number | null }) =>
        api<void>(`${chapter(id, number)}/draft`, json('PUT', body)),
    discardDraft: (id: number, number: number) => api<void>(`${chapter(id, number)}/draft`, { method: 'DELETE' }),
    publish: (id: number, number: number, body: { title: string; blocks: StudioBlock[]; baseRevisionId: number | null }) =>
        api<{ revisionId: number }>(`${chapter(id, number)}/publish`, json('POST', body)),
    paragraphs: (id: number, number: number) => api<ParagraphMarks>(`${chapter(id, number)}/paragraphs`),
    /** «Позначити перевіреним»: the paragraph as it reads now; an edit unchecks it. */
    checkParagraph: (id: number, number: number, blockId: string, text: string) =>
        api<void>(`${chapter(id, number)}/paragraphs/${encodeURIComponent(blockId)}/check`, json('PUT', { text })),
    uncheckParagraph: (id: number, number: number, blockId: string) =>
        api<void>(`${chapter(id, number)}/paragraphs/${encodeURIComponent(blockId)}/check`, { method: 'DELETE' }),
    setLabel: (id: number, number: number, label: string | null) => api<void>(`${chapter(id, number)}/label`, json('PUT', { label })),
    revisions: (id: number, number: number) => api<RevisionInfo[]>(`${chapter(id, number)}/revisions`),
    revision: (id: number, number: number, revisionId: number) => api<RevisionView>(`${chapter(id, number)}/revisions/${revisionId}`),
    /** «Повернути цю версію»: its text published anew; the later versions stay in the history. */
    restore: (id: number, number: number, revisionId: number) =>
        api<{ revisionId: number }>(`${chapter(id, number)}/revisions/${revisionId}/restore`, json('POST', {})),
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
        // A file keeps its name: «Мої картинки» show it, and «27-5.png» says where it goes.
        const name = file instanceof File && file.name ? file.name : kind === 'cover' ? 'cover.jpg' : 'picture.jpg';
        const request = upload('/api/media/images', file, name, { kind });
        return api<{ id: number; url: string }>(request.path, request.init);
    },
    imageFromUrl: (url: string) => api<{ id: number; url: string }>('/api/media/images/from-url', json('POST', { url, kind: 'illustration' })),
    takeoverRequests: (id: number) => api<TakeoverRequest[]>(`${edition(id)}/takeover-requests`),
    answerTakeover: (id: number, requestId: number, grant: boolean) =>
        api<void>(`${edition(id)}/takeover-requests/${requestId}`, json('POST', { grant })),
    workspaceDocuments: (id: number, kind?: string, q?: string) => {
        const params = new URLSearchParams();
        if (kind && kind !== 'all') params.set('kind', kind);
        if (q) params.set('q', q);
        const query = params.toString();
        return api<DocumentSummary[]>(`${edition(id)}/workspace/documents${query ? `?${query}` : ''}`);
    },
    workspaceDocument: (id: number, slug: string) =>
        api<DocumentDetails>(`${edition(id)}/workspace/documents/${encodeURIComponent(slug)}`),
    saveWorkspaceDocument: (id: number, slug: string, body: { title: string; kind: string; content: string; version?: number | null; comment?: string }) =>
        api<DocumentDetails>(`${edition(id)}/workspace/documents/${encodeURIComponent(slug)}`, json('PUT', body)),
    deleteWorkspaceDocument: (id: number, slug: string) =>
        api<void>(`${edition(id)}/workspace/documents/${encodeURIComponent(slug)}`, { method: 'DELETE' }),
    workspaceRevisions: (id: number, slug: string) =>
        api<WorkspaceRevision[]>(`${edition(id)}/workspace/documents/${encodeURIComponent(slug)}/revisions`),
    restoreWorkspaceRevision: (id: number, slug: string, revisionId: number) =>
        api<DocumentDetails>(`${edition(id)}/workspace/documents/${encodeURIComponent(slug)}/revisions/${revisionId}/restore`, json('POST', {})),
    workspaceAttachments: (id: number, documentSlug?: string) =>
        api<WorkspaceAttachment[]>(`${edition(id)}/workspace/attachments${documentSlug ? `?documentSlug=${encodeURIComponent(documentSlug)}` : ''}`),
    addWorkspaceAttachment: (id: number, body: { documentSlug?: string; imageId: number; name: string; description?: string }) =>
        api<WorkspaceAttachment>(`${edition(id)}/workspace/attachments`, json('POST', body)),
    deleteWorkspaceAttachment: (id: number, attachmentId: number) =>
        api<void>(`${edition(id)}/workspace/attachments/${attachmentId}`, { method: 'DELETE' }),
    importWorkspace: (id: number, body: {
        dryRun: boolean;
        documents?: { slug?: string; title: string; kind: string; content: string; comment?: string }[];
        attachments?: { imageId: number; name: string; description?: string; documentSlug?: string }[];
    }) => api<WorkspaceImportReport>(`${edition(id)}/workspace/import`, json('POST', body)),
    exportWorkspace: (id: number) => api<WorkspaceExport>(`${edition(id)}/workspace/export`),
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
    /** A team's own translation of a novel already here: from the start, or after another translation's last chapter. */
    own: (slug: string, team: string, after: number | null) =>
        api<{ editionId: number; novelSlug: string }>(`/api/novels/${encodeURIComponent(slug)}/own-translation`, json('POST', { team, after })),
};

export const ROLE_LABELS: Record<TeamRole, string> = { owner: 'власник', translator: 'перекладач', editor: 'редактор' };
