package dk.sdu.cloud.integration.e2e

import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement

class AppStoreComponent(private val driver: WebDriver) {
    fun cards(): List<ApplicationCard> {
        return driver.findComponents("app-card").map { ApplicationCard(driver, it) }
    }
}

class ApplicationCard(private val driver: WebDriver, val element: WebElement) {
    fun navigate() {
        element.click()
    }

    fun title(): String {
        return element.findComponentOrNull("app-title")?.text ?: error("Could not find title")
    }

    fun version(): String {
        return element.findComponentOrNull("app-version")?.text?.removePrefix("v") ?: error("Could not find version")
    }
}