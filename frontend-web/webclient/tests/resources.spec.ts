import {expect, test} from "@playwright/test";
import {User, Resources, Applications, Runs, Components, NetworkCalls, Terminal} from "./shared";
import {default as data} from "./test_data.json" with {type: "json"};

test.beforeEach(async ({page}) => {
    await User.login(page, data.users.with_resources);
});

const {PublicLinks, IPs, SSHKeys, Licenses} = Resources;

test.describe("Public links - check public links work", () => {
    test("Create public link, view properties, delete", async ({page}) => {
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

    test("Create public link, mount link for job, check that link is available, stop job, delete public link", async ({page}) => {
        const publicLinkName = await PublicLinks.createNew(page);
        await page.getByRole("button", {name: "Create", disabled: false}).click();

        await PublicLinks.delete(page, publicLinkName);
    });
    test("Create public link, add to job, verify it's available for the job, delete public link", async ({page}) => {
        test.setTimeout(240_000);

        // Create public link
        const publicLinkName = await PublicLinks.createNew(page);
        await page.getByRole("button", {name: "Create", disabled: false}).click();

        await Applications.goToApplications(page);
        await page.getByRole("button", {name: "Open application"}).click();
        const jobName = Runs.newJobName();

        await Runs.setJobTitle(page, jobName);
        await Components.selectAvailableMachineType(page);
        await Runs.extendTimeBy(page, 1);
        await Runs.submitAndWaitForRunning(page);
        await Runs.extendTimeBy(page, 1);



        // Delete public link
        await Resources.goTo(page, "Links");
        await PublicLinks.delete(page, publicLinkName);
    });
});

/* Resources.IPs */
test.describe("Public IPs - check public IPs work", () => {
    test("Create ip and view properties", async ({page}) => {
        const publicIpName = await IPs.createNew(page);
        /* TODO(Jonas): Get actual name of IP, only through retrieve? */
        console.log(publicIpName);
    });
});

test("Create ssh key, delete ssh key", async ({page}) => {
    await Resources.goTo(page, "SSH keys");
    const sshkey = await SSHKeys.createNew(page);
    await expect(page.getByText(sshkey)).toHaveCount(1);
    await SSHKeys.delete(page, sshkey);
    await expect(page.getByText(sshkey)).toHaveCount(0);
});

test.describe("SSH - check SSH connections work", () => {
    test("Create SSH key, add to job, poll job through terminal to validate it being present, delete key, stop run", async ({page}) => {
        await Resources.goTo(page, "SSH keys");
        const sshkey = await SSHKeys.createNew(page);
        await Applications.goToApplications(page);
        await page.getByRole("button", {name: "Open application"}).click();
        await Components.selectAvailableMachineType(page);
        await Runs.JobResources.toggleEnableSSHServer(page);
        const jobname = Runs.newJobName();
        await Runs.setJobTitle(page, jobname);
        await Runs.submitAndWaitForRunning(page);

        // check for tab that contains ssh
        await page.locator("nav > div:nth-child(4)").click();
        const terminalPage = await Runs.openTerminal(page);

        await Terminal.enterCmd(terminalPage, "cat /etc/ucloud/ssh/authorized_keys.ucloud");
        await terminalPage.getByText(Resources.SSHKeys.DefaultSSHKey).first().hover();
        await terminalPage.close();
        await Resources.goTo(page, "SSH keys");
        await SSHKeys.delete(page, sshkey);
        await Runs.stopRun(page, jobname)
    });
});

test("Create licenses", async ({page}) => {
    await NetworkCalls.awaitProducts(page, async () => {
        await Resources.goTo(page, "Licenses");
    });

    const licenseId = await Licenses.activateLicense(page);
    console.log(licenseId)
});