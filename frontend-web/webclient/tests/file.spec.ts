import {test, expect} from '@playwright/test';
import {Folder, login} from "./shared";

test.beforeEach(async ({page}) => {
    await login(page);
});

/// File operations

test('Create and delete folder (with available resources)', async ({page}) => {
    const folderName = Folder.newFolderName();
    await page.getByRole('link', {name: 'Go to Files'}).click();
    await page.getByText('Home').click();
    await Folder.create(page, folderName);
    await page.locator('div').filter({hasText: folderName}).nth(1).dblclick();
    await page.goBack();
    await Folder.delete(page, folderName);
    await page.goForward();
    await expect(page.locator('div').filter({hasText: "Could not find directory"})).toHaveCount(0);
});

test('Rename (with available resources)', async ({page}) => {
    const folderName = Folder.newFolderName();
    const newFolderName = Folder.newFolderName();
    await page.getByRole('link', {name: 'Go to Files'}).click();
    await page.locator('span').filter({hasText: 'Home'}).dblclick();
    await page.getByText('Create folder').click();
    await page.getByRole('textbox').nth(1).fill(folderName);
    await page.keyboard.press('Enter');
    await page.locator('div').filter({hasText: folderName}).nth(1).click();
    await page.getByText('Rename').click();
    await page.locator('.rename-field').fill(newFolderName);
    await page.keyboard.press('Enter');
    await page.getByText(newFolderName).dblclick();
    await expect(page.getByText('This folder is empty')).toHaveCount(1);
    await page.goBack();

    // Cleanup
    await Folder.delete(page, newFolderName);
});
