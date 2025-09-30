import test, {expect} from "@playwright/test";
import {User} from "./shared";

test("Login using password, logout", async ({page}) => {
    await User.login(page);
    await User.logout(page);
    await expect(page.getByText("Other login options")).toHaveCount(1);
});