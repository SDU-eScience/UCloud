import {test, expect} from '@playwright/test';
import {createFolder, deleteFolder, login} from "./shared";

test.beforeEach(async ({page}) => {
  await login(page);
});

/// Drive operations

test('Create and delete drive (with available resources)', async ({page}) => {
  const DRIVE_NAME = "DriveName" + `${(Math.random() * 100_000)}`.slice(0, 4);
  await page.getByRole('link', {name: 'Go to Files'}).click();
  await page.getByText('Create drive').click();
  await page.getByRole('textbox', {name: 'Choose a name*'}).fill(DRIVE_NAME);
  await page.getByRole('button', {name: 'Create', disabled: false}).click();
  await page.locator('div > span').filter({hasText: DRIVE_NAME}).click();
  await page.getByText('Delete⌥ R').click();
  await page.locator('#collectionName').fill(DRIVE_NAME);
  await page.getByRole('button', {name: 'I understand what I am doing'}).click();
  await expect(page.locator('div > span').filter({hasText: DRIVE_NAME})).toHaveCount(0);
});


/// File operations

test('Create and delete folder (with available resources)', async ({page}) => {
  const FOLDER_NAME = "FolderName" + `${(Math.random() * 100_000)}`.slice(0, 4);
  await page.getByRole('link', {name: 'Go to Files'}).click();
  await page.getByText('Home').click();
  await createFolder(page, FOLDER_NAME);
  await page.locator('div').filter({hasText: FOLDER_NAME}).nth(1).dblclick();
  await page.goBack();
  // delete
  await deleteFolder(page, FOLDER_NAME);
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
  await deleteFolder(page, NEW_FOLDER_NAME);
});
