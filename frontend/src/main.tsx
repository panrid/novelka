import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from '@tanstack/react-router';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { ApiError } from './api/client';
import { createAppRouter } from './app/router';
import './styles/global.css';

const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            staleTime: 30_000,
            // Repeating a 4xx will not change the answer; network and server errors may pass.
            retry: (failures, error) => failures < 2 && !(error instanceof ApiError && error.status >= 400 && error.status < 500),
        },
    },
});

const router = createAppRouter(queryClient);

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <QueryClientProvider client={queryClient}>
            <RouterProvider router={router} />
        </QueryClientProvider>
    </StrictMode>,
);
