import {expect, test} from "@playwright/test";
import {Applications, Components, User, Runs, File, Drive, Terminal} from "./shared";

test.beforeEach(async ({page}) => {
    await User.login(page);
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

    while (!await page.getByText("Time remaining: 02").isVisible()) {
        await page.waitForTimeout(200);
    }

    await Components.clickConfirmationButton(page, "Stop application");

    // Note(Jonas): I would have thought that the `expect` below would be enough, but 
    while (!await page.getByText("Run application again").isVisible()) {
        await page.waitForTimeout(1000);
    }

    await expect(page.getByText("Run application again")).toHaveCount(1);
});

const AppNameThatIsExpectedToBePresent = "Visual Studio Code";
test("Favorite app, unfavorite app", async ({page}) => {
    await Applications.openApp(page, AppNameThatIsExpectedToBePresent);
    await Applications.toggleFavorite(page);
    await Applications.toggleFavorite(page);
});

test("Start app and stop app from runs page. Start it from runs page, testing parameter import", async ({page}) => {
    test.setTimeout(240_000);
    const jobName = Runs.newJobName();

    await Applications.goToApplications(page);
    await page.getByRole("button", {name: "Open application"}).click();
    await Runs.setJobTitle(page, jobName);
    await Components.selectAvailableMachineType(page);
    await Runs.extendTimeBy(page, 1);
    await Runs.submitAndWaitForRunning(page);
    await Runs.stopRun(page, jobName);
    await Applications.actionByRowTitle(page, jobName, "dblclick");
    await expect(page.getByText("Your job has completed")).toHaveCount(1);
    await Runs.runApplicationAgain(page, jobName);
    await Components.clickConfirmationButton(page, "Stop application");
    // Note(Jonas): I would have thought that the `expect` below would be enough, but alas!
    while (!await page.getByText("Run application again").isVisible()) {
        await page.waitForTimeout(1000);
    }
    await expect(page.getByText("Run application again")).toHaveCount(1);

});

test("Mount folder with file in job, and cat inside contents", async ({page}) => {
    const driveName = Drive.newDriveName();
    const folderName = File.newFolderName();
    const [uploadedFileName, contents] = ["UploadedFile.txt", "Am I not invisible???"];
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
    await Runs.addFolderResource(page, driveName, folderName);
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
    await page.getByText("New version available.").click();
    await page.waitForTimeout(500);
    const latestVersionSelectContent = await versionSelect.innerText();
    expect(newestVersion).toMatch(latestVersionSelectContent);
});

test("Test application search", async ({page}) => {
    await Applications.goToApplications(page);
    await Applications.searchFor(page, AppNameThatIsExpectedToBePresent);
    await page.locator("a[class^=app-card]").getByText(AppNameThatIsExpectedToBePresent).first().click();
    expect(page.url()).toContain("/create?app=");
});