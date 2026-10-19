import { ApiError } from '../api/client';
import { dropQueuedProgress, flushProgress, forgetQueuedProgress, queueProgress, queuedProgress, type QueuedProgress } from './progressQueue';

const entry = (over: Partial<QueuedProgress> = {}): QueuedProgress =>
    ({ account: 1, editionId: 7, chapterNumber: 12, position: 0.4, at: 1000, ...over });

describe('the offline reading place queue', () => {
    beforeEach(() => localStorage.clear());

    it('keeps one place per account and translation, the latest', () => {
        queueProgress(entry());
        queueProgress(entry({ chapterNumber: 13, position: 0.1, at: 2000 }));
        queueProgress(entry({ editionId: 8, at: 1500 }));
        queueProgress(entry({ account: 2, at: 1700 }));

        expect(queuedProgress()).toHaveLength(3);
        expect(queuedProgress().find((queued) => queued.account === 1 && queued.editionId === 7)).toMatchObject({ chapterNumber: 13, position: 0.1 });
    });

    it('sends only the account’s own places, oldest first, and forgets what was sent', async () => {
        queueProgress(entry({ editionId: 8, at: 3000 }));
        queueProgress(entry({ at: 1000 }));
        queueProgress(entry({ account: 2, at: 500 }));
        const sent: QueuedProgress[] = [];

        expect(await flushProgress(1, async (queued) => { sent.push(queued); })).toBe(2);

        expect(sent.map((queued) => queued.editionId)).toEqual([7, 8]);
        expect(queuedProgress()).toEqual([entry({ account: 2, at: 500 })]);
    });

    it('keeps the places while there is no network and tries no further', async () => {
        queueProgress(entry({ at: 1000 }));
        queueProgress(entry({ editionId: 8, at: 2000 }));
        const send = vi.fn().mockRejectedValue(new ApiError(0, 'Немає зʼєднання'));

        expect(await flushProgress(1, send)).toBe(0);

        expect(send).toHaveBeenCalledTimes(1);
        expect(queuedProgress()).toHaveLength(2);
    });

    it('drops a place the server refused and goes on with the rest', async () => {
        queueProgress(entry({ at: 1000 }));
        queueProgress(entry({ editionId: 8, at: 2000 }));
        const send = vi.fn()
            .mockRejectedValueOnce(new ApiError(404, 'Такого перекладу немає.'))
            .mockResolvedValueOnce(undefined);

        expect(await flushProgress(1, send)).toBe(1);

        expect(queuedProgress()).toEqual([]);
    });

    it('keeps a newer place queued while an older one was being sent', async () => {
        queueProgress(entry({ at: 1000 }));
        await flushProgress(1, async () => {
            queueProgress(entry({ chapterNumber: 14, at: 5000 }));
        });

        expect(queuedProgress()).toEqual([entry({ chapterNumber: 14, at: 5000 })]);
    });

    it('drops a translation’s place once a newer one reached the server, and all on sign-out', () => {
        queueProgress(entry());
        queueProgress(entry({ editionId: 8 }));

        dropQueuedProgress(1, 7);
        expect(queuedProgress().map((queued) => queued.editionId)).toEqual([8]);

        forgetQueuedProgress();
        expect(queuedProgress()).toEqual([]);
    });
});
