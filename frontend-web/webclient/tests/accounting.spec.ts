import test from "@playwright/test";
import {Accounting, Admin, Components, Project, Rows, User} from "./shared";


test("Apply for resources, approve (from admin user), verify resources are in allocations", async ({page, context}) => {
    test.setTimeout(60_000);
    const adminUserPage = await Admin.newLoggedInAdminPage(context);
    await Components.projectSwitcher(adminUserPage, "click");
    await adminUserPage.getByText("Provider K8s").click();

    // Create user with no resources
    const {username, password} = User.newUserCredentials();

    await User.create(adminUserPage, {username, password});

    const newUserPage = await context.browser()?.newPage();
    if (!newUserPage) throw Error("Couldn't create page-instance for new user. Exiting");
    await User.login(newUserPage, {username, password}, true);

    await Accounting.goTo(newUserPage, "Allocations");
    await newUserPage.getByText("You do not have any allocations at the moment.").waitFor();
    await Components.projectSwitcher(newUserPage, "click");
    await newUserPage.getByText("No projects found").waitFor();

    await Accounting.goTo(newUserPage, "Apply for resources");

    const projectName = Accounting.Project.newProjectName();
    await Accounting.GrantApplication.fillProjectName(newUserPage, projectName);
    await Accounting.GrantApplication.toggleGrantGiver(newUserPage, "k8s");
    await Accounting.GrantApplication.fillQuotaFields(newUserPage, [{field: "Core-hours requested", quota: 1000}, {field: "GB requested", quota: 1000}]);
    await Accounting.GrantApplication.fillDefaultApplicationTextFields(newUserPage);

    const id = await Accounting.GrantApplication.submit(newUserPage);

    await Accounting.goTo(adminUserPage, "Grant applications");
    await adminUserPage.getByText("Show applications received").click();
    await Rows.actionByRowTitle(adminUserPage, `${id}: ${projectName}`, "dblclick");
    await Accounting.GrantApplication.approve(adminUserPage);

    await Accounting.goTo(newUserPage, "Allocations");
    await newUserPage.getByText("You do not have any allocations at the moment.").waitFor();
    await Project.changeTo(newUserPage, projectName);

    await newUserPage.getByText("0K / 1K Core-hours (0%)").first().hover();
    await newUserPage.getByText("0 GB / 1,000 GB (0%)").first().hover();
});