import test, {expect} from "@playwright/test";
import {Components, User} from "./shared";

test("Login using password, logout", async ({page}) => {
    await User.login(page);
    await User.logout(page);
    await expect(page.getByText("Other login options")).toHaveCount(1);
});

test("While logged in, ensure docs link works", async ({page}) => {
    await User.login(page);
    await Components.toggleUserMenu(page);
    const ucloudDocsPagePromise = page.waitForEvent("popup");
    await page.getByText("UCloud docs").click();
    const ucloudDocsPage = await ucloudDocsPagePromise;
    await expect(ucloudDocsPage.getByText("UCloud User Guide¶")).toHaveCount(1);
    expect(ucloudDocsPage.url()).toMatch("https://docs.cloud.sdu.dk/");
    await ucloudDocsPage.close();
});

test("Ensure data protection link works", async ({page}) => {
    await User.login(page);
    await Components.toggleUserMenu(page);
    const dataProtectionPagePromise = page.waitForEvent("popup");
    await page.getByText("SDU Data Protection").click();
    const dataProtectionPage = await dataProtectionPagePromise;
    await expect(dataProtectionPage.getByText("Databeskyttelse")).toHaveCount(1);
    expect(dataProtectionPage.url()).toMatch("https://www.sdu.dk/en/om_dette_websted/databeskyttelse");
    await dataProtectionPage.close();
    await page.close();
});

test("Docs link on login page", async ({page}) => {
    await User.toLoginPage(page);
    const ucloudDocsPagePromise = page.waitForEvent("popup");
    await page.getByText("Docs").click();
    const ucloudDocsPage = await ucloudDocsPagePromise;
    await expect(ucloudDocsPage.getByText("UCloud User Guide¶")).toHaveCount(1);
    expect(ucloudDocsPage.url()).toMatch("https://docs.cloud.sdu.dk/");
});

test("Help button on login page", async ({page}) => {
    await User.toLoginPage(page);
    await page.locator("svg[data-component=icon-suggestion]").click();
    expect(page.getByText("Need help?")).toBeVisible();
});