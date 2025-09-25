import {expect, type Page} from "@playwright/test";

// Note(Jonas): If it complains that it doesn"t exist, create it.
import {default as data} from "./test_data.json" with {type: "json"};

const user = data.users.with_resources;

export async function login(page: Page): Promise<void> {
    if (!user) throw Error("No username or password provided");
    await page.goto(ucloudUrl("login"));
    await page.getByText("Other login options →").click();
    await page.getByRole("textbox", {name: "Username"}).fill(user.username);
    await page.getByRole("textbox", {name: "Password"}).fill(user.password);
    await page.getByRole("button", {name: "Login"}).click();
};

export function ucloudUrl(pathname: string): string {
    return data.location_origin + "/app" + (pathname.startsWith("/") ? pathname : "/" + pathname);
};

export const Rows = {
    async actionByRowTitle(page: Page, name: string, action: "click" | "dblclick" | "hover"): Promise<void> {
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

    async open(page: Page, resourceTitle: string): Promise<void> {
        this.actionByRowTitle(page, resourceTitle, "dblclick");
    },

    async rename(page: Page, oldName: string, newName: string): Promise<void> {
        await Rows.actionByRowTitle(page, oldName, "click");
        await page.getByText("Rename").click();
        await page.getByRole("textbox").nth(1).fill(newName);
        await page.getByRole("textbox").nth(1).press("Enter");
    }
};

export const File = {
    ...Rows,
    newFolderName(): string {
        return Help.newResourceName("FolderName");
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

    async openOperationsDropsdown(page: Page, file: string): Promise<void> {
        await File.actionByRowTitle(page, file, "click");
        await page.locator(".operation.button6.in-header:nth-child(6)").click();
    },

    async moveFileTo(page: Page, fileToMove: string, targetFolder: string): Promise<void> {
        await this.openOperationsDropsdown(page, fileToMove);
        await page.getByText("Move to...").click();
        await page.locator("div.ReactModal__Content div.row", {hasText: targetFolder})
            .getByRole("button").filter({hasText: "Move to"}).click();
        await page.waitForTimeout(200);
    },

    async copyFileTo(page: Page, fileToCopy: string, targetFolder: string): Promise<void> {
        await this.openOperationsDropsdown(page, fileToCopy);
        await page.getByText("Copy to...").click();

        await page.locator("div.ReactModal__Content div.row", {hasText: targetFolder})
            .getByRole("button").filter({hasText: "Copy to"}).click();

        await page.waitForTimeout(200);
    },

    async copyFileInPlace(page: Page, folderName: string): Promise<void> {
        await this.openOperationsDropsdown(page, folderName);
        await page.getByText("Copy to...").click();
        await page.getByText("Use this folder").first().click();
        await page.waitForTimeout(200);
    },

    async moveFileToTrash(page: Page, fileName: string): Promise<void> {
        await this.openOperationsDropsdown(page, fileName);
        await Components.clickConfirmationButton(page, "Move to trash");

    },

    async emptyTrash(page: Page): Promise<void> {
        await page.getByText("Empty Trash").click();
        await page.getByRole("button", {name: "Empty trash"}).click();
    },

    async searchFor(page: Page, query: string): Promise<void> {
        await Components.toggleSearch(page);
        await page.getByRole("textbox").fill(query);
        await page.keyboard.press("Enter");
    },

    async toggleFavorite(page: Page, name: string): Promise<void> {
        await this.actionByRowTitle(page, name, "hover");
        // TODO(Jonas): This will click the first favorited rows icon, not necessarily the one we want.
        await page.getByRole('img', {name: 'Star', includeHidden: false}).first().click();
    }
};

export const Drive = {
    ...Rows,
    newDriveName(): string {
        return Help.newResourceName("DriveName");
    },

    async goToDrives(page: Page): Promise<void> {
        await page.getByRole("link", {name: "Go to Files"}).click();
        await Components.projectSwitcher(page, "hover");
    },

    async openDrive(page: Page, name: string): Promise<void> {
        if (!page.url().includes("/app/drives")) {
            await this.goToDrives(page);
        }
        await this.actionByRowTitle(page, name, "dblclick");
    },

    async create(page: Page, name: string): Promise<void> {
        await this.goToDrives(page);
        await page.waitForTimeout(200);
        await page.locator("div.operation").filter({hasText: "Create drive"}).click();
        await page.getByRole("textbox", {name: "Choose a name"}).fill(name);
        await page.getByRole("button", {name: "Create", disabled: false}).click();
    },

    async delete(page: Page, name: string): Promise<void> {
        await this.goToDrives(page);
        await Rows.actionByRowTitle(page, name, "click");
        await page.getByText("Delete").click();
        await page.locator("#collectionName").fill(name);
        await page.getByRole("button", {name: "I understand what I am doing", disabled: false}).click();
    },

    async permissions(page: Page, name: string): Promise<void> {
        await Rows.actionByRowTitle(page, name, "click");
        await page.getByText("Permissions").click();
    },

    async properties(page: Page, name: string): Promise<void> {
        await Rows.actionByRowTitle(page, name, "click");
        await page.getByText("Properties").click();
    }
};

export const Components = {
    async projectSwitcher(page: Page, action: "click" | "hover"): Promise<void> {
        const loc = page.locator("div.project-switcher").first();
        await loc[action]();
    },

    async clickRefreshAndWait(page: Page, waitForMs: number = 500): Promise<void> {
        await page.locator(".refresh-icon").click();
        if (waitForMs > 0) await page.waitForTimeout(waitForMs);
    },

    async toggleSearch(page: Page): Promise<void> {
        await page.locator(".search-icon").click();
    },

    async setSidebarSticky(page: Page): Promise<void> {
        await page.getByRole("link", {name: "Go to Files"}).hover();
        await page.getByRole("banner").locator("svg").click();
    },

    async clickConfirmationButton(page: Page, text: string, delay = 1200): Promise<void> {
        await page.getByRole('button', {name: text}).click({delay});
    },

    async selectAvailableMachineType(page: Page): Promise<void> {
        await page.getByText('No machine type selected').click();
        // Find a way of getting the first non-disabled table-row with a product
        await page.getByRole('cell', {name: "standard-cpu-1", disabled: false}).first().click();
    },

    async selectAvailableProduct(page: Page): Promise<void> {
        await page.getByText('No product selected').click();
        for (const row of await page.locator("tbody > tr", {}).all()) {
            if (await row.isDisabled() === false) {
                await row.click();
                return;
            }
        }
        throw Error("No available product found");
    },

    async toggleTasksDialog(page: Page) {
        // Note(Jonas): Selector for class that starts with 'static-circle'.
        await page.locator("[class*='static-circle']").click();
    },
};

export const Applications = {
    ...Rows,
    async gotoApplications(page: Page): Promise<void> {
        await page.getByRole("link", {name: "Go to Applications"}).click();
    },

    async gotoRuns(page: Page): Promise<void> {
        await page.getByRole("link", {name: "Go to Runs"}).click();
    }
};

export const Runs = {
    newJobName(): string {
        return Help.newResourceName("JobName");
    },

    async goToRuns(page: Page): Promise<void> {
        await page.getByRole("link", {name: "Go to Runs"}).click();
    }
};

export const Project = {
    newProjectName(): string {
        return Help.newResourceName("ProjectName");
    },

    async changeTo(page: Page, projectName: string): Promise<void> {
        await Components.projectSwitcher(page, "click");
        await page.getByText(projectName).click();
    },

    async create(page: Page, projectName: string): Promise<void> {
        throw Error("Not implemented")
    },
};

export const Resources = {
    ...Rows,
    async goTo(page: Page, resource: "Links" | "IP addresses" | "SSH keys" | "Licenses") {
        await page.getByRole("link", {name: "Go to Resources"}).hover();
        await page.getByText(resource).click();
        await Components.projectSwitcher(page, "hover");
    },

    PublicLinks: {
        newPublicLinkName(): string {
            return Help.newResourceName("PublicLink");
        },

        async createNew(page: Page, publicLinkName?: string): Promise<string> {
            const name = publicLinkName ?? this.newPublicLinkName();
            await page.getByText("Create public link").click();
            // Note(Jonas): nth(1) because we are skipping the hidden search field
            await page.getByRole("textbox").nth(1).fill(name);
            await Components.selectAvailableProduct(page);
            return name;
            //return `app-${name}.dev.cloud.sdu.dk`;
        },

        async delete(page: Page, name: string): Promise<void> {
            await Rows.actionByRowTitle(page, name, "click");
            await Components.clickConfirmationButton(page, "Delete");
        }
    },

    IPs: {
        async createNew(page: Page): Promise<void> {
            await page.getByText("Create public IP").click();
            await Components.selectAvailableProduct(page);
            await this.fillPortRowInDialog(page);
        },

        async fillPortRowInDialog(page: Page): Promise<void> {
            await page.getByRole("dialog").locator("input").first().fill("123");
            await page.getByRole("dialog").locator("input").nth(1).fill("321");
            await page.locator("td > button").click();
            await page.getByRole("button", {name: "create", disabled: false}).click();
        },

        async delete(page: Page, name: string): Promise<void> {
            throw Error("Not implemented");
        }
    },

    SSHKeys: {
        newSSHKeyName(): string {
            return Help.newResourceName("SSHKey")
        },

        async delete(page: Page, name: string): Promise<void> {
            throw Error("Not implemented");
        },

        async createNew(page: Page): Promise<string> {
            await page.getByText("Add SSH key").click();
            const title = this.newSSHKeyName();
            await page.locator("#key-title").fill(title);
            await page.locator("#key-contents").fill("ssh-rsa" + "a-bunch-of-text");
            await page.getByRole("button", {name: "Add SSH key"}).click();
            return title;
        }
    },

    Licenses: {
        async activateLicense(page: Page): Promise<void> {
            await page.getByText("Activate license").click();
            await Components.selectAvailableProduct(page);
            await page.getByText("Activate").click();
            // TODO(Jonas): Find out how to get the name of the newly activated license.
        }
    },
}


const Help = {
    newResourceName(namespace: string): string {
        return namespace + Math.random().toString().slice(2, 7);
    }
};