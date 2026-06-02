import {Browser, test as setup, Page} from "@playwright/test";
import {Accounting, Admin, Components, Project, ProviderInfo, Rows, TestUsers, User} from "./shared";
import fs from "fs";
import {default as data} from "./test_data.json" with {type: "json"};
import {default as pAndP} from "./provider_and_products.json" with {type: "json"};
const PRODUCTS = pAndP.find(it => it.location_origin === data.location_origin)!.products_used_in_tests;

setup("Setup 'pi', 'admin', and 'user'", async ({page, browser}) => {
    if (data.login_cookie) {
        await page.context().addCookies([data.login_cookie]);
    }

    setup.setTimeout(120_000);

    const ucloudAdminPage = await Admin.newLoggedInAdminPage(page);
    if (data.login_cookie) {
        await ucloudAdminPage.context().addCookies([data.login_cookie]);
    }


    const pi = await createNewUserAndLogin(ucloudAdminPage, browser, "pi");
    const admin = await createNewUserAndLogin(ucloudAdminPage, browser, "admin");
    const user = await createNewUserAndLogin(ucloudAdminPage, browser, "user");

    // pi should apply for resources
    const projectName = Accounting.Project.newProjectName();
    const idProject = await makeGrantApplication(pi.page, projectName);


    // UCloud admin accepts grant application
    await ucloudAdminPage.reload();
    await Accounting.goTo(ucloudAdminPage, "Grant applications");
    await ucloudAdminPage.getByText("Show applications received").click();
    await Project.changeTo(ucloudAdminPage, ProviderInfo.providerTitle());
    await Rows.actionByRowTitle(ucloudAdminPage, `${idProject}: ${projectName}`, "dblclick");
    await Accounting.GrantApplication.approve(ucloudAdminPage);

    const userList = [pi, admin, user];

    for (const testUser of userList) {
        const grantId = await makeGrantApplication(testUser.page);

        await Accounting.goTo(ucloudAdminPage, "Grant applications");
        await ucloudAdminPage.getByText("Show applications received").click();

        await Rows.actionByRowTitle(ucloudAdminPage, `${grantId}: Personal workspace of ${testUser.credentials.username}`, "dblclick");
        await waitForHiddenGrantNotification(ucloudAdminPage);
        await Accounting.GrantApplication.approve(ucloudAdminPage);
    }

    // admin and user should be added to the project
    await pi.page.getByRole("link", {name: "Go to dashboard"}).click();
    await waitForHiddenGrantNotification(pi.page);

    if (await pi.page.getByText("Grant awarded").first().isVisible()) {
        await pi.page.getByText("Grant awarded").waitFor({state: "hidden"});
    }
    await Project.changeTo(pi.page, projectName);
    await Project.inviteUsers(pi.page, [admin.credentials.username, user.credentials.username]);

    // admin and user should accept. 
    await Project.acceptInvites([admin.page, user.page], projectName);

    // Set up roles for users
    await Components.clickRefreshAndWait(pi.page);
    await Project.changeRoles(pi.page, admin.credentials.username, "Admin");

    TestUsers["Project PI"] = pi.credentials;
    TestUsers["Project Admin"] = admin.credentials;
    TestUsers["Project User"] = user.credentials;

    fs.writeFileSync("./test_data/user_test_data.json", JSON.stringify({...TestUsers, projectName}));

    for (const user of userList) {
        await user.page.close();
    }
});

async function waitForHiddenGrantNotification(page: Page): Promise<void> {
    if (await page.getByText("Grant awarded").first().isVisible()) {
        await page.getByText("Grant awarded").waitFor({state: "hidden"});
    }
}

async function createNewUserAndLogin(ucloudAdminPage: Page, browser: Browser, kind: "admin" | "pi" | "user"): Promise<{page: Page, credentials: {username: string; password: string;}}> {
    const credentials = User.newUserCredentials(kind);
    await User.create(ucloudAdminPage, credentials);
    const page = await browser.newPage();
    if (data.login_cookie) {
        await page.context().addCookies([data.login_cookie]);
    }
    await User.login(page, credentials, true);
    await Components.goToDashboard(page);
    return {page, credentials};
}

async function makeGrantApplication(page: Page, projectName?: string): Promise<string> {
    await Accounting.goTo(page, "Apply for resources");
    if (projectName) {
        await Accounting.GrantApplication.fillProjectName(page, projectName);
        await Accounting.GrantApplication.toggleGrantGiver(page, ProviderInfo.providerTitle());
        await Accounting.GrantApplication.setMonths(page, 1);

        const requestedResources: [string, number][] = [
            [PRODUCTS.compute, 50],
            [PRODUCTS.storage, 10],
            [PRODUCTS.license, projectName ? 3 : 1],
        ];

        if (PRODUCTS.public_ip != null) {
            requestedResources.push([PRODUCTS.public_ip, 5]);
        }

        await Accounting.GrantApplication.fillQuotaFields(page, requestedResources);
    } else {
        await page.getByRole("link", {name: "select an existing project instead"}).click();
        await Accounting.GrantApplication.toggleGrantGiver(page, ProviderInfo.providerTitle());
        await Accounting.GrantApplication.fillQuotaFields(page, [[PRODUCTS.storage, 1]]);
    }
    await Accounting.GrantApplication.fillDefaultApplicationTextFields(page, !!projectName);

    return await Accounting.GrantApplication.submit(page);
}