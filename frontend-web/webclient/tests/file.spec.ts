import {expect, Page} from '@playwright/test';
import {test} from "./fixtures";
import {cleanupWorkerDrives, walletUsage} from "./fixtures";
import {Components, Drive, File, User, Terminal, NetworkCalls, Contexts, Runs, Accounting, Applications, Admin, isDev, isProd, apiProductCategory, isByteStorage, driveIdByName, emptyTrashByApi, findFilePathByApi, trashByApi} from "./shared";
import {default as data} from "./test_data.json" with {type: "json"};
import {default as pAndP} from "./provider_and_products.json" with {type: "json"};
const PRODUCTS = pAndP.find(it => it.location_origin === data.location_origin)!.products_used_in_tests;

test.afterAll(async ({browser}) => {
    await cleanupWorkerDrives(browser);
});

async function deleteShareForFolder(page: Page, folderName: string): Promise<void> {
    await page.locator(".row", {hasText: folderName}).click();
    const deleteButton = page.getByRole("button", {name: "Delete share"});
    await deleteButton.waitFor();

    await NetworkCalls.awaitResponse(page, "**/api/shares", async () => {
        await deleteButton.dispatchEvent("mousedown");
    });

    await page.getByText(folderName, {exact: false}).waitFor({state: "hidden"});
}


function newSyncthingDeviceId(): string {
    return Array.from({length: 8}, () =>
        Math.floor(Math.random() * 10_000_000).toString().padStart(7, "0")
    ).join("-");
}

function filesAccountingTest(ctx: Contexts): void {
    test("Files - accounting works", async ({page: adminPage, context}) => {
        test.setTimeout(120_000);
        const AUTOGIFTED_RESOURCES = (isProd(data.location_origin) || isDev(data.location_origin)) && ctx == "Personal Workspace";

        if (data["login_cookie"]) await adminPage.context().addCookies([data["login_cookie"]]);
        await Admin.newLoggedInAdminPage(adminPage);

        const storageCategory = await apiProductCategory(adminPage, PRODUCTS.storage);
        const smallStorage = isByteStorage(storageCategory);
        const storageQuotaGb = smallStorage ? 0.01 : 2;
        const fileBytes = AUTOGIFTED_RESOURCES ? 1_000_000_000 : (smallStorage ? 5_000_000 : 1_000_000_000);

        const quotas: [string, number][] = [[PRODUCTS.compute, 1]];
        // Skip applying for storage for personal workspaces, as they already are given gifts.
        if (AUTOGIFTED_RESOURCES == false) {
            quotas.push([PRODUCTS.storage, storageQuotaGb]);
        }

        const {userPage, user} = await User.createUserWithProjectAndAssignRole(adminPage, context, ctx, quotas);

        await Accounting.goTo(userPage, "Allocations");
        if (AUTOGIFTED_RESOURCES) {
            await userPage.getByText(`0 GB / 5 GB (0%)`).first().waitFor();
        } else if (smallStorage) {
            await userPage.getByText(`0 MB / 10 MB (0%)`).first().waitFor();
        } else {
            await userPage.getByText(`0 GB / 2 GB (0%)`).first().waitFor();
        }

        const jobName = Runs.newJobName();
        const term = await Applications.runAppAndOpenTerminalWithTerminalPage(userPage, Applications.AppNames.TestApplication, 1, undefined, jobName);
        await Terminal.createFileOfSize(term, fileBytes);
        await Runs.terminateViewedRun(userPage, {waitForUi: false});

        await userPage.reload();
        const isPersonalWorkspace = ctx === "Personal Workspace"
        const driveName = isPersonalWorkspace ? "Home" : Drive.memberFiles(user.username);

        await File.triggerStorageScan(userPage, driveName);

        await expect.poll(async () => {
            return await walletUsage(userPage, PRODUCTS.storage);
        }, {timeout: 90_000, intervals: [1_000]}).toBeGreaterThan(0);

        await Accounting.goTo(userPage, "Allocations");

        const expectedAllocation = AUTOGIFTED_RESOURCES
            ? `1 GB / 5 GB (20%)`
            : smallStorage
                ? /^5(\.\d+)? MB \/ 10 MB \(5\d%\)$/
                : `1 GB / 2 GB (50%)`;
        const locator = userPage.getByText(expectedAllocation).first();
        await expect(locator).toBeVisible({timeout: 30_000});

        const driveId = await driveIdByName(userPage, driveName);
        await trashByApi(userPage, await findFilePathByApi(userPage, driveId, "example"));
        await emptyTrashByApi(userPage, driveId);
    });
}

function filesFolderSharingTest(): void {
    test("Files - folder sharing works", async ({testPage: page, fileRoot, browser}) => {
        test.setTimeout(180_000);
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
        if (data["login_cookie"]) {
            await sharedWithUserPage.context().addCookies([data["login_cookie"]]);
        }

        await User.loginDirect(sharedWithUserPage, data.users.without_resources);

        // Accept share
        await File.goToSharedWithMe(sharedWithUserPage);
        await sharedWithUserPage.locator(".row", {hasText: folderToShare}).getByRole("button", {name: "Accept"}).click();

        // Check folder is available
        await File.actionByRowTitle(sharedWithUserPage, folderToShare, "dblclick");
        await sharedWithUserPage.getByText("This folder is empty").waitFor({state: "visible"});

        // Delete share
        await Components.clickRefreshAndWait(page, "**/api/shares**");
        await deleteShareForFolder(page, folderToShare);

        await page.goto(url);

        await File.moveToTrash(page, folderToShare);
    });
}

/// File operations
(["Project PI"] as Contexts[]).map(ctx => {
    test.describe(ctx, () => {
        test("Favorite file, unfavorite file", async ({testPage: page, fileRoot}) => {
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

        test("View properties", async ({testPage: page, fileRoot}) => {
            const folderName = File.newFolderName();
            await File.create(page, folderName);
            await File.openOperationsDropsdown(page, folderName);
            await page.getByRole("menu").getByText("Properties", {exact: true}).click();

            await expect(page.locator("b").filter({hasText: "Path"})).toHaveCount(1);
            await expect(page.locator("b").filter({hasText: "Product"})).toHaveCount(1);
            await expect(page.locator("b").filter({hasText: "Created at"})).toHaveCount(1);

            await page.getByRole("button", {name: "View in folder"}).click();
            await page.waitForURL("**/app/files?**");
            await File.moveFileToTrash(page, folderName);
        });

        test("Stress testing the row selector", async ({testPage: page, fileDrive}) => {
            test.setTimeout(300_000);
            await File.createMany(page, Array.from({length: 100}, (_, i) => "Folder" + i));
            await File.actionByRowTitle(page, "Folder99", "click");
            await File.actionByRowTitle(page, "Folder0", "click");
        });

        test.describe("Files - upload/download works", () => {
            test("Upload file, validate contents, ensure shown as task", async ({testPage: page, fileRoot}) => {
                const testFileName = "test_upload_task.txt";
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

            test("Upload file, download file, validate contents", async ({testPage: page, fileRoot}) => {
                const testFileName = "test_retrieve_file.txt";
                const testFileContents = "Single test file content.";
                await File.uploadFiles(page, [{name: testFileName, contents: testFileContents}]);
                const result = await File.download(page, testFileName);
                expect(result).toBe(testFileContents);
                await File.moveFileToTrash(page, testFileName);
            });
        });

        test.describe("Files - Basic file browsing and operations works", () => {

            test("Create single folder, delete single folder", async ({testPage: page, fileRoot}) => {
                const folderName = File.newFolderName();
                await File.create(page, folderName);
                await page.locator('div > span', {hasText: folderName}).waitFor({state: "visible"});
                await File.moveToTrash(page, folderName);
                await page.locator('div > span', {hasText: folderName}).waitFor({state: "hidden"});
            });

            test("Create multiple folders (use / in the name)", async ({testPage: page, fileRoot}) => {
                const folderName1 = File.newFolderName();
                const folderName2 = File.newFolderName();
                const folderName3 = File.newFolderName();
                await File.create(page, folderName1 + "/" + folderName2 + "/" + folderName3);
                await File.open(page, folderName1);
                await File.open(page, folderName2);
                await File.open(page, folderName3);
                // TODO: Clean-up folders for project-user ctx

                for (let i = 0; i < 3; i++) {
                    await File.goUp(page);
                }

                await File.moveFileToTrash(page, folderName1);
            });

            test("Rename", async ({testPage: page, fileRoot}) => {
                const folderName = File.newFolderName();
                const newFolderName = File.newFolderName();
                await File.create(page, folderName);
                await File.rename(page, folderName, newFolderName)
                await File.open(page, newFolderName);
                await expect(page.getByText("This folder is empty")).toHaveCount(1);
                await File.goUp(page);
                await File.moveFileToTrash(page, newFolderName);
            });

            test("Move file", async ({testPage: page, fileRoot}) => {
                const folderTarget = File.newFolderName();
                const uploadedFileName = "uploadedFile.txt";
                await File.create(page, folderTarget);
                await File.uploadFiles(page, [{name: uploadedFileName, contents: "Some content. Doesn't matter."}]);
                await File.moveFileTo(page, uploadedFileName, folderTarget);
                await File.actionByRowTitle(page, folderTarget, "dblclick");
                await expect(page.getByText(uploadedFileName)).toHaveCount(1);
                await File.goUp(page);
                await File.moveFileToTrash(page, folderTarget);
            });

            test("Move folder", async ({testPage: page, fileRoot}) => {
                const folderToMove = File.newFolderName();
                const folderTarget = File.newFolderName();
                await File.create(page, folderToMove);
                await File.create(page, folderTarget);
                await File.moveFileTo(page, folderToMove, folderTarget);
                await File.actionByRowTitle(page, folderTarget, "dblclick");

                await expect(page.getByText(folderToMove)).toHaveCount(1);
            });

            test("Move folder to child (invalid op)", async ({testPage: page, fileRoot}) => {
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

            test("Copy file", async ({testPage: page, fileRoot}) => {
                const fileToUpload = {name: "File.txt", contents: "Contents"};
                const fileToCopy = fileToUpload.name;
                const folder = File.newFolderName();
                await File.create(page, folder);
                await File.uploadFiles(page, [fileToUpload]);
                await File.copyFileTo(page, fileToCopy, folder);
                await File.actionByRowTitle(page, folder, "dblclick");
                while (!await page.locator(".row").getByText(fileToCopy, {exact: true}).isVisible()) {
                    await Components.clickRefreshAndWait(page, "**/api/files/**");
                    await page.waitForTimeout(200);
                }

                await File.goUp(page);
                await File.moveFileToTrash(page, folder);
            });

            test("Copy file to self (check renaming)", async ({testPage: page, fileRoot}) => {
                const fileToUpload = {name: "File.txt", contents: "Contents"};
                const fileToCopy = fileToUpload.name;
                const folder = File.newFolderName();
                await File.create(page, folder);
                await File.uploadFiles(page, [fileToUpload]);
                await File.copyFileInPlace(page, fileToCopy);
                await expect(page.getByText("File.txt", {exact: true})).toHaveCount(1);
                await File.waitForFile(page, "File(1).txt");
                await Components.clickRefreshAndWait(page, "**/api/files/**");
                await expect(page.locator(".row").getByText("File(1).txt", {exact: true})).toBeVisible();

                if (ctx === "Project User") {
                    await File.moveToTrash(page, "File.txt");
                    await File.moveToTrash(page, "File(1).txt");
                }
            });

            // Note(Jonas): Flaky. Will complete as expected if only test,
            // but copy operation will be moved to background tasks if a lot is going on
            // (e.g. full test suite is being run!)
            test("Copy folder", async ({testPage: page, fileRoot}) => {
                const folderToCopy = File.newFolderName();
                await File.create(page, folderToCopy);
                await File.copyFileInPlace(page, folderToCopy);
                await File.waitForFile(page, folderToCopy + "(1)");
                await Components.clickRefreshAndWait(page, "**/api/files/**");
                await expect(page.locator(".row", {hasText: folderToCopy + "(1)"})).toBeVisible();

                await File.moveFileToTrash(page, folderToCopy);
                await File.moveFileToTrash(page, folderToCopy + "(1)");
            });

            test("Move to trash, empty trash", async ({testPage: page, fileDrive}) => {
                const folderName = File.newFolderName();
                await File.create(page, folderName);
                await File.moveFileToTrash(page, folderName);
                // Note(Jonas): Trash folder doesn't show up until refresh if not already present.
                await Components.clickRefreshAndWait(page, "**/api/files/**");
                await File.open(page, "Trash");
                await expect(page.getByText(folderName)).toHaveCount(1);
                await File.emptyTrash(page);
                await File.open(page, "Trash");
                await expect.poll(async () => {
                    await page.reload();
                    return await page.getByText(folderName).count();
                }, {timeout: 30_000, intervals: [1_000]}).toBe(0);
            });
        });

        test("Files - search works", async ({testPage: page, fileDrive}) => {
            test.setTimeout(120_000);
            const theFolderToFind = "Please find meeee";
            const foldersToCreate = `A/B/C/D/${theFolderToFind}`;
            await File.create(page, foldersToCreate);
            await expect(page.getByText(theFolderToFind)).toHaveCount(0);
            const triggerFolder = "trigger" + File.newFolderName();
            await File.create(page, triggerFolder);
            await File.moveFileToTrash(page, triggerFolder);
            // Note(Jonas): Trash folder doesn't show up until refresh if not already present.
            await Components.clickRefreshAndWait(page, "**/api/files/**");
            await File.open(page, "Trash");
            await File.emptyTrash(page);
            await File.searchFor(page, theFolderToFind);
            while (!await page.locator(".row", {hasText: theFolderToFind}).isVisible()) {
                await Components.clickRefreshAndWait(page, "**/api/files/**");
                await page.waitForTimeout(200);
            }
        });

        filesAccountingTest(ctx);

        test("Terminal - check integrated terminal works", async ({testPage: page, fileRoot}) => {

            test.setTimeout(120_000);
            const testFileName = "test_terminal.txt";
            const testFileContents = "Single test file content.";
            await File.uploadFiles(page, [{name: testFileName, contents: testFileContents}]);
            await File.openIntegratedTerminal(page);
            await expect.poll(async () => {
                await Terminal.enterCmd(page, `cat "${fileRoot.driveName}/${fileRoot.name}/${testFileName}"`);
                return await page.getByText(testFileContents).count();
            }, {timeout: 30_000, intervals: [1_000]}).toBeGreaterThan(0);
            await page.locator(".tab-strip-close").first().click();
            await File.moveFileToTrash(page, testFileName);


        });

        test("Files - Syncthing works", async ({testPage: page, fileRoot}) => {
            const folderName = fileRoot.name;
            const deviceName = File.newFolderName().replace("FolderName", "DeviceName");
            const deviceId = newSyncthingDeviceId();
            await File.create(page, File.newFolderName());

            const url = page.url()
            const syncButton = page.getByRole("button", {name: /^Sync/});
            await expect(syncButton).toBeEnabled({timeout: 30_000});
            await syncButton.click();
            await page.waitForURL("**/syncthing**", {timeout: 10_000});

            const installHeading = page.getByRole("heading", {name: "Install Syncthing"});
            const syncHeading = page.getByRole("heading", {name: "File synchronization"});
            await installHeading.or(syncHeading).waitFor({timeout: 30_000});

            const nextStep = page.getByRole("button", {name: "Next step", exact: true});
            const addDevice = page.getByRole("button", {name: "Add device", exact: true});

            if (await installHeading.isVisible()) {
                await nextStep.click();
                await page.getByRole("textbox", {name: /^Device name/}).fill(deviceName);
                await page.getByRole("textbox", {name: /^My device ID/}).fill(deviceId);
                await expect(async () => {
                    await addDevice.click({timeout: 2_000});
                    await page.getByRole("button", {name: "Choose folder", exact: true}).waitFor({timeout: 2_000});
                }).toPass({timeout: 30_000});
            } else {
                await expect(async () => {
                    await addDevice.click({timeout: 2_000});
                    await page.getByRole("dialog").getByRole("textbox", {name: /^Device name/}).waitFor({timeout: 2_000});
                }).toPass({timeout: 30_000});
                await page.getByRole("dialog").getByRole("textbox", {name: /^Device name/}).fill(deviceName);
                await page.getByRole("dialog").getByRole("textbox", {name: /^Device ID/}).fill(deviceId);
                await page.getByRole("dialog").getByRole("button", {name: "Add device", exact: true}).click();
            }

            await NetworkCalls.awaitResponse(page, "**/api/files/browse**", async () => {
                await page.getByRole("button", {name: "Choose folder", exact: true}).click();
            });
            const drive = fileRoot.driveName;
            await File.ensureDialogDriveActive(page, drive, url);

            await NetworkCalls.awaitResponse(page, "**/api/iapps/syncthing/update", async () => {
                await Components.useDialogBrowserItem(page, folderName, "Sync");
            });
            await page.getByRole("dialog").waitFor({state: "hidden"});

            // Remove folder
            await page.getByText(folderName).waitFor();
            await page.locator(".sync-row").filter({hasText: folderName})
                .getByRole("button").last().click();
            await NetworkCalls.awaitResponse(page, "**/api/iapps/syncthing/update", async () => {
                await page.getByRole("dialog").getByRole("button", {name: "Stop synchronizing", exact: true}).click();
            });

            // Remove syncthing device
            await page.getByText(deviceName).waitFor();
            await page.locator(".sync-row").filter({hasText: deviceName}).getByRole("button").last().click();
            await NetworkCalls.awaitResponse(page, "**/api/iapps/syncthing/update", async () => {
                await page.getByRole("dialog").getByRole("button", {name: "Remove", exact: true}).click();
            });

            await page.goto(url);
        });
    });
});

test.describe("Personal Workspace", () => {
    filesFolderSharingTest();

    if (isDev(data.location_origin) || isProd(data.location_origin)) {
        filesAccountingTest("Personal Workspace");
    }
});
