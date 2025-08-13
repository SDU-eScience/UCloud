import {test, expect} from '@playwright/test';
import {login} from "./shared";

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
  await page.getByRole('link', {name: 'Go to Files'}).click();
  await page.getByText('Home').click();
  await page.getByText('Create folder⌥ F').click();
  await page.getByRole('textbox').nth(1).fill('FOOBAR');
  await page.getByRole('textbox').nth(1).press('Enter');
  await page.locator('div').filter({hasText: /^FOOBAR$/}).nth(1).dblclick();
  await page.goBack();
  // TODO(Jonas): I don't think this link is considered idiomatic
  expect(page.locator('div').filter({hasText: "Could not find directory"})).toBeTruthy();
});
