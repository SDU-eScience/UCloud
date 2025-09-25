import {expect, test} from '@playwright/test';
import {Applications, Components, login, Runs} from './shared';

test.beforeEach(async ({page}) => {
    login(page);
});

test("Run job with jobname, extend time, stop job, validate jobname in runs", async ({page}) => {
    test.setTimeout(240_000);
    await Applications.gotoApplications(page);
    await page.getByRole('button', {name: 'Open application'}).click();
    await page.getByRole('textbox', {name: 'Job name'}).click();
    const jobName = Runs.newJobName();
    await page.getByRole('textbox', {name: 'Job name'}).fill(jobName);
    await Components.selectAvailableMachineType(page);
    await page.getByRole('button', {name: '+1'}).click();
    await page.getByRole('button', {name: 'Submit'}).click();

    while (!await page.getByText("Time remaining: 01").isVisible()) {
        await page.waitForTimeout(1000);
    }

    await page.getByRole('button', {name: '+1'}).click();

    while (!await page.getByText("Time remaining: 02").isVisible()) {
        await page.waitForTimeout(1000);
    }

    await Components.clickConfirmationButton(page, "Stop application");

    // Note(Jonas): I would have thought that the `expect` below would be enough, but 
    while (!await page.getByText("Run application again").isVisible()) {
        await page.waitForTimeout(1000);
    }

    await expect(page.getByText("Run application again")).toHaveCount(1);
});

test("Favorite app, unfavorite app", async ({page}) => {
    // Note(Jonas): Stuff like finding the favorite icon is pretty difficult through selectors,
    // so maybe consider adding test ids? I don't know what makes the most sense.
    throw Error("Not implemented");
});
