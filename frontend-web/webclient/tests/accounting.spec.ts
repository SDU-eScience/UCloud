import test from "@playwright/test";
import {Accounting, User} from "./shared";
import {default as data} from "./test_data.json" with {type: "json"};

test("Apply for resources, approve (from admin user),", async ({page: adminUserPage, context}) => {

    // Create user with no resources
    const username = "useruser";
    const password = "useruserpasswordpassword";
    await User.create(adminUserPage, {username, password});

    const newUserPage = await context.browser()?.newPage();
    if (!newUserPage) throw Error("Couldn't create page-instance for new user. Exiting");

    await Accounting.goTo(adminUserPage, "Allocations");

    await Accounting.goTo(adminUserPage, "Apply for resources");
    await adminUserPage.getByText("Project information").waitFor();

    const projectName = Accounting.Project.newProjectName();
    await Accounting.Project.Application.fillProjectName(adminUserPage, projectName);
    await Accounting.Project.Application.toggleGrantGiver(adminUserPage, data.providers.k8s);
    await Accounting.Project.Application.fillQuotaFields(adminUserPage, [{field: "Core-hours requested", quota: 1000}, {field: "GB requested", quota: 1000}]);
    await Accounting.Project.Application.fillApplicationTextFields(adminUserPage, [{name: "Application", content: "Text description"}]);
    const id = await Accounting.Project.Application.submit(adminUserPage);
    // So far, so good, but we aren't presented with any grant givers
    throw Error("Not remotely done");
});