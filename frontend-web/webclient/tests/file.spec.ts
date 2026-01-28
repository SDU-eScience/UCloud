import {test, expect} from '@playwright/test';
import {Components, Drive, File, User, Rows, Terminal, NetworkCalls} from "./shared";
import {default as data} from "./test_data.json" with {type: "json"};

const {dirname} = import.meta;

const Drives: Record<string, string> = {};

test.beforeEach(async ({page, userAgent}) => {
    const driveName = Drive.newDriveName();
    await User.login(page, data.users.with_resources);
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
// Isn't supported by the test-provider
test.skip('Change sensitivity (with available resources)', async ({page}) => {
    const folderName = File.newFolderName();
    await File.create(page, folderName);
    await Components.clickRefreshAndWait(page);
    await Rows.actionByRowTitle(page, folderName, "click");
    await page.locator("div.operation.in-header").last().click();
    await page.getByText('Change sensitivity').click();
    // TODO(Jonas): Ensure NO confidential is present (or ensure that specific one has? If they happen simultaniously, more than one could be)
    await page.locator('#sensitivityDialogValue').selectOption('CONFIDENTIAL');
    await page.getByRole('textbox', {name: 'Reason for sensitivity change'}).fill('Content');
    await page.getByRole('button', {name: 'Update'}).click();
    // TODO(Jonas): Ensure 1 confidential is present (or ensure that specific one has?)

    await expect(page.getByText("C", {exact: true})).toHaveCount(1);
});

test("Favorite file, unfavorite file", async ({page}) => {
    const folder = File.newFolderName();
    await File.create(page, folder);
    await File.toggleFavorite(page, folder);
    await page.getByRole("link", {name: "Go to Files"}).hover();
    await expect(page.getByText(folder)).toHaveCount(2);
    await Components.projectSwitcher(page, "hover");
    await File.toggleFavorite(page, folder);
    await page.getByRole("link", {name: "Go to Files"}).hover();
    await expect(page.getByText(folder)).toHaveCount(1);
});

test("View properties", async ({page}) => {
    const folderName = File.newFolderName();
    await File.create(page, folderName);
    await Rows.actionByRowTitle(page, folderName, "click");
    await page.locator("div.operation.in-header").last().click();
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
        await File.create(page, "Folder" + i);
    }
    await File.actionByRowTitle(page, "Folder99", "click");
    await File.actionByRowTitle(page, "Folder0", "click");
});

test.describe("Files - upload/download works", () => {

    test("Upload file, validate contents, ensure shown as task", async ({page}) => {
        const testFileName = "test_single_file.txt";
        const testFileContents = "Single test file content.";
        await File.uploadFiles(page, [{name: testFileName, contents: testFileContents}]);
        await File.open(page, testFileName);
        await expect(page.getByText(testFileContents)).toHaveCount(1);
        await Components.toggleTasksDialog(page);
        await expect(page.locator("svg > circle").first()).toHaveCount(1);
    });

    test("Upload file, download file, validate contents", async ({page}) => {
        const testFileName = "test_single_file.txt";
        const testFileContents = "Single test file content.";
        await File.uploadFiles(page, [{name: testFileName, contents: testFileContents}]);
        const result = await File.download(page, testFileName);
        expect(result).toBe(testFileContents);
    });
});

// setInputFiles doesn't allow folders
test.skip("Upload folder", async ({page}) => {
    await page.getByText("Upload files").click();
    // Folders are not allowed, keeping this test for now.
    await page.locator("#fileUploadBrowse").setInputFiles(dirname + "/" + "upload_folder");
    await page.keyboard.press("Escape");
    await Components.clickRefreshAndWait(page);
});


test.skip("Upload files after running out of space (and again after cleaning up)", async ({page}) => {});

test.describe("Files - Basic file browsing and operations works", () => {

    test("Create single folder, delete single folder", async ({page}) => {
        const folderName = File.newFolderName();
        await File.create(page, folderName);
        await expect(page.locator('div > span', {hasText: folderName})).toHaveCount(1);
        await File.moveToTrash(page, folderName);
        await expect(page.locator('div > span', {hasText: folderName})).toHaveCount(0);
    });

    test("Create multiple folders (use / in the name)", async ({page}) => {
        const folderName1 = File.newFolderName();
        const folderName2 = File.newFolderName();
        const folderName3 = File.newFolderName();
        await File.create(page, folderName1 + "/" + folderName2 + "/" + folderName3);
        await File.open(page, folderName1);
        await File.open(page, folderName2);
        await File.open(page, folderName3);
    });

    test("Rename", async ({page}) => {
        const folderName = File.newFolderName();
        const newFolderName = File.newFolderName();
        await File.create(page, folderName);
        await File.rename(page, folderName, newFolderName)
        await File.open(page, newFolderName);
        await expect(page.getByText("This folder is empty")).toHaveCount(1);
    });

    test("Move file", async ({page}) => {
        const folderTarget = File.newFolderName();
        const uploadedFileName = "uploadedFile.txt";
        await File.create(page, folderTarget);
        await File.uploadFiles(page, [{name: uploadedFileName, contents: "Some content. Doesn't matter."}]);
        await File.moveFileTo(page, uploadedFileName, folderTarget);
        await File.actionByRowTitle(page, folderTarget, "dblclick");
        await expect(page.getByText(uploadedFileName)).toHaveCount(1);
    });

    test("Move folder", async ({page}) => {
        const folderToMove = File.newFolderName();
        const folderTarget = File.newFolderName();
        await File.create(page, folderToMove);
        await File.create(page, folderTarget);
        await File.moveFileTo(page, folderToMove, folderTarget);
        await File.actionByRowTitle(page, folderTarget, "dblclick");

        await expect(page.getByText(folderToMove)).toHaveCount(1);
    });

    test("Move folder to child (invalid op)", async ({page}) => {
        const rootFolder = "From";
        await File.create(page, rootFolder);
        await File.openOperationsDropsdown(page, rootFolder);
        await page.getByText("Move to...").click();
        await page.getByRole("dialog").getByText("Move to", {exact: true}).click();
        await expect(page.getByText("Unable to move file.")).toHaveCount(1);
        await page.keyboard.press("Escape");
    });

    test("Copy file", async ({page}) => {
        const fileToUpload = {name: "File.txt", contents: "Contents"};
        const fileToCopy = fileToUpload.name;
        const folder = File.newFolderName();
        await File.create(page, folder);
        await File.uploadFiles(page, [fileToUpload]);
        await File.copyFileTo(page, fileToCopy, folder);
        await File.actionByRowTitle(page, folder, "dblclick");
        await expect(page.getByText(fileToCopy)).toHaveCount(1);
    });

    test("Copy file to self (check renaming)", async ({page}) => {
        const fileToUpload = {name: "File.txt", contents: "Contents"};
        const fileToCopy = fileToUpload.name;
        const folder = File.newFolderName();
        await File.create(page, folder);
        await File.uploadFiles(page, [fileToUpload]);
        await File.copyFileInPlace(page, fileToCopy);
        await expect(page.getByText("File.txt")).toHaveCount(1);
        await expect(page.getByText("File(1).txt")).toHaveCount(1);
    });

    // Note(Jonas): Flaky. Will complete as expected if only test, 
    // but copy operation will be moved to background tasks if a lot is going on
    // (e.g. full test suite is being run!)
    test("Copy folder", async ({page}) => {
        const folderToCopy = File.newFolderName();
        await File.create(page, folderToCopy);
        await File.copyFileInPlace(page, folderToCopy);
        await page.waitForTimeout(2_000);
        await Components.clickRefreshAndWait(page);
        await page.getByText(folderToCopy + "(1)").waitFor();
    });

    test("Move to trash, empty trash", async ({page}) => {
        const folderName = File.newFolderName();
        await File.create(page, folderName);
        await File.moveFileToTrash(page, folderName);
        // Note(Jonas): Trash folder doesn't show up until refresh if not already present.
        await Components.clickRefreshAndWait(page);
        await File.open(page, "Trash");
        await expect(page.getByText(folderName)).toHaveCount(1);
        await File.emptyTrash(page);
        await File.open(page, "Trash");
        await expect(page.getByText(folderName)).toHaveCount(0);
    });
});

test.describe("Files - search works", () => {
    test("File search (use empty trash to trigger scan)", async ({page}) => {
        const theFolderToFind = "Please find meeee";
        const foldersToCreate = `A/B/C/D/${theFolderToFind}`;
        await File.create(page, foldersToCreate);
        await expect(page.getByText(theFolderToFind)).toHaveCount(0);
        const triggerFolder = "trigger";
        await File.create(page, triggerFolder);
        await File.moveFileToTrash(page, triggerFolder);
        // Note(Jonas): Trash folder doesn't show up until refresh if not already present.
        await Components.clickRefreshAndWait(page);
        await File.open(page, "Trash");
        await File.emptyTrash(page);
        await File.searchFor(page, theFolderToFind);
        await expect(page.getByText(theFolderToFind)).toHaveCount(1);
    });
});

test.describe("Files - transfer works", () => {
    test("Transfer file between providers", () => {
        throw new Error("Manual test");
    })
});

test.describe("Terminal - check integrated terminal works", () => {
    test("Create folder, upload file, cat contents in integrated terminal", async ({page, userAgent}) => {
        test.setTimeout(120_000);
        const drive = Drives[userAgent!];
        const testFileName = "test_single_file.txt";
        const testFileContents = "Single test file content.";
        await File.uploadFiles(page, [{name: testFileName, contents: testFileContents}]);
        await File.openIntegratedTerminal(page);
        await Terminal.enterCmd(page, `cat ${drive}/${testFileName}`);
        await expect(page.getByText(testFileContents)).toHaveCount(1);
    });
});

test.describe("Files - folder sharing works", () => {
    test("Create folder and share with other user, ensure invitation can be viewed", async ({page, browser}) => {
        // Create folder
        const folderToShare = File.newFolderName();
        await File.create(page, folderToShare);

        // Share with user
        await File.shareFolderWith(page, folderToShare, data.users.without_resources.username);

        // Ensure share exists
        await File.goToSharedByMe(page);
        await page.getByText(folderToShare, {exact: false}).waitFor({state: "visible"})
        await page.getByText("1 pending", {exact: false}).first().waitFor({state: "visible"});

        // Open new page and login for user folder is shared with
        const sharedWithUserPage = await browser.newPage();
        await User.login(sharedWithUserPage, data.users.without_resources);

        // Accept share
        await File.goToSharedWithMe(sharedWithUserPage);
        await sharedWithUserPage.locator(".row", {hasText: folderToShare}).getByRole("button", {name: "Accept"}).click();

        // Check folder is available
        await File.actionByRowTitle(sharedWithUserPage, folderToShare, "dblclick");
        await sharedWithUserPage.getByText("This folder is empty").waitFor({state: "visible"});

        // Delete share
        await Components.clickRefreshAndWait(page);
        await page.locator(".row", {hasText: folderToShare}).click();
        await Components.clickConfirmationButton(page, "Delete share");

        // See that access has been revoked for receiver
        // Should be `await Components.clickRefreshAndWait(sharedWithUserPage);`, but blocked by #5268
        await sharedWithUserPage.reload();
        await sharedWithUserPage.getByText("We could not find any data related to this folder.").waitFor({state: "visible"});
    });
});

test.describe("Files - Syncthing works", () => {
    test("Set up Syncthing", async ({page, userAgent}) => {
        const folderName = File.newFolderName();
        const deviceName = "Device name fill";
        await File.create(page, folderName);

        await page.getByText("Sync").click();
        await page.getByText("Next step").click();
        await page.getByRole("textbox", {name: "Device name"}).fill(deviceName);
        await page.getByRole("textbox", {name: "My device ID"}).fill("1111111-1111111-1111111-1111111-1111111-1111111-1111111-1111111");
        await page.getByText("Next step").filter({visible: true}).first().click();

        await page.getByRole("button", {name: "Add folder"}).filter({visible: true}).first().click();
        await File.ensureDialogDriveActive(page, Drives[userAgent!]);
        await NetworkCalls.awaitResponse(page, "**/api/iapps/syncthing/update", async () => {
            await page.getByRole("dialog").locator(".row", {hasText: folderName}).getByRole("button", {name: "Sync"}).click();
        });

        // Remove folder
        await NetworkCalls.awaitResponse(page, "**/api/iapps/syncthing/update", async () => {
            await page.getByText(folderName, {exact: false}).hover();
            await page.getByRole("button", {name: "", exact: true}).nth(1).click();
            await page.getByRole("button", {name: "Remove"}).click();
        });

        await NetworkCalls.awaitResponse(page, "**/api/iapps/syncthing/update", async () => {
            await page.getByText(deviceName).hover();
            await page.getByRole("button", {name: "", exact: true}).first().click();
            await page.getByRole("button", {name: "Remove"}).click();
        });
    });
})

test.skip("Start a large amount of heavy tasks and observe completion", async ({page}) => {

});