export interface TaskPreset {
    operation: 'translate' | 'proofread' | 'resume';
    novelId: string;
    chapter: number;
    jobId?: string;
    force?: boolean;
    dictionarySearchLimit?: number;
}
