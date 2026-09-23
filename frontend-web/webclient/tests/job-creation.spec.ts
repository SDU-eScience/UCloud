import {expect, test} from "@playwright/test";
import {Applications, Components, Project, Runs, testCtx, User} from "./shared";
import {default as data} from "./test_data.json" with {type: "json"};

test.beforeEach(async ({page}, testInfo) => {
    if (data.login_cookie) {
        await page.context().addCookies([data.login_cookie]);
    }

    const args = testCtx(testInfo.titlePath);
    await User.loginDirect(page, args.user);
    if (args.projectName) await Project.changeTo(page, args.projectName);
});

test.describe("Project PI", () => {
    test("Create and start a job", async ({page}) => {
        test.setTimeout(180_000);
        await Applications.openAppBySearch(page, Applications.AppNames.TestApplication);

        await Runs.setJobTitle(page, Runs.newJobName());
        await Components.selectAvailableMachineType(page);
        await Runs.submitAndWaitForRunning(page);
        await Runs.terminateViewedRun(page);
    });

    test("Import provider parameters", async ({page}) => {
        await Applications.openAppBySearch(page, Applications.AppNames.TestApplication);

        await page.getByRole("button", {name: "Import"}).click();
        const fileChooser = page.waitForEvent("filechooser");
        await page.getByText("Upload JobParameters.json", {exact: true}).click();
        await (await fileChooser).setFiles({
            name: "JobParameters.json",
            mimeType: "application/json",
            buffer: Buffer.from(JSON.stringify({
                siteVersion: 3,
                request: {
                    application: {name: "test-app", version: "1"},
                    product: {id: "u1-standard", category: "u1-standard", provider: "k8s"},
                    replicas: 1,
                    parameters: {
                        ucMetricSampleRate: {type: "text", value: "5000ms"},
                    },
                    resources: [],
                },
            })),
        });

        await expect(page.getByRole("combobox", {name: "Job report sample rate"})).toHaveValue("5000ms");
        await expect(page.getByText("Corrupt parameter: ucMetricSampleRate")).toHaveCount(0);
    });
});
