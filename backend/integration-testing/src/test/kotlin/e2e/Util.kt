package dk.sdu.cloud.integration.e2e

import dk.sdu.cloud.integration.retrySection
import org.openqa.selenium.By
import org.openqa.selenium.SearchContext
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.interactions.Actions

suspend fun await(attempts: Long = 100, delayMs: Long = 100, condition: () -> Boolean) {
    for (i in 1..attempts) {
        if (condition()) return
        Thread.sleep(delayMs)
    }

    throw AssertionError("await() failed")
}

suspend fun SearchContext.awaitElements(selector: By, attempts: Long = 100, delay: Long = 100): List<WebElement> {
    var elements: List<WebElement> = emptyList()
    await(attempts = attempts, delayMs = delay) {
        elements = findElements(selector)
        elements.isNotEmpty()
    }
    return elements
}

suspend fun SearchContext.awaitElement(selector: By, attempts: Long = 100, delay: Long = 100): WebElement {
    return awaitElements(selector, attempts, delay).single()
}

suspend fun SearchContext.awaitNoElements(selector: By, attempts: Long = 100, delay: Long = 100) {
    await(attempts = attempts, delayMs = delay) {
        findElements(selector).isEmpty()
    }
}

fun SearchContext.findElementOrNull(selector: By): WebElement? = findElements(selector).firstOrNull()

fun SearchContext.parent(): WebElement = findElement(By.xpath("./.."))

fun SearchContext.clickUniqueButton(text: String) {
    retrySection {
        val button = findElements(By.cssSelector("button")).find { it.text == text }
            ?: error("Could not find '$text'")
        button.click()
    }
}

val WebDriver.mainContainer: WebElement get() =
    findComponentOrNull("main") ?: error("No main container")

fun WebDriver.clickSidebarOperation(text: String, hold: Boolean = false) {
    val sidebar = findComponentOrNull("sidebar") ?: error("Could not find sidebar")
    val button = sidebar.findElements(By.tagName("button")).find { it.text.trim() == text.trim() }
    fun interact(element: WebElement) {
        if (hold) {
            Actions(this).clickAndHold(element).pause(3000).release().perform()
        } else {
            element.click()
        }
    }

    if (button != null) {
        interact(button)
        return
    }

    val div = sidebar.findElements(By.tagName("div")).find { it.text.trim() == text.trim() }
    if (div != null) {
        interact(div)
        return
    }

    error("Could not find sidebar operation: '$text'")
}
