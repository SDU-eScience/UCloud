import test from "@playwright/test";
import {Accounting, User} from "./shared";
import {default as data} from "./test_data.json" with {type: "json"};

test.beforeEach(async ({page}) => {
    await User.login(page, data.users.with_resources);
})

test("Go to usage", async ({page}) => {
    console.log(Accounting.Usage.ProductCategoryNames)
    await Accounting.goTo(page, "Usage");
});

test("Apply for resources", async ({page}) => {
    await Accounting.goTo(page, "Apply for resources");
    await page.getByText("Project information").waitFor();
    const projectName = Accounting.Project.newProjectName();
    await Accounting.Project.Application.fillProjectName(page, projectName);
    // So far, so good, but we aren't presented with any grant givers
    throw Error("Not remotely done");
});