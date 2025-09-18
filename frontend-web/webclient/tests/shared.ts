import {expect, type Page} from "@playwright/test";

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
        let iterations = 1000;

        await page.locator(".scrolling").hover();
        await page.mouse.wheel(0, -Number.MAX_SAFE_INTEGER);

        while (!await page.locator("div > span", {hasText: name}).isVisible()) {
            await page.locator(".scrolling").hover();
            await page.mouse.wheel(0, 150);
            await page.waitForTimeout(50);
            iterations -= 1;
            if (iterations <= 0) {
                console.warn("Many such iterations, no result");
                break;
            }
        }
        await page.locator("div > span", {hasText: name})[action]();
    },

    async rename(page: Page, oldName: string, newName: string): Promise<void> {
        await Rows.actionByRowTitle(page, oldName, "click");
        await page.getByText("Rename").click();
        await page.getByRole("textbox").nth(1).fill(newName);
        await page.getByRole("textbox").nth(1).press("Enter");
    }
};

export const Folder = {
    ...Rows,
    newFolderName(): string {
        return "FolderName" + Math.random().toString().slice(2, 7);
    },

    async create(page: Page, name: string): Promise<void> {
        expect(page.url().includes("/files?path=")).toBeTruthy();
        await page.getByText("Create folder").click();
        await page.getByRole("textbox").nth(1).fill(name);
        await page.getByRole("textbox").nth(1).press("Enter");
    },

    async delete(page: Page, name: string): Promise<void> {
        await this.actionByRowTitle(page, name, "click");
        await page.locator(".operation.button6.in-header:nth-child(6)").click(); // Ellipses
        await Components.clickConfirmationButton(page, "Move to trash");
    },

    async uploadFiles(page: Page, files: {name: string, contents: string}[]): Promise<void> {
        await page.waitForTimeout(500); // We need to wait for Redux to propagate the changes of the drive, for use with the upload.
        await page.getByText("Upload files").click();
        await page.locator("#fileUploadBrowse").setInputFiles(files.map(f => ({
            name: f.name,
            mimeType: "text/plain",
            buffer: Buffer.from(f.contents),
        })));
        await page.waitForTimeout(1000);
        await page.keyboard.press("Escape");
        await Components.clickRefreshAndWait(page);
    },

    async moveFileTo(page: Page, fileToMove: string, targetFolder: string): Promise<void> {
        await Folder.actionByRowTitle(page, fileToMove, "click");
        await page.locator(".operation.button6.in-header:nth-child(6)").click();
        await page.getByText('Move to...').click();

        await page.locator("div.ReactModal__Content div.row", {hasText: targetFolder})
            .getByRole("button").filter({hasText: "Move to"}).click();

        await page.waitForTimeout(200);
    },

    async copyFileTo(page: Page, fileToCopy: string, targetFolder: string): Promise<void> {
        await Folder.actionByRowTitle(page, fileToCopy, "click");
        await page.locator(".operation.button6.in-header:nth-child(6)").click();
        await page.getByText('Copy to...').click();

        await page.locator("div.ReactModal__Content div.row", {hasText: targetFolder})
            .getByRole("button").filter({hasText: "Copy to"}).click();

        await page.waitForTimeout(200);
    },

    async copyFileInPlace(page: Page, folderName: string): Promise<void> {
        await Folder.actionByRowTitle(page, folderName, "click");
        await page.locator(".operation.button6.in-header:nth-child(6)").click();
        await page.getByText("Copy to...").click();
        await page.getByText("Use this folder").first().click();
        await page.waitForTimeout(200);
    },
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
        await page.getByRole("textbox", {name: "Choose a name"}).fill(name);
        await page.getByRole("button", {name: "Create", disabled: false}).click();
    },

    async delete(page: Page, name: string) {
        await this.goToDrives(page);
        await Rows.actionByRowTitle(page, name, "click");
        await page.getByText("Delete").click();
        await page.locator("#collectionName").fill(name);
        await page.getByRole("button", {name: "I understand what I am doing", disabled: false}).click();
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
    },

    async clickConfirmationButton(page: Page, text: string, delay = 1200) {
        await page.getByRole('button', {name: text}).click({delay});
    }
}

export const Applications = {
    async gotoApplications(page: Page) {
        await page.getByRole('link', {name: 'Go to Applications'}).click();
    }
}

export const Runs = {
    newJobName() {
        return "JobName" + Math.random().toString().slice(2, 7);
    },
    async goToRuns(page: Page) {
        await page.getByRole('link', {name: 'Go to Runs'}).click();
    }
}