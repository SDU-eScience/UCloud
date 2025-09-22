import {test, expect} from '@playwright/test';
import {Components, Drive, Folder, login, Rows} from "./shared";

const {dirname} = import.meta;

const Drives: Record<string, string> = {};

test.beforeEach(async ({page, userAgent}) => {
    const driveName = Drive.newDriveName();
    await login(page);
    await Drive.create(page, driveName);
    Drives[userAgent!] = driveName;
    await Drive.openDrive(page, driveName);
});

test.afterEach(async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    if (driveName) await Drive.delete(page, driveName);
});

/// File operations

// Depends on an update to `dev` (or run it with your own up to date backend)
test.skip('Change sensitivity (with available resources)', async ({page}) => {
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

test("View properties", async ({page}) => {
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

test("Stress testing the row selector", async ({page}) => {
    for (let i = 0; i < 100; i++) {
        await Folder.create(page, "Folder" + i);
    }
    await Folder.actionByRowTitle(page, "Folder0", "click");
    await Folder.actionByRowTitle(page, "Folder99", "click");
});


test("Upload file", async ({page}) => {
    const testFileName = "test_single_file.txt";
    const testFileContents = "Single test file content.";
    await Folder.uploadFiles(page, [{name: testFileName, contents: testFileContents}]);
    await Folder.actionByRowTitle(page, testFileName, "dblclick");
    await expect(page.getByText(testFileContents)).toHaveCount(1);
});

// setInputFiles doesn't allow folders
test.skip("Upload folder", async ({page}) => {
    await page.waitForTimeout(200); // We need to wait for Redux to propagate the changes of the drive, for use with the upload.
    await page.getByText("Upload files").click();
    // Folders are not allowed, keeping this test for now.
    await page.locator("#fileUploadBrowse").setInputFiles(dirname + "/" + "upload_folder");
    await page.waitForTimeout(1000); // I don't know what's a better selector.
    await page.keyboard.press("Escape");
    await Components.clickRefreshAndWait(page);
});


test.skip("Upload files after running out of space (and again after cleaning up)", async ({page}) => {});

test("Create single folder, delete single folder", async ({page}) => {
    const folderName = Folder.newFolderName();
    await Folder.create(page, folderName);
    await expect(page.locator('div > span', {hasText: folderName})).toHaveCount(1);
    await Folder.delete(page, folderName);
    await expect(page.locator('div > span', {hasText: folderName})).toHaveCount(0);
});

test("Create multiple folders (use / in the name)", async ({page}) => {
    const folderName1 = Folder.newFolderName();
    const folderName2 = Folder.newFolderName();
    const folderName3 = Folder.newFolderName();
    await Folder.create(page, folderName1 + "/" + folderName2 + "/" + folderName3);
    await Folder.actionByRowTitle(page, folderName1, "dblclick");
    await Folder.actionByRowTitle(page, folderName2, "dblclick");
    await Folder.actionByRowTitle(page, folderName3, "dblclick");
});

test("Rename", async ({page}) => {
    const folderName = Folder.newFolderName();
    const newFolderName = Folder.newFolderName();
    await Folder.create(page, folderName);
    await Folder.rename(page, folderName, newFolderName)
    await page.getByText(newFolderName).dblclick();
    await expect(page.getByText("This folder is empty")).toHaveCount(1);
});

test("Move file", async ({page}) => {
    const folderTarget = Folder.newFolderName();
    const uploadedFileName = "uploadedFile.txt";
    await Folder.create(page, folderTarget);
    await Folder.uploadFiles(page, [{name: uploadedFileName, contents: "Some content. Doesn't matter."}]);
    await Folder.moveFileTo(page, uploadedFileName, folderTarget);
    await Folder.actionByRowTitle(page, folderTarget, "dblclick");
    await expect(page.getByText(uploadedFileName)).toHaveCount(1);
});

test("Move folder", async ({page}) => {
    const folderToMove = Folder.newFolderName();
    const folderTarget = Folder.newFolderName();
    await Folder.create(page, folderToMove);
    await Folder.create(page, folderTarget);
    await Folder.moveFileTo(page, folderToMove, folderTarget);
    await Folder.actionByRowTitle(page, folderTarget, "dblclick");

    await expect(page.getByText(folderToMove)).toHaveCount(1);
});

test("Move folder to child (invalid op)", async ({page}) => {
    const rootFolder = "From";
    await Folder.create(page, rootFolder);
    await Folder.moveFileTo(page, rootFolder, rootFolder);
    await expect(page.getByText("Unable to move file.")).toHaveCount(1);
    await page.keyboard.press("Escape");
});

test("Copy file", async ({page}) => {
    const fileToUpload = {name: "File.txt", contents: "Contents"};
    const fileToCopy = fileToUpload.name;
    const folder = Folder.newFolderName();
    await Folder.create(page, folder);
    await Folder.uploadFiles(page, [fileToUpload]);
    await Folder.copyFileTo(page, fileToCopy, folder);
    await Folder.actionByRowTitle(page, folder, "dblclick");
    await expect(page.getByText(fileToCopy)).toHaveCount(1);
});

test("Copy file to self (check renaming)", async ({page}) => {
    const fileToUpload = {name: "File.txt", contents: "Contents"};
    const fileToCopy = fileToUpload.name;
    const folder = Folder.newFolderName();
    await Folder.create(page, folder);
    await Folder.uploadFiles(page, [fileToUpload]);
    await Folder.copyFileInPlace(page, fileToCopy);
    await expect(page.getByText("File.txt")).toHaveCount(1);
    await expect(page.getByText("File(1).txt")).toHaveCount(1);
});

test("Copy folder", async ({page}) => {
    const folderToCopy = Folder.newFolderName();
    await Folder.create(page, folderToCopy);
    await Folder.copyFileInPlace(page, folderToCopy);
    await expect(page.getByText(folderToCopy + "(1)")).toHaveCount(1);
});

test("Move to trash, empty trash", async ({page}) => {
    const folderName = Folder.newFolderName();
    await Folder.create(page, folderName);
    await Folder.moveFileToTrash(page, folderName);
    // Note(Jonas): Trash folder doesn't show up until refresh if not already present.
    await Components.clickRefreshAndWait(page);
    await Folder.open(page, "Trash");
    await expect(page.getByText(folderName)).toHaveCount(1);
    await Folder.emptyTrash(page);
    await page.waitForTimeout(200);
    await Folder.open(page, "Trash");
    await expect(page.getByText(folderName)).toHaveCount(0);
});

test("File search (use empty trash to trigger scan)", async ({page}) => {
    const theFolderToFind = "Please find meeee";
    const foldersToCreate = `A/B/C/D/${theFolderToFind}`;
    await Folder.create(page, foldersToCreate);
    await expect(page.getByText(theFolderToFind)).toHaveCount(0);
    const triggerFolder = "trigger";
    await Folder.create(page, triggerFolder);
    await Folder.moveFileToTrash(page, triggerFolder);
    // Note(Jonas): Trash folder doesn't show up until refresh if not already present.
    await Components.clickRefreshAndWait(page);
    await Folder.open(page, "Trash");
    await Folder.emptyTrash(page);
    await page.waitForTimeout(200);
    await Folder.searchFor(page, theFolderToFind);
    await expect(page.getByText(theFolderToFind)).toHaveCount(1);
});

test.skip("Start a large amount of heavy tasks and observe completion", async ({page}) => {

});