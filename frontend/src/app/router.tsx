import type { QueryClient } from '@tanstack/react-query';
import { createRootRouteWithContext, createRoute, createRouter, notFound, redirect, type RouterHistory } from '@tanstack/react-router';
import { authApi } from '../auth/api';
import { meQuery } from '../auth/me';
import { GoogleNewcomerPage } from '../pages/auth/GoogleNewcomerPage';
import { LoginPage } from '../pages/auth/LoginPage';
import { RegisterPage } from '../pages/auth/RegisterPage';
import { ResetPage } from '../pages/auth/ResetPage';
import { TokenPage } from '../pages/auth/TokenPage';
import { ErrorPage, NotFoundPage, PendingPage } from '../pages/ErrorPages';
import { MePage } from '../pages/me/MePage';
import { MySuggestionsPage } from '../pages/me/MySuggestionsPage';
import { PrivacyPage } from '../pages/me/PrivacyPage';
import { NotificationSettingsPage } from '../pages/me/NotificationSettingsPage';
import { ReaderAppearancePage, SiteAppearancePage } from '../pages/me/AppearancePages';
import { SettingsPage } from '../pages/me/SettingsPage';
import { WelcomePage } from '../pages/me/WelcomePage';
import { UserPage } from '../pages/people/UserPage';
import { CatalogPage, validateCatalogSearch } from '../pages/reading/CatalogPage';
import { HomePage } from '../pages/reading/HomePage';
import { ProposalsPage } from '../pages/proposals/ProposalsPage';
import { LibraryPage } from '../pages/reading/LibraryPage';
import { NovelPage } from '../pages/reading/NovelPage';
import { ProposeChapterPage } from '../pages/reading/ProposeChapterPage';
import { ReaderPage } from '../pages/reading/ReaderPage';
import { chapterQuery, novelQuery } from '../reading/queries';
import { AboutPage } from '../pages/studio/AboutPage';
import { ChapterEditorPage } from '../pages/studio/ChapterEditorPage';
import { EditionPage } from '../pages/studio/EditionPage';
import { ChaptersPage } from '../pages/studio/ChaptersPage';
import { SuggestionQueuePage } from '../pages/studio/SuggestionQueuePage';
import { EditionSettingsPage } from '../pages/studio/EditionSettingsPage';
import { HistoryPage } from '../pages/studio/HistoryPage';
import { ImportPage } from '../pages/studio/ImportPage';
import { NewPublication } from '../pages/studio/NewPublication';
import { AutotranslatePage } from '../pages/studio/AutotranslatePage';
import { JobLogPage } from '../pages/studio/JobLogPage';
import { GlossaryPage } from '../pages/studio/GlossaryPage';
import { ProcessesPage } from '../pages/studio/ProcessesPage';
import { TitlesPage } from '../pages/studio/TitlesPage';
import { WalletPage } from '../pages/me/WalletPage';
import { AdminHome, AuditPage, ModerationPage, SiteSettingsPage, UsersPage } from '../pages/admin/AdminPages';
import { AnalyticsPage } from '../pages/admin/AnalyticsPage';
import { ChatPage } from '../pages/inbox/ChatPage';
import { ConversationAboutPage } from '../pages/inbox/ConversationAboutPage';
import { ConversationPage } from '../pages/inbox/ConversationPage';
import { MessagesPage } from '../pages/inbox/MessagesPage';
import { NewGroupPage } from '../pages/inbox/NewGroupPage';
import { NotificationsPage } from '../pages/inbox/NotificationsPage';
import { RelayPage } from '../pages/studio/RelayPage';
import { StudioHome } from '../pages/studio/StudioHome';
import { StructurePage } from '../pages/studio/StructurePage';
import { MyTeamsPage } from '../pages/team/MyTeamsPage';
import { TeamPage } from '../pages/team/TeamPage';
import { Shell } from './Shell';
import { ShahsPage } from '../pages/me/ShahsPage';

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
/** The login page also shows what went wrong on the way back from Google. */
const loginSearch = (search: Record<string, unknown>): { next?: string; google_error?: string } => ({
    ...nextSearch(search),
    ...(typeof search.google_error === 'string' ? { google_error: search.google_error } : {}),
});
const tokenSearch = (search: Record<string, unknown>): { token?: string } =>
    typeof search.token === 'string' ? { token: search.token } : {};
const teamSearch = (search: Record<string, unknown>): { t?: string } =>
    typeof search.t === 'string' && search.t ? { t: search.t } : {};
/** ?find= highlights a word in the chapter (the glossary's «У тексті»). */
/** {@code look}: opened to look at something (a suggestion, a comment), not to read — the place stays. */
const readerSearch = (search: Record<string, unknown>): { t?: string; find?: string; look?: true } => ({
    ...teamSearch(search), ...(typeof search.find === 'string' && search.find.trim() ? { find: search.find } : {}),
    ...(search.look === true || search.look === 'true' || search.look === 1 || search.look === '1' ? { look: true as const } : {}),
});
const LIST_NAMES = ['reading', 'planned', 'done', 'paused', 'dropped'] as const;
const listSearch = (search: Record<string, unknown>): { list?: (typeof LIST_NAMES)[number]; page?: number } => ({
    ...(LIST_NAMES.includes(search.list as (typeof LIST_NAMES)[number]) ? { list: search.list as (typeof LIST_NAMES)[number] } : {}),
    ...(Number.isInteger(Number(search.page)) && Number(search.page) > 1 ? { page: Number(search.page) } : {}),
});

/** Studio pages: signed-in only; team rights are checked by the API on every call. */
const studio = <TPath extends string>(path: TPath, component: () => React.ReactNode, heading: string) =>
    createRoute({ getParentRoute: () => rootRoute, path, component, beforeLoad: requireSignedIn, head: title(heading) });

const routeTree = rootRoute.addChildren([
    createRoute({ getParentRoute: () => rootRoute, path: '/', component: HomePage, head: () => ({ meta: [{ title: 'Новелка' }] }) }),
    createRoute({ getParentRoute: () => rootRoute, path: '/catalog', component: CatalogPage, validateSearch: validateCatalogSearch, head: title('Каталог') }),
    createRoute({ getParentRoute: () => rootRoute, path: '/proposals', component: ProposalsPage, head: title('Що перекласти') }),
    createRoute({ getParentRoute: () => rootRoute, path: '/library', component: LibraryPage, validateSearch: listSearch, head: title('Бібліотека') }),
    createRoute({
        getParentRoute: () => rootRoute, path: '/n/$slug', component: NovelPage, validateSearch: teamSearch,
        loaderDeps: ({ search }) => ({ t: search.t }),
        // Loads the novel before the page shows, so the tab title is the novel's name.
        // Errors are left to the page, which explains 18+ and missing novels itself.
        loader: async ({ context, params, deps }) => {
            try {
                const novel = await context.queryClient.ensureQueryData(novelQuery(params.slug, deps.t));
                // The same title search engines get from the server (SearchPages): Google reads the one the app sets.
                const english = novel.facts?.titleEnglish;
                const shown = english && english.toLowerCase() !== novel.title.toLowerCase() ? `${novel.title} (${english})` : novel.title;
                return { title: `${shown} — читати українською безкоштовно` };
            } catch {
                return { title: null };
            }
        },
        head: ({ loaderData }) => ({ meta: [{ title: loaderData?.title ? `${loaderData.title} | Новелка` : 'Новелка' }] }),
    }),
    createRoute({
        getParentRoute: () => rootRoute, path: '/n/$slug/$number', component: ReaderPage, validateSearch: readerSearch,
        beforeLoad: ({ params }) => {
            if (!/^[1-9]\d{0,5}$/.test(params.number)) throw notFound();
        },
        loaderDeps: ({ search }) => ({ t: search.t }),
        loader: async ({ context, params, deps }) => {
            try {
                const chapter = await context.queryClient.ensureQueryData(chapterQuery(params.slug, Number(params.number), deps.t));
                return { title: `${chapter.title} — ${chapter.novelTitle} — читати безкоштовно | Новелка` };
            } catch {
                return { title: null };
            }
        },
        head: ({ loaderData }) => ({ meta: [{ title: loaderData?.title ?? 'Новелка' }] }),
    }),
    createRoute({
        getParentRoute: () => rootRoute, path: '/inbox', component: NotificationsPage, head: title('Вхідні'),
        // Guests have no notifications, but they may read the site chat.
        beforeLoad: async ({ context }) => {
            if (!(await context.queryClient.ensureQueryData(meQuery))) throw redirect({ to: '/inbox/chat' });
        },
    }),
    studio('/inbox/messages', MessagesPage, 'Повідомлення'),
    studio('/inbox/messages/new', NewGroupPage, 'Нова група'),
    studio('/inbox/messages/$id', ConversationPage, 'Розмова'),
    studio('/inbox/messages/$id/about', ConversationAboutPage, 'Про розмову'),
    studio('/admin', AdminHome, 'Адміністрування'),
    studio('/admin/moderation', ModerationPage, 'Модерація'),
    studio('/admin/users', UsersPage, 'Користувачі й ролі'),
    studio('/admin/settings', SiteSettingsPage, 'Налаштування сайту'),
    studio('/admin/audit', AuditPage, 'Журнал дій'),
    studio('/admin/analytics', AnalyticsPage, 'Аналітика'),
    createRoute({ getParentRoute: () => rootRoute, path: '/inbox/chat', component: ChatPage, head: title('Чат') }),
    createRoute({ getParentRoute: () => rootRoute, path: '/me', component: MePage, head: title('Я') }),
    studio('/me/suggestions', MySuggestionsPage, 'Мої правки'),
    createRoute({
        getParentRoute: () => rootRoute, path: '/n/$slug/$number/propose', component: ProposeChapterPage,
        validateSearch: teamSearch, beforeLoad: requireSignedIn, head: title('Правка глави'),
    }),
    studio('/studio', StudioHome, 'Студія'),
    studio('/studio/new', NewPublication, 'Нова публікація'),
    studio('/studio/teams', MyTeamsPage, 'Мої команди'),
    studio('/studio/$editionId', EditionPage, 'Студія'),
    studio('/studio/$editionId/chapters', ChaptersPage, 'Глави'),
    studio('/studio/$editionId/suggestions', SuggestionQueuePage, 'Правки'),
    studio('/studio/$editionId/settings', EditionSettingsPage, 'Налаштування перекладу'),
    studio('/studio/$editionId/about', AboutPage, 'Дані й обкладинка'),
    studio('/studio/$editionId/import', ImportPage, 'Глави з файлу'),
    studio('/studio/$editionId/relay', RelayPage, 'Естафета'),
    studio('/studio/$editionId/structure', StructurePage, 'Структура й томи'),
    studio('/studio/$editionId/translate', AutotranslatePage, 'Автопереклад'),
    studio('/studio/$editionId/translate/jobs/$jobId', JobLogPage, 'Журнал запуску'),
    studio('/studio/$editionId/glossary', GlossaryPage, 'Словник'),
    studio('/studio/$editionId/titles', TitlesPage, 'Назви глав'),
    studio('/studio/processes', ProcessesPage, 'Процеси'),
    studio('/me/wallet', WalletPage, 'Шаги'),
    studio('/me/shahs', ShahsPage, 'Мої шаги'),
    studio('/studio/$editionId/chapters/$number', ChapterEditorPage, 'Редактор'),
    studio('/studio/$editionId/chapters/$number/history', HistoryPage, 'Історія глави'),
    createRoute({ getParentRoute: () => rootRoute, path: '/team/$handle', component: TeamPage, head: ({ params }) => ({ meta: [{ title: `$${params.handle} — Новелка` }] }) }),
    createRoute({
        getParentRoute: () => rootRoute, path: '/me/settings', component: SettingsPage,
        beforeLoad: requireSignedIn, head: title('Налаштування'),
    }),
    // The look of the site and of the reader: guests choose too, kept in their browser.
    createRoute({ getParentRoute: () => rootRoute, path: '/me/settings/appearance', component: SiteAppearancePage, head: title('Вигляд') }),
    createRoute({ getParentRoute: () => rootRoute, path: '/me/settings/reader', component: ReaderAppearancePage, head: title('Вигляд читалки') }),
    createRoute({
        getParentRoute: () => rootRoute, path: '/me/settings/notifications', component: NotificationSettingsPage,
        beforeLoad: requireSignedIn, head: title('Сповіщення'),
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
    createRoute({ getParentRoute: () => rootRoute, path: '/login', component: LoginPage, validateSearch: loginSearch, head: title('Вхід') }),
    createRoute({ getParentRoute: () => rootRoute, path: '/login/google', component: GoogleNewcomerPage, head: title('Вхід через Google') }),
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
        // Shown when a page's data takes longer than a moment; the frame stays drawn around it.
        defaultPendingComponent: PendingPage,
        defaultPendingMs: 400,
        scrollRestoration: true,
        ...(history ? { history } : {}),
    });
}

declare module '@tanstack/react-router' {
    interface Register {
        router: ReturnType<typeof createAppRouter>;
    }
}
