import {test, expect} from '@playwright/test';
import {login, Drive} from "./shared";

test.beforeEach(async ({page}) => {
    await login(page);
});

/// Drive operations

test('Create and delete drive (with available resources)', async ({page}) => {
    const driveName = "DriveName" + `${(Math.random() * 100_000)}`.slice(0, 4);
    await Drive.create(page, driveName);
    await expect(page.locator('div > span').filter({hasText: driveName})).toHaveCount(1);
    await Drive.delete(page, driveName);
    await expect(page.locator('div > span').filter({hasText: driveName})).toHaveCount(0);
});
