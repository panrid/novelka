import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        strictPort: true,
        proxy: {
            // The API runs from `./gradlew :backend:bootRun` on 8080.
            '/api': 'http://127.0.0.1:8080',
            // Uploaded and drawn pictures are served by the backend too.
            '/media': 'http://127.0.0.1:8080',
        },
    },
    build: {
        outDir: 'dist',
        sourcemap: false,
    },
    test: {
        environment: 'jsdom',
        globals: true,
        setupFiles: ['./src/test-setup.ts'],
        css: false,
    },
});
