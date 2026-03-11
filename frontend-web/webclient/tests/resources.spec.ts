import {expect, test} from "@playwright/test";
import {User, Resources, Applications, Runs, Components, Terminal, TestContexts, testCtx, Project, Rows} from "./shared";

test.beforeEach(async ({page}, testInfo) => {
    const args = testCtx(testInfo.titlePath);
    await User.login(page, args.user);
    if (args.projectName) await Project.changeTo(page, args.projectName);
});


const {PublicLinks, IPs, SSHKeys} = Resources;

TestContexts.map(ctx => {
    test.describe(ctx, () => {
        test.describe("Public links - check public links work", () => {
            test("Create public link, view properties, delete", async ({page}) => {
                const publicLinkName = await PublicLinks.createNew(page);
                await Resources.open(page, publicLinkName);
                await expect(page.getByText("ID:")).toHaveCount(1);
                await expect(page.getByText("Product:")).toHaveCount(1);
                await expect(page.getByText("Created by:")).toHaveCount(1);
                await expect(page.getByText("Created at:")).toHaveCount(1);
                await page.goBack();
                await PublicLinks.delete(page, publicLinkName);
            });


            test("interface(s) connectivity", async ({page}) => {
                test.setTimeout(120_000);
                const publicLinkName = await PublicLinks.createNew(page);
                await Applications.openAppBySearch(page, Applications.AppNames.TestApplication);
                await Components.selectAvailableMachineType(page);
                await Runs.JobResources.addPublicLink(page, publicLinkName);
                await Runs.submitAndWaitForRunning(page);

                const interfacePage = page.waitForEvent("popup");
                await page.getByRole("link", {name: "Open interface", exact: true}).click();
                const followedInterface = await interfacePage;
                await followedInterface.getByText("Directory listing for /").hover();
                await followedInterface.close();

                await page.getByText("Links (1)").click();
                const publicLinkPagePromise = page.waitForEvent("popup");
                await page.getByRole("link", {name: publicLinkName, exact: false}).click();
                const followedPublicLink = await publicLinkPagePromise;
                await followedPublicLink.getByText("Directory listing for /").hover();
                await followedPublicLink.close();

                await Runs.terminateViewedRun(page);

                await Resources.goTo(page, "Links");
                await PublicLinks.delete(page, publicLinkName);
            });
        });

        /* Resources.IPs */
        test("Public IPs - check public IPs work", async ({page}) => {
            const publicIp = await IPs.createNew(page);
            await Rows.actionByRowTitle(page, publicIp, "dblclick");

            await Applications.goToApplications(page);
            await Applications.openAppBySearch(page, "Test application");
            await Components.selectAvailableMachineType(page);
            await Runs.JobResources.addPublicIP(page, publicIp);
            await Runs.submitAndWaitForRunning(page);
            await page.getByText(`Successfully attached the following IP addresses: ${publicIp}`).hover();
            await Runs.terminateViewedRun(page);
        });

        test("Create ssh key, delete ssh key", async ({page}) => {
            await Resources.goTo(page, "SSH keys");
            const sshkey = await SSHKeys.createNew(page);
            await expect(page.getByText(sshkey)).toHaveCount(1);
            await SSHKeys.delete(page, sshkey);
            await expect(page.getByText(sshkey)).toHaveCount(0);
        });

        test("SSH - check SSH connections work", async ({page}) => {
            test.setTimeout(60_000);
            await Resources.goTo(page, "SSH keys");
            const sshkey = await SSHKeys.createNew(page);
            await Applications.openAppBySearch(page, "Test application");
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

            await Runs.terminateViewedRun(page);

            await Resources.goTo(page, "SSH keys");
            await SSHKeys.delete(page, sshkey);

        });
    });
});