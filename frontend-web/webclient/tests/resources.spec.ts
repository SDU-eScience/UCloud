import {expect, test} from "@playwright/test";
import {User, Resources, Applications, Runs, Components, Terminal, TestContexts, testCtx, Project, Rows} from "./shared";

test.beforeEach(async ({page}, testInfo) => {
    const args = testCtx(testInfo.titlePath);
    await User.login(page, args.user);
    if (args.projectName) await Project.changeTo(page, args.projectName);
});

const {PublicLinks, IPs, SSHKeys, Licenses} = Resources;

TestContexts.map(ctx => {
    test.describe(ctx, () => {
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
                await Applications.openApp(page, "Coder");
                await Components.selectAvailableMachineType(page);
                await Runs.JobResources.addPublicLink(page, publicLinkName);
                await Runs.submitAndWaitForRunning(page);
                await page.getByText("Links (1)").click();
                const publicLinkPagePromise = page.waitForEvent("popup");
                await page.getByRole("link", {name: publicLinkName, exact: false}).click();
                const followedPublicLink = await publicLinkPagePromise;
                await followedPublicLink.getByText("No, I don't trust the authors").hover();
                await followedPublicLink.close();

                await Runs.terminateViewedRun(page);

                await Resources.goTo(page, "Links");
                await PublicLinks.delete(page, publicLinkName);
            });
        });

        /* Resources.IPs */
        test.describe("Public IPs - check public IPs work", () => {
            test("Create public IP, view properties, attach to job, ensure it's visible on job/properties-page", async ({page}) => {
                const publicIp = await IPs.createNew(page);
                await Rows.actionByRowTitle(page, publicIp, "dblclick");

                await Applications.goToApplications(page);
                await Applications.openApp(page, "Terminal");
                await Components.selectAvailableMachineType(page);
                await Runs.JobResources.addPublicIP(page, publicIp);
                await Runs.submitAndWaitForRunning(page);
                await page.getByText(`Successfully attached the following IP addresses: ${publicIp}`).hover();
                await Runs.terminateViewedRun(page);
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
    });
});