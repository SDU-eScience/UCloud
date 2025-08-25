import {test, expect} from '@playwright/test';
import {login, Drive} from "./shared";

test.beforeEach(async ({page}) => {
    await login(page);
});

/// Drive operations

test('Create and delete drive (with available resources)', async ({page}) => {
    const driveName = Drive.newDriveName();
    await Drive.create(page, driveName);
    await expect(page.locator('div > span').filter({hasText: driveName})).toHaveCount(1);
    await Drive.delete(page, driveName);
    await expect(page.locator('div > span').filter({hasText: driveName})).toHaveCount(0);
});

test('Rename drive', async ({page}) => {
    const driveName = Drive.newDriveName();
    const newDriveName = "NewDriveName" + `${(Math.random() * 100_000)}`.slice(0, 4);
    await Drive.create(page, driveName);
    await expect(page.locator('div > span').filter({hasText: driveName})).toHaveCount(1);
    await Drive.rename(page, driveName, newDriveName);
    await expect(page.locator('div > span').filter({hasText: newDriveName})).toHaveCount(1);
    // Cleanup
    await Drive.delete(page, newDriveName);
});

test('View properties', async ({page}) => {
    const driveName = Drive.newDriveName();
    await Drive.create(page, driveName);
    await expect(page.locator('div > span').filter({hasText: driveName})).toHaveCount(1);
    await Drive.properties(page, driveName);
    await expect(page.locator('b').filter({hasText: "ID:"})).toHaveCount(1);
    await expect(page.locator('b').filter({hasText: "Product:"})).toHaveCount(1);
    await expect(page.locator('b').filter({hasText: "Created by:"})).toHaveCount(1);
    await expect(page.locator('b').filter({hasText: "Created at:"})).toHaveCount(1);
    // Cleanup
    await page.goBack();
    await Drive.delete(page, driveName);
});