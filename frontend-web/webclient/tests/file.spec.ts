import {test, expect} from '@playwright/test';
import {Components, Drive, Folder, login, Rows} from "./shared";

const Drives: Record<string, string> = {};

test.beforeEach(async ({page, userAgent}) => {
    const driveName = Drive.newDriveName();
    await login(page);
    await Drive.create(page, driveName);
    Drives[userAgent!] = driveName
});

/// File operations

test('Create and delete folder (with available resources)', async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    const folderName = Folder.newFolderName();
    await Drive.openDrive(page, driveName);
    await Folder.create(page, folderName);
    await expect(page.locator('div > span', {hasText: folderName})).toHaveCount(1);
    await Folder.delete(page, folderName);
    await expect(page.locator('div > span', {hasText: folderName})).toHaveCount(0);
});

test('Rename (with available resources)', async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    const folderName = Folder.newFolderName();
    const newFolderName = Folder.newFolderName();
    await Drive.openDrive(page, driveName);
    await Folder.create(page, folderName);
    await page.locator('div > span').filter({hasText: folderName}).click();
    await page.getByText('Rename').click();
    await page.locator('.rename-field').fill(newFolderName);
    await page.keyboard.press('Enter');
    await page.getByText(newFolderName).dblclick();
    await expect(page.getByText('This folder is empty')).toHaveCount(1);
});


test('Change sensitivity (with available resources)', async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    await Drive.openDrive(page, driveName);

    const folderName = Folder.newFolderName();
    await Folder.create(page, folderName);

    /* TODO(Jonas): One should be enough! */
    await Components.clickRefreshAndWait(page);

    await Components.clickRefreshAndWait(page);

    await Components.clickRefreshAndWait(page);

    await Components.clickRefreshAndWait(page);
    /* TODO(Jonas): One should be enough! */

    await Rows.actionByRowTitle(page, folderName, "click");
    await page.locator('div:nth-child(6)').first().click();
    await page.getByText('Change sensitivity').click();
    // TODO(Jonas): Ensure NO confidential is present (or ensure that specific one has? If they happen simultaniously, more than one could be)
    await page.locator('#sensitivityDialogValue').selectOption('CONFIDENTIAL');
    await page.getByRole('textbox', {name: 'Reason for sensitivity change'}).fill('Content');
    await page.getByRole('button', {name: 'Update'}).click();
    // TODO(Jonas): Ensure 1 confidential is present (or ensure that specific one has?)

    await expect(page.getByText("C", {exact: true})).toHaveCount(1);
    // Cleanup    
});

test.afterEach(async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    await Drive.delete(page, driveName);
})