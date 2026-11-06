import {expect, Page} from "@playwright/test";
import {currentPath, test, trashByApi, walletLocked, walletUsage} from "./fixtures";
import {
    Applications,
    Components,
    User,
    Runs,
    File,
    Drive,
    Terminal,
    NetworkCalls,
    Resources,
    Accounting,
    Admin,
    TestContexts,
    Contexts,
    isProd,
    isDev,
    apiProductCategory,
    isByteStorage,
    driveIdByName,
    emptyTrashByApi,
    findFilePathByApi,
} from "./shared";
import {default as data} from "./test_data.json" with {type: "json"};
import {default as pAndP} from "./provider_and_products.json" with {type: "json"};
const PRODUCTS = pAndP.find(it => it.location_origin === data.location_origin)!.products_used_in_tests;

const {AppNames} = Applications;

function computeAccountingTest(ctx: Contexts): void {
    test("Compute - check accounting", async ({page: adminPage, context}) => {
        const AUTOGIFTED_RESOURCES = (isProd(data.location_origin) || isDev(data.location_origin)) && ctx === "Personal Workspace";

        test.setTimeout(240_000);

        const quotas: [string, number][] = [[PRODUCTS.compute, 1]];

        if (AUTOGIFTED_RESOURCES === false) {
            quotas.push([PRODUCTS.storage, 1]);
        }

        if (data["login_cookie"]) await adminPage.context().addCookies([data["login_cookie"]]);
        await Admin.newLoggedInAdminPage(adminPage);
        const {userPage} = await User.createUserWithProjectAndAssignRole(adminPage, context, ctx, quotas);

        await Accounting.goTo(userPage, "Allocations");
        if (AUTOGIFTED_RESOURCES) {
            if (isDev(data.location_origin)) {
                await userPage.getByText("0K / 1K Core-hours (0%)", {exact: true}).first().waitFor();
            } else if (isProd(data.location_origin)) {
                await userPage.getByText("0 / 101 Core-hours (0%)", {exact: true}).first().waitFor();
            }
        } else {
            await userPage.getByText("0 / 1 Core-hours (0%)", {exact: true}).first().waitFor();
        }
        const jobName = Runs.newJobName();
        const coreCount = AUTOGIFTED_RESOURCES ? 8 : 2;
        await Applications.runAppAndOpenTerminal(userPage, AppNames.TestApplication, coreCount, undefined, jobName);

        await expect.poll(async () => {
            return await walletUsage(userPage, PRODUCTS.compute);
        }, {timeout: 120_000, intervals: [1_000]}).toBeGreaterThan(0);

        await Runs.terminateViewedRun(userPage, {waitForUi: false});

        await userPage.reload();
        const percentage = await getPercentUsage(userPage, "Core-hours");
        expect(percentage).toBeGreaterThan(0);
    });
}

(["Project PI"] as Contexts[]).map((ctx) => {
    test.describe(ctx, () => {
        test("Run job with jobname, extend time, stop job, validate jobname in runs", async ({testPage: page}) => {
            test.setTimeout(300_000);
            await Applications.openAppBySearch(page, AppNames.TestApplication);
            const jobName = Runs.newJobName();

            await Runs.setJobTitle(page, jobName);
            await Components.selectAvailableMachineType(page);
            await Runs.extendTimeBy(page, 1);
            await Runs.submitAndWaitForRunning(page);
            await Runs.extendTimeBy(page, 1);

            await page.getByText("Time remaining: 02").isVisible();

            await Runs.stopRun(page, jobName);
            await Applications.actionByRowTitle(page, jobName, "dblclick");
            await expect(page.getByText("Your job has completed")).toHaveCount(1);
            await Runs.runApplicationAgain(page, jobName);
            await Runs.terminateViewedRun(page, {waitForUi: false});
        });

        test("Favorite app, unfavorite app", async ({testPage: page}) => {
            await Applications.openApp(page, AppNames.Terminal);
            await Applications.toggleFavorite(page);
            await Applications.toggleFavorite(page);
        });

        test("terminal access", async ({testPage: page, fileDrive}) => {
            test.setTimeout(240_000);
            const driveName = fileDrive.name;
            const folderName = File.newFolderName();
            const {uploadedFileName, contents} = {uploadedFileName: "UploadedFile.txt", contents: "Am I not invisible???"};
            const jobName = Runs.newJobName();

            await File.create(page, folderName);
            await File.open(page, folderName);
            const folderPath = currentPath(page);
            await File.uploadFiles(page, [{name: uploadedFileName, contents: contents},]);
            await Applications.openAppBySearch(page, AppNames.TestApplication);

            await Runs.setJobTitle(page, jobName);
            await Components.selectAvailableMachineType(page);
            await Runs.JobResources.addFolder(page, driveName, folderName);
            await Runs.submitAndWaitForRunning(page);

            const terminalPage = await Runs.openTerminal(page);

            await Terminal.enterCmd(terminalPage, `cat ${folderName}/${uploadedFileName}`);

            await expect(terminalPage.getByText(contents)).toHaveCount(1);

            await terminalPage.close();
            await Runs.terminateViewedRun(page, {waitForUi: false});
            if (!fileDrive.ephemeral) await trashByApi(page, folderPath);
        });

        test("Ensure 'New version available' button shows up and works.", async ({testPage: page}) => {
            await Applications.openApp(page, AppNames.Terminal);
            const versionSelect = page.locator("div[class^=rich-select-trigger]").nth(1);
            const newestVersion = await versionSelect.innerText();
            await versionSelect.click();
            await page.locator("div[class^=rich-select-result-wrapper] > div").last().click();
            await page.locator("div[class^='trigger-div']", {hasText: "New version available."}).isVisible();
            await page.locator("div[class^='trigger-div']", {hasText: "New version available."}).click();
            await page.locator("div[class^=rich-select-trigger]", {hasText: newestVersion}).isVisible();
        });

        test("Test application search", async ({testPage: page}) => {
            await Applications.goToApplications(page);
            await Applications.openAppBySearch(page, AppNames.TestApplication);
        });

        test("Compute - modules available, network connectivity", async ({testPage: page}) => {
            test.setTimeout(300_000);
            const term = await Applications.runAppAndOpenTerminalWithTerminalPage(page, AppNames.Terminal, 1);
            await Terminal.enterCmd(term, "ls ~/.local");
            await term.getByText("easybuild").waitFor();
            await Terminal.enterCmd(term, "curl -4 -I https://www.google.com");
            await term.getByText("HTTP/2 200").waitFor();
            await term.close();
            await Runs.terminateViewedRun(page, {waitForUi: false});
        });

        test.describe("Compute - check starting jobs works", () => {
            test("optional/mandatory parameters, logs", async ({testPage: page, fileDrive}) => {
                test.setTimeout(120_000);
                const pseudoRandomNumberString = Math.random().toString().slice(2, 7);
                const BashScriptName = "init" + pseudoRandomNumberString + ".sh";
                const BashScriptStringContent = "Visible from the terminal " + ((Math.random() * 100) | 0);
                const FancyBashScript = `
#!/usr/bin/env bash
echo "${BashScriptStringContent}"
`;
                const driveName = fileDrive.name;
                const scriptPath = `${fileDrive.id}/${BashScriptName}`;
                await File.uploadFiles(page, [{name: BashScriptName, contents: FancyBashScript},]);

                await Applications.goToApplications(page);
                await Applications.openAppBySearch(page, AppNames.TestApplication);
                // Optional parameter to be used
                await NetworkCalls.awaitResponse(page, "**/api/files/browse**", async () => {
                    await page.getByRole("textbox", {name: "Initialization script"}).click();
                });
                await File.ensureDialogDriveActive(page, driveName);
                await Components.useDialogBrowserItem(page, BashScriptName, "Use");

                await page.mouse.wheel(0, -5000);
                await Components.selectAvailableMachineType(page);
                await Runs.submitAndWaitForRunning(page);

                await page.mouse.wheel(0, 1000);
                await page.getByText(BashScriptStringContent).waitFor();
                await NetworkCalls.awaitResponse(page, "**api/files/browse?path=**", async () => {
                    await Runs.terminateViewedRun(page, {waitForUi: false});
                });
                await File.actionByRowTitle(page, "stdout-0.log", "dblclick");
                await page.getByText(BashScriptStringContent).waitFor();
                if (!fileDrive.ephemeral) await trashByApi(page, scriptPath);
            });
        });

        test("Licenses - check license system works", async ({testPage: page}) => {
            test.setTimeout(240_000);
            await Resources.goTo(page, "Licenses");
            const licenseId = await Resources.Licenses.activateLicense(page);
            await Applications.goToApplications(page);
            await Applications.searchFor(page, AppNames.LicenseTestApplication);
            await page.locator("a[class^=app-card]").getByText("License Test").first().click();

            await page.locator("div[class^=rich-select-trigger]").nth(1).waitFor();

            const versionSelect = page.locator("div[class^=rich-select-trigger]").last();
            if (await versionSelect.innerText() === "3") {
                await versionSelect.click();
                await page.locator("div[class^=rich-select-result-wrapper] > div", {hasText: "4"}).click();
            }

            await Components.selectAvailableMachineType(page);

            await page.getByRole("button", {name: "Submit"}).waitFor();
            await page.mouse.wheel(0, 1000);
            await page.getByPlaceholder("Select license server...").waitFor();

            // Click submit, don't expect a backend response
            await page.getByRole("button", {name: "Submit"}).click();
            // See that license is required
            await page.getByText("A value is missing for this mandatory field").first().waitFor();
            await page.getByPlaceholder("Select license server...").click();
            await page.getByRole("dialog").locator(".row", {hasText: licenseId.toString()}).getByRole("button", {name: "Use"}).click();

            await Runs.submitAndWaitForRunning(page);
            await Runs.terminateViewedRun(page, {waitForUi: false});

            await Resources.goTo(page, "Licenses");
            await Resources.actionByRowTitle(page, `${PRODUCTS.license} (${licenseId})`, "click");
            await NetworkCalls.awaitResponse(page, "**/api/licenses**", async () => {
                await Components.clickConfirmationButton(page, "Delete");
            });
        });

        test("multinode, connect to other jobs", async ({testPage: page}) => {
            test.setTimeout(180_000);


            // Create private network
            const networkName = Resources.PrivateNetworks.newName();
            const newSubdomainName = Resources.PrivateNetworks.newSubdomainName();
            await Resources.PrivateNetworks.createPrivateNetwork(page, networkName, newSubdomainName);

            const jobName = Runs.newJobName();
            const jobNetworkId1 = Resources.PrivateNetworks.newJobNetworkName();
            const term1 = await Applications.runAppAndOpenTerminalWithTerminalPage(page, AppNames.TestApplication, 2, async p => {
                await p.getByRole("textbox", {name: "Private network #1"}).click();
                await p.getByRole("dialog").locator(".row", {hasText: networkName}).getByRole("button", {name: "Use"}).click();
                await p.locator("[data-field-row]", {hasText: "Hostname"}).getByRole("textbox").fill(jobNetworkId1);
            }, jobName);


            const otherPage = await page.context().newPage();
            const jobNetworkId2 = Resources.PrivateNetworks.newJobNetworkName();
            await User.toLoginPage(otherPage); // Should redirect to dashboard, as this is already logged in.
            const term2 = await Applications.runAppAndOpenTerminalWithTerminalPage(otherPage, AppNames.TestApplication, 2, async p => {
                await p.getByRole("textbox", {name: "Private network #1"}).click();
                await p.getByRole("dialog").locator(".row", {hasText: networkName}).getByRole("button", {name: "Use"}).click();
                await p.locator("[data-field-row]", {hasText: "Hostname"}).getByRole("textbox").fill(jobNetworkId2);
            }, jobName);

            await Terminal.enterCmd(term1, `apt-get install -y busybox && echo "setup done!"`);
            await Terminal.enterCmd(term2, `apt-get install -y busybox && echo "setup done!"`);

            await term1.getByText("setup done!", {exact: true}).waitFor({timeout: 60_000});
            await term2.getByText("setup done!", {exact: true}).waitFor({timeout: 60_000});
            await Terminal.enterCmd(term1, `busybox ping invalid_address`);
            await term1.getByText("ping: bad address 'invalid_address'").waitFor();

            await Terminal.enterCmd(term1, `busybox ping ${jobNetworkId2}.${newSubdomainName}`);
            await Terminal.enterCmd(term2, `busybox ping ${jobNetworkId1}.${newSubdomainName}`);
            const responseText = "64 bytes from"
            await Promise.all([term1, term2].map(t => t.getByText(responseText).first().waitFor()));

            await Runs.terminateViewedRun(page, {waitForUi: false});
            await Runs.terminateViewedRun(otherPage, {waitForUi: false});
            await page.getByText("stdout-0.log").hover();
            await page.getByText("stdout-1.log").hover();

            await Resources.PrivateNetworks.delete(page, networkName);
        });

        computeAccountingTest(ctx);
    });
});

TestContexts.map((ctx) => {
    test.describe(ctx, () => {
        test("disallow start from locked allocation", async ({page: adminPage, context}) => {
            // Extremely slow as `fallocate` is not available. Run with local dev and production.
            if (isDev(data.location_origin) && ctx === "Personal Workspace") test.skip();
            test.setTimeout(90_000);

            const AUTOGIFTED_RESOURCES = (isProd(data.location_origin) || isDev(data.location_origin)) && ctx === "Personal Workspace";

            if (data["login_cookie"]) await adminPage.context().addCookies([data["login_cookie"]]);
            await Admin.newLoggedInAdminPage(adminPage);

            const storageCategory = await apiProductCategory(adminPage, PRODUCTS.storage);
            const smallStorage = isByteStorage(storageCategory);
            const storageQuotaGb = smallStorage ? 0.01 : 1;
            const fileBytes = AUTOGIFTED_RESOURCES ? 6_000_000_000 : (smallStorage ? 20_000_000 : 2_000_000_000);

            const quotas: [string, number][] = [[PRODUCTS.compute, 5]];
            if (!AUTOGIFTED_RESOURCES)
                quotas.push([PRODUCTS.storage, storageQuotaGb]);

            const {userPage, user} = await User.createUserWithProjectAndAssignRole(adminPage, context, ctx, quotas);
            await adminPage.close();

            const jobName = Runs.newJobName();
            const isPersonalWorkspace = ctx === "Personal Workspace";
            const driveName = isPersonalWorkspace ? "Home" : Drive.memberFiles(user.username);

            const term = await Applications.runAppAndOpenTerminalWithTerminalPage(userPage, AppNames.TestApplication, 1, undefined, jobName);

            await Terminal.createFileOfSize(term, fileBytes);
            await term.close();
            await Runs.terminateViewedRun(userPage, {waitForUi: false});

            await File.triggerStorageScan(userPage, driveName);
            await expect.poll(async () => await walletLocked(userPage, PRODUCTS.storage), {
                timeout: 30_000,
                intervals: [250],
            }).toBe(true);

            await Runs.goToRuns(userPage);
            await Components.projectSwitcher(userPage, "hover");
            await Applications.actionByRowTitle(userPage, jobName, "click");
            await NetworkCalls.awaitResponse(userPage, "**/api/jobs/retrieve?id=**", async () => {
                await userPage.getByText("Run again").click();
            });
            await userPage.getByText("No machine type selected").waitFor({state: "hidden"});
            await Runs.setJobTitle(userPage, "")
            await userPage.getByText("Submit").click();

            const insufficientStorage = userPage
                .getByText("You do not have enough storage credits.")
                .or(userPage.getByText("Insufficient funds for storage", {exact: false}));
            await expect(insufficientStorage.first()).toBeVisible({timeout: 30_000});

            /* Clean-up */
            const driveId = await driveIdByName(userPage, driveName);
            await trashByApi(userPage, await findFilePathByApi(userPage, driveId, "example"));
            await emptyTrashByApi(userPage, driveId);
        });

        if ((isDev(data.location_origin) || isProd(data.location_origin)) && ctx === "Personal Workspace") {
            computeAccountingTest(ctx);
        }
    });
});

async function getPercentUsage(page: Page, kind: "Core-hours"): Promise<number> {
    await Accounting.goTo(page, "Allocations");
    await page.getByText(kind).first().waitFor();
    return await page.evaluate(() => {
        const element = document.querySelector("div[style^='--percentage']");
        if (!element) return -1;
        const percentageString = element["style"].getPropertyValue("--percentage");
        return parseFloat(percentageString.replace("%", ""))
    });
}

/**
 * 
 * Simpler approach to 'Compute - check accounting'. Should work for personal workspace, others are more difficult, due to small total amount of core-hours
            test.setTimeout(150_000);
            const AUTOGIFTED_RESOURCES = isDev(data.location_origin) && ctx === "Personal Workspace";
            const initialPercentage = await getPercentUsage(page, "Core-hours");
            const jobName = Runs.newJobName();
            const coreCount = AUTOGIFTED_RESOURCES ? 8 : 2;
            await Applications.runAppAndOpenTerminal(page, AppNames.TestApplication, coreCount, undefined, jobName);

            await page.waitForTimeout(60_000);

            await Runs.terminateViewedRun(page, {waitForUi: false});

            await page.reload();
            const isPersonalWorkspace = ctx === "Personal Workspace";
            const args = testCtx(["", ctx]);
            const user = args.user;
            const driveName = isPersonalWorkspace ? "Home" : Drive.memberFiles(user.username);
            await File.triggerStorageScan(page, driveName);
            const percentage = await getPercentUsage(page, "Core-hours");
            expect(percentage).toBeGreaterThan(initialPercentage);
 * 
 */
