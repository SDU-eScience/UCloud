import {defineConfig, devices, Project} from '@playwright/test';
import {default as data} from "./tests/test_data.json" with {type: "json"};


const manuallyConfiguredUsers = process.env.UCLOUD_PLAYWRIGHT_USER_DATA !== undefined;
const preparingUsers = process.env.UCLOUD_PLAYWRIGHT_PREPARE_USERS === "1";
const useUserSetup = !manuallyConfiguredUsers || preparingUsers;

const userSetup: Project = {
    name: "setup",
    testMatch: /users.setup\.ts/,
}

const chrome: Project = {
    name: 'chromium',
    use: {...devices['Desktop Chrome']},
    dependencies: useUserSetup ? ["setup"] : [],
};

const firefox: Project = {
    name: 'firefox',
    use: {...devices['Desktop Firefox']},
    dependencies: useUserSetup ? ["setup"] : [],
};

const webkit: Project = {
    name: 'webkit',
    use: {...devices['Desktop Safari']},
    dependencies: useUserSetup ? ["setup"] : [],
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
        actionTimeout: 10_000,
        /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
        trace: 'on-first-retry',
        screenshot: 'only-on-failure',
        ignoreHTTPSErrors: true,
        baseURL: data.location_origin,
    },


    /* Configure projects for major browsers */
    projects: process.env.CI
        ? useUserSetup ? [userSetup, chrome] : [chrome]
        : useUserSetup ? [userSetup, chrome, firefox, webkit] : [chrome, firefox, webkit]
});
