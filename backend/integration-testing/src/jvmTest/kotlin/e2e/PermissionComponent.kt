package dk.sdu.cloud.integration.e2e

import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement

class PermissionComponent(val driver: WebDriver) {
    fun close() {
        driver.findElementOrNull(By.tagName("body"))!!.click()
    }

    fun findRows(): List<PermissionRow> {
        return driver.findComponents("permission-row").map { PermissionRow(driver, it) }
    }
}

class PermissionRow(val driver: WebDriver, val element: WebElement) {
    private fun container(): WebElement {
        return element.findComponentOrNull("permission-container") ?: error("Could not find container for permissions")
    }

    fun groupTitle(): String {
        return element.getAttribute("data-group") ?: error("No data-group attribute")
    }

    fun groupId(): String {
        return element.getAttribute("data-group-id") ?: error("No data-group-id attribute")
    }

    fun selected(): String? {
        return container().findElements(By.tagName("input[type=radio]")).find { it.getAttribute("checked") != null }
            ?.getAttribute("value")
    }

    fun select(value: String) {
        println(container().findElements(By.cssSelector("input[type=radio]")).map { it.getAttribute("value") })
        return container().findElements(By.cssSelector("input[type=radio]")).find { it.getAttribute("value") == value }
            ?.click() ?: error("No such value: '$value'")
    }
}
