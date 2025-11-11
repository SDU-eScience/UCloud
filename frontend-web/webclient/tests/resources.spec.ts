import {expect, test} from "@playwright/test";
import {User, Resources} from "./shared";

Resources.PublicLinks;

test.beforeEach(async ({page}) => {
    await User.login(page);
});

const {PublicLinks, IPs, SSHKeys, Licenses} = Resources;

test.skip("Create public link, view properties, delete", async ({page}) => {
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
    const publicIpName = await IPs.createNew(page);
});

test("Create ssh key, delete ssh key", async ({page}) => {
    await Resources.goTo(page, "SSH keys");
    const sshkey = await SSHKeys.createNew(page);
    await expect(page.getByText(sshkey)).toHaveCount(1);
    await SSHKeys.delete(page, sshkey);
    await expect(page.getByText(sshkey)).toHaveCount(0);
});

test.skip("Create licenses", async ({page}) => {
    await Resources.goTo(page, "Licenses");
    await Licenses.activateLicense(page);
});