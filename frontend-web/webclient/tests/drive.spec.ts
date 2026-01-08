import {test, expect} from "@playwright/test";
import {User, Drive, Project} from "./shared";
import {default as data} from "./test_data.json" with {type: "json"};

test.beforeEach(async ({page}) => {
    await User.login(page, data.users.with_resources);
});

/// Drive operations

test("Create and delete drive (with available resources)", async ({page}) => {
    const driveName = Drive.newDriveName();
    await Drive.create(page, driveName);
    await Drive.delete(page, driveName);
    await expect(page.locator("div > span").filter({hasText: driveName})).toHaveCount(0);
});

test("Rename drive", async ({page}) => {
    const driveName = Drive.newDriveName();
    const newDriveName = Drive.newDriveName();
    await Drive.create(page, driveName);
    await Drive.rename(page, driveName, newDriveName);
    await expect(page.locator("span").filter({hasText: newDriveName})).toHaveCount(1);
    // Cleanup
    await Drive.delete(page, newDriveName);
});

test("View properties", async ({page}) => {
    const driveName = Drive.newDriveName();
    await Drive.create(page, driveName);
    await Drive.properties(page, driveName);
    await expect(page.locator("b").filter({hasText: "ID:"})).toHaveCount(1);
    await expect(page.locator("b").filter({hasText: "Product:"})).toHaveCount(1);
    await expect(page.locator("b").filter({hasText: "Created by:"})).toHaveCount(1);
    await expect(page.locator("b").filter({hasText: "Created at:"})).toHaveCount(1);
    // Cleanup
    await Drive.delete(page, driveName);
});

// Note(Jonas): Won't work without activating a project that you have admin rights to.
test.skip("Change permissions", async ({page}) => {
    const driveName = Drive.newDriveName();
    await Drive.create(page, driveName);
    await Drive.actionByRowTitle(page, driveName, "click");
    await Drive.properties(page, driveName);

    const projectName = Project.newProjectName();
    await Project.create(page, projectName);
    await Project.changeTo(page, projectName);

    await expect(page.locator("input#Write").first()).toBeChecked({checked: false});
    await page.locator("input#Write").first().click();
    await expect(page.locator("input#Write").first()).toBeChecked();
    await page.keyboard.press("Escape");

    await Drive.actionByRowTitle(page, driveName, "click");
    await Drive.properties(page, driveName);
    await expect(page.locator("input#Write").first()).toBeChecked();
    await page.keyboard.press("Escape");
});