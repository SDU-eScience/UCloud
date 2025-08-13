import {type Page} from '@playwright/test';

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
}

export function ucloudUrl(pathname: string) {
    return data.location_origin + "/app" + (pathname.startsWith("/") ? pathname : "/" + pathname);
}
