import { api } from '../api/client';

/** Volumes and numbering of a translation (етап 15). */
export type VolumeKind = 'volume' | 'prologue' | 'side' | 'extra';
export type Volume = { firstNumber: number; title: string; kind: VolumeKind };
export type StructureChapter = { number: number; title: string; label: string | null; manual: boolean; published: boolean };
export type Numbering = 'continuous' | 'per_volume';
export type Structure = { numbering: Numbering; volumes: Volume[]; chapters: StructureChapter[] };
export type StructureChange = { numbering: Numbering; volumes: Volume[]; automatic: number[]; unnumbered: number[] };

export const VOLUME_KINDS: Record<VolumeKind, string> = { volume: 'Том', prologue: 'Пролог', side: 'Побічні історії', extra: 'Екстра' };

/** «Том 2. Подорож удвох», «Пролог»: what a reader sees above the volume's chapters. */
export function volumeName(volume: Pick<Volume, 'title' | 'kind'>, index?: number): string {
    if (volume.kind !== 'volume') return volume.title || VOLUME_KINDS[volume.kind];
    const prefix = index !== undefined ? `Том ${index}` : 'Том';
    return volume.title ? `${prefix}. ${volume.title}` : prefix;
}

/** Ordinary volumes counted from 1, the others skipped: the number in «Том 2». */
export function volumeIndexes(volumes: Volume[]): Map<number, number> {
    const out = new Map<number, number>();
    let index = 0;
    [...volumes].sort((a, b) => a.firstNumber - b.firstNumber).forEach((volume) => {
        if (volume.kind === 'volume') out.set(volume.firstNumber, ++index);
    });
    return out;
}

/** The volume a chapter is in: the last one starting at or before it. */
export function volumeOf<T extends { firstNumber: number }>(volumes: T[], number: number): T | undefined {
    let found: T | undefined;
    for (const volume of volumes) if (volume.firstNumber <= number && (!found || volume.firstNumber > found.firstNumber)) found = volume;
    return found;
}

const base = (id: number) => `/api/studio/editions/${id}/structure`;

export const structureApi = {
    get: (id: number) => api<Structure>(base(id)),
    preview: (id: number, change: StructureChange) => api<Record<string, string>>(`${base(id)}/preview`, { method: 'POST', body: JSON.stringify(change) }),
    save: (id: number, change: StructureChange) => api<Structure>(base(id), { method: 'PUT', body: JSON.stringify(change) }),
};
