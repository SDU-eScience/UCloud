import {test, expect} from '@playwright/test';
import {login} from './shared';

test.beforeEach(async ({page}) => {
  login(page);
});

test('Create and delete folder (with available resources)', async ({page}) => {
  await page.getByRole('link', {name: 'Go to Files'}).click();
  await page.getByRole('link', {name: 'Logo for K8s Dev Provider Home'}).click();
  await page.getByText('Create folder⌥ F').click();
  await page.getByRole('textbox').nth(1).fill('FOOBAR');
  await page.getByRole('textbox').nth(1).press('Enter');
  await page.getByText('FOOBAR').click();
  await page.locator('div').filter({hasText: /^FOOBAR$/}).nth(1).dblclick();
  await page.goBack();
  expect(page.locator('div').filter({hasText: "Could not find directory"})).toBeTruthy();
});