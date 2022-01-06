package dk.sdu.cloud.integration.e2e

import dk.sdu.cloud.integration.retrySection
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.html5.WebStorage

suspend fun E2EIntegrationContext<*>.openLoginPage() {
    driver.get("$address/app/login")
    await { driver.currentUrl == "$address/app/login" }
}

suspend fun E2EIntegrationContext<*>.openMoreLoginOptions() {
    val element = driver.findElements(By.tagName("span")).find { it.text.startsWith("More login options") }
        ?: error("More login options not present on current page: ${driver.currentUrl}")

    element.click()
}

suspend fun E2EIntegrationContext<*>.login(username: String, password: String) {
    openLoginPage()
    openMoreLoginOptions()

    val form = driver.awaitElement(By.tagName("form"))
    form.findElement(By.id("username")).sendKeys(username)
    form.findElement(By.id("password")).sendKeys(password)
    form.findElement(By.tagName("button")).click()
    await { !driver.currentUrl.contains("login") }
}

suspend fun E2EIntegrationContext<*>.logout() {
    openAvatarMenu()
    retrySection {
        val startUrl = driver.currentUrl
        driver.findComponentOrNull("logout-button")?.click() ?: error("Could not find logout button")
        await { driver.currentUrl != startUrl }
    }
}
