import {expect, test, type Page} from '@playwright/test';
import {login} from './shared';

test.beforeEach(async ({page}) => {
    login(page);
});

const Applications = {


    async gotoApplications(page: Page) {
        await page.getByRole('link', {name: 'Go to Applications'}).click();
    }
}

const Runs = {
    newJobName() {
        return "JobName" + Math.random().toString().slice(2, 7);
    },
    async goToRuns(page: Page) {
        await page.getByRole('link', {name: 'Go to Runs'}).click();
    }
}

test.skip("Run job with jobname, extend time, stop job, validate jobname in runs", async ({page}) => {
    await Applications.gotoApplications(page);
    await page.getByRole('button', {name: 'Open application'}).click();
    await page.getByRole('textbox', {name: 'Job name'}).click();
    const jobName = Runs.newJobName();
    await page.getByRole('textbox', {name: 'Job name'}).fill(jobName);
    await page.getByText('No machine type selected').click();
    await page.getByRole('cell', {name: 'standard-cpu-1', exact: true}).click();
    await page.getByRole('button', {name: '+1'}).click();
    await page.getByRole('button', {name: 'Submit'}).click();
    /* TODO(Jonas): find better thing to wait for */
    await page.waitForTimeout(20_000);
    await expect(page.getByText('Time remaining: 01')).toHaveCount(1);
    await page.getByRole('button', {name: '+1'}).click();
    await page.waitForTimeout(3_000);
    await expect(page.getByText('Time remaining: 02')).toHaveCount(1);
    await page.getByRole('button', {name: 'Stop application'}).click({delay: 1000 + 200});
});