import {type Page} from '@playwright/test';

const USER = "";
const PASSWORD = "";
export async function login(page: Page) {
    if (!USER || !PASSWORD) throw Error("No username or password provided");
    await page.goto(ucloudUrl("login"));
    await page.getByText('Other login options →').click();
    await page.getByRole('textbox', {name: 'Username'}).fill(USER);
    await page.getByRole('textbox', {name: 'Password'}).fill(PASSWORD);
    await page.getByRole('button', {name: 'Login'}).click();
}

const LOCATION_ORIGIN = "https://dev.cloud.sdu.dk";
export function ucloudUrl(pathname: string) {
    return LOCATION_ORIGIN + "/app" + (pathname.startsWith("/") ? pathname : "/" + pathname);
}