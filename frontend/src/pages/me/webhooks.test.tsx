import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';

const ME = {
    id: 1, nick: 'mika', email: 'mika@example.com', emailVerified: true, role: 'reader', bio: '', avatarUrl: null,
    dmPolicy: 'everyone', showReading: true, adultConfirmed: false, showShah: true,
};
const TOKENS = { tokens: [], offered: ['read'], days: [7, 30, 90, 365] };
const EVENTS = [
    { code: 'chapter.published', team: false }, { code: 'run.finished', team: true }, { code: 'run.stopped', team: true },
    { code: 'suggestion.created', team: true }, { code: 'source.chapters', team: true },
];
const TRANSLATIONS = [{ editionId: 7, title: 'Мисливець на драконів', novel: 'myslyvets', team: 'mika', member: true }];
const ENDPOINT = {
    id: 3, url: 'https://hooks.example/novelka', events: ['chapter.published', 'run.stopped'], editionIds: [7], enabled: true,
    disabledAt: null, disabledReason: null, failures: 0, secretHint: 'whsec_Ab3d…', createdAt: '2026-10-18T10:00:00Z',
};
const FAILED = {
    id: 11, deliveryId: 'a1b2', event: 'chapter.published', state: 'failed', attempts: 9, status: 500, error: 'Сервер відповів 500; чекаємо 2xx.',
    durationMs: 120, createdAt: '2026-10-18T10:00:00Z', lastAttemptAt: '2026-10-19T08:00:00Z', nextAttemptAt: null,
};

afterEach(() => vi.unstubAllGlobals());

describe('«Налаштування → API → Вебхуки»', () => {
    it('adds a webhook for chosen events and translations and shows its secret once', async () => {
        const { calls } = await renderAt('/me/settings/api', {
            'GET /api/me': { body: ME },
            'GET /api/me/api-tokens': { body: TOKENS },
            'GET /api/me/webhooks': { body: { endpoints: [], events: EVENTS, translations: TRANSLATIONS } },
            'POST /api/me/webhooks': (body) => ({ status: 201, body: { endpoint: { ...ENDPOINT, ...(body as object) }, secret: 'whsec_SECRETvalue' } }),
        });
        expect(await screen.findByRole('heading', { name: 'Вебхуки' })).toBeInTheDocument();
        expect(await screen.findByText('Вебхуків ще немає.')).toBeInTheDocument();
        const form = screen.getByRole('form', { name: 'Новий вебхук' });

        await userEvent.type(within(form).getByLabelText(/Адреса/), 'https://hooks.example/novelka');
        await userEvent.click(within(form).getByRole('switch', { name: 'Запуск зупинився' }));
        await userEvent.click(within(form).getByRole('radio', { name: 'Вибрані' }));
        await userEvent.click(within(form).getByRole('switch', { name: 'Мисливець на драконів · $mika' }));
        await userEvent.click(within(form).getByRole('button', { name: 'Додати вебхук' }));

        await waitFor(() => expect(calls.find((call) => call.method === 'POST' && call.path === '/api/me/webhooks')?.body)
            .toEqual({ url: 'https://hooks.example/novelka', events: ['chapter.published', 'run.stopped'], editionIds: [7] }));
        expect(await screen.findByLabelText('Секрет вебхука')).toHaveTextContent('whsec_SECRETvalue');
        await userEvent.click(screen.getByRole('button', { name: 'Готово, скопіював' }));
        expect(screen.queryByText('whsec_SECRETvalue')).not.toBeInTheDocument();
    });

    it('sends a trial event, shows the journal and repeats a delivery', async () => {
        const { calls } = await renderAt('/me/settings/api', {
            'GET /api/me': { body: ME },
            'GET /api/me/api-tokens': { body: TOKENS },
            'GET /api/me/webhooks': { body: { endpoints: [ENDPOINT], events: EVENTS, translations: TRANSLATIONS } },
            'POST /api/me/webhooks/3/test': { body: { ...FAILED, id: 12, event: 'ping', state: 'delivered', status: 200, error: null, attempts: 1 } },
            'GET /api/me/webhooks/3/deliveries': { body: [FAILED] },
            'POST /api/me/webhooks/3/deliveries/11/retry': { body: { ...FAILED, state: 'delivered', status: 204, error: null, attempts: 1 } },
        });
        const row = within(await screen.findByRole('list', { name: 'Мої вебхуки' })).getByRole('listitem', { name: ENDPOINT.url });
        expect(row).toHaveTextContent('Нова глава опублікована, Запуск зупинився');
        expect(row).toHaveTextContent('Мисливець на драконів');
        expect(row).toHaveTextContent('whsec_Ab3d…');

        await userEvent.click(within(row).getByRole('button', { name: 'Надіслати пробну подію' }));
        expect(await within(row).findByText(/Пробну подію доставлено: відповідь 200/)).toBeInTheDocument();

        await userEvent.click(within(row).getByRole('button', { name: 'Журнал доставок' }));
        const journal = await within(row).findByRole('list', { name: 'Журнал доставок' });
        const delivery = within(journal).getByRole('listitem', { name: 'Доставка 11' });
        expect(delivery).toHaveTextContent('не доставлено');
        expect(delivery).toHaveTextContent('код 500');
        expect(delivery).toHaveTextContent('120 мс');
        expect(delivery).toHaveTextContent('спроба 9');
        expect(delivery).toHaveTextContent('Сервер відповів 500');
        await userEvent.click(within(delivery).getByRole('button', { name: 'Повторити' }));
        await waitFor(() => expect(calls.some((call) => call.method === 'POST' && call.path === '/api/me/webhooks/3/deliveries/11/retry')).toBe(true));
    });
});
