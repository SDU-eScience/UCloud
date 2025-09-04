import {expect, Locator, type Page} from "@playwright/test";

// Note(Jonas): If it complains that it doesn"t exist, create it.
import {default as data} from "./test_data.json" with {type: "json"};

const user = data.users.with_resources;

export async function login(page: Page) {
    if (!user) throw Error("No username or password provided");
    await page.goto(ucloudUrl("login"));
    await page.getByText("Other login options →").click();
    await page.getByRole("textbox", {name: "Username"}).fill(user.username);
    await page.getByRole("textbox", {name: "Password"}).fill(user.password);
    await page.getByRole("button", {name: "Login"}).click();
}

export function ucloudUrl(pathname: string) {
    return data.location_origin + "/app" + (pathname.startsWith("/") ? pathname : "/" + pathname);
}

export const Rows = {
    async actionByRowTitle(page: Page, name: string, action: "click" | "dblclick" | "hover") {
        let iterations = 100;
        while (!await page.locator("div > span", {hasText: name}).isVisible()) {
            await page.locator(".scrolling").hover();
            await page.mouse.wheel(0, 200);
            await page.waitForTimeout(200);
            iterations -= 1;
            if (iterations <= 0) {
                console.warn("Many such iterations, no result");
                break;
            }
        }
        await page.locator("div > span", {hasText: name})[action]();
    },
};

export const Folder = {
    ...Rows,
    newFolderName(): string {
        return "FolderName" + Math.random().toString().slice(2, 7);
    },

    async create(page: Page, name: string) {
        expect(page.url().includes("/files?path=")).toBeTruthy();
        await page.getByText("Create folder").click();
        await page.getByRole("textbox").nth(1).fill(name);
        await page.getByRole("textbox").nth(1).press("Enter");
    },

    async delete(page: Page, name: string) {
        await this.actionByRowTitle(page, name, "click");
        await page.locator(".operation.button6.in-header:nth-child(6)").click(); // Ellipses
        await page.getByRole("button", {name: "Move to trash"}).click({delay: 1000 + 200});
    }
}

export const Drive = {
    ...Rows,
    newDriveName(): string {
        return "DriveName" + Math.random().toString().slice(2, 7);
    },

    async goToDrives(page: Page) {
        await page.getByRole("link", {name: "Go to Files"}).click();
        await Components.projectSwitcher(page, "hover");
    },

    async openDrive(page: Page, name: string) {
        if (!page.url().includes("/app/drives")) {
            await this.goToDrives(page);
        }
        await this.actionByRowTitle(page, name, "dblclick");
    },

    async create(page: Page, name: string) {
        await this.goToDrives(page);
        await page.waitForTimeout(200);
        await page.locator("div.operation").filter({hasText: "Create drive"}).click();
        await page.getByRole("textbox", {name: "Choose a name*"}).fill(name);
        await page.getByRole("button", {name: "Create", disabled: false}).click();
    },

    async delete(page: Page, name: string) {
        await this.goToDrives(page);
        await Rows.actionByRowTitle(page, name, "click");
        await page.getByText("Delete").click();
        await page.locator("#collectionName").fill(name);
        await page.getByRole("button", {name: "I understand what I am doing", disabled: false}).click();
    },

    async rename(page: Page, oldName: string, newName: string) {
        await Rows.actionByRowTitle(page, oldName, "click");
        await page.getByText("Rename").click();
        await page.getByRole("textbox").nth(1).fill(newName);
        await page.getByRole("textbox").nth(1).press("Enter");
    },

    async properties(page: Page, name: string) {
        await Rows.actionByRowTitle(page, name, "click");
        await page.getByText("Properties").click();
    }
};

export const Components = {
    async projectSwitcher(page: Page, action: "click" | "hover") {
        const loc = page.locator("div.project-switcher").first();
        await loc[action]();
    },

    async clickRefreshAndWait(page: Page, waitForMs: number = 500) {
        await page.locator(".refresh-icon").click();
        if (waitForMs > 0) await page.waitForTimeout(waitForMs);
    },

    async setSidebarSticky(page: Page) {
        await page.getByRole("link", {name: "Go to Files"}).hover();
        await page.getByRole("banner").locator("svg").click();
    }
}