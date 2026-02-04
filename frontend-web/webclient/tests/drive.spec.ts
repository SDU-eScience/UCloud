import {test, expect} from "@playwright/test";
import {User, Drive, Project, Admin, Accounting, Rows, testCtx, Components, ctxUser, sharedTestProjectName, TestContexts} from "./shared";

test.beforeEach(async ({page}, testInfo) => {
    const args = testCtx(testInfo.titlePath);
    await User.login(page, args.user);
    if (args.projectName) await Project.changeTo(page, args.projectName);
});

/// Drive operations

TestContexts.map(ctx => {
    test.describe(ctx, () => {
        if (ctx !== "Project User") {
            test("Create and delete drive (with available resources)", async ({page}) => {
                const driveName = Drive.newDriveNameOrMemberFiles(ctx);
                await Drive.create(page, driveName);
                await Drive.delete(page, driveName);
                await expect(page.locator("div > span").filter({hasText: driveName})).toHaveCount(0);
            });
        }

        if (ctx !== "Project User") {
            test("Rename drive", async ({page}) => {
                const driveName = Drive.newDriveNameOrMemberFiles(ctx);
                const newDriveName = Drive.newDriveNameOrMemberFiles(ctx);
                await Drive.create(page, driveName);
                await Drive.rename(page, driveName, newDriveName);
                await expect(page.locator("span").filter({hasText: newDriveName})).toHaveCount(1);
                // Cleanup
                await Drive.delete(page, newDriveName);
            });
        }

        test("View properties", async ({page}) => {
            const userCtx = ctx === "Project User";
            const driveName = Drive.newDriveNameOrMemberFiles(ctx);
            if (!userCtx) await Drive.create(page, driveName);
            else await Drive.goToDrives(page);
            await Drive.properties(page, driveName);
            await expect(page.locator("b").filter({hasText: "ID:"})).toHaveCount(1);
            await expect(page.locator("b").filter({hasText: "Product:"})).toHaveCount(1);
            await expect(page.locator("b").filter({hasText: "Created by:"})).toHaveCount(1);
            await expect(page.locator("b").filter({hasText: "Created at:"})).toHaveCount(1);
            // Cleanup
            await Drive.delete(page, driveName);
        });

        if (ctx === "Project Admin" || ctx === "Project PI") {
            test.describe("Drives - check change permissions works", () => {
                test("check change permissions works", async ({page: adminPage, browser}) => {

                    const userPage = await (await browser.newContext()).newPage();
                    if (!userPage) throw Error("Failed to create user page");
                    const userInfo = ctxUser("Project User")!;
                    const projectName = sharedTestProjectName();
                    await User.login(userPage, userInfo);

                    // Create drive
                    const driveName = Drive.newDriveNameOrMemberFiles(ctx);
                    // Project User will not reach here, so we can safely create
                    await Drive.create(adminPage, driveName);

                    // See that drive is not visible for project user
                    await Project.changeTo(userPage, projectName);
                    await Drive.goToDrives(userPage);
                    /* TODO(Jonas): Find a different approach */
                    await userPage.waitForLoadState("networkidle");

                    // Change rights for user to allow viewing
                    await Drive.openPermissions(adminPage, driveName);
                    await adminPage.locator(`div[data-group='All users']`).locator("#Read").click();

                    // Reload drives for user, see drive appears
                    await Components.clickRefreshAndWait(userPage);
                    await Rows.actionByRowTitle(userPage, driveName, "hover");

                    await Drive.delete(adminPage, driveName);
                });
            });
        }
    });
});