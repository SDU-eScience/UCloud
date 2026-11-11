import {Browser, expect, Page, test as base, TestInfo} from "@playwright/test";
import {default as data} from "./test_data.json" with {type: "json"};
import {
    apiJson,
    Contexts,
    ctxUser,
    deleteDriveByApi,
    deleteDriveByApiWithTokens,
    Drive,
    File,
    Project,
    sharedTestProjectName,
    testCtx,
    trashByApi,
    User,
} from "./shared";

export {deleteDriveByApi, trashByApi};

export type FileRoot = {
    name: string;
    driveName: string;
    path: string;
};

export type FileDrive = {
    name: string;
    id: string;
    ephemeral: boolean;
};

type Fixtures = {
    testPage: Page;
    fileRoot: FileRoot;
    fileDrive: FileDrive;
};

const workerDrives = new Map<Contexts, {name: string; id: string; projectId: string}>();

function uniqueName(prefix: string, testInfo: TestInfo): string {
    return `${prefix}-${testInfo.workerIndex}-${testInfo.retry}-${Math.random().toString(36).slice(2, 10)}`;
}

export function currentPath(page: Page): string {
    const path = new URL(page.url()).searchParams.get("path");
    if (!path) throw new Error(`No path in URL: ${page.url()}`);
    return path;
}

export async function walletState(page: Page, category: string): Promise<{usage: number; quota: number;}> {
    const result = await apiJson<{
        items: Array<{
            paysFor: {name: string;};
            totalUsage: number;
            activeUsage?: number;
            quota: number;
        }>;
    }>(page, "GET", "/api/accounting/v2/browseWallets?itemsPerPage=250");
    const wallet = result.items.find(it => it.paysFor.name === category);
    return {
        usage: wallet ? (wallet.activeUsage ?? wallet.totalUsage) : 0,
        quota: wallet?.quota ?? 0,
    };
}

export async function walletUsage(page: Page, category: string): Promise<number> {
    return (await walletState(page, category)).usage;
}

export async function walletLocked(page: Page, category: string): Promise<boolean> {
    const state = await walletState(page, category);
    return state.quota > 0 && state.usage >= state.quota;
}

async function ensureWorkerDrive(page: Page, ctx: Contexts, testInfo: TestInfo): Promise<{name: string; id: string}> {
    const existing = workerDrives.get(ctx);
    if (existing) return existing;

    let drive: {name: string; id: string};
    if (ctx === "Project User") {
        const name = Drive.memberFiles(ctxUser(ctx).username);
        await Drive.openDrive(page, name);
        drive = {name, id: currentPath(page)};
    } else {
        const name = uniqueName("E2E-Drive", testInfo);
        await Drive.create(page, name);
        await Drive.openDrive(page, name);
        drive = {name, id: currentPath(page)};
    }
    const projectId = await page.evaluate(() => localStorage.getItem("project") ?? "");
    workerDrives.set(ctx, {...drive, projectId});
    return drive;
}

export async function cleanupWorkerDrives(browser: Browser): Promise<void> {
    for (const [ctx, drive] of workerDrives) {
        if (ctx === "Project User") continue;

        const page = await browser.newPage();
        try {
            if (data["login_cookie"]) await page.context().addCookies([data["login_cookie"]]);
            const tokens = await User.loginTokens(page, ctxUser(ctx));
            await deleteDriveByApiWithTokens(page, tokens, drive.id, drive.projectId);
        } catch (e) {
            console.warn(`Failed to delete worker drive ${drive.name}`, e);
        } finally {
            await page.close();
        }
    }
    workerDrives.clear();
}

export const test = base.extend<Fixtures>({
    testPage: async ({page}, use, testInfo) => {
        if (data["login_cookie"]) await page.context().addCookies([data["login_cookie"]]);
        const args = testCtx(testInfo.titlePath);
        await User.loginDirect(page, args.user);
        if (args.projectName) await Project.changeTo(page, args.projectName);
        await use(page);
    },

    fileRoot: async ({testPage}, use, testInfo) => {
        const ctx = testInfo.titlePath[1] as Contexts;
        const drive = await ensureWorkerDrive(testPage, ctx, testInfo);
        await Drive.openDrive(testPage, drive.name);

        const name = uniqueName("E2E-Root", testInfo);
        await File.create(testPage, name);
        await File.open(testPage, name);
        const path = currentPath(testPage);

        await use({name, driveName: drive.name, path});

        try {
            await trashByApi(testPage, path);
        } catch (e) {
            console.warn(`Failed to trash file root ${name}`, e);
        }
    },

    fileDrive: async ({testPage}, use, testInfo) => {
        const ctx = testInfo.titlePath[1] as Contexts;
        if (ctx === "Project User") {
            const name = Drive.memberFiles(ctxUser(ctx).username);
            await Drive.openDrive(testPage, name);
            await use({name, id: currentPath(testPage), ephemeral: false});
            return;
        }

        const name = uniqueName("E2E-TestDrive", testInfo);
        await Drive.create(testPage, name);
        await Drive.openDrive(testPage, name);
        const id = currentPath(testPage);

        await use({name, id, ephemeral: true});

        try {
            await deleteDriveByApi(testPage, id);
        } catch (e) {
            console.warn(`Failed to delete test drive ${name}`, e);
        }
    },
});

export {expect};
