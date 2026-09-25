import type { QueryClient } from '@tanstack/react-query';
import { createRootRouteWithContext, createRoute, createRouter, notFound, redirect, type RouterHistory } from '@tanstack/react-router';
import { authApi } from '../auth/api';
import { meQuery } from '../auth/me';
import { LoginPage } from '../pages/auth/LoginPage';
import { RegisterPage } from '../pages/auth/RegisterPage';
import { ResetPage } from '../pages/auth/ResetPage';
import { TokenPage } from '../pages/auth/TokenPage';
import { ErrorPage, NotFoundPage } from '../pages/ErrorPages';
import { MePage } from '../pages/me/MePage';
import { MySuggestionsPage } from '../pages/me/MySuggestionsPage';
import { PrivacyPage } from '../pages/me/PrivacyPage';
import { SettingsPage } from '../pages/me/SettingsPage';
import { WelcomePage } from '../pages/me/WelcomePage';
import { UserPage } from '../pages/people/UserPage';
import { CatalogPage, validateCatalogSearch } from '../pages/reading/CatalogPage';
import { HomePage } from '../pages/reading/HomePage';
import { LibraryPage } from '../pages/reading/LibraryPage';
import { NovelPage } from '../pages/reading/NovelPage';
import { ReaderPage } from '../pages/reading/ReaderPage';
import { chapterQuery, novelQuery } from '../reading/queries';
import { Placeholder } from '../pages/Placeholder';
import { AboutPage } from '../pages/studio/AboutPage';
import { ChapterEditorPage } from '../pages/studio/ChapterEditorPage';
import { EditionPage } from '../pages/studio/EditionPage';
import { HistoryPage } from '../pages/studio/HistoryPage';
import { ImportPage } from '../pages/studio/ImportPage';
import { NewPublication } from '../pages/studio/NewPublication';
import { RelayPage } from '../pages/studio/RelayPage';
import { StudioHome } from '../pages/studio/StudioHome';
import { MyTeamsPage } from '../pages/team/MyTeamsPage';
import { TeamPage } from '../pages/team/TeamPage';
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
const teamSearch = (search: Record<string, unknown>): { t?: string } =>
    typeof search.t === 'string' && search.t ? { t: search.t } : {};
const LIST_NAMES = ['reading', 'planned', 'done', 'paused', 'dropped'] as const;
const listSearch = (search: Record<string, unknown>): { list?: (typeof LIST_NAMES)[number] } =>
    LIST_NAMES.includes(search.list as (typeof LIST_NAMES)[number]) ? { list: search.list as (typeof LIST_NAMES)[number] } : {};

/** Studio pages: signed-in only; team rights are checked by the API on every call. */
const studio = <TPath extends string>(path: TPath, component: () => React.ReactNode, heading: string) =>
    createRoute({ getParentRoute: () => rootRoute, path, component, beforeLoad: requireSignedIn, head: title(heading) });

const placeholder = <TPath extends string>(path: TPath, heading: string, text: string) =>
    createRoute({
        getParentRoute: () => rootRoute,
        path,
        component: () => <Placeholder title={heading} text={text} />,
        head: () => ({ meta: [{ title: path === '/' ? 'Новелка' : `${heading} — Новелка` }] }),
    });

const routeTree = rootRoute.addChildren([
    createRoute({ getParentRoute: () => rootRoute, path: '/', component: HomePage, head: () => ({ meta: [{ title: 'Новелка' }] }) }),
    createRoute({ getParentRoute: () => rootRoute, path: '/catalog', component: CatalogPage, validateSearch: validateCatalogSearch, head: title('Пошук') }),
    createRoute({ getParentRoute: () => rootRoute, path: '/library', component: LibraryPage, validateSearch: listSearch, head: title('Бібліотека') }),
    createRoute({
        getParentRoute: () => rootRoute, path: '/n/$slug', component: NovelPage, validateSearch: teamSearch,
        loaderDeps: ({ search }) => ({ t: search.t }),
        // Loads the novel before the page shows, so the tab title is the novel's name.
        // Errors are left to the page, which explains 18+ and missing novels itself.
        loader: async ({ context, params, deps }) => {
            try {
                const novel = await context.queryClient.ensureQueryData(novelQuery(params.slug, deps.t));
                return { title: novel.title };
            } catch {
                return { title: null };
            }
        },
        head: ({ loaderData }) => ({ meta: [{ title: loaderData?.title ? `${loaderData.title} — Новелка` : 'Новелка' }] }),
    }),
    createRoute({
        getParentRoute: () => rootRoute, path: '/n/$slug/$number', component: ReaderPage, validateSearch: teamSearch,
        beforeLoad: ({ params }) => {
            if (!/^[1-9]\d{0,5}$/.test(params.number)) throw notFound();
        },
        loaderDeps: ({ search }) => ({ t: search.t }),
        loader: async ({ context, params, deps }) => {
            try {
                const chapter = await context.queryClient.ensureQueryData(chapterQuery(params.slug, Number(params.number), deps.t));
                return { title: `${chapter.title} — ${chapter.novelTitle}` };
            } catch {
                return { title: null };
            }
        },
        head: ({ loaderData }) => ({ meta: [{ title: loaderData?.title ?? 'Новелка' }] }),
    }),
    placeholder('/inbox', 'Вхідні', 'Тут будуть сповіщення, повідомлення й загальний чат.'),
    createRoute({ getParentRoute: () => rootRoute, path: '/me', component: MePage, head: title('Я') }),
    studio('/me/suggestions', MySuggestionsPage, 'Мої правки'),
    studio('/studio', StudioHome, 'Студія'),
    studio('/studio/new', NewPublication, 'Нова публікація'),
    studio('/studio/teams', MyTeamsPage, 'Мої команди'),
    studio('/studio/$editionId', EditionPage, 'Студія'),
    studio('/studio/$editionId/about', AboutPage, 'Дані й обкладинка'),
    studio('/studio/$editionId/import', ImportPage, 'Глави з файлу'),
    studio('/studio/$editionId/relay', RelayPage, 'Естафета'),
    studio('/studio/$editionId/chapters/$number', ChapterEditorPage, 'Редактор'),
    studio('/studio/$editionId/chapters/$number/history', HistoryPage, 'Історія глави'),
    createRoute({ getParentRoute: () => rootRoute, path: '/team/$handle', component: TeamPage, head: ({ params }) => ({ meta: [{ title: `$${params.handle} — Новелка` }] }) }),
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
