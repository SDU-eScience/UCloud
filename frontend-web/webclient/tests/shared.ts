import test, {BrowserContext, expect, type Locator, type Page} from "@playwright/test";
import fs from "fs";
import path from "path";

import {default as data} from "./test_data.json" with {type: "json"};
import {default as providerAndProductsImport} from "./provider_and_products.json" with {type: "json"};

const LoginPageUrl = ucloudUrl("login");

export function isDev(url: string): boolean {
    return url === "https://dev.cloud.sdu.dk";
}

export function isProd(url: string): boolean {
    return url === "https://cloud.sdu.dk";
}

export function isLocalDev(url: string): boolean {
    return url === "https://ucloud.localhost.direct";
}

export type Contexts =
    | "Project PI" | "Project Admin" | "Project User" | "Personal Workspace";

export const TestUserDataPath = path.resolve(
    process.env.UCLOUD_PLAYWRIGHT_USER_DATA ?? "./test_data/playwright-users.json"
);

const runtimeTestUsers = fs.existsSync(TestUserDataPath)
    ? JSON.parse(fs.readFileSync(TestUserDataPath, "utf8"))
    : {};

export const TestContexts: Contexts[] = ["Project PI", "Project Admin", "Project User", "Personal Workspace"];

export const TestUsers: Record<Contexts, {username: string; password: string;}> = {
    "Project PI": runtimeTestUsers["Project PI"] ?? {username: "", password: ""},
    "Project Admin": runtimeTestUsers["Project Admin"] ?? {username: "", password: ""},
    "Project User": runtimeTestUsers["Project User"] ?? {username: "", password: ""},
    "Personal Workspace": runtimeTestUsers["Personal Workspace"] ?? data.users.with_resources
};

const providerAndProducts = providerAndProductsImport.find(it => it.location_origin === data.location_origin);

if (!providerAndProducts) {
    throw Error("Location origin doesn't match any provider/product combination");
}

export const ProviderInfo = {
    providerTitle(): string {
        return providerAndProducts.provider_used_in_tests;
    },

    providerId(): string | undefined {
        return typeof runtimeTestUsers.providerId === "string" ? runtimeTestUsers.providerId : undefined;
    },

    providerProjectId(): string | undefined {
        return typeof runtimeTestUsers.providerProjectId === "string" ? runtimeTestUsers.providerProjectId : undefined;
    },
}

type ApiProductCategory = {
    name: string;
    provider: string;
    productType: string;
    accountingUnit: {name: string; floatingPoint: boolean;};
    accountingFrequency: string;
};

type ApiDrive = {
    id: string;
    specification: {
        title: string;
        product: {provider: string;};
    };
};

const apiCategoryCache = new Map<string, ApiProductCategory>();
let cachedProviderProjectId: string | undefined;

type UserTokens = {accessToken: string; csrfToken: string;};

async function apiHeaders(page: Page, projectOverride?: string): Promise<Record<string, string>> {
    const tokens = await page.evaluate(() => ({
        accessToken: localStorage.getItem("accessToken") ?? "",
        csrfToken: localStorage.getItem("csrfToken") ?? "",
        project: localStorage.getItem("project") ?? "",
    }));
    const headers: Record<string, string> = {
        Accept: "application/json",
        Authorization: `Bearer ${tokens.accessToken}`,
        "Content-Type": "application/json",
        Origin: data.location_origin,
        "X-CSRFToken": tokens.csrfToken,
    };
    const project = projectOverride ?? tokens.project;
    if (project) headers.Project = project;
    return headers;
}

export async function apiRequest(
    page: Page,
    method: "GET" | "POST" | "DELETE",
    path: string,
    body?: any,
    projectOverride?: string,
): Promise<Response> {
    const headers = await apiHeaders(page, projectOverride);
    const response = await page.request.fetch(new URL(path, data.location_origin).toString(), {
        method,
        headers,
        data: body,
    });
    if (!response.ok()) {
        throw new Error(`${method} ${path} failed: ${response.status()} ${await response.text()}`);
    }
    return response;
}

export async function apiJson<T>(
    page: Page,
    method: "GET" | "POST" | "DELETE",
    path: string,
    body?: any,
    projectOverride?: string,
): Promise<T> {
    return await (await apiRequest(page, method, path, body, projectOverride)).json() as T;
}

export async function refreshAccessToken(page: Page): Promise<void> {
    const csrfToken = await page.evaluate(() => localStorage.getItem("csrfToken") ?? "");
    const response = await page.request.post(new URL("/auth/refresh/web", data.location_origin).toString(), {
        headers: {
            Origin: data.location_origin,
            "X-CSRFToken": csrfToken,
        },
    });
    if (!response.ok()) {
        throw new Error(`POST /auth/refresh/web failed: ${response.status()} ${await response.text()}`);
    }

    const tokens = await response.json() as {accessToken: string; csrfToken: string;};
    await page.evaluate(({accessToken, csrfToken}) => {
        localStorage.setItem("accessToken", accessToken);
        localStorage.setItem("csrfToken", csrfToken);
    }, tokens);
}

async function apiBrowseAll<T>(page: Page, path: string): Promise<T[]> {
    const items: T[] = [];
    let next: string | null | undefined = undefined;
    for (let pageIndex = 0; pageIndex < 10; pageIndex++) {
        const result = await apiJson<{items: T[]; next?: string | null;}>(
            page,
            "GET",
            next ? `${path}&next=${encodeURIComponent(next)}` : path,
        );
        items.push(...result.items);
        next = result.next;
        if (!next) break;
    }
    return items;
}

export async function createUserByApi(admin: Page, user: {username: string; password: string;}): Promise<void> {
    const result = await apiJson<Array<{created: boolean; error: string;}>>(admin, "POST", "/auth/users/register", [{
        username: user.username,
        password: user.password,
        email: `${user.username}@e2etest.internal`,
        role: "USER",
        firstNames: user.username,
        lastname: "Test",
    }]);
    const entry = result[0];
    if (!entry?.created) {
        throw new Error(`Failed to create user ${user.username}: ${entry?.error ?? "unknown error"}`);
    }
}

export async function createProjectByApi(admin: Page, title: string): Promise<string> {
    const result = await apiJson<{responses: Array<{id: string;}>;}>(admin, "POST", "/api/projects/v2", {
        type: "bulk",
        items: [{title, canConsumeResources: true}],
    });
    return result.responses[0].id;
}

export async function inviteUserByApi(admin: Page, projectId: string, username: string): Promise<void> {
    await apiRequest(admin, "POST", "/api/projects/v2/invites", {
        type: "bulk",
        items: [{recipient: username}],
    }, projectId);
}

async function acceptProjectInviteWithToken(page: Page, projectId: string, tokens: UserTokens): Promise<void> {
    const response = await page.request.post(new URL("/api/projects/v2/acceptInvite", data.location_origin).toString(), {
        headers: {
            Accept: "application/json",
            Authorization: `Bearer ${tokens.accessToken}`,
            "Content-Type": "application/json",
            Origin: data.location_origin,
            "X-CSRFToken": tokens.csrfToken,
        },
        data: {type: "bulk", items: [{project: projectId}]},
    });
    if (!response.ok()) {
        throw new Error(`POST /api/projects/v2/acceptInvite failed: ${response.status()} ${await response.text()}`);
    }
}

export async function changeProjectRoleByApi(
    admin: Page,
    projectId: string,
    username: string,
    role: "USER" | "ADMIN" | "PI",
): Promise<void> {
    await apiRequest(admin, "POST", "/api/projects/v2/changeRole", {
        type: "bulk",
        items: [{username, role}],
    }, projectId);
}

export async function apiProductCategory(page: Page, name: string): Promise<ApiProductCategory> {
    const cached = apiCategoryCache.get(name);
    if (cached) return cached;

    const products = await apiBrowseAll<{category: ApiProductCategory;}>(
        page,
        "/api/products/v2/browse?itemsPerPage=250",
    );
    const providerId = ProviderInfo.providerId();
    const categories = products.map(it => it.category);
    const category =
        categories.find(it => it.name === name && (providerId === undefined || it.provider === providerId)) ??
        categories.find(it => it.name === name);

    if (!category) throw new Error(`Product category not found: ${name}`);
    apiCategoryCache.set(name, category);
    return category;
}

export function isByteStorage(category: ApiProductCategory): boolean {
    return category.productType === "STORAGE" && category.accountingUnit.name === "B";
}

export function apiAllocationRaw(category: ApiProductCategory, amount: number): number {
    if (isByteStorage(category)) return Math.round(amount * 1_000_000_000);
    if (category.accountingUnit.floatingPoint) return Math.round(amount * 1_000_000);

    switch (category.accountingFrequency) {
        case "PERIODIC_SECOND":
            return Math.round(amount * 3600);
        case "PERIODIC_MINUTE":
            return Math.round(amount * 60);
        default:
            return Math.round(amount);
    }
}

export async function resolveProviderProjectId(admin: Page): Promise<string> {
    if (cachedProviderProjectId) return cachedProviderProjectId;

    const projects = await apiBrowseAll<{id: string; specification: {title: string;};}>(
        admin,
        "/api/projects/v2/browse?itemsPerPage=250",
    );

    const configured = ProviderInfo.providerProjectId();
    if (configured && projects.some(it => it.id === configured)) {
        cachedProviderProjectId = configured;
        return configured;
    }

    const project = projects.find(it => it.specification.title === ProviderInfo.providerTitle());
    if (!project) throw new Error("Unable to determine the provider project ID");
    cachedProviderProjectId = project.id;
    return project.id;
}

export async function submitGrantByApi(
    admin: Page,
    recipient: {type: "existingProject"; id: string;} | {type: "personalWorkspace"; username: string;},
    quotas: RequestedQuotas,
    providerProjectId: string,
): Promise<void> {
    const start = Date.now();
    const end = start + 7 * 24 * 60 * 60 * 1000;

    const allocationRequests = [];
    for (const [name, amount] of quotas) {
        const category = await apiProductCategory(admin, name);
        allocationRequests.push({
            category: category.name,
            provider: category.provider,
            grantGiver: providerProjectId,
            balanceRequested: apiAllocationRaw(category, amount),
        });
    }

    await apiRequest(admin, "POST", "/api/grants/v2/submitRevision", {
        revision: {
            recipient,
            allocationRequests,
            allocationPeriod: {start, end},
            form: {type: "grant_giver_initiated", text: "Grant test resources"},
        },
        comment: "Grant test resources",
    }, providerProjectId);
}

export async function driveIdByName(page: Page, name: string): Promise<string> {
    const providerId = ProviderInfo.providerId();
    const drives = await apiBrowseAll<ApiDrive>(page, "/api/files/collections/browse?itemsPerPage=250");

    const matches = drives.filter(it =>
        it.specification.title === name &&
        (providerId === undefined || it.specification.product.provider === providerId)
    );
    const drive = matches[0] ?? drives.find(it => it.specification.title === name);
    if (!drive) throw new Error(`Drive not found: ${name}`);
    return drive.id;
}

export async function waitForDriveByName(page: Page, name: string, timeoutMs: number = 60_000): Promise<string> {
    const startedAt = Date.now();
    let lastError: unknown;
    while (Date.now() - startedAt < timeoutMs) {
        try {
            return await driveIdByName(page, name);
        } catch (e) {
            lastError = e;
            await page.waitForTimeout(250);
        }
    }
    throw lastError instanceof Error ? lastError : new Error(`Drive not found: ${name}`);
}

export async function waitForDriveReady(page: Page, name: string, timeoutMs: number = 60_000): Promise<string> {
    const driveId = await waitForDriveByName(page, name, timeoutMs);
    const startedAt = Date.now();
    while (Date.now() - startedAt < timeoutMs) {
        const result = await apiJson<{items: Array<{status: {icon?: string;};}>;}>(page, "GET",
            `/api/files/browse?path=${encodeURIComponent(driveId)}&itemsPerPage=250`);
        if (result.items.some(it => it.status.icon === "DIRECTORY_JOBS")) return driveId;
        await page.waitForTimeout(200);
    }
    throw new Error(`Drive is not ready: ${name}`);
}

export async function waitForFileByApi(
    page: Page,
    parent: string,
    name: string,
    timeoutMs: number = 30_000,
): Promise<void> {
    const startedAt = Date.now();
    let lastError: unknown;
    while (Date.now() - startedAt < timeoutMs) {
        try {
            const result = await apiBrowseAll<{id: string;}>(
                page,
                `/api/files/browse?path=${encodeURIComponent(parent)}&itemsPerPage=250`,
            );
            if (result.some(it => it.id.split("/").pop() === name)) return;
        } catch (e) {
            lastError = e;
        }
        await page.waitForTimeout(250);
    }
    throw lastError instanceof Error ? lastError : new Error(`File not found: ${name}`);
}

export async function findFilePathByApi(page: Page, root: string, name: string): Promise<string> {
    const pending = [root];
    const seen = new Set<string>();
    while (pending.length > 0) {
        const current = pending.shift()!;
        if (seen.has(current)) continue;
        seen.add(current);

        const result = await apiBrowseAll<{id: string; status: {type: string;};}>(
            page,
            `/api/files/browse?path=${encodeURIComponent(current)}&itemsPerPage=250`,
        );
        for (const item of result) {
            if (item.id.split("/").pop() === name) return item.id;
            if (item.status.type === "DIRECTORY") pending.push(item.id);
        }
    }
    throw new Error(`File not found: ${name}`);
}

export async function trashByApi(page: Page, path: string): Promise<void> {
    await apiRequest(page, "POST", "/api/files/trash", {type: "bulk", items: [{id: path}]});
}

export async function emptyTrashByApi(page: Page, driveId: string): Promise<void> {
    const trashPath = `${driveId}/Trash`;
    await apiRequest(page, "POST", "/api/files/folder", {
        type: "bulk",
        items: [{id: trashPath, conflictPolicy: "REJECT"}],
    }).catch(() => undefined);
    await apiRequest(page, "POST", "/api/files/emptyTrash", {type: "bulk", items: [{id: trashPath}]});
}

export async function deleteDriveByApi(page: Page, id: string): Promise<void> {
    const driveId = id.replace(/^\//, "");
    await apiRequest(page, "DELETE", "/api/files/collections", {type: "bulk", items: [{id: driveId}]});
}

export async function deleteDriveByApiWithTokens(
    page: Page,
    tokens: {accessToken: string; csrfToken: string;},
    id: string,
    projectId?: string,
): Promise<void> {
    const headers: Record<string, string> = {
        Accept: "application/json",
        Authorization: `Bearer ${tokens.accessToken}`,
        "Content-Type": "application/json",
        Origin: data.location_origin,
        "X-CSRFToken": tokens.csrfToken,
    };
    if (projectId) headers.Project = projectId;

    const driveId = id.replace(/^\//, "");
    const response = await page.request.fetch(new URL("/api/files/collections", data.location_origin).toString(), {
        method: "DELETE",
        headers,
        data: {type: "bulk", items: [{id: driveId}]},
    });
    if (!response.ok()) {
        throw new Error(`DELETE /api/files/collections failed: ${response.status()} ${await response.text()}`);
    }
}

export const User = {
    newUserCredentials(postfix?: string): {username: string; password: string;} {
        const username = Help.newResourceName(`test-user-${postfix ?? ""}`);
        return {username, password: username + "_" + username};
    },

    async toLoginPage(page: Page): Promise<void> {
        await page.goto(LoginPageUrl);
        await page.waitForLoadState("domcontentloaded");
        await page.getByRole("textbox", {name: "Username"})
            .or(page.getByText("Other login options"))
            .first()
            .waitFor();
    },

    async login(page: Page, user: {username: string; password: string;}, fillUserInfo: boolean = false): Promise<void> {
        await page.addInitScript(() => {
            localStorage.setItem("instantConfirmation", "true");
        });
        await this.toLoginPage(page);
        if (await page.getByText("Other login options").isVisible()) {
            await page.getByText("Other login options").click();
        }
        await page.getByRole("textbox", {name: "Username"}).fill(user.username);
        await page.getByRole("textbox", {name: "Password"}).fill(user.password);
        await page.getByRole("button", {name: "Login"}).click();
        await page.waitForLoadState("domcontentloaded");

        const dashboardLink = page.getByRole("link", {name: "Go to dashboard"});
        const acceptTerms = page.getByRole("button", {name: "I have read and accept the terms of service"});
        const additionalInfo = page.getByText("Additional user information");
        await dashboardLink.or(acceptTerms).or(additionalInfo).first().waitFor({timeout: 30_000});

        if (page.url().endsWith("/sla") || await acceptTerms.isVisible()) {
            await acceptTerms.click();
            await page.getByRole("dialog").getByRole("button", {name: "I have read and accept the terms of service"}).click();
            await page.waitForURL("**/app");
        }


        if (fillUserInfo) {
            await this.dismissAdditionalInfoPrompt(page);
            await this.setAdditionalUserInfo(page);
            await this.disableNotifications(page);
        }

        await page.getByRole("link", {name: "Go to dashboard"}).waitFor({timeout: 30_000});
    },

    async loginTokens(page: Page, user: {username: string; password: string;}): Promise<UserTokens> {
        const service = "web";
        const response = await page.request.post(`/auth/login?service=${service}`, {
            headers: {Accept: "application/json", Origin: data.location_origin},
            multipart: {service, username: user.username, password: user.password},
        });
        if (!response.ok()) throw new Error(`Login failed: ${response.status()} ${await response.text()}`);
        return await response.json() as UserTokens;
    },

    async initializeNewUser(
        page: Page,
        tokens: UserTokens,
        options: {acceptSla?: boolean; completeUserInfo?: boolean;},
    ): Promise<void> {
        if (!options.acceptSla && !options.completeUserInfo) return;

        const headers = {
            Accept: "application/json",
            Authorization: `Bearer ${tokens.accessToken}`,
            "Content-Type": "application/json",
            Origin: data.location_origin,
            "X-CSRFToken": tokens.csrfToken,
        };
        if (options.acceptSla) {
            const slaResponse = await page.request.get(new URL("/api/sla", data.location_origin).toString(), {headers});
            if (!slaResponse.ok()) throw new Error(`GET /api/sla failed: ${slaResponse.status()} ${await slaResponse.text()}`);
            const sla = await slaResponse.json() as {version: number;};
            const acceptResponse = await page.request.post(new URL("/api/sla/accept", data.location_origin).toString(), {
                headers,
                data: {version: sla.version},
            });
            if (!acceptResponse.ok()) {
                throw new Error(`POST /api/sla/accept failed: ${acceptResponse.status()} ${await acceptResponse.text()}`);
            }
        }
        if (options.completeUserInfo) {
            const infoResponse = await page.request.post(new URL("/auth/users/optionalInfo", data.location_origin).toString(), {
                headers,
                data: {
                    organizationFullName: "Test",
                    department: "Not available",
                    researchField: "0.1 Other",
                    position: "Administrative Staff",
                    gender: "Another gender identity",
                    unit: "Unit division",
                },
            });
            if (!infoResponse.ok()) {
                throw new Error(`POST /auth/users/optionalInfo failed: ${infoResponse.status()} ${await infoResponse.text()}`);
            }
        }
    },

    async useTokens(page: Page, tokens: UserTokens, projectId?: string): Promise<void> {
        await page.addInitScript(({accessToken, csrfToken, projectId}) => {
            localStorage.setItem("accessToken", accessToken);
            localStorage.setItem("csrfToken", csrfToken);
            if (projectId) localStorage.setItem("project", projectId);
            localStorage.setItem("instantConfirmation", "true");
        }, {...tokens, projectId});
        await page.goto(ucloudUrl(""), {waitUntil: "domcontentloaded"});
    },

    async loginDirect(
        page: Page,
        user: {username: string; password: string;},
        options: {acceptSla?: boolean; completeUserInfo?: boolean;} = {},
    ): Promise<void> {
        const tokens = await this.loginTokens(page, user);
        await this.initializeNewUser(page, tokens, options);
        await this.useTokens(page, tokens);
        await page.getByRole("link", {name: "Go to dashboard"}).waitFor({timeout: 30_000});
    },

    async refreshTokens(page: Page, csrfToken: string): Promise<UserTokens> {
        const response = await page.request.post(new URL("/auth/refresh/web", data.location_origin).toString(), {
            headers: {Origin: data.location_origin, "X-CSRFToken": csrfToken},
        });
        if (!response.ok()) throw new Error(`POST /auth/refresh/web failed: ${response.status()} ${await response.text()}`);
        return await response.json() as UserTokens;
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

        await page.getByRole("textbox", {name: "Email"}).fill(user.username + "@e2etest.internal");

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
        await page.getByText("Sends a notification when jobs start or stop", {exact: true}).click();
    },

    async dismissAdditionalInfoPrompt(page: Page): Promise<void> {
        await page.getByText("Additional user information").waitFor();
        await page.keyboard.press("Escape");
    },

    async createUserWithProjectAndAssignRole(admin: Page, context: BrowserContext, ctx: Contexts, quotas: RequestedQuotas): Promise<{userPage: Page; user: {username: string; password: string;}}> {
        const user = User.newUserCredentials();
        const userPage = await context.browser()?.newPage();
        if (!userPage) throw Error("Failed to create userpage");

        await createUserByApi(admin, user);

        if (data["login_cookie"]) {
            await userPage.context().addCookies([data["login_cookie"]]);
        }

        const providerProjectId = await resolveProviderProjectId(admin);

        const tokens = await User.loginTokens(userPage, user);
        await User.initializeNewUser(userPage, tokens, {acceptSla: true, completeUserInfo: true});

        if (ctx === "Personal Workspace") {
            await submitGrantByApi(admin, {
                type: "personalWorkspace",
                username: user.username
            }, quotas, providerProjectId);
            await User.useTokens(userPage, tokens);
            await userPage.getByRole("link", {name: "Go to dashboard"}).waitFor({timeout: 30_000});
            await waitForDriveReady(userPage, "Home");
            return {userPage, user};
        }

        const projectName = Project.newProjectName();
        const projectId = await createProjectByApi(admin, projectName);
        await refreshAccessToken(admin);
        await inviteUserByApi(admin, projectId, user.username);
        await acceptProjectInviteWithToken(userPage, projectId, tokens);

        const role = ctx.split(" ")[1].toUpperCase() as "USER" | "ADMIN" | "PI";
        await changeProjectRoleByApi(admin, projectId, user.username, role);
        const refreshed = await User.refreshTokens(userPage, tokens.csrfToken);
        await submitGrantByApi(admin, {
            type: "existingProject",
            id: projectId
        }, quotas, providerProjectId);

        await User.useTokens(userPage, refreshed, projectId);
        await userPage.getByRole("link", {name: "Go to dashboard"}).waitFor({timeout: 30_000});
        await waitForDriveReady(userPage, Drive.memberFiles(user.username));

        return {userPage, user};
    }
}

type RequestedQuotas = [string, number][];

export function ucloudUrl(pathname: string): string {
    return ("/app/" + pathname).replaceAll("//", "/");
}

const RowDoubleClickWindowMs = 300;
const lastRowClicks = new Map<string, number>();

async function closeSidebar(page: Page): Promise<void> {
    await page.mouse.move(640, 10);
    await page.waitForTimeout(150);
}

async function centerRow(page: Page, locator: Locator): Promise<void> {
    await locator.evaluate(element => {
        const container = element.closest(".scrolling")?.parentElement;
        if (!container) return;
        const elementRect = element.getBoundingClientRect();
        const containerRect = container.getBoundingClientRect();
        container.scrollTop += (elementRect.top - containerRect.top) -
            (containerRect.height - elementRect.height) / 2;
    });
    await page.waitForTimeout(50);
}

async function clickRow(page: Page, locator: Locator): Promise<void> {
    const idx = await locator.evaluate(element => element.closest(".row")?.getAttribute("data-idx") ?? "");
    const now = Date.now();
    if (idx !== "") {
        const lastClick = lastRowClicks.get(idx);
        if (lastClick !== undefined) {
            const waitMs = RowDoubleClickWindowMs - (now - lastClick);
            if (waitMs > 0) await page.waitForTimeout(waitMs);
        }
    }
    await centerRow(page, locator);
    await locator.evaluate(element => (element as HTMLElement).click());
    if (idx !== "") lastRowClicks.set(idx, Date.now());
}

async function scrollRows(page: Page, inModal: boolean | undefined, amount: number | "top"): Promise<void> {
    const scrolling = inModal
        ? page.getByRole("dialog").locator(".scrolling").last()
        : page.locator(".scrolling").first();

    await scrolling.evaluate((element, value) => {
        const container = element.parentElement;
        if (!container) return;
        if (value === "top") {
            container.scrollTop = 0;
        } else {
            container.scrollTop += value;
        }
    }, amount);
}

function rowLocator(page: Page, name: string, inModal: boolean | undefined): Locator {
    return inModal
        ? page.getByRole("dialog").locator(".row div > span", {hasText: name})
        : page.locator(".row div > span", {hasText: name}).first();
}

export const Rows = {
    async actionByRowTitle(page: Page, name: string, action: "click" | "dblclick" | "hover" | "rightclick", inModal?: boolean): Promise<void> {
        if (!inModal) await closeSidebar(page);
        await scrollRows(page, inModal, "top");

        const locator = rowLocator(page, name, inModal);

        for (let i = 0; i < 400 && !(await locator.isVisible()); i++) {
            await scrollRows(page, inModal, 50);
            await page.waitForTimeout(20);
        }

        if (action === "rightclick" || action === "dblclick") {
            await centerRow(page, locator);
        }

        switch (action) {
            case "click": {
                await clickRow(page, locator);
                break;
            }
            case "rightclick": {
                await locator.click({button: "right"});
                break;
            }
            default: {
                await locator[action]();
                break;
            }
        }
    },

    async rowIsVisible(page: Page, name: string, inModal?: boolean): Promise<boolean> {
        if (!inModal) await closeSidebar(page);
        await scrollRows(page, inModal, "top");

        const locator = rowLocator(page, name, inModal);
        for (let i = 0; i < 400 && !(await locator.isVisible()); i++) {
            await scrollRows(page, inModal, 50);
            await page.waitForTimeout(20);
        }
        return await locator.isVisible();
    },
};

export const File = {
    ...Rows,

    async open(page: Page, filename: string) {
        const before = page.url();
        const response = page.waitForResponse("**/api/files/browse**", {timeout: 2_000}).catch(() => null);
        await this.actionByRowTitle(page, filename, "dblclick");
        await page.waitForURL(url => url.href !== before, {timeout: 5_000}).catch(() => null);
        await response;
    },

    async goUp(page: Page): Promise<void> {
        await NetworkCalls.awaitResponse(page, "**/api/files/browse**", async () => {
            const breadcrumbs = page.locator(".location ul").first().locator("li");
            const count = await breadcrumbs.count();
            await breadcrumbs.nth(Math.max(0, count - 2)).click();
        });
    },

    async goToSharedByMe(page: Page): Promise<void> {
        await page.goto(ucloudUrl("shares/outgoing"), {waitUntil: "domcontentloaded"});
        await page.waitForLoadState("domcontentloaded");
    },

    async goToSharedWithMe(page: Page): Promise<void> {
        await page.getByRole("link", {name: "Go to dashboard"}).waitFor({timeout: 30_000});
        await page.goto(ucloudUrl("shares/"), {waitUntil: "domcontentloaded"});
        await page.waitForLoadState("domcontentloaded");
    },

    newFolderName(): string {
        return Help.newResourceName("FolderName");
    },

    async rename(page: Page, oldName: string, newName: string): Promise<void> {
        await NetworkCalls.awaitResponse(page, "**/api/files/move", async () => {
            await this.actionByRowTitle(page, oldName, "click");
            await _rename(page, newName);
        });
    },

    async create(page: Page, name: string): Promise<void> {
        await page.getByRole("button", {name: "Create folder", disabled: false}).click();
        await page.getByRole("textbox").nth(1).fill(name);
        await NetworkCalls.awaitResponse(page, "**/files/folder", async () => {
            await page.getByRole("textbox").nth(1).press("Enter");
        });
        const firstFolderName = name.split("/")[0];
        await page.locator(".row div > span").getByText(firstFolderName, {exact: true}).waitFor({state: "visible"});
    },

    async createMany(page: Page, names: string[]): Promise<void> {
        const path = new URL(page.url()).searchParams.get("path");
        if (!path) throw new Error(`No path in URL: ${page.url()}`);

        const chunkSize = 10;
        const requests = [];
        for (let i = 0; i < names.length; i += chunkSize) {
            const chunk = names.slice(i, i + chunkSize);
            requests.push(apiRequest(page, "POST", "/api/files/folder", {
                type: "bulk",
                items: chunk.map(name => ({id: `${path}/${name}`, conflictPolicy: "RENAME"})),
            }));
        }
        await Promise.all(requests);

        await page.reload();
        await page.locator(".row div > span").getByText(names[0], {exact: true}).waitFor({state: "visible"});
    },

    async waitForFile(page: Page, name: string, timeoutMs: number = 30_000): Promise<void> {
        const path = new URL(page.url()).searchParams.get("path");
        if (!path) throw new Error(`No path in URL: ${page.url()}`);
        await waitForFileByApi(page, path, name, timeoutMs);
    },

    async moveToTrash(page: Page, name: string): Promise<void> {
        await NetworkCalls.awaitResponse(page, "**/files/trash", async () => {
            await File.openOperationsDropsdown(page, name);
            await Components.clickConfirmationButton(page, "Delete");
        });
    },

    async uploadFiles(page: Page, files: {name: string, contents: string}[]): Promise<void> {
        await page.getByText("Upload files").click();
        const uploadPath = new URL(page.url()).searchParams.get("path") ?? "/";
        const uploadModal = page.locator("[data-tag=uploadModal]");
        await uploadModal.waitFor();
        await expect.poll(async () => await uploadModal.getAttribute("data-upload-path"), {timeout: 10_000}).toBe(uploadPath);

        await page.locator("#fileUploadBrowse").setInputFiles(files.map(f => ({
            name: f.name,
            mimeType: "text/plain",
            buffer: Buffer.from(f.contents),
        })));

        for (const file of files) {
            const uploadRow = page.locator('[class^="uploader-row"]', {hasText: file.name}).last();
            await expect.poll(async () => {
                if (await uploadRow.getAttribute("data-has-error") === "true") return "error";
                return await uploadRow.locator('[data-component="icon-check"]').count() > 0 ? "done" : "pending";
            }, {timeout: 30_000, intervals: [500]}).not.toBe("pending");
            if (await uploadRow.getAttribute("data-has-error") === "true") {
                throw new Error(`Upload failed for ${file.name}: ${await uploadRow.innerText()}`);
            }
        }

        await page.keyboard.press("Escape");
        try {
            for (const file of files) {
                await expect.poll(async () => {
                    await Components.clickRefreshAndWait(page, "**/api/files/**");
                    return await Rows.rowIsVisible(page, file.name);
                }, {timeout: 30_000, intervals: [1_000]}).toBe(true);
            }
        } catch (e) {
            const rows = await page.locator(".row div > span").allInnerTexts();
            const uploaderRows = await page.locator('[class^="uploader-row"]').allInnerTexts();
            throw new Error(`Uploaded file not found. url=${page.url()} uploadPath=${uploadPath} rows=${rows.slice(0, 20).join(" | ")} uploader=${uploaderRows.slice(0, 20).join(" | ")}`);
        }
    },

    async openOperationsDropsdown(page: Page, file: string): Promise<void> {
        await File.actionByRowTitle(page, file, "rightclick");
    },

    async moveFileTo(page: Page, fileToMove: string, targetFolder: string): Promise<void> {
        await this.openOperationsDropsdown(page, fileToMove);
        await page.getByText("Move to...").click();
        await this.actionByRowTitle(page, targetFolder, "hover", true);
        await NetworkCalls.awaitResponse(page, "**/api/files/move", async () => {
            await page.getByRole("dialog").locator("div.row", {hasText: targetFolder})
                .getByRole("button").filter({hasText: "Move to"}).click();
        })
        await page.getByRole("dialog").waitFor({state: "hidden"});
    },

    async copyFileTo(page: Page, fileToCopy: string, targetFolder: string): Promise<void> {
        await this.openOperationsDropsdown(page, fileToCopy);
        await page.getByText("Copy to...").click();

        await this.actionByRowTitle(page, targetFolder, "hover", true);
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
        await page.getByRole("dialog", {name: "File copy will begin soon"}).waitFor({state: "hidden"});
    },

    async moveFileToTrash(page: Page, fileName: string): Promise<void> {
        await this.openOperationsDropsdown(page, fileName);
        await NetworkCalls.awaitResponse(page, "**/api/files/trash", async () => {
            await Components.clickConfirmationButton(page, "Delete");
        });
    },

    async emptyTrash(page: Page): Promise<void> {
        await page.locator(".scrolling .row").first().waitFor({timeout: 30_000});
        await page.getByText("Empty Trash").click();
        await NetworkCalls.awaitResponse(page, "**/api/files/emptyTrash", async () => {
            const confirm = page.getByRole("dialog").getByRole("button", {name: "Empty trash", exact: true});
            await confirm.waitFor();
            await confirm.evaluate(element => (element as HTMLElement).click());
        }, 60_000);
        await page.getByText("Empty Trash").waitFor({state: "hidden"});
        await page.waitForLoadState("domcontentloaded");
    },

    async searchFor(page: Page, query: string): Promise<void> {
        const browser = page.locator(".scrolling").first()
            .locator("xpath=ancestor::*[starts-with(@class,'browser')]")
            .first();
        await browser.locator("img.search-icon").click();
        await browser.locator("input.search-field").fill(query);
        await page.keyboard.press("Enter");
    },

    async toggleFavorite(page: Page, name: string): Promise<void> {
        await NetworkCalls.awaitResponse(page, "**/api/files/metadata", async () => {
            await this.actionByRowTitle(page, name, "hover");
            await page.locator(".scrolling .row", {hasText: name}).first().getByRole("img", {name: "Star", includeHidden: false}).click();
        });
    },

    async ensureDialogDriveActive(page: Page, driveName: string, driveUrl?: string): Promise<void> {
        const dialog = page.getByRole("dialog");
        await dialog.waitFor();
        const locationBar = dialog.locator(".location-bar");
        const currentPath = await locationBar.getAttribute("data-real-path") ?? "";

        if (driveUrl === undefined) {
            const breadcrumbName = driveName.startsWith("Member Files:") ? "Member Files" : driveName;
            const driveBreadcrumb = dialog.getByRole("listitem", {name: breadcrumbName, exact: false});
            if (!(await driveBreadcrumb.isVisible())) {
                throw new Error(`Unable to find drive '${driveName}' in the file dialog`);
            }

            const driveId = currentPath.split("/").filter(Boolean)[0];
            if (driveId && currentPath === `/${driveId}/`) return;

            await NetworkCalls.awaitResponse(page, "**/api/files/browse**", async () => {
                await driveBreadcrumb.click();
            });
            return;
        }

        const driveId = (new URL(driveUrl).searchParams.get("path") ?? "").split("/").filter(Boolean)[0];
        if (!driveId) {
            throw new Error(`Unable to determine the id of drive '${driveName}'`);
        }

        const targetPath = `/${driveId}/`;
        if (currentPath === targetPath) return;

        await dialog.locator(".location").click();
        await NetworkCalls.awaitResponse(page, "**/api/files/browse**", async () => {
            await locationBar.fill(targetPath);
            await locationBar.press("Enter");
        });
    },

    async download(page: Page, filename: string): Promise<string> {
        const downloadPromise = page.waitForEvent("download");
        await File.actionByRowTitle(page, filename, "click");
        await page.getByRole("button", {name: /^Download/}).click();
        const dl = await downloadPromise;
        return fs.readFileSync(await dl.path(), {encoding: 'utf8', flag: 'r'});
    },

    async openIntegratedTerminal(page: Page): Promise<void> {
        await page.getByText("Open terminal").click();
        const prompt = page.locator("span", {hasText: "/work"}).last();
        let didClick = false;
        try {
            await expect.poll(async () => {
                if (!didClick && await page.getByRole("button", {name: "Reconnect"}).isVisible()) {
                    await page.getByRole("button", {name: "Reconnect"}).click();
                    didClick = true;
                }
                return await prompt.isVisible();
            }, {timeout: 60_000, intervals: [500]}).toBe(true);
        } catch (e) {
            const text = await page.locator("body").innerText();
            throw new Error(`Integrated terminal did not become ready. url=${page.url()} text=${text.slice(0, 2_000)}`);
        }
        await prompt.click({force: true});
    },

    async createShareLinkForFolder(page: Page, foldername: string): Promise<string> {
        await this._openShareModal(page, foldername);
        await page.getByText("Create link").click();
        return await page.getByRole("dialog").getByRole("textbox").nth(1)?.innerText() ?? "";
    },

    async shareFolderWith(page: Page, foldername: string, username: string): Promise<void> {
        await this._openShareModal(page, foldername);
        await page.getByRole("dialog").getByRole("button", {name: "Share with user"}).click();
        await page.getByRole("dialog").getByPlaceholder("Username").fill(username);
        await page.getByRole("dialog").getByRole("button", {name: "Share", exact: true}).click();
    },

    async _openShareModal(page: Page, foldername: string): Promise<void> {
        await this.openOperationsDropsdown(page, foldername);
        await page.getByText("Share with...").last().click();
    },

    async triggerStorageScan(page: Page, driveName: string): Promise<void> {
        const driveId = await driveIdByName(page, driveName);
        await emptyTrashByApi(page, driveId);
    }
};

export const Drive = {
    // Specific for drive (right now) is that resources can have same name, without being from same provider. This is problematic!
    async actionByRowTitle(page: Page, name: string, action: "click" | "dblclick" | "hover"): Promise<void> {
        await closeSidebar(page);
        await scrollRows(page, false, "top");

        const locator = page.locator(".scrolling").locator(".row").filter({hasText: providerAndProducts.drive_provider}).getByText(name).first();
        for (let i = 0; i < 400 && !(await locator.isVisible()); i++) {
            await scrollRows(page, false, 50);
            await page.waitForTimeout(20);
        }
        if (action === "click") {
            await clickRow(page, locator);
        } else {
            if (action === "dblclick") await centerRow(page, locator);
            await locator[action]();
        }
    },

    newDriveNameOrMemberFiles(ctx: Contexts): string {
        if (ctx === "Project User") return this.memberFiles(ctxUser("Project User").username);
        return Help.newResourceName("DriveName");
    },

    memberFiles(username: string): string {
        return `Member Files: ${username}`;
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
            await _rename(page, newName);
        });
    },

    async openDrive(page: Page, name: string): Promise<void> {
        await this.goToDrives(page);

        if (name !== "Home") {
            await NetworkCalls.awaitResponse(page, "**/api/files/collections/search**", async () => {
                await File.searchFor(page, name);
            });
            await page.locator(".row div > span", {hasText: name}).first().waitFor({timeout: 30_000});
        }

        await this.actionByRowTitle(page, name, "click");

        let opened = false;
        if (name !== "Home") {
            for (let attempt = 0; attempt < 5 && !opened; attempt++) {
                const response = page.waitForResponse("**/api/files/browse**", {timeout: 5_000}).catch(() => null);
                await this.actionByRowTitle(page, name, "dblclick");
                opened = (await response) !== null && page.url().includes("/app/files?path=");
                if (!opened) await page.waitForTimeout(1_000);
            }
        }
        if (!opened) {
            const message = `Drive '${name}' did not open from the drive list; using the properties fallback`;
            test.info().annotations.push({type: "drive-open-fallback", description: message});
            console.warn(message);
            await page.getByRole("button", {name: /^Properties/}).first().click();
            await page.waitForURL("**/app/drives/properties/**");
            const driveId = new URL(page.url()).pathname.split("/").filter(Boolean).pop();
            if (!driveId) throw new Error(`Unable to open drive '${name}'`);
            await page.goto(`/app/files?path=${encodeURIComponent("/" + driveId)}`);
            await page.waitForLoadState("domcontentloaded");
        }

        await Components.projectSwitcher(page, "hover")

        await page.waitForLoadState("domcontentloaded");
    },

    async create(page: Page, name: string): Promise<void> {
        await this.goToDrives(page);
        await page.getByRole("button", {name: "Create drive", disabled: false}).click();
        await page.getByRole("textbox", {name: "Choose a name"}).fill(name);

        if (await page.getByRole("dialog").getByText("No product selected").isVisible()) {
            await page.getByRole("dialog").getByText("No product selected").click();
            await page.getByRole("cell", {name: providerAndProducts.products_used_in_tests.storage}).click();
        }

        await NetworkCalls.awaitResponse(page, "**/api/files/collections", async () => {
            await page.getByRole("dialog").getByRole("button", {name: "Create", exact: true, disabled: false}).click();
        });

        await page.getByRole("dialog").waitFor({state: "hidden"});
    },

    async delete(page: Page, name: string): Promise<void> {
        await this.goToDrives(page);
        await this.actionByRowTitle(page, name, "click");

        await page.getByText("Delete").click();
        await page.locator("#collectionName").fill(name);
        await page.getByRole("button", {name: "I understand what I am doing", disabled: false}).click();
    },

    async openPermissions(page: Page, name: string): Promise<void> {
        await this.properties(page, name);
    },

    async properties(page: Page, name: string): Promise<void> {
        await this.actionByRowTitle(page, name, "click");
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

    async clickRefreshAndWait(page: Page, responsePattern?: string): Promise<void> {
        const response = responsePattern
            ? page.waitForResponse(responsePattern, {timeout: 1_000}).catch(() => null)
            : null;

        await page.locator(".refresh-icon, #refresh-icon").click();

        if (response) await response;
    },

    async toggleSearch(page: Page): Promise<void> {
        await page.locator("svg[data-component=icon-heroMagnifyingGlass]").click();
    },

    async setSidebarSticky(page: Page): Promise<void> {
        await page.getByRole("link", {name: "Go to Files"}).hover();
        await page.waitForTimeout(300);
        await page.getByRole("banner").locator("svg").click();
    },

    async clickConfirmationButton(page: Page, text: string): Promise<void> {
        const directButton = page.getByRole("button", {name: text}).filter({visible: true}).first();
        const hasDirectButton = await directButton.waitFor({timeout: 1_000}).then(() => true).catch(() => false);
        if (hasDirectButton) {
            if (await directButton.getAttribute("data-tag") === "confirm-button") {
                await directButton.dispatchEvent("mousedown");
            } else {
                await directButton.click();
            }
            return;
        }

        await page.getByText(text, {exact: true}).filter({visible: true}).last().click();
        await page.getByRole("alertdialog").getByRole("button", {name: text, exact: true}).click();
    },

    async selectAvailableMachineType(page: Page): Promise<void> {
        await page.getByText('No machine type selected').first().click();
        await page.getByRole('cell', {
            name: providerAndProducts.machine_used_in_tests,
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
        await page.locator("[class*='static-circle'], [class*='ripple']").click();
    },

    async toggleUserMenu(page: Page): Promise<void> {
        await page.locator(".SIDEBAR_IDENTIFIER > div[class^=flex] > div[class^=dropdown]").last().click();
    },

    async useDialogBrowserItem(page: Page, rowTitle: string, buttonName: string = "Use") {
        await Rows.actionByRowTitle(page, rowTitle, "hover", true);
        await page.getByRole("dialog").locator(".row", {hasText: rowTitle}).getByRole("button", {name: buttonName}).click();
    },
    async goToDashboard(page: Page): Promise<void> {
        await page.getByRole("link", {name: "Go to Dashboard"}).click();
    }
};

export const Applications = {
    ...Rows,
    async goToApplications(page: Page): Promise<void> {
        if (page.url().endsWith("/app/applications")) return;
        await page.getByRole("link", {name: "Go to Applications"}).click();
        await page.getByText("Open application").waitFor();
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
        });
    },

    async openAppBySearch(page: Page, appName: string): Promise<void> {
        await this.goToApplications(page);
        await this.searchFor(page, appName);
        await page.locator("a[class^=app-card]").getByText(appName).first().click();
        await page.waitForURL("**/app/jobs/create?app=**")
    },

    async runAppAndOpenTerminal(page: Page, appName: string, nodeCount: number, beforeSubmit?: (page: Page) => Promise<void>, jobName?: string): Promise<void> {
        if (appName === this.AppNames.TestApplication) {
            await Applications.openAppBySearch(page, appName);
        } else {
            await Applications.openApp(page, appName);
        }
        await Components.selectAvailableMachineType(page);
        if (jobName) await Runs.setJobTitle(page, jobName);
        if (nodeCount > 0) await Runs.setNodeCount(page, nodeCount);
        await beforeSubmit?.(page);
        await Runs.submitAndWaitForRunning(page);
    },

    async runAppAndOpenTerminalWithTerminalPage(page: Page, appName: string, nodeCount: number, beforeSubmit?: (page: Page) => Promise<void>, jobName?: string): Promise<Page> {
        await this.runAppAndOpenTerminal(page, appName, nodeCount, beforeSubmit, jobName);
        return await Runs.openTerminal(page);
    },

    AppNames: {
        TestApplication: "Test application",
        LicenseTestApplication: "License Test",
        Terminal: "Terminal",
    }
};

export const Runs = {
    newJobName(): string {
        return Help.newResourceName("JobName");
    },

    async goToRuns(page: Page): Promise<void> {
        await NetworkCalls.awaitResponse(page, "**/api/jobs/browse**", async () => {
            await page.getByRole("link", {name: "Go to Compute"}).click();
        });
        await Components.projectSwitcher(page, "hover");
    },

    async runApplicationAgain(page: Page, jobName: string): Promise<void> {
        await Runs.goToRuns(page);
        await Components.projectSwitcher(page, "hover");
        await Applications.actionByRowTitle(page, jobName, "click");
        await NetworkCalls.awaitResponse(page, "**/api/jobs/retrieve?id=**", async () => {
            await page.getByText("Run again").click();
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

    async terminateViewedRun(page: Page, options: {waitForUi?: boolean} = {}): Promise<void> {
        const waitForUi = options.waitForUi ?? true;
        const completed = page.getByText("Your job has completed").first();
        const runAgain = page.getByText("Run again");
        const stopButton = page.getByRole("button", {name: "Stop application"}).filter({visible: true}).first();

        await expect(completed.or(stopButton)).toBeVisible({timeout: 10_000});

        if (await completed.isVisible()) {
            if (waitForUi) await runAgain.waitFor();
            return;
        }

        const response = page.waitForResponse("**/jobs/terminate", {timeout: 10_000}).catch(() => null);
        const clickStop = async () => {
            if (await stopButton.getAttribute("data-tag") === "confirm-button") {
                await stopButton.dispatchEvent("mousedown");
            } else {
                await stopButton.click();
            }
        };
        const clicked = await clickStop().then(() => true).catch(() => false);

        if (clicked && (await response) !== null && !waitForUi) return;

        await completed.waitFor({timeout: 10_000});
        await runAgain.waitFor();
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

        await page.waitForURL("**/app/jobs/properties/**", {timeout: 30_000});

        const testInfo = test.info();
        test.setTimeout(testInfo.timeout + 60_000);
        const running = page.getByText("is now running").first();
        const failed = page.getByText("Your job has failed").first();
        await expect(running.or(failed)).toBeVisible({timeout: 120_000});
        if (await failed.isVisible()) throw new Error("Job failed before reaching the running state");
    },

    async extendTimeBy(page: Page, extension: 1 | 8 | 24) {
        await page.getByRole("button", {name: `+${extension}`}).click();
    },

    async openTerminal(page: Page): Promise<Page> {
        for (let attempt = 0; attempt < 2; attempt++) {
            const terminalPagePromise = page.waitForEvent("popup");
            await page.getByRole("button", {name: "Open terminal"}).click();
            const terminalPage = await terminalPagePromise;
            const prompt = terminalPage.locator("span", {hasText: "/work"});
            const ready = await prompt.waitFor({timeout: 60_000}).then(() => true).catch(() => false);
            if (ready) {
                await terminalPage.keyboard.press("Tab");
                return terminalPage;
            }
            await terminalPage.close();
        }
        throw new Error("Terminal did not become ready");
    },

    async activateOptionalParameter(page: Page, parameterName: string): Promise<void> {
        await page.getByRole("textbox", {name: parameterName}).waitFor();
    },

    JobResources: {
        async addFolder(page: Page, driveName: string, folderName: string): Promise<void> {
            await NetworkCalls.awaitResponse(page, "**/api/files/browse?**", async () => {
                await page.getByRole("textbox", {name: "Folder #1"}).click();
            });
            await File.ensureDialogDriveActive(page, driveName);
            await Components.useDialogBrowserItem(page, folderName, "Use");
        },

        async addPublicLink(page: Page, publicLinkName: string): Promise<void> {
            await NetworkCalls.awaitResponse(page, "**/api/ingresses/browse?**", async () => {
                await page.getByRole("textbox", {name: "Public link #1"}).click();
            });
            await Components.useDialogBrowserItem(page, publicLinkName);
        },

        async addPublicIP(page: Page, publicIP: string): Promise<void> {
            await NetworkCalls.awaitResponse(page, "**/api/networkips/browse?**", async () => {
                await page.getByRole("textbox", {name: "Public IP #1"}).click();
            });
            await Components.useDialogBrowserItem(page, publicIP);
        },

        async toggleEnableSSHServer(page: Page): Promise<void> {
            expect(page.url()).toContain("/jobs/create");
            await page.getByRole("combobox", {name: "SSH access"}).selectOption("true");
        },
    }
};

export const Project = {
    newProjectName(): string {
        return Help.newResourceName("ProjectName");
    },

    async changeTo(page: Page, projectTitle: string): Promise<void> {
        await Components.projectSwitcher(page, "hover");
        await Components.projectSwitcher(page, "click");
        await page.getByTitle(projectTitle, {exact: true}).click();
    },

    async inviteUsers(page: Page, usernames: string[]): Promise<void> {
        await Accounting.goTo(page, "Members");
        await page.getByRole("button", {name: "Invite"}).click();
        await page.getByRole("dialog").getByRole("button").nth(1).click();
        for (const username of usernames) {
            await Accounting.Project.inviteUser(page, username);
        }

        await page.getByPlaceholder("Add by username").click(); // https://github.com/SDU-eScience/UCloud/issues/5307
        await page.keyboard.press("Escape");
    },

    async acceptInvites(pages: Page[], projectName: string) {
        for (const page of pages) {
            if (page.url().endsWith("/app") || page.url().endsWith("/app/dashboard")) {
                await Components.clickRefreshAndWait(page);
            } else {
                await Components.goToDashboard(page);
            }
            const inviteRow = page.locator(".row", {hasText: projectName});
            await inviteRow.waitFor();
            await inviteRow.getByRole("button", {name: "Accept"}).click();
            await inviteRow.waitFor({state: "hidden", timeout: 30_000});
        }
    },

    async changeRoles(page: Page, username: string, role: "Admin" | "User" | "PI"): Promise<void> {
        const roleControl = role === "PI"
            ? page.locator(`input[name=PI${username}]`)
            : page.locator("div[class^=list-item]", {hasText: username}).getByRole("radio", {name: role});
        for (let attempt = 0; attempt < 10; attempt++) {
            if (await roleControl.isVisible()) break;
            await Components.clickRefreshAndWait(page, "**/api/projects/v2/retrieve**");
            await page.waitForTimeout(500);
        }
        await roleControl.waitFor({timeout: 30_000});
        await roleControl.click();
        if (role === "PI") {
            await page.getByRole("dialog").getByRole("button", {name: "Transfer"}).click();
        }
    }
};

export const Resources = {
    ...Rows,
    async open(page: Page, resourceName: string) {
        await this.actionByRowTitle(page, resourceName, "dblclick");
    },

    async goTo(page: Page, resource: "Links" | "IP addresses" | "SSH keys" | "Licenses" | "Private networks") {
        await page.getByRole("link", {name: "Go to Resources"}).hover();
        await page.getByRole("link", {name: resource, exact: true}).click();
        await page.locator(`div[data-component="project-switcher"]`).first().waitFor();
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

            await page.getByRole("button", {name: /^Create public link/}).click();
            await page.getByPlaceholder("my-link").fill(name);

            if (await page.getByRole("dialog").getByText("No product selected").isVisible()) {
                await page.getByRole("dialog").getByText("No product selected").click();
                await page.getByRole('cell', {name: providerAndProducts.products_used_in_tests.public_link}).click();
            }


            await NetworkCalls.awaitResponse(page, "**/api/ingresses", async () => {
                await page.getByRole("dialog").getByRole("button", {name: "Create", exact: true, disabled: false}).click();
            });
            await page.getByRole("dialog").waitFor({state: "hidden"});
            return name;
        },

        async delete(page: Page, name: string): Promise<void> {
            await Rows.actionByRowTitle(page, name, "click");
            await NetworkCalls.awaitResponse(page, "**/api/ingresses", async () => {
                await Components.clickConfirmationButton(page, "Delete");
            });
        }
    },

    IPs: {
        async createNew(page: Page): Promise<string> {
            await NetworkCalls.awaitProducts(page, async () => {
                await Resources.goTo(page, "IP addresses");
            });

            await page.getByRole("button", {name: /^Create public IP/}).click();

            if (providerAndProducts.products_used_in_tests.public_ip == null) {
                throw Error("Public IP is null in `IPs.createNew`. This should have been caught before.")
            }
            await page.getByRole("dialog").getByText(providerAndProducts.products_used_in_tests.public_ip).waitFor();
            await this.fillPortRowInDialog(page);
            const result = await NetworkCalls.awaitResponse(page, "**/api/networkips", async () => {
                await page.getByRole("dialog").getByRole("button", {name: "Create", exact: true, disabled: false}).click();
            });
            await page.getByRole("dialog").waitFor({state: "hidden"});

            const parsed: {responses: [{id: string}]} = JSON.parse(await result.text());

            // TODO(Jonas): This should get the IP and not the `id` that is initially shown.
            await page.getByText(parsed.responses[0].id, {exact: true}).waitFor({state: "hidden"})
            return await page.locator(".row").nth(1).innerText();
        },

        async fillPortRowInDialog(page: Page): Promise<void> {
            await page.getByRole("dialog").locator("input").first().fill("123");
            await page.getByRole("dialog").locator("input").nth(1).fill("321");
            await page.getByRole("dialog").locator("td > button").click();
        },

        async delete(page: Page, name: string): Promise<void> {
            await Rows.actionByRowTitle(page, name, "click");
            await NetworkCalls.awaitResponse(page, "**/api/networkips", async () => {
                await Components.clickConfirmationButton(page, "Delete");
            });
        }
    },

    SSHKeys: {
        DefaultSSHKey: "ssh-rsa a-bunch-of-text",

        newSSHKeyName(): string {
            return Help.newResourceName("SSHKey")
        },

        async delete(page: Page, name: string): Promise<void> {
            await Rows.actionByRowTitle(page, name, "click");
            await NetworkCalls.awaitResponse(page, "**/api/ssh", async () => {
                await Components.clickConfirmationButton(page, "Delete");
            });
        },

        async createNew(page: Page): Promise<string> {
            await page.getByText("Add SSH key").click();
            const title = this.newSSHKeyName();
            await page.locator("#key-title").fill(title);
            await page.locator("#key-contents").fill(this.DefaultSSHKey);
            await NetworkCalls.awaitResponse(page, "**/api/ssh", async () => {
                await page.getByRole("dialog").getByRole("button", {name: "Add SSH key", exact: true}).click();
            });
            await page.waitForURL(url => url.pathname.endsWith("/ssh-keys"));
            return title;
        }
    },

    Licenses: {
        async activateLicense(page: Page): Promise<number> {
            await page.getByText("Activate license").click();

            const result = (await NetworkCalls.awaitResponse(page, "**/api/licenses", async () => {
                await page.getByRole("dialog").getByRole("button", {name: "Activate", disabled: false}).click();
            }));

            const obj: {responses: {id: number}[]} = JSON.parse(await result.text());
            return obj.responses[0].id;
        }
    },

    PrivateNetworks: {
        newName(): string {
            return Help.newResourceName("PrivateNetwork");
        },

        newSubdomainName(): string {
            return Help.newResourceName("Subdomain");
        },

        newJobNetworkName(): string {
            return Help.newResourceName("Subdomain");
        },

        async createPrivateNetwork(page: Page, name: string, subdomain: string): Promise<void> {
            await Resources.goTo(page, "Private networks");
            await page.getByText("Create private network").click();
            await page.getByPlaceholder("My private network").fill(name);
            await page.getByPlaceholder("my-network").fill(subdomain);

            if (await page.getByRole("dialog").getByText("No product selected").isVisible()) {
                await page.getByRole("dialog").getByText("No product selected").click();
                await page.getByRole("cell", {name: providerAndProducts.products_used_in_tests.private_network}).last().click();
            }


            await page.getByRole("dialog").getByRole("button", {name: "Create", exact: true}).click();
        },

        async delete(page: Page, name: string): Promise<void> {
            await Resources.goTo(page, "Private networks");
            await Rows.actionByRowTitle(page, name, "click");
            await NetworkCalls.awaitResponse(page, "**/api/private-networks**", async () => {
                await Components.clickConfirmationButton(page, "Delete");
            });
        }
    }
}

export const Terminal = {
    async enterCmd(page: Page, command: string): Promise<void> {
        await page.keyboard.type(command);
        await page.keyboard.press("Enter");
    },

    async createFile(page: Page, sizeInGB: number) {
        await this.createFileOfSize(page, sizeInGB * 1_000_000_000);
    },

    async createFileOfSize(page: Page, sizeInBytes: number) {
        if (isDev(data.location_origin)) {
            // fallocate is not supported on dev.
            await this.enterCmd(page, `dd if=/dev/zero of=example bs=1000 count=${Math.ceil(sizeInBytes / 1000)} && echo "file creation done!"`);
        } else {
            await this.enterCmd(page, `fallocate -l ${sizeInBytes} example && echo "file creation done!"`);
        }

        await page.getByText("file creation done!", {exact: true}).waitFor({timeout: 300_000});
    }
}


const Help = {
    newResourceName(namespace: string): string {
        return namespace + Math.random().toString().slice(2, 7);
    }
};

export const NetworkCalls = {
    async awaitResponse(page: Page, path: string, block: () => Promise<void>, timeout?: number): Promise<ReturnType<typeof page.waitForResponse>> {
        const waiter = page.waitForResponse(path, timeout == null ? undefined : {timeout});
        await block();
        return await waiter;
    },

    async awaitProducts(page: Page, block: () => Promise<void>) {
        return await NetworkCalls.awaitResponse(page, "**/retrieveProducts**", block);
    }
}


type AccountingPage = "Allocations" | "Usage" | "Grant applications" | "Apply for resources" | "Members" | "Project settings" | "Sub-projects";
export const Accounting = {
    async goTo(page: Page, linkName: AccountingPage): Promise<void> {
        await page.getByRole("link", {name: "Go to Project"}).hover();

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

        newGroupName(): string {
            return Help.newResourceName("GroupName")
        },

        async newGroup(page: Page, groupName?: string): Promise<string> {
            const _groupName = groupName ?? this.newGroupName();
            await page.getByRole("button", {name: "New group"}).click();
            await page.getByPlaceholder("New group name...").fill(_groupName);
            await page.getByRole("button", {name: "Create"}).click();
            return _groupName;
        },

        async inviteUser(page: Page, username: string): Promise<void> {
            await page.getByPlaceholder("Add by username").fill(username);
            await page.getByRole("dialog").getByRole("button", {name: "Send"}).click();
        },

        async acceptProjectInvite(page: Page, projectName: string): Promise<void> {
            await page.locator(".row", {hasText: projectName}).getByRole("button").first().click();
        }
    },

    GrantApplication: {
        async fillProjectName(page: Page, projectName: string): Promise<void> {
            await page.getByPlaceholder("Please enter the title of your project").fill(projectName);
        },

        async setMonths(page: Page, months: number): Promise<void> {
            await page.getByLabel("Duration").selectOption(`${months}`);
        },

        async toggleGrantGiver(page: Page, grantGiver: string): Promise<void> {
            await page.waitForLoadState();
            await page.locator("label", {hasText: grantGiver}).waitFor();
            await page.locator("label", {hasText: grantGiver}).last().click();
        },

        async fillQuotaFields(page: Page, quotas: RequestedQuotas): Promise<void> {
            for (const [productName, quota] of quotas) {
                await page.locator(`input[id *= '/${productName}/']`).fill(quota.toString());
            }
        },

        async fillDefaultApplicationTextFields(page: Page, isNewProject: boolean): Promise<void> {
            const textFields = isNewProject ? providerAndProducts.application_text_fields.new : providerAndProducts.application_text_fields.existing;
            for (const applicationField of textFields) {
                await page.getByRole("textbox", {name: applicationField}).last().fill("Testing purposes");
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
            await page.reload();
            await page.locator(".grant-giver button").first().waitFor();
            await page.mouse.wheel(0, 5000);
            await NetworkCalls.awaitResponse(page, "**/grants/v2/updateState", async () => {
                await page.locator(".grant-giver button").nth(1).dispatchEvent("mousedown");
            });
        }
    },
};

export const Admin = {
    AdminUser: data.users.admin,

    async newLoggedInAdminPage(existingPage: Page): Promise<Page> {
        const page = existingPage;
        if (!page) throw new Error("Failed to create page for admin login");

        await User.loginDirect(page, Admin.AdminUser);
        await page.getByRole("link", {name: "Go to dashboard"}).waitFor();

        return page;
    },
};

async function _rename(page: Page, newName: string) {
    await page.getByText("Rename").click();
    const renameField = page.locator("input.rename-field");
    await renameField.fill(newName);
    await renameField.press("Enter");
}

export function testCtx(args: string[]): {user: {username: string; password: string;}; projectName?: string;} {
    const context = args[1] as Contexts;
    return {
        user: ctxUser(context),
        projectName: context === "Personal Workspace" ? undefined : sharedTestProjectName()
    }
}

export function ctxUser(ctx: Contexts) {
    return TestUsers[ctx];
}
export function sharedTestProjectName(): string {
    return runtimeTestUsers.projectName ?? "";
}
