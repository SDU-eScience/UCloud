import {expect, test} from "@playwright/test";
import {login, Resources} from "./shared";

Resources.PublicLinks;

test.beforeEach(async ({page}) => {
    await login(page);
});

const {PublicLinks, IPs, SSHKeys, Licenses} = Resources;

test("Create public link, view properties, delete", async ({page}) => {
    await Resources.goTo(page, "Links");
    const publicLinkName = await PublicLinks.createNew(page);
    await page.getByRole("button", {name: "Create", disabled: false}).click();
    await Resources.open(page, publicLinkName);
    await expect(page.getByText("ID:")).toHaveCount(1);
    await expect(page.getByText("Product:")).toHaveCount(1);
    await expect(page.getByText("Created by:")).toHaveCount(1);
    await expect(page.getByText("Created at:")).toHaveCount(1);
    await page.goBack();
    await PublicLinks.delete(page, publicLinkName);
});

/* Resources.IPs */
test("Create ip and view properties", async ({page}) => {
    await Resources.goTo(page, "IP addresses");
    const publicIpName = await IPs.createNew(page);
});

test("Create ssh keys", async ({page}) => {
    await Resources.goTo(page, "SSH keys");
    const sshkey = SSHKeys.createNew(page);
});

test("Create licenses", async ({page}) => {
    await Resources.goTo(page, "Licenses");
    await Licenses.activateLicense(page);
});