import {expect, test, Page} from "@playwright/test";
import {Applications, Components, User, Runs, File, Drive, Terminal, NetworkCalls, Resources, Accounting, Admin} from "./shared";
import {default as data} from "./test_data.json" with {type: "json"};

test.beforeEach(async ({page}) => {
    await User.login(page, data.users.with_resources);
});

test("Run job with jobname, extend time, stop job, validate jobname in runs", async ({page}) => {
    test.setTimeout(240_000);
    await Applications.goToApplications(page);
    await page.getByRole("button", {name: "Open application"}).click();
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

const AppNameThatIsExpectedToBePresent = "Terminal";
test("Favorite app, unfavorite app", async ({page}) => {
    await Applications.openApp(page, AppNameThatIsExpectedToBePresent);
    await Applications.toggleFavorite(page);
    await Applications.toggleFavorite(page);
});

test("Upload bash-script, run job and add script as optional parameter, check for keyword in log while running and after job terminated", async ({page}) => {
    test.setTimeout(120_000)
    const BashScriptName = "init.sh";
    const BashScriptStringContent = "Visible from the terminal " + ((Math.random() * 100) | 0);
    const FancyBashScript = `
#!/usr/bin/env bash
echo "${BashScriptStringContent}"
`;
    const driveName = Drive.newDriveName();
    await Drive.create(page, driveName);
    await Drive.openDrive(page, driveName);
    await File.uploadFiles(page, [{name: BashScriptName, contents: FancyBashScript}]);

    await Applications.goToApplications(page);
    await Applications.searchFor(page, "coder");
    await page.getByRole('link', {name: "Coder", exact: false}).click();
    // Optional paramter to be used
    await page.getByRole("button", {name: "Use"}).first().click();
    await NetworkCalls.awaitResponse(page, "**/api/files/browse**", async () => {
        await page.getByRole("textbox", {name: "No file selected"}).click();
    });
    await File.ensureDialogDriveActive(page, driveName);
    await page.getByRole("dialog").locator(".row", {hasText: BashScriptName}).getByRole("button", {name: "Use"}).click();
    await Components.selectAvailableMachineType(page);
    await page.mouse.wheel(0, 1000);
    await Runs.submitAndWaitForRunning(page);
    await page.getByText(BashScriptStringContent).waitFor({state: "visible"});
    await NetworkCalls.awaitResponse(page, "**api/files/browse?path=**", async () => {
        await Runs.terminateViewedRun(page);
    });
    await File.actionByRowTitle(page, "stdout-0.log", "dblclick");
    await page.getByText(BashScriptStringContent).hover();
});

test("Create license, omit mandatory argument, then use license as argument for mandatory parameter, ", async ({page}) => {
    test.setTimeout(120_000);
    await NetworkCalls.awaitProducts(page, async () => {
        await Resources.goTo(page, "Licenses");
    })
    const licenseId = await Resources.Licenses.activateLicense(page);
    await Applications.openApp(page, "COMSOL");
    await page.getByRole("button", {name: "Submit"}).waitFor();
    await page.mouse.wheel(0, 1000);
    await page.getByPlaceholder("Select license server...").waitFor();

    // Click submit, don't expect a backend response
    await page.getByRole("button", {name: "Submit"}).click();
    // See that license is required
    await page.getByText("A value is missing for this mandatory field").hover();
    await page.getByPlaceholder("Select license server...").click();
    await page.getByRole("dialog").locator(".row", {hasText: licenseId.toString()}).getByRole("button", {name: "Use"}).click();
    await Components.selectAvailableMachineType(page);
    await page.getByRole("button", {name: "Submit"}).click();
    await page.waitForURL("**/jobs/properties/**");
});

test("Start app and stop app from runs page. Start it from runs page, testing parameter import", async ({page}) => {
    test.setTimeout(240_000);
    const jobName = Runs.newJobName();

    await Applications.goToApplications(page);
    await NetworkCalls.awaitProducts(page, async () => {
        await page.getByRole("button", {name: "Open application"}).click();
    });
    await Runs.setJobTitle(page, jobName);
    await Components.selectAvailableMachineType(page);
    await Runs.extendTimeBy(page, 1);
    await Runs.submitAndWaitForRunning(page);
    await Runs.stopRun(page, jobName);
    await Applications.actionByRowTitle(page, jobName, "dblclick");
    await expect(page.getByText("Your job has completed")).toHaveCount(1);
    await Runs.runApplicationAgain(page, jobName);
    await Runs.terminateViewedRun(page);
    await page.getByText("Run application again").waitFor({state: "visible"});
});

test("Mount folder with file in job, and cat inside contents", async ({page}) => {
    test.setTimeout(240_000);
    const driveName = Drive.newDriveName();
    const folderName = File.newFolderName();
    const {uploadedFileName, contents} = {uploadedFileName: "UploadedFile.txt", contents: "Am I not invisible???"};
    const jobName = Runs.newJobName();

    await Drive.create(page, driveName);
    await Drive.openDrive(page, driveName);
    await File.create(page, folderName);
    await File.open(page, folderName);
    await File.uploadFiles(page, [{name: uploadedFileName, contents: contents}]);
    await Applications.goToApplications(page);
    await page.getByRole("button", {name: "Open application"}).click();

    await Runs.setJobTitle(page, jobName)
    await Components.selectAvailableMachineType(page);
    await Runs.JobResources.addFolder(page, driveName, folderName);
    await Runs.submitAndWaitForRunning(page);

    const terminalPage = await Runs.openTerminal(page);

    await Terminal.enterCmd(terminalPage, `cat ${folderName}/${uploadedFileName}`)

    await expect(terminalPage.getByText(contents)).toHaveCount(1);

    await terminalPage.close();
    await Runs.stopRun(page, jobName);
    await Drive.delete(page, driveName);
});

test("Ensure 'New version available' button shows up and works.", async ({page}) => {
    await Applications.openApp(page, AppNameThatIsExpectedToBePresent);
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
    await Applications.searchFor(page, AppNameThatIsExpectedToBePresent);
    await page.locator("a[class^=app-card]").getByText(AppNameThatIsExpectedToBePresent).first().click();
    expect(page.url()).toContain("/create?app=");
});

test("Start application with multiple nodes, connect to job from other job and validate connection", async ({page}) => {
    test.setTimeout(240_000)
    async function openSparkApp(page: Page) {
        await Applications.goToApplications(page);
        await Applications.searchFor(page, multinodeAppSearchString);
        await page.locator("a[class^=app-card]").getByText(multinodeApp).first().click();

        await Components.selectAvailableMachineType(page);
        await page.getByPlaceholder("No directory selected").click();
        await File.ensureDialogDriveActive(page, driveName);
        await Components.useDialogBrowserItem(page, folderName)
    }

    const multinodeApp = "Spark Cluster";
    const multinodeAppSearchString = "Spark"; // Add 'Cluster' and it disappears. Weird! #5272
    const driveName = Drive.newDriveName();
    const folderName = File.newFolderName();

    await Drive.create(page, driveName);
    await Drive.openDrive(page, driveName);
    await File.create(page, folderName);
    await openSparkApp(page)
    await Runs.setNodeCount(page, 2);

    const jobName = Runs.newJobName();
    await Runs.setJobTitle(page, jobName);
    await Runs.submitAndWaitForRunning(page);
    // This isn't ideal, but this is the easiest way to get the job id
    const jobId = new URL(page.url()).pathname.split("/").at(-1) ?? "";

    const otherPage = await page.context().newPage();
    // Should redirect to dashboard, as this is already logged in.
    await User.toLoginPage(otherPage);
    await openSparkApp(otherPage);
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

test("Start terminal job, find mounted 'easybuild' modules mounted, use networking, terminate job", async ({page}) => {
    test.setTimeout(120_000);
    await Applications.openApp(page, "Terminal");
    await Components.selectAvailableMachineType(page);
    await Runs.submitAndWaitForRunning(page);
    const term = await Runs.openTerminal(page);
    await Terminal.enterCmd(term, "ls ~/.local");
    await term.getByText("easybuild").hover();
    await Terminal.enterCmd(term, "curl -I https://example.org");
    await term.getByText("HTTP/2 200").hover();
    await term.close();
    await Runs.terminateViewedRun(page);
});

test("Create new user without resources, fail to create drive, apply for resources, be granted resources, run terminal, create large file, trigger accounting, see creation now blocked", async ({browser}) => {
    const adminPage = await browser.newPage();
    await User.login(adminPage, Admin.AdminUser);
    const user = User.newUserCredentials();
    await User.create(adminPage, user);

    const newUserPage = await browser.newPage();
    await User.login(newUserPage, user, true);
    await newUserPage.getByText("Additional user information").waitFor();
    await newUserPage.keyboard.press("Escape");
    await Drive.create(newUserPage, "DriveToFail");
    await newUserPage.getByText("Failed to create new drive. Insufficient funds").hover();
    await newUserPage.keyboard.press("Escape");

    await Accounting.goTo(newUserPage, "Apply for resources");
    await newUserPage.getByText("select an existing project instead").click();
    await Accounting.GrantApplication.toggleGrantGiver(newUserPage, data.providers.k8s);
    await Accounting.GrantApplication.fillQuotaFields(newUserPage, [{field: "Core-hours requested", quota: 1000}, {field: "GB requested", quota: 1000}]);
    await Accounting.GrantApplication.fillApplicationTextFields(newUserPage, [{name: "Application", content: "Text description"}]);
    const id = await Accounting.GrantApplication.submit(newUserPage);

    throw Error("TODO")
});