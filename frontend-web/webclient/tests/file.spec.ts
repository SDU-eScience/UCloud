import {test, expect} from '@playwright/test';
import {Folder, login} from "./shared";

test.beforeEach(async ({page}) => {
    await login(page);
});

/// File operations

test('Create and delete folder (with available resources)', async ({page}) => {

    const FOLDER_NAME = "FolderName" + `${(Math.random() * 100_000)}`.slice(0, 4);
    await page.getByRole('link', {name: 'Go to Files'}).click();
    await page.getByText('Home').click();
    await Folder.create(page, FOLDER_NAME);
    await page.locator('div').filter({hasText: FOLDER_NAME}).nth(1).dblclick();
    await page.goBack();
    // delete
    await Folder.delete(page, FOLDER_NAME);
    await page.goForward();
    await expect(page.locator('div').filter({hasText: "Could not find directory"})).toHaveCount(0);
});

test('Rename (with available resources)', async ({page}) => {

    const FOLDER_NAME = "FolderName" + `${(Math.random() * 100_000)}`.slice(0, 4);
    const NEW_FOLDER_NAME = "NewFolderName" + `${(Math.random() * 100_000)}`.slice(0, 4);
    await page.getByRole('link', {name: 'Go to Files'}).click();
    await page.locator('span').filter({hasText: 'Home'}).dblclick();
    await page.getByText('Create folder').click();
    await page.getByRole('textbox').nth(1).fill(FOLDER_NAME);
    await page.keyboard.press('Enter');
    await page.locator('div').filter({hasText: FOLDER_NAME}).nth(1).click();
    await page.getByText('Rename').click();
    await page.locator('.rename-field').fill(NEW_FOLDER_NAME);
    await page.keyboard.press('Enter');
    await page.getByText(NEW_FOLDER_NAME).dblclick();
    await expect(page.getByText('This folder is empty')).toHaveCount(1);
    await page.goBack();

    // Cleanup
    await Folder.delete(page, NEW_FOLDER_NAME);
});
