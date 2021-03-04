package dk.sdu.cloud.integration.e2e

import org.openqa.selenium.By
import org.openqa.selenium.WebDriver

suspend fun WebDriver.goToFiles() {
    clickSidebarOption("Files", "files")
}

suspend fun WebDriver.goToApps() {
    clickSidebarOption("Apps", "applications/overview")
}

suspend fun WebDriver.clickSidebarOption(divText: String, expectedUrl: String) {
    findElements(By.tagName("div")).find {
        val svg = it.findElementOrNull(By.tagName("svg"))
        val div = it.findElementOrNull(By.tagName("div"))
        div?.text == divText && svg != null
    }?.click()
    await { println(currentUrl) ; currentUrl.contains(expectedUrl) }
}