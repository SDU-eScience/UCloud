import {expect, test} from "@playwright/test";
import {Applications, Components, login, Runs, File, Drive} from "./shared";

test.beforeEach(async ({page}) => {
    login(page);
});

test("Run job with jobname, extend time, stop job, validate jobname in runs", async ({page}) => {
    test.setTimeout(240_000);
    await Applications.gotoApplications(page);
    await page.getByRole("button", {name: "Open application"}).click();
    await page.getByRole("textbox", {name: "Job name"}).click();
    const jobName = Runs.newJobName();
    await page.getByRole("textbox", {name: "Job name"}).fill(jobName);
    await Components.selectAvailableMachineType(page);
    await page.getByRole("button", {name: "+1"}).click();
    await page.getByRole("button", {name: "Submit"}).click();

    while (!await page.getByText("Time remaining: 01").isVisible()) {
        await page.waitForTimeout(1000);
    }

    await page.getByRole("button", {name: "+1"}).click();

    while (!await page.getByText("Time remaining: 02").isVisible()) {
        await page.waitForTimeout(1000);
    }

    await Components.clickConfirmationButton(page, "Stop application");

    // Note(Jonas): I would have thought that the `expect` below would be enough, but 
    while (!await page.getByText("Run application again").isVisible()) {
        await page.waitForTimeout(1000);
    }

    await expect(page.getByText("Run application again")).toHaveCount(1);
});

test("Favorite app, unfavorite app", async ({page}) => {
    // Note(Jonas): Stuff like finding the favorite icon is pretty difficult through selectors,
    // so maybe consider adding test ids? I don"t know what makes the most sense.
    throw Error("Not implemented");
});

test("Start app and stop app from runs page. Start it from runs page, testing parameter import", async ({page}) => {
    test.setTimeout(240_000);
    const jobName = Runs.newJobName();

    await Applications.gotoApplications(page);
    await page.getByRole("button", {name: "Open application"}).click();
    await page.getByRole("textbox", {name: "Job name"}).click();
    await page.getByRole("textbox", {name: "Job name"}).fill(jobName);
    await Components.selectAvailableMachineType(page);
    await page.getByRole("button", {name: "+1"}).click();
    await page.getByRole("button", {name: "Submit"}).click();

    while (!await page.getByText("Time remaining: 01").isVisible()) {
        await page.waitForTimeout(1000);
    }

    await Applications.gotoRuns(page);
    await page.locator(".row").nth(1).click();
    await Components.clickConfirmationButton(page, "Stop");
    await page.waitForTimeout(500);
    await Applications.actionByRowTitle(page, jobName, "dblclick");
    await expect(page.getByText("Your job has completed")).toHaveCount(1);

    await Applications.gotoRuns(page);

    await Applications.actionByRowTitle(page, jobName, "click");
    await page.getByText("Run application again").click();
    await page.waitForTimeout(500);
    await page.getByText("Submit").click();
    while (!await page.getByText("Time remaining: 01").isVisible()) {
        await page.waitForTimeout(1000);
    }

    await Components.clickConfirmationButton(page, "Stop application");
    // Note(Jonas): I would have thought that the `expect` below would be enough, but 
    while (!await page.getByText("Run application again").isVisible()) {
        await page.waitForTimeout(1000);
    }
    await expect(page.getByText("Run application again")).toHaveCount(1);

});

test("Mount folder with file in job, and cat inside contents", async ({page}) => {
    const driveName = Drive.newDriveName();
    await Drive.create(page, driveName);
    await Drive.openDrive(page, driveName);
    const folderName = File.newFolderName();
    await File.create(page, folderName);
    await File.open(page, folderName);
    const [uploadedFileName, contents] = ["UploadedFile.txt", "Am I not invisible???"];
    await File.uploadFiles(page, [{name: uploadedFileName, contents: contents}]);
    await Applications.gotoApplications(page);
    await page.getByRole("button", {name: "Open application"}).click();
    await Components.selectAvailableMachineType(page);

    await page.getByRole("button", {name: "Add folder"}).click();
    await page.getByRole("textbox", {name: "No directory selected"}).click();
    await page.getByRole("dialog").getByRole("button", {name: "Use"}).click();

    await page.getByRole("button", {name: "Submit"}).click();

    while (!await page.getByRole('button', {name: 'Open terminal'}).isVisible()) {
        await page.waitForTimeout(500);
    }

    const terminalPagePromise = page.waitForEvent('popup');

    await page.getByRole('button', {name: 'Open terminal'}).click();

    const terminalPage = await terminalPagePromise;

    await terminalPage.waitForTimeout(1000);
    await terminalPage.getByText("ucloud@").click();

    for (const key of "cat") {
        await terminalPage.keyboard.press(key);
    }
    
    terminalPage.keyboard.press("Space");

    for (const key of (folderName + "/" + uploadedFileName)) {
        await terminalPage.keyboard.press(key);    
    }

    await terminalPage.keyboard.press("Enter");

    await expect(terminalPage.getByText(contents)).toHaveCount(1);

});