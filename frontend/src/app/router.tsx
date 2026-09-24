import type { QueryClient } from '@tanstack/react-query';
import { createRootRouteWithContext, createRoute, createRouter, redirect, type RouterHistory } from '@tanstack/react-router';
import { authApi } from '../auth/api';
import { meQuery } from '../auth/me';
import { LoginPage } from '../pages/auth/LoginPage';
import { RegisterPage } from '../pages/auth/RegisterPage';
import { ResetPage } from '../pages/auth/ResetPage';
import { TokenPage } from '../pages/auth/TokenPage';
import { ErrorPage, NotFoundPage } from '../pages/ErrorPages';
import { MePage } from '../pages/me/MePage';
import { PrivacyPage } from '../pages/me/PrivacyPage';
import { SettingsPage } from '../pages/me/SettingsPage';
import { WelcomePage } from '../pages/me/WelcomePage';
import { UserPage } from '../pages/people/UserPage';
import { Placeholder } from '../pages/Placeholder';
import { Shell } from './Shell';

export type RouterContext = { queryClient: QueryClient };

const rootRoute = createRootRouteWithContext<RouterContext>()({
    component: Shell,
    notFoundComponent: NotFoundPage,
    errorComponent: ErrorPage,
});

const title = (text: string) => () => ({ meta: [{ title: `${text} — Новелка` }] });

/** Pages for signed-in people send guests to «Вхід» and bring them back afterwards. */
async function requireSignedIn({ context, location }: { context: RouterContext; location: { href: string } }) {
    const me = await context.queryClient.ensureQueryData(meQuery);
    if (!me) {
        throw redirect({ to: '/login', search: { next: location.href } });
    }
}

const nextSearch = (search: Record<string, unknown>): { next?: string } =>
    typeof search.next === 'string' ? { next: search.next } : {};
const tokenSearch = (search: Record<string, unknown>): { token?: string } =>
    typeof search.token === 'string' ? { token: search.token } : {};

const placeholder = <TPath extends string>(path: TPath, heading: string, text: string) =>
    createRoute({
        getParentRoute: () => rootRoute,
        path,
        component: () => <Placeholder title={heading} text={text} />,
        head: () => ({ meta: [{ title: path === '/' ? 'Новелка' : `${heading} — Новелка` }] }),
    });

const routeTree = rootRoute.addChildren([
    placeholder('/', 'Що почитати', 'Тут зʼявляться «Продовжити», популярні новели й нові глави.'),
    placeholder('/catalog', 'Пошук', 'Тут можна буде шукати новели за назвою, автором і тегами.'),
    placeholder('/library', 'Бібліотека', 'Тут будуть ваші списки: «Читаю», «В планах», «Прочитано».'),
    placeholder('/inbox', 'Вхідні', 'Тут будуть сповіщення, повідомлення й загальний чат.'),
    createRoute({ getParentRoute: () => rootRoute, path: '/me', component: MePage, head: title('Я') }),
    createRoute({
        getParentRoute: () => rootRoute, path: '/me/settings', component: SettingsPage,
        beforeLoad: requireSignedIn, head: title('Налаштування'),
    }),
    createRoute({
        getParentRoute: () => rootRoute, path: '/me/settings/privacy', component: PrivacyPage,
        beforeLoad: requireSignedIn, head: title('Приватність'),
    }),
    createRoute({
        getParentRoute: () => rootRoute, path: '/welcome', component: WelcomePage,
        beforeLoad: requireSignedIn, head: title('Ласкаво просимо'),
    }),
    createRoute({ getParentRoute: () => rootRoute, path: '/u/$nick', component: UserPage, head: ({ params }) => ({ meta: [{ title: `${params.nick} — Новелка` }] }) }),
    createRoute({ getParentRoute: () => rootRoute, path: '/login', component: LoginPage, validateSearch: nextSearch, head: title('Вхід') }),
    createRoute({ getParentRoute: () => rootRoute, path: '/register', component: RegisterPage, validateSearch: nextSearch, head: title('Реєстрація') }),
    createRoute({ getParentRoute: () => rootRoute, path: '/reset', component: ResetPage, validateSearch: tokenSearch, head: title('Відновлення пароля') }),
    createRoute({
        getParentRoute: () => rootRoute, path: '/verify', validateSearch: tokenSearch, head: title('Підтвердження пошти'),
        component: () => <TokenPage title="Підтверджуємо пошту" action={authApi.verify} then="/welcome" />,
    }),
    createRoute({
        getParentRoute: () => rootRoute, path: '/confirm-email', validateSearch: tokenSearch, head: title('Нова пошта'),
        component: () => <TokenPage title="Підтверджуємо нову пошту" action={authApi.confirmEmail} then="/me/settings" />,
    }),
]);

export function createAppRouter(queryClient: QueryClient, history?: RouterHistory) {
    return createRouter({
        routeTree,
        context: { queryClient },
        defaultPreload: 'intent',
        scrollRestoration: true,
        ...(history ? { history } : {}),
    });
}

declare module '@tanstack/react-router' {
    interface Register {
        router: ReturnType<typeof createAppRouter>;
    }
}
