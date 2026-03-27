import {expect, test} from "@playwright/test";
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
    Project,
    testCtx,
    TestContexts,
} from "./shared";

test.beforeEach(async ({page}, testInfo) => {
    const doSkipInitialization = testInfo.titlePath.find((it) => [
        "disallow start from locked allocation",
        "Compute - check accounting",
    ].includes(it));
    if (doSkipInitialization) {
        await Admin.newLoggedInAdminPage(page);
        return;
    }
    const args = testCtx(testInfo.titlePath);
    await User.login(page, args.user);
    if (args.projectName) await Project.changeTo(page, args.projectName);
});

const {AppNames} = Applications;

TestContexts.map((ctx) => {
    test.describe(ctx, () => {
        test("Run job with jobname, extend time, stop job, validate jobname in runs", async ({page, }) => {
            test.setTimeout(240_000);
            await Applications.openAppBySearch(page, AppNames.TestApplication);
            const jobName = Runs.newJobName();

            await Runs.setJobTitle(page, jobName);
            await Components.selectAvailableMachineType(page);
            await Runs.extendTimeBy(page, 1);
            await Runs.submitAndWaitForRunning(page);
            await Runs.extendTimeBy(page, 1);

            await page.getByText("Time remaining: 02").isVisible();

            await Runs.terminateViewedRun(page);

            await page.getByText("Run application again").hover();
        });

        const AppNameThatIsExpectedToBePresentButWeWillNotRun = "Terminal";
        test("Favorite app, unfavorite app", async ({page}) => {
            await Applications.openApp(
                page,
                AppNameThatIsExpectedToBePresentButWeWillNotRun
            );
            await Applications.toggleFavorite(page);
            await Applications.toggleFavorite(page);
        });

        test("Compute - check job termination", async ({page}) => {
            test.setTimeout(300_000);
            const jobName = Runs.newJobName();

            await Applications.openAppBySearch(page, AppNames.TestApplication);
            await Runs.setJobTitle(page, jobName);
            await Components.selectAvailableMachineType(page);
            await Runs.extendTimeBy(page, 1);
            await Runs.submitAndWaitForRunning(page);
            await Runs.stopRun(page, jobName);
            await Applications.actionByRowTitle(page, jobName, "dblclick");
            await expect(page.getByText("Your job has completed")).toHaveCount(1);
            await Runs.runApplicationAgain(page, jobName);
            await Runs.terminateViewedRun(page);
        });

        test("terminal access", async ({page}) => {
            test.setTimeout(240_000);
            const driveName = Drive.newDriveNameOrMemberFiles(ctx);
            const folderName = File.newFolderName();
            const {uploadedFileName, contents} = {uploadedFileName: "UploadedFile.txt", contents: "Am I not invisible???"};
            const jobName = Runs.newJobName();

            if (ctx !== "Project User") await Drive.create(page, driveName);
            await Drive.openDrive(page, driveName);
            await File.create(page, folderName);
            await File.open(page, folderName);
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
            await Runs.terminateViewedRun(page);
            if (ctx !== "Project User") await Drive.delete(page, driveName);
        });

        test("Ensure 'New version available' button shows up and works.", async ({page}) => {
            await Applications.openApp(page, AppNameThatIsExpectedToBePresentButWeWillNotRun);
            const versionSelect = page.locator("div[class^=rich-select-trigger]").last();
            const newestVersion = await versionSelect.innerText();
            await versionSelect.click();
            await page.locator("div[class^=rich-select-result-wrapper] > div").last().click();
            await page.locator("div[class^='trigger-div']", {hasText: "New version available."}).isVisible();
            await page.locator("div[class^='trigger-div']", {hasText: "New version available."}).click();
            await page.locator("div[class^=rich-select-trigger]", {hasText: newestVersion}).isVisible();
        });

        test("Test application search", async ({page}) => {
            await Applications.goToApplications(page);
            await Applications.openAppBySearch(page, AppNames.TestApplication);
        });

        test("Compute - modules available, network connectivity", async ({page}) => {
            test.setTimeout(300_000);
            const term = await Applications.runAppAndOpenTerminalWithTerminalPage(page, "Terminal", 1);
            await Terminal.enterCmd(term, "ls ~/.local");
            await term.getByText("easybuild").waitFor();
            await Terminal.enterCmd(term, "curl -I https://www.google.com");
            await term.getByText("HTTP/2 200").waitFor();
            await term.close();
            await Runs.terminateViewedRun(page);
        });

        test.describe("Compute - check starting jobs works", () => {
            test("optional/mandatory parameters, logs", async ({page}) => {
                test.setTimeout(120_000);
                const BashScriptName = "init.sh";
                const BashScriptStringContent = "Visible from the terminal " + ((Math.random() * 100) | 0);
                const FancyBashScript = `
#!/usr/bin/env bash
echo "${BashScriptStringContent}"
`;
                const driveName = Drive.newDriveNameOrMemberFiles(ctx);
                if (ctx !== "Project User") await Drive.create(page, driveName);
                await Drive.openDrive(page, driveName);
                await File.uploadFiles(page, [{name: BashScriptName, contents: FancyBashScript},]);

                await Applications.goToApplications(page);
                await Applications.openAppBySearch(page, AppNames.TestApplication);
                // Optional parameter to be used
                await Runs.activateOptionalParameter(page, "Initialization script");
                await NetworkCalls.awaitResponse(page, "**/api/files/browse**", async () => {
                    await page.getByRole("textbox", {name: "No file selected"}).click();
                });
                await File.ensureDialogDriveActive(page, driveName);
                await page.getByRole("dialog").locator(".row", {hasText: BashScriptName}).getByRole("button", {name: "Use"}).click();

                await page.mouse.wheel(0, -5000);
                await Components.selectAvailableMachineType(page);
                await Runs.submitAndWaitForRunning(page);

                await page.mouse.wheel(0, 1000);
                await page.getByText(BashScriptStringContent).waitFor({state: "visible"});
                await NetworkCalls.awaitResponse(page, "**api/files/browse?path=**", async () => {
                    await Runs.terminateViewedRun(page);
                });
                await File.actionByRowTitle(page, "stdout-0.log", "dblclick");
                await page.getByText(BashScriptStringContent).waitFor();
            });

            test("Licenses - check license system works", async ({page}) => {
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
                await Runs.terminateViewedRun(page);
            });
        });

        test("multinode, connect to other jobs", async ({page}) => {
            test.setTimeout(120_000);
            const jobName = Runs.newJobName();
            await Applications.runAppAndOpenTerminal(page, AppNames.TestApplication, 2, jobName);
            // This isn't ideal, but this is the easiest way to get the job id
            const jobId = new URL(page.url()).pathname.split("/").at(-1) ?? "";

            const otherPage = await page.context().newPage();
            // Should redirect to dashboard, as this is already logged in.
            await User.toLoginPage(otherPage);
            await Applications.openAppBySearch(otherPage, AppNames.TestApplication);
            await Components.selectAvailableMachineType(otherPage);
            await otherPage.getByText("Connect to job").first().click();
            await otherPage.getByRole("textbox", {name: "Hostname"}).fill("foobar");
            await otherPage.getByPlaceholder("No selected run").click();
            await Components.useDialogBrowserItem(otherPage, jobName);
            await Runs.submitAndWaitForRunning(otherPage);
            await otherPage.getByText("Connected jobs (1)").click();
            await otherPage.getByText(jobId, {exact: true}).hover();
            await Runs.terminateViewedRun(otherPage);

            await Runs.terminateViewedRun(page);
            await page.getByText("stdout-0.log").hover();
            await page.getByText("stdout-1.log").hover();
        });

        test("disallow start from locked allocation", async ({page: adminPage, context}) => {
            test.setTimeout(240_000);
            const {userPage, user} =
                await User.createUserWithProjectAndAssignRole(adminPage, context, ctx, {"Core-hours requested": 5, "GB requested": 1});

            const jobName = Runs.newJobName();
            const term = await Applications.runAppAndOpenTerminalWithTerminalPage(userPage, AppNames.TestApplication, 1, jobName);
            await Terminal.createLargeFile(term);
            await Runs.terminateViewedRun(userPage);

            await userPage.reload();
            const isPersonalWorkspace = ctx === "Personal Workspace";
            const driveName = isPersonalWorkspace ? "Home" : Drive.memberFiles(user.username);

            await File.triggerStorageScan(userPage, driveName);
            await Runs.goToRuns(userPage);
            await Components.projectSwitcher(userPage, "hover");
            await Applications.actionByRowTitle(userPage, jobName, "click");
            await NetworkCalls.awaitResponse(userPage, "**/api/jobs/retrieve?id=**", async () => {
                await userPage.getByText("Run application again").click();
            });
            await userPage.getByText("No machine type selected").waitFor({state: "hidden"});
            await userPage.getByText("Submit").click();
            await userPage.getByText("You do not have enough storage credits.").hover();

            /* Reclaim resources and retry */
            // await Runs.goToRuns(newUserPage);
            // await NetworkCalls.awaitResponse(newUserPage, "**/api/files/browse**", async () => {
            //     await Rows.actionByRowTitle(newUserPage, jobName, "dblclick");
            // });
            // await NetworkCalls.awaitResponse(newUserPage, "**/api/files/trash", async () => {
            //     await File.actionByRowTitle(newUserPage, "example", "click");
            //     await newUserPage.keyboard.press("Delete");
            // });
            // await Drive.openDrive(newUserPage, "Home");
            // await File.open(newUserPage, "Trash");
            // await File.emptyTrash(newUserPage);
            // await runTerminalApp(newUserPage);
        });

        test("Compute - check accounting", async ({page: adminPage, context}) => {
            test.setTimeout(240_000);
            const {userPage, user} = await User.createUserWithProjectAndAssignRole(adminPage, context, ctx, {"Core-hours requested": 1, "GB requested": 1});

            await Accounting.goTo(userPage, "Allocations");
            await userPage.getByText("0 / 1 Core-hours (0%)", {exact: true}).first().waitFor();
            const jobName = Runs.newJobName();
            await Applications.runAppAndOpenTerminal(userPage, AppNames.TestApplication, 2, jobName);
            await userPage.waitForTimeout(90_000);
            await Runs.terminateViewedRun(userPage);

            await userPage.reload();
            const isPersonalWorkspace = ctx === "Personal Workspace";
            const driveName = isPersonalWorkspace ? "Home" : Drive.memberFiles(user.username);
            await File.triggerStorageScan(userPage, driveName);
            await Accounting.goTo(userPage, "Allocations");
            const text = await userPage.getByText("Core-hours").first().innerText();
            expect(parseInt(text.split("Core-hours")[1].trim().replaceAll(/%|\(|\)/g, ""))).toBeGreaterThan(0);
        });
    });
});
