import {expect, type Page} from "@playwright/test";
import fs from "fs";

// Note(Jonas): If it complains that it doesn"t exist, create it.
import {default as data} from "./test_data.json" with {type: "json"};

const LoginPageUrl = ucloudUrl("login");

export const User = {
    newUserCredentials(): {username: string; password: string;} {
        const username = Help.newResourceName("user");
        return {username, password: username + "_" + username};
    },

    async toLoginPage(page: Page): Promise<void> {
        await page.goto(LoginPageUrl);
    },

    async login(page: Page, user: {username: string; password: string;}, fillUserInfo: boolean = false): Promise<void> {
        await this.toLoginPage(page);
        await page.getByRole("textbox", {name: "Username"}).fill(user.username);
        await page.getByRole("textbox", {name: "Password"}).fill(user.password);
        await page.getByRole("button", {name: "Login"}).click();

        if (fillUserInfo) {
            await this.dismissAdditionalInfoPrompt(page);
            await this.setAdditionalUserInfo(page);
            await this.disableNotifications(page);
        }
    },


    async logout(page: Page): Promise<void> {
        await Components.toggleUserMenu(page);
        await page.getByText("Logout").click();
        await page.waitForURL(LoginPageUrl);
    },

    async create(page: Page, user: {username: string; password: string}): Promise<void> {
        await page.getByRole("link", {name: "Go to Admin"}).hover();
        await page.getByRole("link", {name: "User creation"}).click();

        await page.getByRole("textbox", {name: "Username"}).fill(user.username);
        await page.getByRole("textbox", {name: "Password", exact: true}).fill(user.password);
        await page.getByRole("textbox", {name: "Repeat password", exact: true}).fill(user.password);

        await page.getByRole("textbox", {name: "Email"}).fill(user.username + "@mail.dk");

        await page.getByRole("textbox", {name: "First names"}).fill(user.username)
        await page.getByRole("textbox", {name: "Last name"}).fill(Math.random() > .5 ? "the Second" : "the Third");

        await NetworkCalls.awaitResponse(page, "**/auth/users/register", async () => {
            await page.getByRole("button", {name: "Create user"}).click();
        });
    },

    async setAdditionalUserInfo(page: Page): Promise<void> {
        await Components.toggleUserMenu(page);
        await NetworkCalls.awaitResponse(page, "**/retrieveOptionalInfo**", async () => {
            await page.getByText("Settings").click();
        });
        await this.fillOutUserInfo(page);
    },

    async fillOutUserInfo(page: Page) {
        await page.getByRole("textbox", {name: "Organization"}).fill("Test");
        await page.keyboard.press("Escape");
        await page.getByRole("textbox", {name: "Department"}).fill("Not available");
        await page.keyboard.press("Escape");
        await page.getByRole("textbox", {name: "Position"}).fill("Administrative Staff");
        await page.keyboard.press("Escape");
        await page.getByRole("textbox", {name: "Unit"}).fill("Unit division");
        await page.getByRole("textbox", {name: "Primary research field"}).fill("0.1 Other");
        await page.getByRole("textbox", {name: "Gender"}).fill("Another gender identity");
        await page.getByRole("button", {name: "Update information"}).nth(1).click();
    },

    async disableNotifications(page: Page): Promise<void> {
        const emailSettings = [
            "Application approved",
            "Application rejected",
            "Application withdrawn",
            "New application received",
            "Status change by other admins",
            "Transfers from other projects",
            "New comment in application",
            "Application has been edited",
        ];

        for (const setting of emailSettings) {
            await page.getByText(setting).first().click();
        }

        await page.getByRole("button", {name: "Update email settings"}).click();

        await page.getByText("Job started or stopped").nth(1).click();
        await page.getByRole("button", {name: "Update notification settings"}).click();
    },

    async dismissAdditionalInfoPrompt(page: Page): Promise<void> {
        await page.getByText("Additional user information").waitFor();
        await page.keyboard.press("Escape");
    }
}

export function ucloudUrl(pathname: string): string {
    const origin = data.location_origin;
    return (origin + "/app/" + pathname).replaceAll("//", "/");
}

export const Rows = {
    async actionByRowTitle(page: Page, name: string, action: "click" | "dblclick" | "hover" | "rightclick"): Promise<void> {
        let iterations = 1000;
        await page.locator(".scrolling").hover();
        for (let i = 0; i < 50; i++) {
            await page.mouse.wheel(0, -5000);
        }

        const locator = page.locator(".row div > span", {hasText: name});

        while (!await locator.isVisible()) {
            await page.mouse.wheel(0, 150);
            iterations -= 1;
            if (iterations <= 0) {
                console.warn("Many such iterations, no result");
                break;
            }
        }
        switch (action) {
            case "rightclick": {
                await locator.click({button: "right"})
                break;
            }
            default: {
                await locator[action]();
                break;
            }
        }
    },
};

export const File = {
    ...Rows,

    async open(page: Page, filename: string) {
        await NetworkCalls.awaitResponse(page, "**/api/files/browse**", async () => {
            await this.actionByRowTitle(page, filename, "dblclick");
        });
    },

    async goToSharedByMe(page: Page): Promise<void> {
        await page.getByRole("link", {name: "Go to Files"}).hover();
        await page.getByRole("link", {name: "Shared by me"}).click();
    },

    async goToSharedWithMe(page: Page): Promise<void> {
        await page.getByRole("link", {name: "Go to Files"}).hover();
        await page.getByRole("link", {name: "Shared with me"}).click();
    },

    newFolderName(): string {
        return Help.newResourceName("FolderName");
    },

    async rename(page: Page, oldName: string, newName: string): Promise<void> {
        await NetworkCalls.awaitResponse(page, "**/api/files/move", async () => {
            await _move(page, oldName, newName);
        });
    },

    async create(page: Page, name: string): Promise<void> {
        await NetworkCalls.awaitResponse(page, "**/files/folder", async () => {
            await page.locator('div[data-disabled="false"]', {hasText: "Create folder"}).click();
            await page.getByRole("textbox").nth(1).fill(name);
            await page.getByRole("textbox").nth(1).press("Enter");
        });
    },

    async moveToTrash(page: Page, name: string): Promise<void> {
        await NetworkCalls.awaitResponse(page, "**/files/trash", async () => {
            await this.actionByRowTitle(page, name, "click");
            await page.locator(".operation.button6.in-header:nth-child(6)").click(); // Ellipses
            await Components.clickConfirmationButton(page, "Move to trash");
        });
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
        await NetworkCalls.awaitResponse(page, "**/api/files/move", async () => {
            await page.getByRole("dialog").locator("div.row", {hasText: targetFolder})
                .getByRole("button").filter({hasText: "Move to"}).click();
        })
        await page.getByRole("dialog").waitFor({state: "hidden"});
    },

    async copyFileTo(page: Page, fileToCopy: string, targetFolder: string): Promise<void> {
        await this.openOperationsDropsdown(page, fileToCopy);
        await page.getByText("Copy to...").click();

        await NetworkCalls.awaitResponse(page, "**/api/files/copy", async () => {
            await page.getByRole("dialog").locator("div.row", {hasText: targetFolder})
                .getByRole("button").filter({hasText: "Copy to"}).click();
        });

        await page.getByRole("dialog").waitFor({state: "hidden"});
    },

    async copyFileInPlace(page: Page, folderName: string): Promise<void> {
        await this.openOperationsDropsdown(page, folderName);
        await NetworkCalls.awaitResponse(page, "**/api/files/browse?path=**", async () => {
            await page.getByText("Copy to...").click();
        })
        await NetworkCalls.awaitResponse(page, "**/api/files/copy", async () => {
            await page.getByText("Use this folder").first().click();
        });
        await page.getByRole("dialog", {name: "Files copied"}).waitFor({state: "hidden"});
    },

    async moveFileToTrash(page: Page, fileName: string): Promise<void> {
        await NetworkCalls.awaitResponse(page, "**/api/files/trash", async () => {
            await this.openOperationsDropsdown(page, fileName);
            await Components.clickConfirmationButton(page, "Move to trash");
        });
    },

    async emptyTrash(page: Page): Promise<void> {
        await NetworkCalls.awaitResponse(page, "**/api/files/emptyTrash", async () => {
            await page.getByText("Empty Trash").click();
            await page.getByRole("button", {name: "Empty trash"}).click();
        });
    },

    async searchFor(page: Page, query: string): Promise<void> {
        await page.locator("img[class=search-icon]").click();
        await page.getByRole("textbox").fill(query);
        await page.keyboard.press("Enter");
    },

    async toggleFavorite(page: Page, name: string): Promise<void> {
        await NetworkCalls.awaitResponse(page, "**/api/files/metadata", async () => {
            await this.actionByRowTitle(page, name, "hover");
            // TODO(Jonas): This will click the first favorited rows icon, not necessarily the one we want.
            await page.getByRole('img', {name: 'Star', includeHidden: false}).first().click();
        });
    },

    async ensureDialogDriveActive(page: Page, driveName: string): Promise<void> {
        // Check locator input for content
        await page.getByRole("dialog").isVisible();
        const correctDrive = await page.getByRole("dialog")
            .getByRole('listitem', {name: driveName}).isVisible();

        if (correctDrive) {
            // Already matches. No work to be done.
            return;
        }

        // if not matches, click
        await page.getByRole("dialog").locator("div.drive-icon-dropdown").click();
        await NetworkCalls.awaitResponse(page, "**/api/files/browse?**", async () => {
            await page.getByText(driveName).last().click();
        });
    },

    async download(page: Page, filename: string): Promise<string> {
        const downloadPromise = page.waitForEvent("download");
        await File.actionByRowTitle(page, filename, "click");
        await page.getByText("Download").click();
        const dl = await downloadPromise;
        return fs.readFileSync(await dl.path(), {encoding: 'utf8', flag: 'r'});
    },

    async openIntegratedTerminal(page: Page): Promise<void> {
        await page.getByText("Open terminal").click();
        await page.getByText("/work").waitFor({state: "visible"});
        await page.getByText("/work").click();
    },

    async createShareLinkForFolder(page: Page, foldername: string): Promise<string> {
        await this._openShareModal(page, foldername);
        await page.getByText("Create link").click();
        return await page.getByRole("dialog").getByRole("textbox").nth(1)?.innerText() ?? "";
    },

    async shareFolderWith(page: Page, foldername: string, username: string): Promise<void> {
        await this._openShareModal(page, foldername);
        await page.getByRole("dialog").getByRole("textbox").fill(username);
        await page.getByRole("button").getByText("Share").click();
    },

    async _openShareModal(page: Page, foldername: string): Promise<void> {
        await this.openOperationsDropsdown(page, foldername);
        await this.actionByRowTitle(page, foldername, "rightclick");
        await page.getByText("Share6").click();
    },

    async triggerStorageScan(page: Page, driveName: string): Promise<void> {
        await Drive.openDrive(page, driveName);
        const folder = "trigger" + this.newFolderName();
        await File.create(page, folder);
        await File.moveFileToTrash(page, folder);
        // Note(Jonas): Trash folder doesn't show up until refresh if not already present.
        await Components.clickRefreshAndWait(page);
        await File.open(page, "Trash");
        await File.emptyTrash(page);
    }
};

export const Drive = {
    ...Rows,
    newDriveName(): string {
        return Help.newResourceName("DriveName");
    },

    async goToDrives(page: Page): Promise<void> {
        if (page.url().endsWith("/app/drives")) return;
        await NetworkCalls.awaitResponse(page, "**/api/files/collections/browse**", async () => {
            await page.getByRole("link", {name: "Go to Files"}).click();
            await Components.projectSwitcher(page, "hover");
        });
    },

    async rename(page: Page, oldName: string, newName: string): Promise<void> {
        await NetworkCalls.awaitResponse(page, "**/api/files/collections/rename", async () => {
            await this.actionByRowTitle(page, oldName, "click");
            await _move(page, oldName, newName);
        });
    },

    async openDrive(page: Page, name: string): Promise<void> {
        if (!page.url().includes("/app/drives")) {
            await this.goToDrives(page);
        }

        await NetworkCalls.awaitResponse(page, "**/api/files/browse**", async () => {
            await this.actionByRowTitle(page, name, "dblclick");
            await Components.projectSwitcher(page, "hover")
        })
    },

    async create(page: Page, name: string): Promise<void> {
        await this.goToDrives(page);
        await NetworkCalls.awaitResponse(page, "**/api/files/collections", async () => {
            await page.locator('div[data-disabled="false"]', {hasText: "Create drive"}).click();
            await page.getByRole("textbox", {name: "Choose a name"}).fill(name);
            await page.getByRole("button", {name: "Create", disabled: false}).click();
        })
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
        await page.locator(`div[data-component="project-switcher"]`).first().waitFor();
        const loc = page.locator(`div[data-component="project-switcher"]`).first();
        await loc[action]();
    },

    async dismissProviderConnect(page: Page): Promise<void> {
        await page.getByText("Snooze").click();
        await page.getByText("Snooze").waitFor({state: "hidden"});
    },

    async clickRefreshAndWait(page: Page): Promise<void> {
        await page.locator(".refresh-icon").click();
    },

    async toggleSearch(page: Page): Promise<void> {
        await page.locator("svg[data-component=icon-heroMagnifyingGlass]").click();
    },

    async setSidebarSticky(page: Page): Promise<void> {
        await page.getByRole("link", {name: "Go to Files"}).hover();
        await page.getByRole("banner").locator("svg").click();
    },

    async clickConfirmationButton(page: Page, text: string, delay = 1500): Promise<void> {
        await page.getByRole('button', {name: text}).click({delay});
    },

    async selectAvailableMachineType(page: Page): Promise<void> {
        await page.getByText('No machine type selected').first().click();
        await page.getByRole('cell', {
            name: data.products_by_provider_and_type.k8s.COMPUTE.name,
            disabled: false
        }).first().click();
    },

    async selectAvailableProduct(page: Page): Promise<void> {
        await page.getByText('No product selected').click();
        for (const row of await page.locator("tbody > tr").all()) {
            if (!await row.isDisabled()) {
                await row.click();
                return;
            }
        }
        throw Error("No available product found");
    },

    async toggleTasksDialog(page: Page) {
        await page.locator("[class*='static-circle']").click();
    },

    async toggleUserMenu(page: Page): Promise<void> {
        await page.locator(".SIDEBAR_IDENTIFIER > div[class^=flex] > div[class^=dropdown]").last().click();
    },

    async useDialogBrowserItem(page: Page, rowTitle: string, buttonName: string = "Use") {
        await page.getByRole("dialog").locator(".row", {hasText: rowTitle}).getByRole("button", {name: buttonName}).click();
    }
};

export const Applications = {
    ...Rows,
    async goToApplications(page: Page): Promise<void> {
        if (page.url().endsWith("/app/applications")) return;
        await NetworkCalls.awaitResponse(page, "**/api/hpc/apps/retrieveGroupLogo**", async () => {
            await page.getByRole("link", {name: "Go to Applications"}).click();
        })
    },

    async openApp(page: Page, appName: string, exact: boolean = true): Promise<void> {
        await this.goToApplications(page);
        await Components.projectSwitcher(page, "hover");
        const locatorString = exact ? `img[alt='${appName}']` : `img[alt^='${appName}']`;
        let iterations = 1000;
        await page.mouse.wheel(0, -Number.MAX_SAFE_INTEGER);
        while (await page.locator(locatorString).first().isHidden()) {
            await page.mouse.wheel(0, 150);
            iterations -= 1;
            if (iterations <= 0) {
                console.warn("Many such iterations, no result");
                break;
            }
        }

        await page.locator(locatorString).first().click();
        await page.waitForURL("**/app/jobs/create?app=**")
    },


    async toggleFavorite(page: Page): Promise<void> {
        // Ensure mount of svg before checking value
        await page.locator("svg[data-component^='icon-star']").hover();
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
        await NetworkCalls.awaitResponse(page, "**/hpc/apps/search", async () => {
            await page.keyboard.press("Enter");
        })
    },
};

export const Runs = {
    newJobName(): string {
        return Help.newResourceName("JobName");
    },

    async goToRuns(page: Page): Promise<void> {
        await NetworkCalls.awaitResponse(page, "**/api/jobs/browse**", async () => {
            await page.getByRole("link", {name: "Go to Runs"}).click();
        })
    },

    async runApplicationAgain(page: Page, jobName: string): Promise<void> {
        await Runs.goToRuns(page);
        await NetworkCalls.awaitResponse(page, "**/api/jobs/retrieve?id=**", async () => {
            await Components.projectSwitcher(page, "hover");
            await Applications.actionByRowTitle(page, jobName, "click");
            await page.getByText("Run application again").click();
        })
        await page.getByText("No machine type selected").waitFor({state: "hidden"});
        await Runs.submitAndWaitForRunning(page);
    },

    async stopRun(page: Page, jobName: string): Promise<void> {
        await this.goToRuns(page);
        await Components.projectSwitcher(page, "hover");
        await page.locator(".row").getByText(jobName).click();
        await NetworkCalls.awaitResponse(page, "**/jobs/terminate", async () => {
            await Components.clickConfirmationButton(page, "Stop");
        });
    },

    async terminateViewedRun(page: Page): Promise<void> {
        await NetworkCalls.awaitResponse(page, "**/jobs/terminate", async () => {
            await Components.clickConfirmationButton(page, "Stop application");
        });
        await page.getByText("Run application again").waitFor();
    },

    async setJobTitle(page: Page, name: string): Promise<void> {
        await page.getByRole("textbox", {name: "Job name"}).fill(name);
    },

    async setNodeCount(page: Page, count: number): Promise<void> {
        await page.getByRole("textbox", {name: "Number of nodes"}).fill(count.toString());
    },


    async submitAndWaitForRunning(page: Page, extension?: 1 | 8 | 24): Promise<void> {
        if (extension != null) {
            await this.extendTimeBy(page, extension);
        }

        await NetworkCalls.awaitResponse(page, "**/api/jobs", async () => {
            await page.getByRole("button", {name: "Submit"}).click();
        })

        await page.getByText("is now running").first().hover();
    },

    async extendTimeBy(page: Page, extension: 1 | 8 | 24) {
        await page.getByRole("button", {name: `+${extension}`}).click();
    },

    async openTerminal(page: Page): Promise<Page> {
        await page.locator("div[class^=notification]").waitFor({state: "hidden"});
        const terminalPagePromise = page.waitForEvent("popup");
        await page.getByRole("button", {name: "Open terminal"}).click();
        const terminalPage = await terminalPagePromise;
        await terminalPage.getByText("➜").first().click();
        return terminalPage;
    },

    JobResources: {
        async addFolder(page: Page, driveName: string, folderName: string): Promise<void> {
            await page.getByRole("button", {name: "Add folder"}).click();
            await NetworkCalls.awaitResponse(page, "**/api/files/browse?**", async () => {
                await page.getByRole("textbox", {name: "No directory selected"}).click();
            })
            await File.ensureDialogDriveActive(page, driveName);
            await page.getByRole("dialog").locator(".row", {hasText: folderName}).getByRole("button", {name: "Use"}).click();
        },

        async addPublicLink(page: Page, publicLinkName: string): Promise<void> {
            await page.getByText("Add public link").first().click();
            await NetworkCalls.awaitResponse(page, "**/api/ingresses/browse?**", async () => {
                await page.getByPlaceholder("No public link selected").click();
            });
            await page.getByRole("dialog").locator(".row", {hasText: publicLinkName}).getByRole("button", {name: "Use"}).click();
        },

        async toggleEnableSSHServer(page: Page): Promise<void> {
            expect(page.url()).toContain("/jobs/create");
            await page.getByText("Enable SSH server").click();
        },
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

    async open(page: Page, resourceName: string) {
        await this.actionByRowTitle(page, resourceName, "dblclick");
    },

    async goTo(page: Page, resource: "Links" | "IP addresses" | "SSH keys" | "Licenses") {
        await page.getByRole("link", {name: "Go to Resources"}).hover();
        await page.getByText(resource).first().click();
        await Components.projectSwitcher(page, "hover");
    },

    PublicLinks: {
        newPublicLinkName(): string {
            return Help.newResourceName("PublicLink").toLocaleLowerCase();
        },

        async createNew(page: Page, publicLinkName?: string): Promise<string> {
            await NetworkCalls.awaitProducts(page, async () => {
                await Resources.goTo(page, "Links");
            });
            const name = publicLinkName ?? this.newPublicLinkName();
            await page.getByText("Create public link").click();
            // Note(Jonas): nth(1) because we are skipping the hidden search field
            await page.getByRole("textbox").nth(1).fill(name);
            await page.getByRole("button", {name: "Create", disabled: false}).click();
            return name;
        },

        async delete(page: Page, name: string): Promise<void> {
            await Rows.actionByRowTitle(page, name, "click");
            await Components.clickConfirmationButton(page, "Delete");
        }
    },

    IPs: {
        async createNew(page: Page): Promise<string> {
            await NetworkCalls.awaitProducts(page, async () => {
                await Resources.goTo(page, "IP addresses");
            });

            await page.getByText("Create public IP").click();

            await page.getByRole("dialog").getByText("public-ip").hover();
            await this.fillPortRowInDialog(page);
            const ip = await NetworkCalls.awaitResponse(page, "**/api/networkips", async () => {
                await page.getByRole("button", {name: "create", disabled: false}).click();
            });
            const ipAdress: {
                responses: [
                    {
                        id: string
                    }
                ]
            } = JSON.parse(await ip.text());
            return ipAdress.responses[0].id;
        },

        async fillPortRowInDialog(page: Page): Promise<void> {
            await page.getByRole("dialog").locator("input").first().fill("123");
            await page.getByRole("dialog").locator("input").nth(1).fill("321");
            await page.locator("td > button").click();
        },

        async delete(page: Page, name: string): Promise<void> {
            throw Error("Not implemented");
        }
    },

    SSHKeys: {
        DefaultSSHKey: "ssh-rsa a-bunch-of-text",

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
            await page.locator("#key-contents").fill(this.DefaultSSHKey);
            await NetworkCalls.awaitResponse(page, "**/api/ssh", async () => {
                await page.getByRole("button", {name: "Add SSH key"}).click();
            });
            await page.waitForURL(url => url.pathname.endsWith("/ssh-keys"));
            return title;
        }
    },

    Licenses: {
        async activateLicense(page: Page): Promise<number> {
            const result = (await NetworkCalls.awaitResponse(page, "**/api/licenses", async () => {
                await page.getByText("Activate license").click();
                await page.getByRole("dialog").getByRole("button", {name: "Activate"}).click();
            }));

            const obj: {responses: {id: number}[]} = JSON.parse(await result.text());
            return obj.responses[0].id;
        }
    },
}

export const Terminal = {
    async enterCmd(page: Page, command: string): Promise<void> {
        for (const key of command) {
            await page.keyboard.press(key);
        }

        await page.keyboard.press("Enter");
    },

    async createLargeFile(page: Page): Promise<void> {
        await this.enterCmd(page, "fallocate -l 5G example");
    }
}


const Help = {
    newResourceName(namespace: string): string {
        return namespace + Math.random().toString().slice(2, 7);
    },

    ensureActiveProject(): void | never {

    }
};

export const NetworkCalls = {
    async awaitResponse(page: Page, path: string, block: () => Promise<void>): Promise<ReturnType<typeof page.waitForResponse>> {
        const waiter = page.waitForResponse(path);
        await block();
        return await waiter;
    },

    async awaitProducts(page: Page, block: () => Promise<void>) {
        return await NetworkCalls.awaitResponse(page, "**/retrieveProducts?*", block);
    }
}


type AccountingPage = "Allocations" | "Usage" | "Grant applications" | "Apply for resources" | "Members" | "Project settings" | "Sub-projects";
export const Accounting = {
    async goTo(page: Page, linkName: AccountingPage): Promise<void> {
        await page.getByRole("link", {name: "Go to Project"}).hover();
        if (["Members", "Project settings", "Sub-projects"].includes(linkName)) {
            Help.ensureActiveProject();
        }

        switch (linkName) {
            case "Usage":
                await NetworkCalls.awaitResponse(page, "**/api/accounting/v2/usage/retrieve?**", async () => {
                    await page.getByRole("link", {name: "Usage"}).click();
                });
                return;
        }

        await page.getByRole("link", {name: linkName}).first().click();
    },

    Project: {
        newProjectName(): string {
            return Help.newResourceName("ProjectTitle");
        },
    },

    GrantApplication: {
        async fillProjectName(page: Page, projectName: string): Promise<void> {
            await page.getByPlaceholder("Please enter the title of your project").fill(projectName);
        },

        async toggleGrantGiver(page: Page, grantGiver: string): Promise<void> {
            await page.getByText(grantGiver, {exact: false}).click();
        },

        async fillQuotaFields(page: Page, quotas: {field: string; quota: number}[]): Promise<void> {
            for (const quota of quotas) {
                await page.getByRole("spinbutton", {name: quota.field}).fill(quota.quota.toString());
            }
        },

        async fillApplicationTextFields(page: Page, textFields: {name: string; content: string}[]): Promise<void> {
            for (const applicationField of textFields) {
                await page.getByRole("textbox", {name: applicationField.name}).fill(applicationField.content);
            }
        },

        async submit(page: Page): Promise<string> {
            const response = (await NetworkCalls.awaitResponse(page, "**/submitRevision", async () => {
                await page.getByRole("button", {name: "Submit application"}).click();
            }));

            const result = JSON.parse(await response.text()).id;
            return result;
        },

        async approve(page: Page): Promise<void> {
            await page.locator(".grant-giver button").nth(1).click({delay: 2000});
        }
    },

    Usage: {
        ProductCategoryNames: Object.keys(
            data.products_by_provider_and_type
        ).flatMap(provider => Object.keys(data.products_by_provider_and_type[provider]).map(productType =>
            data.products_by_provider_and_type[provider][productType].category.name
        ))
    }
};

export const Admin = {
    AdminUser: {
        username: "user",
        password: "mypassword"
    },

    async acceptGrantApplicationId(page: Page, grantId: string) {
        // Create new page without cookies from existing page
        const adminPage = await page.context().browser()?.newPage();
        if (!adminPage) throw Error("Failed to initialize admin page.");
        await User.login(adminPage, this.AdminUser);
        await Accounting.goTo(adminPage, "Grant applications");
        await Rows.actionByRowTitle(adminPage, grantId, "dblclick")
    }
};

async function _move(page: Page, oldName: string, newName: string) {
    await Rows.actionByRowTitle(page, oldName, "click");
    await page.getByText("Rename").click();
    await page.getByRole("textbox").nth(1).fill(newName);
    await page.getByRole("textbox").nth(1).press("Enter");
}