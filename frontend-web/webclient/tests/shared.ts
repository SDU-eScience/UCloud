import {expect, type Page} from "@playwright/test";

// Note(Jonas): If it complains that it doesn"t exist, create it.
import {default as data} from "./test_data.json" with {type: "json"};

const user = data.users.with_resources;

export const User = {
    async toLoginPage(page: Page): Promise<void> {
        await page.goto(ucloudUrl("login"));
    },

    async login(page: Page): Promise<void> {
        if (!user) throw Error("No username or password provided");
        await this.toLoginPage(page);
        await page.getByText("Other login options →").click();
        await page.getByRole("textbox", {name: "Username"}).fill(user.username);
        await page.getByRole("textbox", {name: "Password"}).fill(user.password);
        await page.getByRole("button", {name: "Login"}).click();
    },


    async logout(page: Page) {
        await Components.toggleUserMenu(page);
        await page.getByText("Logout").click();
    },
}

export function ucloudUrl(pathname: string): string {
    const origin = data.location_origin;
    return (origin.endsWith("/") ? origin : origin + "/") + "app" + (pathname.startsWith("/") ? pathname : "/" + pathname);
};

export const Rows = {
    async actionByRowTitle(page: Page, name: string, action: "click" | "dblclick" | "hover"): Promise<void> {
        let iterations = 1000;
        await Components.projectSwitcher(page, "click");
        await page.keyboard.press("Escape");
        for (let i = 0; i < 10; i++) {
            await page.locator(".scrolling").hover();
            await page.mouse.wheel(0, -5000);
            await page.waitForTimeout(50);
        }

        while (!await page.locator(".row div > span", {hasText: name}).isVisible()) {
            await page.locator(".scrolling").hover();
            await page.mouse.wheel(0, 150);
            await page.waitForTimeout(50);
            iterations -= 1;
            if (iterations <= 0) {
                console.warn("Many such iterations, no result");
                break;
            }
        }
        await page.locator(".row div > span", {hasText: name})[action]();
    },

    async open(page: Page, resourceTitle: string): Promise<void> {
        await this.actionByRowTitle(page, resourceTitle, "dblclick");
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
        await page.waitForTimeout(1000); // We need to wait for Redux to propagate the changes of the drive, for use with the upload.
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
        await page.locator("img[class=search-icon]").click();
        await page.getByRole("textbox").fill(query);
        await page.keyboard.press("Enter");
    },

    async toggleFavorite(page: Page, name: string): Promise<void> {
        await this.actionByRowTitle(page, name, "hover");
        // TODO(Jonas): This will click the first favorited rows icon, not necessarily the one we want.
        await page.getByRole('img', {name: 'Star', includeHidden: false}).first().click();
    },

    async ensureDialogDriveActive(page: Page, driveName: string): Promise<void> {
        // Check locator input for content
        while (!await page.locator("div.location").isVisible()) {
            await page.waitForTimeout(500);
        }

        await page.waitForTimeout(500);

        const correctDrive = await page.getByRole("dialog")
            .getByRole('listitem', {name: driveName}).isVisible();

        if (correctDrive) {
            // Already matches. No work to be done.
            return;
        }

        // if not matches, click
        await page.getByRole("dialog").locator("div.drive-icon-dropdown").click();
        await page.getByText(driveName).click();
        await page.waitForTimeout(200);
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
        const loc = page.locator(`div[data-component="project-switcher"]`).first();
        await loc[action]();
    },

    async clickRefreshAndWait(page: Page, waitForMs: number = 500): Promise<void> {
        await page.locator(".refresh-icon").click();
        if (waitForMs > 0) await page.waitForTimeout(waitForMs);
    },

    async toggleSearch(page: Page): Promise<void> {
        await page.locator("svg[data-component=icon-heroMagnifyingGlass]").click();
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
        await page.getByRole('cell', {name: "u1-standard-1", disabled: false}).first().click();
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

    async toggleUserMenu(page: Page): Promise<void> {
        await page.locator(".SIDEBAR_IDENTIFIER > div[class^=flex] > div[class^=dropdown]").last().click();
    }
};

export const Applications = {
    ...Rows,
    async goToApplications(page: Page): Promise<void> {
        await page.getByRole("link", {name: "Go to Applications"}).click();
    },

    async openApp(page: Page, appName: string, exact: boolean = true): Promise<void> {
        this.goToApplications(page);
        await Components.projectSwitcher(page, "hover");
        const locatorString = exact ? `img[alt='${appName}']` : `img[alt^='${appName}']`;
        let iterations = 1000;
        await page.mouse.wheel(0, -Number.MAX_SAFE_INTEGER);
        while (!await page.locator(locatorString).first().isVisible()) {
            await page.mouse.wheel(0, 150);
            await page.waitForTimeout(50);
            iterations -= 1;
            if (iterations <= 0) {
                console.warn("Many such iterations, no result");
                break;
            }
        }

        await page.locator(locatorString).first().click();
    },

    async toggleFavorite(page: Page): Promise<void> {
        // Ensure mount of svg before checking value
        await page.waitForTimeout(500);
        const isFavorited = await page.locator("svg[data-component='icon-starFilled']").isVisible();
        const emptyIcon = "icon-starEmpty";
        const filledIcon = "icon-starFilled";
        await page.locator(`svg[data-component=${isFavorited ? filledIcon : emptyIcon}]`).click();
        // Expect flipped data-component value.
        await page.locator(`svg[data-component=${isFavorited ? emptyIcon : filledIcon}]`).isVisible();
    },

    async searchFor(page: Page, query: string): Promise<void> {
        await Components.toggleSearch(page);
        await page.getByRole("textbox").fill(query);
        await page.keyboard.press("Enter");
    },
};

export const Runs = {
    newJobName(): string {
        return Help.newResourceName("JobName");
    },

    async goToRuns(page: Page): Promise<void> {
        await page.getByRole("link", {name: "Go to Runs"}).click();
    },

    async runApplicationAgain(page: Page, jobName: string): Promise<void> {
        await Runs.goToRuns(page);
        await Components.projectSwitcher(page, "hover");
        await Applications.actionByRowTitle(page, jobName, "click");
        await page.getByText("Run application again").click();
        await page.waitForTimeout(500);
        await Runs.submitAndWaitForRunning(page);
    },

    async stopRun(page: Page, jobName: string): Promise<void> {
        await this.goToRuns(page);
        await Components.projectSwitcher(page, "hover");
        await page.locator(".row").getByText(jobName).click();
        await Components.clickConfirmationButton(page, "Stop");
        await page.waitForTimeout(500);
    },

    async setJobTitle(page: Page, name: string): Promise<void> {
        await page.getByRole("textbox", {name: "Job name"}).fill(name);
    },

    async addFolderResource(page: Page, driveName: string, folderName: string): Promise<void> {
        await page.getByRole("button", {name: "Add folder"}).click();
        await page.getByRole("textbox", {name: "No directory selected"}).click();

        await File.ensureDialogDriveActive(page, driveName);
        await page.getByRole("dialog").locator(".row", {hasText: folderName}).getByRole("button", {name: "Use"}).click();
    },

    async submitAndWaitForRunning(page: Page, extension?: 1 | 8 | 24): Promise<void> {
        if (extension != null) {
            await this.extendTimeBy(page, extension);
        }

        await page.getByRole("button", {name: "Submit"}).click();

        while (!await page.getByText("is now running").first().isVisible()) {
            await page.waitForTimeout(500);
        }
    },

    async extendTimeBy(page: Page, extension: 1 | 8 | 24) {
        await page.getByRole("button", {name: `+${extension}`}).click();
    },

    async openTerminal(page: Page): Promise<Page> {
        const terminalPagePromise = page.waitForEvent("popup");
        await page.getByRole("button", {name: "Open terminal"}).click();
        const terminalPage = await terminalPagePromise;
        await terminalPage.waitForTimeout(1000);
        await terminalPage.getByText("ucloud@").click();
        return terminalPage;
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
            await Rows.actionByRowTitle(page, name, "click");
            await Components.clickConfirmationButton(page, "Delete");
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
            await page.getByRole("dialog").getByText("Activate").click();
            // TODO(Jonas): Find out how to get the name of the newly activated license.
        }
    },
}

export const Terminal = {
    async enterCmd(page: Page, command: string) {
        for (const key of command) {
            await page.keyboard.press(key);
        }

        await page.keyboard.press("Enter");
    }
}


const Help = {
    newResourceName(namespace: string): string {
        return namespace + Math.random().toString().slice(2, 7);
    }
};