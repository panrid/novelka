import { createRootRoute, createRoute, createRouter } from '@tanstack/react-router';
import { ErrorPage, NotFoundPage } from '../pages/ErrorPages';
import { Placeholder } from '../pages/Placeholder';
import { Shell } from './Shell';

const rootRoute = createRootRoute({
    component: Shell,
    notFoundComponent: NotFoundPage,
    errorComponent: ErrorPage,
});

const page = <TPath extends string>(path: TPath, title: string, text: string) =>
    createRoute({
        getParentRoute: () => rootRoute,
        path,
        component: () => <Placeholder title={title} text={text} />,
        head: () => ({ meta: [{ title: path === '/' ? 'Новелка' : `${title} — Новелка` }] }),
    });

const routeTree = rootRoute.addChildren([
    page('/', 'Що почитати', 'Тут зʼявляться «Продовжити», популярні новели й нові глави.'),
    page('/catalog', 'Пошук', 'Тут можна буде шукати новели за назвою, автором і тегами.'),
    page('/library', 'Бібліотека', 'Тут будуть ваші списки: «Читаю», «В планах», «Прочитано».'),
    page('/inbox', 'Вхідні', 'Тут будуть сповіщення, повідомлення й загальний чат.'),
    page('/me', 'Я', 'Тут будуть профіль, Студія, налаштування й вихід.'),
]);

export function createAppRouter() {
    return createRouter({ routeTree, defaultPreload: 'intent', scrollRestoration: true });
}

export const router = createAppRouter();

declare module '@tanstack/react-router' {
    interface Register {
        router: typeof router;
    }
}
