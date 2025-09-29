import {expect, test} from "@playwright/test";
import {Applications, Components, login, Runs, File, Drive, Terminal} from "./shared";

test.beforeEach(async ({page}) => {
    login(page);
});

test("Run job with jobname, extend time, stop job, validate jobname in runs", async ({page}) => {
    test.setTimeout(240_000);
    await Applications.gotoApplications(page);
    await page.getByRole("button", {name: "Open application"}).click();
    const jobName = Runs.newJobName();
    await page.getByRole("textbox", {name: "Job name"}).click();
    await page.getByRole("textbox", {name: "Job name"}).fill(jobName);
    await Components.selectAvailableMachineType(page);
    await Runs.extendTimeBy(page, 1);
    await Runs.submitAndWaitForRunning(page);
    await Runs.extendTimeBy(page, 1);

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
    await Runs.setJobTitle(page, jobName);
    await Components.selectAvailableMachineType(page);
    await Runs.extendTimeBy(page, 1);
    await Runs.submitAndWaitForRunning(page);
    await Runs.stopRun(page, jobName);
    await Applications.actionByRowTitle(page, jobName, "dblclick");
    await expect(page.getByText("Your job has completed")).toHaveCount(1);
    await Runs.runApplicationAgain(page, jobName);
    await Components.clickConfirmationButton(page, "Stop application");
    // Note(Jonas): I would have thought that the `expect` below would be enough, but 
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
    await Applications.gotoApplications(page);
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