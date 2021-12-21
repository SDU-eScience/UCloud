package dk.sdu.cloud.integration.e2e

import dk.sdu.cloud.integration.retrySection
import org.openqa.selenium.By
import org.openqa.selenium.SearchContext
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement

suspend fun E2EIntegrationContext<*>.goToDashboard() {
    (driver.findComponentOrNull("logo") ?: error("No logo found")).click()
}

suspend fun E2EIntegrationContext<*>.switchToProjectByTitle(projectTitle: String) {
    val projectSwitcher = driver.findComponentOrNull("project-switcher")
        ?: error("No project switcher found")

    projectSwitcher.click()
    retrySection {
        val projectElement = projectSwitcher.findElements(By.tagName("div")).find { it.text == projectTitle }
            ?: error("Project not found: $projectTitle")

        projectElement.click()
    }
}

fun E2EIntegrationContext<*>.findSearchInput(): WebElement {
    return driver.findElementOrNull(By.cssSelector("#search_input")) ?: error("No search input found")
}

suspend fun E2EIntegrationContext<*>.isSearchEnabled(): Boolean = findSearchInput().isEnabled
suspend fun E2EIntegrationContext<*>.search(query: String) {
    val startAddress = driver.currentUrl
    findSearchInput().apply {
        clear()
        sendKeys(query + "\n")
    }
    await { driver.currentUrl != startAddress }
}

fun SearchContext.findComponentOrNull(component: String): WebElement? {
    return findElementOrNull(By.cssSelector("[data-component=$component]"))
}

fun SearchContext.findComponents(component: String): List<WebElement> {
    return findElements(By.cssSelector("[data-component=$component]"))
}

fun E2EIntegrationContext<*>.findReloadButton(): WebElement? = driver.findComponentOrNull("refresh")

suspend fun E2EIntegrationContext<*>.isLoading(): Boolean {
    val button = findReloadButton() ?: return false
    return button.getAttribute("data-loading") == "true"
}

suspend fun E2EIntegrationContext<*>.reload() {
    (findReloadButton() ?: error("Reload button is not visible")).click()
}

suspend fun E2EIntegrationContext<*>.notificationCount(): Int {
    return (driver.findComponentOrNull("notifications-unread") ?: return 0).text.toIntOrNull() ?: 0
}

suspend fun E2EIntegrationContext<*>.openNotifications() {
    (driver.findComponentOrNull("notifications") ?: error("Could not locate notification button")).click()
}
