import {test, expect} from "@playwright/test";
import {User, Drive, Project, Admin, Applications, Accounting, Rows, testCtx, TestContexts} from "./shared";
import {default as data} from "./test_data.json" with {type: "json"};

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
                const driveName = Drive.newDriveName();
                await Drive.create(page, driveName);
                await Drive.delete(page, driveName);
                await expect(page.locator("div > span").filter({hasText: driveName})).toHaveCount(0);
            });
        }

        if (ctx !== "Project User") {
            test("Rename drive", async ({page}) => {
                const driveName = Drive.newDriveName();
                const newDriveName = Drive.newDriveName();
                await Drive.create(page, driveName);
                await Drive.rename(page, driveName, newDriveName);
                await expect(page.locator("span").filter({hasText: newDriveName})).toHaveCount(1);
                // Cleanup
                await Drive.delete(page, newDriveName);
            });
        }

        test("View properties", async ({page}) => {
            const userCtx = ctx === "Project User";
            const args = testCtx(["", ctx]);
            const driveName = userCtx ? `Member Files: ${args.user.username}` : Drive.newDriveName();
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
                test("check change permissions works", async ({page, context}) => {
                    // TODO(Jonas): Redo? It doesn't make sense for multiple contexts, as it currently creates a new project
                    test.setTimeout(120_000);
                    const resourceUserPage = page; // Already logged in.
                    const noResourceUserPage = await context.browser()?.newPage();
                    if (!noResourceUserPage) throw new Error("Failed to create no resources user page");

                    const newProjectName = Project.newProjectName();
                    const driveName = Drive.newDriveName();

                    const {GrantApplication} = Accounting;
                    await Accounting.goTo(resourceUserPage, "Apply for resources");
                    await GrantApplication.fillProjectName(resourceUserPage, newProjectName);
                    await GrantApplication.toggleGrantGiver(resourceUserPage, "k8s");
                    await GrantApplication.fillQuotaFields(resourceUserPage, [{field: "GB requested", quota: 1}]);
                    await GrantApplication.fillDefaultApplicationTextFields(resourceUserPage);
                    const id = await GrantApplication.submit(resourceUserPage);

                    const adminPage = await Admin.newLoggedInAdminPage(context);
                    await Project.changeTo(adminPage, "Provider K8s");
                    await Accounting.goTo(adminPage, "Grant applications");
                    await adminPage.getByText("Show applications received").click();
                    await Rows.actionByRowTitle(adminPage, `${id}: ${newProjectName}`, "dblclick");
                    await GrantApplication.approve(adminPage);

                    await resourceUserPage.getByRole("link", {name: "Go to dashboard"}).click();
                    await resourceUserPage.getByRole("heading", {name: "Grant awarded"}).waitFor({state: "hidden"});
                    await Project.changeTo(resourceUserPage, newProjectName);
                    await Accounting.goTo(resourceUserPage, "Members");

                    await Accounting.Project.inviteUser(resourceUserPage, data.users.without_resources.username);
                    const groupName = await Accounting.Project.newGroup(resourceUserPage);

                    await User.login(noResourceUserPage, data.users.without_resources);
                    await Accounting.Project.acceptProjectInvite(noResourceUserPage, newProjectName);

                    await Accounting.Project.addUsersToGroup(resourceUserPage, groupName, [data.users.with_resources.username, data.users.without_resources.username]);

                    await Drive.create(resourceUserPage, driveName);

                    await Drive.goToDrives(noResourceUserPage);
                    expect(noResourceUserPage.getByText(driveName)).toHaveCount(0);

                    await Drive.openPermissions(resourceUserPage, driveName);
                    await resourceUserPage.reload();
                    await resourceUserPage.locator(`div[data-group='${groupName}']`).locator("#Read").click();
                    await Project.changeTo(noResourceUserPage, newProjectName);
                    await noResourceUserPage.locator("span", {hasText: driveName}).hover();
                });
            });
        }
    });
});