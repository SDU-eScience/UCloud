import {expect, test, Page, BrowserContext} from "@playwright/test";
import {Applications, Components, User, Runs, File, Drive, Terminal, NetworkCalls, Resources, Accounting, Admin, Rows, Project, testCtx, TestContexts, ctxUser, Contexts} from "./shared";

test.beforeEach(async ({page}, testInfo) => {
    const args = testCtx(testInfo.titlePath);
    await User.login(page, args.user);
    if (args.projectName) await Project.changeTo(page, args.projectName);
});

const {AppNames} = Applications;

TestContexts.map(ctx => {
    test.describe(ctx, () => {
        test("Run job with jobname, extend time, stop job, validate jobname in runs", async ({page}) => {
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
            await Applications.openApp(page, AppNameThatIsExpectedToBePresentButWeWillNotRun);
            await Applications.toggleFavorite(page);
            await Applications.toggleFavorite(page);
        });

        test.describe("Compute - check job termination", () => {
            test("Start app and stop app from runs page. Start it from runs page, testing parameter import", async ({page}) => {
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
        })

        test("Mount folder with file in job, and cat inside contents", async ({page}) => {
            test.setTimeout(240_000);
            const driveName = Drive.newDriveNameOrMemberFiles(ctx);
            const folderName = File.newFolderName();
            const {uploadedFileName, contents} = {uploadedFileName: "UploadedFile.txt", contents: "Am I not invisible???"};
            const jobName = Runs.newJobName();

            if (ctx !== "Project User") await Drive.create(page, driveName);
            await Drive.openDrive(page, driveName);
            await File.create(page, folderName);
            await File.open(page, folderName);
            await File.uploadFiles(page, [{name: uploadedFileName, contents: contents}]);
            await Applications.openAppBySearch(page, AppNames.TestApplication);

            await Runs.setJobTitle(page, jobName)
            await Components.selectAvailableMachineType(page);
            await Runs.JobResources.addFolder(page, driveName, folderName);
            await Runs.submitAndWaitForRunning(page);

            const terminalPage = await Runs.openTerminal(page);

            await Terminal.enterCmd(terminalPage, `cat ${folderName}/${uploadedFileName}`)

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

        test("Start terminal job, find mounted 'easybuild' modules mounted, use networking, terminate job", async ({page}) => {
            test.setTimeout(300_000);
            const term = await runAppAndOpenTerminalWithTerminalPage(page, "Terminal", 1);
            await Terminal.enterCmd(term, "ls ~/.local");
            await term.getByText("easybuild").hover();
            await Terminal.enterCmd(term, "curl -I https://example.org");
            await term.getByText("HTTP/2 200").hover();
            await term.close();
            await Runs.terminateViewedRun(page);
        });

        test.describe("Compute - check starting jobs works", () => {
            test.describe("optional/mandatory parameters", () => {
                test("Upload bash-script, run job and add script as optional parameter, check for keyword in log while running and after job terminated", async ({page}) => {
                    test.setTimeout(120_000)
                    const BashScriptName = "init.sh";
                    const BashScriptStringContent = "Visible from the terminal " + ((Math.random() * 100) | 0);
                    const FancyBashScript = `
#!/usr/bin/env bash
echo "${BashScriptStringContent}"
`;
                    const driveName = Drive.newDriveNameOrMemberFiles(ctx);
                    if (ctx !== "Project User") await Drive.create(page, driveName);
                    await Drive.openDrive(page, driveName);
                    await File.uploadFiles(page, [{name: BashScriptName, contents: FancyBashScript}]);

                    await Applications.goToApplications(page);
                    await Applications.openAppBySearch(page, AppNames.TestApplication);
                    // Optional parameter to be used
                    await page.getByRole("button", {name: "Use"}).click()
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
                    await page.getByText(BashScriptStringContent).hover();
                });

                test("Create license, omit mandatory argument, then use license as argument for mandatory parameter", async ({page}) => {
                    test.setTimeout(240_000);
                    await Resources.goTo(page, "Licenses");
                    const licenseId = await Resources.Licenses.activateLicense(page);
                    await Applications.goToApplications(page);
                    await Applications.searchFor(page, AppNames.LicenseTestApplication);
                    await page.locator("a[class^=app-card]").getByText("License Test").first().click();
                    await Components.selectAvailableMachineType(page);
                    await page.getByRole("button", {name: "Submit"}).waitFor();
                    await page.mouse.wheel(0, 1000);
                    await page.getByPlaceholder("Select license server...").waitFor();

                    // Click submit, don't expect a backend response
                    await page.getByRole("button", {name: "Submit"}).click();
                    // See that license is required
                    await page.getByText("A value is missing for this mandatory field").first().hover();
                    await page.getByPlaceholder("Select license server...").click();
                    await page.getByRole("dialog").locator(".row", {hasText: licenseId.toString()}).getByRole("button", {name: "Use"}).click();

                    await Runs.submitAndWaitForRunning(page);
                    await Runs.terminateViewedRun(page);
                });
            });

            test.describe("multinode, connect to other jobs", () => {
                test("Start application with multiple nodes, connect to job from other job and validate connection", async ({page}) => {
                    test.setTimeout(240_000);
                    const jobName = Runs.newJobName();
                    await runAppAndOpenTerminal(page, AppNames.TestApplication, 2, jobName);
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
            });

            test.describe("disallow start from locked allocation", () => {
                test("Storage - Create new user without resources, apply for resources, be granted resources, run terminal, create large file, trigger storage accounting, see creation now blocked", async ({context}) => {
                    test.setTimeout(240_000);

                    const adminPage = await Admin.newLoggedInAdminPage(context);
                    const {userPage, user} = await createUserWithProjectAndAssignRole(adminPage, context, ctx, [5, 1]);

                    const jobName = Runs.newJobName();
                    const term = await runAppAndOpenTerminalWithTerminalPage(userPage, AppNames.TestApplication, 1, jobName);
                    await Terminal.createLargeFile(term);
                    await Runs.terminateViewedRun(userPage);

                    await userPage.reload();
                    const isPersonalWorkspace = ctx === "Personal Workspace"
                    const driveName = isPersonalWorkspace ? "Home" : Drive.memberFiles(user.username);

                    await File.triggerStorageScan(userPage, driveName);
                    await Runs.goToRuns(userPage);
                    await NetworkCalls.awaitResponse(userPage, "**/api/jobs/retrieve?id=**", async () => {
                        await Components.projectSwitcher(userPage, "hover");
                        await Applications.actionByRowTitle(userPage, jobName, "click");
                        await userPage.getByText("Run application again").click();
                    })
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
            });
        });


        test.describe("Compute - check accounting", () => {
            test("Create new user without resources, apply for resources, be granted resources, validate resources in 'Allocations', run terminal, trigger compute accounting, see increase in usage", async ({context}) => {
                test.setTimeout(240_000);

                const adminPage = await Admin.newLoggedInAdminPage(context);
                const {userPage, user} = await createUserWithProjectAndAssignRole(adminPage, context, ctx, [1, 1]);

                await Accounting.goTo(userPage, "Allocations");
                await userPage.getByText("0 / 1 Core-hours (0%)", {exact: true}).first().waitFor();
                const jobName = Runs.newJobName();
                await runAppAndOpenTerminal(userPage, AppNames.TestApplication, 2, jobName);
                await userPage.waitForTimeout(120_000);
                await Runs.terminateViewedRun(userPage);

                await userPage.reload();
                const isPersonalWorkspace = ctx === "Personal Workspace"
                const driveName = isPersonalWorkspace ? "Home" : Drive.memberFiles(user.username);

                await File.triggerStorageScan(userPage, driveName);
                await Accounting.goTo(userPage, "Allocations");
                const text = await userPage.getByText("Core-hours").first().innerText();
                expect(parseInt(text.split("Core-hours")[1].trim().replaceAll(/%|\(|\)/g, ""))).toBeGreaterThan(0);
            });
        });
    });
});

async function runAppAndOpenTerminal(page: Page, appName: string, nodeCount: number, jobName?: string): Promise<void> {
    if (appName === AppNames.TestApplication) {
        await Applications.openAppBySearch(page, appName);
    } else {
        await Applications.openApp(page, appName);
    }
    await Components.selectAvailableMachineType(page);
    if (jobName) await Runs.setJobTitle(page, jobName);
    if (nodeCount > 0) await Runs.setNodeCount(page, nodeCount);
    await Runs.submitAndWaitForRunning(page);
}

async function runAppAndOpenTerminalWithTerminalPage(page: Page, appName: string, nodeCount: number, jobName?: string): Promise<Page> {
    await runAppAndOpenTerminal(page, appName, nodeCount, jobName);
    return await Runs.openTerminal(page);
}

async function createUserWithProjectAndAssignRole(admin: Page, context: BrowserContext, ctx: Contexts, quotas: [number, number]): Promise<{userPage: Page; user: {username: string; password: string;}}> {
    const user = User.newUserCredentials();
    const userPage = await context.browser()?.newPage();
    if (!userPage) throw Error("Failed to create userpage");

    await User.create(admin, user);
    await User.login(userPage, user, true);

    switch (ctx) {
        case "Project Admin":
        case "Project PI":
        case "Project User": {
            const projectName = Project.newProjectName();
            await fillApplicationAndSubmit(admin, projectName, quotas);
            await Accounting.GrantApplication.approve(admin);
            await Components.goToDashboard(admin);
            await Project.changeTo(admin, projectName);
            await Project.inviteUsers(admin, [user.username]);

            await Project.acceptInvites([userPage], projectName);

            await Project.changeRoles(admin, user.username, ctx.split(" ")[1] as "User" | "Admin" | "PI");

            await userPage.reload();
            await Project.changeTo(userPage, projectName);
            break;
        }
        case "Personal Workspace": {
            const id = await fillApplicationAndSubmit(userPage, undefined, quotas);
            await Accounting.goTo(admin, "Grant applications");
            await Project.changeTo(admin, "Provider K8s");
            await admin.getByText("Show applications received").click();
            await Rows.actionByRowTitle(admin, `${id}: Personal workspace of ${user.username}`, "dblclick");
            await Accounting.GrantApplication.approve(admin);
            break;
        }
    }

    return {userPage, user};

    async function fillApplicationAndSubmit(page: Page, projectName: string | undefined, quotas: [number, number]): Promise<string> {
        await Accounting.goTo(page, "Apply for resources");
        if (!projectName) {
            await page.getByText("select an existing project instead").click();
        } else {
            await Accounting.GrantApplication.fillProjectName(page, projectName);
        }

        await Accounting.GrantApplication.toggleGrantGiver(page, "Provider K8s");
        await Accounting.GrantApplication.fillQuotaFields(page, [{field: "Core-hours requested", quota: quotas[0]}, {field: "GB requested", quota: quotas[1]}]);
        await Accounting.GrantApplication.fillDefaultApplicationTextFields(page);
        return await Accounting.GrantApplication.submit(page);
    }
}