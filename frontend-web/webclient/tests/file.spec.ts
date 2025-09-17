import {test, expect} from '@playwright/test';
import {Components, Drive, Folder, login, Rows} from "./shared";

const {dirname} = import.meta;

const Drives: Record<string, string> = {};

test.beforeEach(async ({page, userAgent}) => {
    const driveName = Drive.newDriveName();
    await login(page);
    await Drive.create(page, driveName);
    Drives[userAgent!] = driveName
});


test.afterEach(async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    if (driveName) await Drive.delete(page, driveName);
});

/// File operations

test('Change sensitivity (with available resources)', async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    await Drive.openDrive(page, driveName);

    const folderName = Folder.newFolderName();
    await Folder.create(page, folderName);
    await Components.clickRefreshAndWait(page);
    await Rows.actionByRowTitle(page, folderName, "click");
    await page.locator('div:nth-child(6)').first().click();
    await page.getByText('Change sensitivity').click();
    // TODO(Jonas): Ensure NO confidential is present (or ensure that specific one has? If they happen simultaniously, more than one could be)
    await page.locator('#sensitivityDialogValue').selectOption('CONFIDENTIAL');
    await page.getByRole('textbox', {name: 'Reason for sensitivity change'}).fill('Content');
    await page.getByRole('button', {name: 'Update'}).click();
    // TODO(Jonas): Ensure 1 confidential is present (or ensure that specific one has?)

    await expect(page.getByText("C", {exact: true})).toHaveCount(1);
});

test("View properties", async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    await Drive.openDrive(page, driveName);
    const folderName = Folder.newFolderName();
    await Folder.create(page, folderName);
    await Rows.actionByRowTitle(page, folderName, "click");
    await page.locator("div:nth-child(6)").first().click();
    await page.getByText("Properties").click();

    await expect(page.locator("b").filter({hasText: "Path"})).toHaveCount(1);
    await expect(page.locator("b").filter({hasText: "Product"})).toHaveCount(1);
    await expect(page.locator("b").filter({hasText: "Provider"})).toHaveCount(1);
    await expect(page.locator("b").filter({hasText: "Created at"})).toHaveCount(1);
    await expect(page.locator("b").filter({hasText: "Modified at"})).toHaveCount(1);
    await expect(page.locator("b").filter({hasText: "Accessed at"})).toHaveCount(1);
    await expect(page.locator("b").filter({hasText: "UID/GID"})).toHaveCount(1);
    await expect(page.locator("b").filter({hasText: "Unix mode"})).toHaveCount(1);
});

test("Just testing the row selector", async ({page, userAgent, ...r}) => {
    const driveName = Drives[userAgent!];
    await Drive.openDrive(page, driveName);
    for (let i = 0; i < 100; i++) {
        await Folder.create(page, "Folder" + i);
    }
    await Drive.actionByRowTitle(page, "Folder" + Math.floor(Math.random() * 100), "click");
});


test("Upload file", async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    const testFileName = "test_single_file.txt";
    const testFileContents = "Single test file content.";
    await Drive.openDrive(page, driveName);

    await Folder.uploadFiles(page, [{name: testFileName, contents: testFileContents}]);
    await Folder.actionByRowTitle(page, testFileName, "dblclick");
    await expect(page.getByText(testFileContents)).toHaveCount(1);
});

// setInputFiles doesn't allow folders
test.skip("Upload folder", async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    await Drive.openDrive(page, driveName);
    await page.waitForTimeout(200); // We need to wait for Redux to propagate the changes of the drive, for use with the upload.
    await page.getByText("Upload files").click();
    // Folders are not allowed, keeping this test for now.
    await page.locator("#fileUploadBrowse").setInputFiles(dirname + "/" + "upload_folder");
    await page.waitForTimeout(1000); // I don't know what's a better selector.
    await page.keyboard.press("Escape");
    await Components.clickRefreshAndWait(page);
});

test.skip("Upload files after running out of space (and again after cleaning up)", async ({page, userAgent}) => {
    throw Error("Not implemented")
});

test("Create single folder, delete single folder", async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    const folderName = Folder.newFolderName();
    await Drive.openDrive(page, driveName);
    await Folder.create(page, folderName);
    await expect(page.locator('div > span', {hasText: folderName})).toHaveCount(1);
    await Folder.delete(page, folderName);
    await expect(page.locator('div > span', {hasText: folderName})).toHaveCount(0);
});

test("Create multiple folders (use / in the name)", async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    const folderName1 = Folder.newFolderName();
    const folderName2 = Folder.newFolderName();
    const folderName3 = Folder.newFolderName();
    await Drive.openDrive(page, driveName);
    await Folder.create(page, folderName1 + "/" + folderName2 + "/" + folderName3);
    await Folder.actionByRowTitle(page, folderName1, "dblclick");
    await Folder.actionByRowTitle(page, folderName2, "dblclick");
    await Folder.actionByRowTitle(page, folderName3, "dblclick");
});

test("Rename", async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    const folderName = Folder.newFolderName();
    const newFolderName = Folder.newFolderName();
    await Drive.openDrive(page, driveName);
    await Folder.create(page, folderName);
    await Folder.rename(page, folderName, newFolderName)
    await page.getByText(newFolderName).dblclick();
    await expect(page.getByText('This folder is empty')).toHaveCount(1);
});

test("Move file", async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    const folderTarget = Folder.newFolderName();
    const uploadedFileName = "uploadedFile.txt";
    await Drive.openDrive(page, driveName);
    await Folder.create(page, folderTarget);
    await Folder.uploadFiles(page, [{name: uploadedFileName, contents: "Some content. Doesn't matter."}]);
    await Folder.moveFileTo(page, uploadedFileName, folderTarget);
    await Folder.actionByRowTitle(page, folderTarget, "dblclick");
    await expect(page.getByText(uploadedFileName)).toHaveCount(1);
});

test("Move folder", async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    const folderToMove = Folder.newFolderName();
    const folderTarget = Folder.newFolderName();
    await Drive.openDrive(page, driveName);
    await Folder.create(page, folderToMove);
    await Folder.create(page, folderTarget);
    await Folder.moveFileTo(page, folderToMove, folderTarget);
    await Folder.actionByRowTitle(page, folderTarget, "dblclick");

    await expect(page.getByText(folderToMove)).toHaveCount(1);
});

test.skip("Move folder to child (invalid op)", async ({page, userAgent}) => {
    throw Error("Not implemented")
});

test.skip("Copy file", async ({page, userAgent}) => {
    throw Error("Not implemented")
});

test.skip("Copy file to self (check renaming)", async ({page, userAgent}) => {
    throw Error("Not implemented")
});

test.skip("Copy folder", async ({page, userAgent}) => {
    throw Error("Not implemented")
});

test.skip("Move to trash", async ({page, userAgent}) => {
    throw Error("Not implemented")
});

test.skip("Empty trash", async ({page, userAgent}) => {
    throw Error("Not implemented")
});

test.skip("File search (use empty trash to trigger scan)", async ({page, userAgent}) => {
    throw Error("Not implemented")
});

test.skip("Start a large amount of heavy tasks and observe completion", async ({page, userAgent}) => {
    throw Error("Not implemented")
});