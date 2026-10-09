import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const OWNER = {
    id: 1, nick: 'panrid', email: 'p@example.com', emailVerified: true, role: 'owner', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};
const JOB = { id: 18, kind: 'translate', state: 'done', from: 21, to: 21, done: 1, quoteShah: 2, spentUsd: 0.05, spentShah: 1,
    current: null, error: null, createdAt: '2026-10-06T08:00:00Z', finishedAt: '2026-10-06T08:10:00Z', personal: false, chargedShah: 0 };
const at = '2026-10-06T08:01:00Z';

afterEach(() => vi.unstubAllGlobals());

describe('run journal', () => {
    it('tells what each step of a chapter did', async () => {
        await renderAt('/studio/3/translate/jobs/18', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/3': { body: { editionId: 3, novelSlug: 'medzhyk', title: 'Меджик', author: '', description: [], tags: [], kind: 'machine', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 20, ownNovel: false, teamHandle: 'panrid', teamName: 'panrid', role: 'owner' } },
            'GET /api/studio/editions/3/autotranslate/jobs/18/log': { body: { job: JOB, chapters: 1, page: 1, events: [
                { id: 1, chapter: 21, kind: 'start', payload: { chars: 5400, paragraphs: 270 }, at },
                { id: 2, chapter: 21, kind: 'analysis', payload: { part: 0, added: ['Ґраст', 'слюда'], linked: 0, title: 'Біржа' }, at },
                { id: 3, chapter: 21, kind: 'translated', payload: { part: 0, of: 2, lines: 40, glossary: ['Сіон', 'Ґраст'] }, at },
                { id: 4, chapter: 21, kind: 'retry', payload: { stage: 'translate', part: 1, reason: 'відповідь не у форматі JSON' }, at },
                { id: 5, chapter: 21, kind: 'proofread', payload: { part: 0, of: 2, changes: [{ id: 's3', before: 'Він пішла.', after: 'Він пішов.' }] }, at },
                { id: 6, chapter: 21, kind: 'published', payload: { label: '21', title: 'Біржа', paragraphs: 270 }, at },
            ] } },
        });
        expect(await screen.findByText(/до словника додано/)).toHaveTextContent('Ґраст, слюда');
        expect(screen.getByText(/Зі словника: Сіон, Ґраст/)).toBeInTheDocument();
        expect(screen.getByText(/Повтор \(переклад\): відповідь не у форматі JSON/)).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'що саме' }));
        expect(screen.getByText('Він пішла.').tagName).toBe('DEL');
        expect(screen.getByText('Він пішов.').tagName).toBe('INS');
        const published = screen.getByText(/Опубліковано: глава 21/);
        expect(within(published).getByRole('link', { name: 'Читати ›' })).toHaveAttribute('href', '/n/medzhyk/21?t=panrid&look=true');
    });

    it('shows a long run twenty chapters to a page, the last ones first unless asked otherwise', async () => {
        const EDITION = { editionId: 3, novelSlug: 'medzhyk', title: 'Меджик', author: '', description: [], tags: [], kind: 'machine', status: 'ongoing', adult: false, coverUrl: null, chapterCount: 150, ownNovel: false, teamHandle: 'panrid', teamName: 'panrid', role: 'owner' };
        const { calls } = await renderAt('/studio/3/translate/jobs/18', {
            'GET /api/me': { body: OWNER },
            'GET /api/studio/editions/3': { body: EDITION },
            'GET /api/studio/editions/3/autotranslate/jobs/18/log': { body: { job: { ...JOB, from: 100, to: 142, done: 42 }, chapters: 43, page: 1, events: [
                { id: 900, chapter: 142, kind: 'retry', payload: { stage: 'translate', part: 0, reason: 'модель витратила всю довжину відповіді на роздуми' }, at },
                { id: 880, chapter: 141, kind: 'published', payload: { label: '141', title: 'Нарада', paragraphs: 108 }, at },
            ] } },
        });
        const first = (await screen.findAllByText(/Глава \d+/))[0];
        expect(first).toHaveTextContent('Глава 142');
        expect(screen.getByText(/витратила всю довжину/)).toBeVisible();
        expect(screen.getByText('сторінка 1 з 3')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('radio', { name: 'Спершу перші' }));
        expect(calls.some((call) => call.path.endsWith('/jobs/18/log') && call.query.includes('order=asc'))).toBe(true);
        await userEvent.click(screen.getByRole('button', { name: 'Наступна →' }));
        expect(calls.some((call) => call.query.includes('page=2') && call.query.includes('order=asc'))).toBe(true);
    });
});
