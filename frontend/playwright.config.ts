import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
    testDir: './tests',
    fullyParallel: true,
    workers: 2,
    use: { baseURL: 'http://127.0.0.1:4173', trace: 'retain-on-failure' },
    projects: [
        { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
        { name: 'safari', use: { ...devices['Desktop Safari'] } },
        { name: 'mobile', use: { ...devices['iPhone 13'], defaultBrowserType: 'chromium' } },
    ],
    webServer: {
        command: 'npm run preview',
        url: 'http://127.0.0.1:4173',
        reuseExistingServer: false,
    },
});
