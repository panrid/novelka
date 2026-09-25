import { api } from '../api/client';

export type Aspect = '3:4' | '16:9' | '1:1';
export type IllustrationPrice = { usd: number; shah: number; showShah: boolean; model: string };
export type Drawn = { imageId: number; url: string; costUsd: number; costShah: number };
export type IllustrationSettings = {
    model: string; microUsdPerImage: number; style: string; promptModel: string; promptInputPerMillion: number; promptOutputPerMillion: number;
};

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });
const base = (id: number) => `/api/studio/editions/${id}/illustrations`;

export const illustrationApi = {
    price: (editionId: number) => api<IllustrationPrice>(`${base(editionId)}/price`),
    describe: (editionId: number, fragment: string) => api<{ prompt: string }>(`${base(editionId)}/prompt`, json('POST', { fragment })),
    draw: (editionId: number, body: { prompt: string; fragment: string; aspect: Aspect; chapter: number }) =>
        api<Drawn>(base(editionId), json('POST', body)),
    settings: (days = 30) => api<{ settings: IllustrationSettings; spent: { pictures: number; usd: number; average: number } }>(
        `/api/studio/illustrations/settings?days=${days}`),
    saveSettings: (settings: IllustrationSettings) => api<void>('/api/studio/illustrations/settings', json('PUT', settings)),
};
