import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from '@tanstack/react-router';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { ApiError } from './api/client';
import { createAppRouter } from './app/router';
import { mutationCache, queryCache } from './app/mutationErrors';
import { watchClientErrors } from './app/clientErrors';
import { apply as applyAppearance } from './appearance/store';
import { meQuery } from './auth/me';
import { OfflineNotice } from './offline/OfflineNotice';
import { watchProgressQueue } from './offline/progressQueue';
import { registerServiceWorker } from './offline/register';
import { readingApi } from './reading/api';
import './styles/global.css';

applyAppearance();
watchClientErrors();

const queryClient = new QueryClient({
    mutationCache: mutationCache(),
    queryCache: queryCache(),
    defaultOptions: {
        queries: {
            staleTime: 30_000,
            // Ask even when the browser says it is offline: the service worker may have the answer kept.
            networkMode: 'offlineFirst',
            // Repeating a 4xx will not change the answer; network and server errors may pass on a retry.
            // Offline with nothing kept: say so at once instead of waiting for the network.
            retry: (failures, error) => failures < 2 && !(error instanceof ApiError && error.status >= 400 && error.status < 500)
                && !(error instanceof ApiError && error.status === 0 && navigator.onLine === false),
        },
    },
});

const router = createAppRouter(queryClient);

registerServiceWorker();
watchProgressQueue(queryClient, () => queryClient.getQueryData(meQuery.queryKey)?.id ?? null,
    (entry) => readingApi.saveProgress(entry.editionId, entry.chapterNumber, entry.position));

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <QueryClientProvider client={queryClient}>
            <RouterProvider router={router} />
            <OfflineNotice />
        </QueryClientProvider>
    </StrictMode>,
);
