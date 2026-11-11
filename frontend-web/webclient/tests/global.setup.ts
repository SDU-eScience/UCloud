import {chromium} from "@playwright/test";
import {default as data} from "./test_data.json" with {type: "json"};

export default async function globalSetup(): Promise<void> {
    const browser = await chromium.launch();
    const page = await browser.newPage({ignoreHTTPSErrors: true});
    const deadline = Date.now() + 360_000;

    page.on("pageerror", error => console.warn("Frontend page error:", error.message));
    page.on("console", message => {
        if (message.type() === "error") console.warn("Frontend console error:", message.text());
    });

    while (true) {
        try {
            const response = await page.goto(data.location_origin, {waitUntil: "domcontentloaded", timeout: 15_000});
            if (!response) throw new Error("frontend did not respond");
            if (!response.ok()) throw new Error(`frontend returned ${response.status()}`);
            await page.getByRole("textbox", {name: "Username"})
                .or(page.getByText("Other login options"))
                .first()
                .waitFor({timeout: 15_000});
            break;
        } catch (e) {
            console.warn(`Frontend not ready at ${page.url()}: ${e}`);
            if (Date.now() >= deadline) throw e;
            await page.waitForTimeout(1_000);
        }
    }

    await browser.close();
}
