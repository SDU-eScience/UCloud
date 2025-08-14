import {expect, type Page} from '@playwright/test';

// Note(Jonas): If it complains that it doesn't exist, create it.
import {default as data} from "./test_data.json" with {type: "json"};

const user = data.users.with_resources;

export async function login(page: Page) {
    if (!user) throw Error("No username or password provided");
    await page.goto(ucloudUrl("login"));
    await page.getByText('Other login options →').click();
    await page.getByRole('textbox', {name: 'Username'}).fill(user.username);
    await page.getByRole('textbox', {name: 'Password'}).fill(user.password);
    await page.getByRole('button', {name: 'Login'}).click();
    await stickSidebar(page);
}

async function stickSidebar(page: Page) {
    await page.getByRole('link', {name: 'Go to Files'}).hover();
    await page.getByRole('banner').locator('svg').click();
}

export function ucloudUrl(pathname: string) {
    return data.location_origin + "/app" + (pathname.startsWith("/") ? pathname : "/" + pathname);
}

export async function createFolder(page: Page, name: string) {
    expect(page.url().includes("/files?path=")).toBeTruthy();
    await page.getByText('Create folder').click();
    await page.getByRole('textbox').nth(1).fill(name);
    await page.getByRole('textbox').nth(1).press('Enter');
}

export async function deleteFolder(page: Page, name: string) {
    await page.locator('div').filter({hasText: name}).nth(1).click();
    await page.locator('div:nth-child(6)').first().click(); // Ellipses
    await page.getByRole('button', {name: 'Move to trash'}).click({delay: 1000 + 200});
}