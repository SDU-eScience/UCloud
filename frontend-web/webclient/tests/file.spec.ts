import {test, expect} from '@playwright/test';
import {Drive, Folder, login} from "./shared";

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
    await Drive.goToDrives(page);
    await page.getByText(driveName).dblclick();
    await Folder.create(page, folderName);
    await expect(page.locator('div > span', {hasText: folderName})).toHaveCount(1);
    await Folder.delete(page, folderName);
    await expect(page.locator('div > span', {hasText: folderName})).toHaveCount(0);
});

test('Rename (with available resources)', async ({page}) => {
    const folderName = Folder.newFolderName();
    const newFolderName = Folder.newFolderName();
    const driveName = Drive.newDriveName();
    await Drive.create(page, driveName);
    await page.locator('span').filter({hasText: driveName}).dblclick();
    await Folder.create(page, folderName);
    await page.locator('div > span').filter({hasText: folderName}).click();
    await page.getByText('Rename').click();
    await page.locator('.rename-field').fill(newFolderName);
    await page.keyboard.press('Enter');
    await page.getByText(newFolderName).dblclick();
    await expect(page.getByText('This folder is empty')).toHaveCount(1);

    // Cleanup
    await page.goBack();
    await Folder.delete(page, newFolderName);
});

test.afterEach(async ({page, userAgent}) => {
    const driveName = Drives[userAgent!];
    await Drive.delete(page, driveName);
})