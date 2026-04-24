import {test, expect} from '@playwright/test';
import {Components, Drive, File, User, Rows, Terminal, NetworkCalls, Project, testCtx, TestContexts, Contexts, ctxUser, Runs, Accounting, Applications, Admin, isDev, isProd} from "./shared";
import {default as data} from "./test_data.json" with {type: "json"};
import {default as pAndP} from "./provider_and_products.json" with {type: "json"};
const PRODUCTS = pAndP.find(it => it.location_origin === data.location_origin)!.products_used_in_tests;

const {dirname} = import.meta;

const Drives: Record<string, string> = {};


test.beforeEach(async ({page, userAgent}, testInfo) => {
    const doSkipInitialization = testInfo.titlePath.find(it => ["Files - accounting works"].includes(it));
    if (doSkipInitialization) {
        await Admin.newLoggedInAdminPage(page);
        return;
    }

    const ctx = testInfo.titlePath[1] as Contexts;
    const args = testCtx(testInfo.titlePath);
    const driveName = Drive.newDriveNameOrMemberFiles(ctx);
    await User.login(page, args.user);
    if (args.projectName) await Project.changeTo(page, args.projectName);
    if (ctx !== "Project User") {
        await Drive.create(page, driveName);
        Drives[userAgent! + args.user.username] = driveName;
    }
    await Drive.openDrive(page, driveName);
});

test.afterEach(async ({page, userAgent}, testInfo) => {
    const doSkipDeinit = testInfo.titlePath.find(it => ["Files - accounting works"].includes(it));
    if (doSkipDeinit) {
        return;
    }
    const args = testCtx(testInfo.titlePath);
    const ctx = testInfo.titlePath[1] as Contexts;
    if (ctx !== "Project User") {
        const driveName = Drives[userAgent! + args.user.username];
        if (driveName) await Drive.delete(page, driveName);
    }
});

/// File operations
TestContexts.map(ctx => {
    test.describe(ctx, () => {
        test.skip("Change sensitivity (with available resources)", async ({page}) => {
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

            await File.moveFileToTrash(page, folderName);
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
            await File.moveFileToTrash(page, folder);
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

            await page.goBack();
            await File.moveFileToTrash(page, folderName);
        });

        test("Stress testing the row selector", async ({page}) => {
            test.setTimeout(180_000);
            if (ctx === "Project User") test.skip();
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

                await page.getByText(testFileContents).waitFor({state: "visible"});
                await Components.toggleTasksDialog(page);
                await page.locator("svg > circle").first().waitFor({state: "visible"});

                await page.keyboard.press("Escape");
                await page.goBack();
                await File.moveFileToTrash(page, testFileName);
            });

            test("Upload file, download file, validate contents", async ({page}) => {
                const testFileName = "test_single_file.txt";
                const testFileContents = "Single test file content.";
                await File.uploadFiles(page, [{name: testFileName, contents: testFileContents}]);
                const result = await File.download(page, testFileName);
                expect(result).toBe(testFileContents);
                await File.moveFileToTrash(page, testFileName);
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

        test.describe("Files - Basic file browsing and operations works", () => {

            test("Create single folder, delete single folder", async ({page}) => {
                const folderName = File.newFolderName();
                await File.create(page, folderName);
                await page.locator('div > span', {hasText: folderName}).waitFor({state: "visible"});
                await File.moveToTrash(page, folderName);
                await page.locator('div > span', {hasText: folderName}).waitFor({state: "hidden"});
            });

            test("Create multiple folders (use / in the name)", async ({page}) => {
                const folderName1 = File.newFolderName();
                const folderName2 = File.newFolderName();
                const folderName3 = File.newFolderName();
                await File.create(page, folderName1 + "/" + folderName2 + "/" + folderName3);
                await File.open(page, folderName1);
                await File.open(page, folderName2);
                await File.open(page, folderName3);
                // TODO: Clean-up folders for project-user ctx

                for (let i = 0; i < 3; i++) {
                    await page.goBack();
                }

                await File.moveFileToTrash(page, folderName1);
            });

            test("Rename", async ({page}) => {
                const folderName = File.newFolderName();
                const newFolderName = File.newFolderName();
                await File.create(page, folderName);
                await File.rename(page, folderName, newFolderName)
                await File.open(page, newFolderName);
                await expect(page.getByText("This folder is empty")).toHaveCount(1);
                await page.goBack();
                await File.moveFileToTrash(page, newFolderName);
            });

            test("Move file", async ({page}) => {
                const folderTarget = File.newFolderName();
                const uploadedFileName = "uploadedFile.txt";
                await File.create(page, folderTarget);
                await File.uploadFiles(page, [{name: uploadedFileName, contents: "Some content. Doesn't matter."}]);
                await File.moveFileTo(page, uploadedFileName, folderTarget);
                await File.actionByRowTitle(page, folderTarget, "dblclick");
                await expect(page.getByText(uploadedFileName)).toHaveCount(1);
                await page.goBack();
                await File.moveFileToTrash(page, folderTarget);
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
                await File.actionByRowTitle(page, rootFolder, "hover", true);
                await page.getByRole("dialog").locator(".row", {hasText: "From"}).getByRole("button", {name: "Move to"}).click();
                await expect(page.getByText("Unable to move file.")).toHaveCount(1);
                await page.keyboard.press("Escape");
                await File.moveFileToTrash(page, rootFolder);
            });

            test("Copy file", async ({page}) => {
                const fileToUpload = {name: "File.txt", contents: "Contents"};
                const fileToCopy = fileToUpload.name;
                const folder = File.newFolderName();
                await File.create(page, folder);
                await File.uploadFiles(page, [fileToUpload]);
                await File.copyFileTo(page, fileToCopy, folder);
                await File.actionByRowTitle(page, folder, "dblclick");
                while (!await page.locator(".row").getByText(fileToCopy, {exact: true}).isVisible()) {
                    await Components.clickRefreshAndWait(page);
                    await page.waitForTimeout(200);
                }

                await page.goBack();
                await File.moveFileToTrash(page, folder);
            });

            test("Copy file to self (check renaming)", async ({page}) => {
                const fileToUpload = {name: "File.txt", contents: "Contents"};
                const fileToCopy = fileToUpload.name;
                const folder = File.newFolderName();
                await File.create(page, folder);
                await File.uploadFiles(page, [fileToUpload]);
                await File.copyFileInPlace(page, fileToCopy);
                await expect(page.getByText("File.txt", {exact: true})).toHaveCount(1);
                while (!await page.locator(".row").getByText("File(1).txt", {exact: true}).isVisible()) {
                    await Components.clickRefreshAndWait(page);
                    await page.waitForTimeout(200);
                }

                if (ctx === "Project User") {
                    await File.moveToTrash(page, "File.txt");
                    await File.moveToTrash(page, "File(1).txt");
                }
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
                while (!await page.locator(".row", {hasText: folderToCopy + "(1)"}).isVisible()) {
                    await Components.clickRefreshAndWait(page);
                    await page.waitForTimeout(200);
                }

                await File.moveFileToTrash(page, folderToCopy);
                await File.moveFileToTrash(page, folderToCopy + "(1)");
            });

            test("Move to trash, empty trash", async ({page}) => {
                if (isProd(data.location_origin)) {
                    throw Error("No reason to do it right now")
                }

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

        test("Files - search works", async ({page}) => {
            if (isProd(data.location_origin)) {
                throw Error("No reason to do it right now, requires emptying trash")
            }
            const theFolderToFind = "Please find meeee";
            const foldersToCreate = `A/B/C/D/${theFolderToFind}`;
            await File.create(page, foldersToCreate);
            await expect(page.getByText(theFolderToFind)).toHaveCount(0);
            const triggerFolder = "trigger" + File.newFolderName();
            await File.create(page, triggerFolder);
            await File.moveFileToTrash(page, triggerFolder);
            // Note(Jonas): Trash folder doesn't show up until refresh if not already present.
            await Components.clickRefreshAndWait(page);
            await File.open(page, "Trash");
            await File.emptyTrash(page);
            await File.searchFor(page, theFolderToFind);
            while (!await page.locator(".row", {hasText: theFolderToFind}).isVisible()) {
                await Components.clickRefreshAndWait(page);
                await page.waitForTimeout(200);
            }

            await page.goBack();
            await File.moveFileToTrash(page, "A");
        });

        test.describe("Files - transfer works", () => {
            test.skip("Transfer file between providers", () => {});
        });

        test("Files - accounting works", async ({page: adminPage, context}) => {
            test.setTimeout(120_000);
            const AUTOGIFTED_RESOURCES = (isDev(data.location_origin) || isProd(data.location_origin)) && ctx == "Personal Workspace";

            const quotas: [string, number][] = [[PRODUCTS.compute, 1]];
            // Skip applying for storage for personal workspaces, as they already are given gifts.
            if (AUTOGIFTED_RESOURCES == false) {
                quotas.push([PRODUCTS.storage, 2]);
            }
            const {userPage, user} = await User.createUserWithProjectAndAssignRole(adminPage, context, ctx, quotas);

            await Accounting.goTo(userPage, "Allocations");
            if (AUTOGIFTED_RESOURCES) {
                await userPage.getByText(`0 GB / 50 GB (0%)`).first().waitFor();
            } else {
                await userPage.getByText(`0 GB / 2 GB (0%)`).first().waitFor();
            }

            const jobName = Runs.newJobName();
            const term = await Applications.runAppAndOpenTerminalWithTerminalPage(userPage, Applications.AppNames.TestApplication, 1, undefined, jobName);
            await Terminal.createFile(term, 1);
            await Runs.terminateViewedRun(userPage);

            await userPage.reload();
            const isPersonalWorkspace = ctx === "Personal Workspace"
            const driveName = isPersonalWorkspace ? "Home" : Drive.memberFiles(user.username);

            await File.triggerStorageScan(userPage, driveName);
            await Accounting.goTo(userPage, "Allocations");
            if (AUTOGIFTED_RESOURCES) {
                await userPage.getByText(`1 GB / 50 GB (2%)`).first().waitFor();
            } else {
                await userPage.getByText(`1 GB / 2 GB (50%)`).first().waitFor();
            }

            await Drive.goToDrives(userPage);
            await Drive.openDrive(userPage, driveName);
            await File.searchFor(userPage, "example");
            await File.actionByRowTitle(userPage, "example", "click");
            await userPage.getByText("Go to parent folder").click();
            await File.moveFileToTrash(userPage, "example");
            await Drive.goToDrives(userPage);
            await Drive.openDrive(userPage, driveName);
            await File.open(userPage, "Trash");
            await File.emptyTrash(userPage);
        });

        test("Terminal - check integrated terminal works", async ({page, userAgent}) => {

            test.setTimeout(120_000);
            const args = testCtx(["", ctx]);
            const user = args.user;
            const drive = ctx === "Project User" ? user.username : Drives[userAgent! + user.username];
            const testFileName = "test_single_file.txt";
            const testFileContents = "Single test file content.";
            await File.uploadFiles(page, [{name: testFileName, contents: testFileContents}]);
            await File.openIntegratedTerminal(page);
            await Terminal.enterCmd(page, `cat "${drive}/${testFileName}"`);
            await expect(page.getByText(testFileContents)).toHaveCount(1);

            await File.moveFileToTrash(page, testFileName);

            if (ctx !== "Personal Workspace") {
                await Project.changeTo(page, "My workspace");
            }

            await Runs.goToRuns(page);

            await page.locator(".row", {hasText: "Integrated terminal"}).first().click();

            await NetworkCalls.awaitResponse(page, "**/jobs/terminate", async () => {
                await Components.clickConfirmationButton(page, "Stop");
            });

            if (ctx !== "Personal Workspace") {
                await Project.changeTo(page, args.projectName!);
            }


        });

        if (!ctx.startsWith("Project")) {
            test("Files - folder sharing works", async ({page, browser}) => {
                // Create folder
                const folderToShare = File.newFolderName();
                await File.create(page, folderToShare);

                const url = page.url();

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

                await page.goto(url);

                await File.moveToTrash(page, folderToShare);

            });
        }

        test("Files - Syncthing works", async ({page, userAgent}) => {
            test.setTimeout(60_000);
            const folderName = File.newFolderName();
            const deviceName = File.newFolderName().replace("FolderName", "DeviceName");
            await File.create(page, folderName);

            const url = page.url()

            const result = await NetworkCalls.awaitResponse(page, "**/iapps/syncthing/retrieve**", async () => {
                await page.locator("div.operation", {hasText: "Sync"}).click();
            });

            const syncthingDevicesText = await result.text();
            const parsedDevices: {config: {devices: any[]}} = JSON.parse(syncthingDevicesText);
            if (parsedDevices.config.devices.length > 0) {
                await page.getByText("Add device").first().click();
            }

            await page.getByText("Next step").click();
            await page.getByRole("textbox", {name: "Device name"}).fill(deviceName);
            await page.getByRole("textbox", {name: "My device ID"}).fill("1111111-1111111-1111111-1111111-1111111-1111111-1111111-1111111");
            await page.getByText("Next step").filter({visible: true}).first().click();

            await NetworkCalls.awaitResponse(page, "**/api/files/browse**", async () => {
                await page.getByRole("button", {name: "Add folder"}).filter({visible: true}).first().click();
            });
            const user = ctxUser(ctx);
            const drive = ctx === "Project User" ? Drive.newDriveNameOrMemberFiles(ctx) : Drives[userAgent! + user.username];
            await File.ensureDialogDriveActive(page, drive);

            await NetworkCalls.awaitResponse(page, "**/api/iapps/syncthing/update", async () => {
                await Components.useDialogBrowserItem(page, folderName, "Sync");
            });
            await page.getByRole("dialog").waitFor({state: "hidden"});

            // Remove folder
            await page.getByText(folderName).waitFor();
            await page.locator("div[class^=card] .row:not(.hidden)").last().getByRole("button").click();
            await NetworkCalls.awaitResponse(page, "**/api/iapps/syncthing/update", async () => {
                await page.getByRole("dialog").getByRole("button", {name: "Remove"}).click();
            });

            // Remove syncthing device
            await page.getByText(deviceName).waitFor();
            await page.getByRole("button", {name: "", exact: true}).first().click();
            await NetworkCalls.awaitResponse(page, "**/api/iapps/syncthing/update", async () => {
                await page.getByRole("button", {name: "Remove"}).click();
            });

            await page.goto(url);

            await File.moveToTrash(page, folderName);
        });
    });
});
