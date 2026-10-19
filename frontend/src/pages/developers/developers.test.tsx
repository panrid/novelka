import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderAt } from '../../test/render';
import { endpoints, example, scopes, typeName, type OpenApi } from './openapi';

const SPEC: OpenApi = {
    openapi: '3.1.0',
    info: { title: 'Новелка API', version: '1' },
    paths: {
        '/api/v1/novels/{slug}/translations/{team}/chapters': {
            get: {
                operationId: 'listChapters',
                summary: 'Глави перекладу',
                description: 'Опубліковані глави. Сторінки за ключем — `after` і `next`.',
                parameters: [
                    { name: 'slug', in: 'path', required: true, description: 'Адреса новели', schema: { type: 'string' } },
                    { name: 'order', in: 'query', required: false, schema: { type: 'string', enum: ['asc', 'desc'], default: 'asc' } },
                ],
                responses: { '200': { description: 'Готово', content: { 'application/json': { schema: { $ref: '#/components/schemas/PageChapterEntry' } } } } },
            },
        },
    },
    components: {
        schemas: {
            PageChapterEntry: {
                type: 'object', required: ['items', 'next'],
                properties: {
                    items: { type: 'array', items: { $ref: '#/components/schemas/ChapterEntry' } },
                    next: { type: ['string', 'null'], description: 'Ключ наступної сторінки', example: '20' },
                },
            },
            ChapterEntry: {
                type: 'object', required: ['number', 'title'],
                properties: {
                    number: { type: 'integer', format: 'int32', example: 12 },
                    title: { type: ['string', 'null'], example: 'Нічний ринок' },
                },
            },
        },
    },
};

afterEach(() => vi.unstubAllGlobals());

describe('the OpenAPI helpers', () => {
    it('lists operations and names types as TypeScript does', () => {
        expect(endpoints(SPEC).map(({ method, operation }) => `${method} ${operation.operationId}`)).toEqual(['get listChapters']);
        expect(typeName({ type: ['string', 'null'] })).toBe('string | null');
        expect(typeName({ type: 'array', items: { $ref: '#/components/schemas/ChapterEntry' } })).toBe('ChapterEntry[]');
        expect(typeName({ type: 'integer' })).toBe('number');
        expect(scopes({ security: [{ token: ['studio'] }] })).toEqual(['studio']);
        expect(scopes({})).toEqual([]);
    });

    it('builds an example answer from the examples of its fields', () => {
        expect(example(SPEC, { $ref: '#/components/schemas/PageChapterEntry' }))
            .toEqual({ items: [{ number: 12, title: 'Нічний ринок' }], next: '20' });
    });
});

describe('«Для розробників»', () => {
    it('explains tokens, pages and errors, and shows the reference from the server’s description', async () => {
        const { calls } = await renderAt('/developers', { 'GET /api/v1/openapi.json': { body: SPEC } });

        expect(await screen.findByRole('heading', { name: 'Для розробників', level: 1 })).toBeInTheDocument();
        expect(screen.getByRole('link', { name: '«Я → Налаштування → API»' })).toHaveAttribute('href', '/me/settings/api');
        for (const heading of ['Токен', 'Права', 'Обмеження частоти', 'Сторінки за ключем', 'Кеш і ETag', 'Помилки', 'Приклади']) {
            expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument();
        }
        expect(screen.getByText(/curl --get/)).toBeInTheDocument();
        expect(screen.getByText(/async function\* chapters/)).toBeInTheDocument();

        // The reference is a separate chunk: its first import may take a moment.
        const path = await screen.findByText('/api/v1/novels/{slug}/translations/{team}/chapters', {}, { timeout: 5000 });
        const operation = path.closest('details')!;
        expect(operation).toHaveTextContent('Глави перекладу');
        await userEvent.click(within(operation).getByText('Глави перекладу'));
        expect(within(operation).getByLabelText('Параметри')).toHaveTextContent('Можна: asc, desc');
        expect(within(operation).getByRole('link', { name: 'PageChapterEntry' })).toHaveAttribute('href', '#schema-PageChapterEntry');
        expect(within(operation).getByLabelText('Приклад відповіді')).toHaveTextContent('"title": "Нічний ринок"');
        expect(calls.some((call) => call.path === '/api/v1/openapi.json')).toBe(true);

        // The site's footer leads here from every page.
        expect(screen.getByRole('link', { name: 'Для розробників' })).toHaveAttribute('href', '/developers');
    });
});
