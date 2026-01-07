import {defineConfig, devices, Project} from '@playwright/test';


const chrome: Project = {
    name: 'chromium',
    use: {...devices['Desktop Chrome']},
};

const firefox: Project = {
    name: 'firefox',
    use: {...devices['Desktop Firefox']},
};

const webkit: Project = {
    name: 'webkit',
    use: {...devices['Desktop Safari']},
};

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
    testDir: './tests',
    fullyParallel: true,
    forbidOnly: !!process.env.CI,
    retries: 0,
    reporter: [
        ['line', {printSteps: true}],
        ["html", {
            open: "never",
            outputDir: "playwright-report"
        }],
        ["json", {outputFile: "playwright-report/results.json"}]
    ],

    use: {
        /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
        trace: 'on-first-retry',
        ignoreHTTPSErrors: true,
    },

    /* Configure projects for major browsers */
    projects: process.env.CI ? [chrome] : [chrome, firefox, webkit]
});
