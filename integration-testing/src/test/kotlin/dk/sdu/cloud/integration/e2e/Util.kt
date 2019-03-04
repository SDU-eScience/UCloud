package dk.sdu.cloud.integration.e2e

import org.openqa.selenium.By
import org.openqa.selenium.SearchContext
import org.openqa.selenium.WebElement

fun await(attempts: Long = 100, delay: Long = 100, condition: () -> Boolean) {
    for (i in 1..attempts) {
        if (condition()) return
        Thread.sleep(delay)
    }

    throw AssertionError("await() failed")
}

fun SearchContext.awaitElements(selector: By, attempts: Long = 100, delay: Long = 100): List<WebElement> {
    var elements: List<WebElement> = emptyList()
    await(attempts = attempts, delay = delay) {
        elements = findElements(selector)
        elements.isNotEmpty()
    }
    return elements
}

fun SearchContext.awaitElement(selector: By, attempts: Long = 100, delay: Long = 100): WebElement {
    return awaitElements(selector, attempts, delay).single()
}

fun SearchContext.awaitNoElements(selector: By, attempts: Long = 100, delay: Long = 100) {
    await(attempts = attempts, delay = delay) {
        findElements(selector).isEmpty()
    }
}

fun SearchContext.findElementOrNull(selector: By): WebElement? = findElements(selector).firstOrNull()
