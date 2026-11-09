import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 90_000,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: 'http://localhost:4173', browserName: 'chromium', trace: 'retain-on-failure' },
  webServer: {
    command: 'npm run preview',
    url: 'http://localhost:4173',
    env: { PORT: '4173' },
    reuseExistingServer: false,
    timeout: 120_000,
  },
});
