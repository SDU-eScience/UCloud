import {defineConfig, devices, Project} from '@playwright/test';
import {default as data} from "./tests/test_data.json" with {type: "json"};


const userSetup: Project = {
    name: "setup",
    testMatch: /users.setup\.ts/,
}

const chrome: Project = {
    name: 'chromium',
    use: {...devices['Desktop Chrome']},
    dependencies: ["setup"],
};

const firefox: Project = {
    name: 'firefox',
    use: {...devices['Desktop Firefox']},
    dependencies: ["setup"],
};

const webkit: Project = {
    name: 'webkit',
    use: {...devices['Desktop Safari']},
    dependencies: ["setup"],
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
        baseURL: data.location_origin,
    },


    /* Configure projects for major browsers */
    projects: process.env.CI ? [userSetup, chrome] : [userSetup, chrome, firefox, webkit]
});
