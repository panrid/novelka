import { api } from '../api/client';

/**
 * A novel someone wants translated (рішення 32): its page in Ukrainian, votes, and where it went.
 * chapters and site are null for a novel proposed by hand (no link, or a site the site cannot
 * read); automatic says the site reads the original, so autotranslation is there for whoever takes it.
 */
export type Proposal = {
    id: number; title: string; author: string; description: string[]; chapters: number | null; adult: boolean; site: string | null;
    proposedBy: string; createdAt: string; state: 'open' | 'taken'; votes: number; voted: boolean; mine: boolean;
    taken: { novelSlug: string; teamHandle: string } | null;
    link: string | null; comment: string; automatic: boolean;
};

export type ProposalPage = { items: Proposal[]; total: number; page: number; hasMore: boolean };
export type ProposalSort = 'votes' | 'new' | 'taken';
/** A link, or a name, or both; author, description and comment if the person likes. */
export type NewProposal = { url: string; title: string; author: string; description: string; comment: string };

const json = (method: string, body: unknown) => ({ method, body: JSON.stringify(body) });

export const proposalApi = {
    list: (sort: ProposalSort, page: number) => api<ProposalPage>(
        `/api/proposals?${new URLSearchParams(sort === 'taken' ? { state: 'taken', page: String(page) } : { sort, page: String(page) })}`),
    propose: (proposal: NewProposal) => api<{ id: number; created: boolean }>('/api/proposals', json('POST', proposal)),
    update: (id: number, change: { title: string; author: string; description: string; comment?: string }) =>
        api<void>(`/api/proposals/${id}`, json('PUT', change)),
    remove: (id: number) => api<void>(`/api/proposals/${id}`, { method: 'DELETE' }),
    vote: (id: number) => api<void>(`/api/proposals/${id}/vote`, json('POST', {})),
    unvote: (id: number) => api<void>(`/api/proposals/${id}/vote`, { method: 'DELETE' }),
    take: (id: number, team: string) => api<{ editionId: number; novelSlug: string }>(`/api/proposals/${id}/take`, json('POST', { team })),
};
