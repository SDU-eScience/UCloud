package dk.sdu.cloud.integration.e2e

import org.openqa.selenium.By
import org.openqa.selenium.WebDriver

enum class SidebarOption(val divText: String, val expectedUrl: String) {
    Files("Files", "/drives"),
    Shares("Shares", "/shares"),
    Projects("Projects", "/projects"),
    Resources("Resources", "/public-ips"),
    Apps("Apps", "/applications/overview"),
    Runs("Runs", "/jobs"),
    Admin("Admin", "/admin")
}

suspend fun E2EIntegrationContext<*>.clickSidebarOption(option: SidebarOption) {
    driver.findElements(By.tagName("div")).find {
        val svg = it.findElementOrNull(By.tagName("svg"))
        val div = it.findElementOrNull(By.tagName("div"))
        div?.text == option.divText && svg != null
    }?.click()
    await { driver.currentUrl.contains(option.expectedUrl) }
}
