import { queryOptions } from '@tanstack/react-query';
import { readingApi } from './api';

/** Shared by route loaders and pages, so both hit the same cache entry. */
export const novelQuery = (slug: string, team?: string) =>
    queryOptions({ queryKey: ['novel', slug, team ?? ''], queryFn: () => readingApi.novel(slug, team) });

export const chapterQuery = (slug: string, number: number, team?: string) =>
    queryOptions({ queryKey: ['chapter', slug, team ?? '', number], queryFn: () => readingApi.chapter(slug, number, team) });
