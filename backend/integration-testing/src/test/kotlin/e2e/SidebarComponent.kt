package dk.sdu.cloud.integration.e2e

import org.openqa.selenium.By
import org.openqa.selenium.WebDriver

suspend fun WebDriver.goToFiles() {
    findElements(By.tagName("div")).find {
        val svg = it.findElementOrNull(By.tagName("svg"))
        val div = it.findElementOrNull(By.tagName("div"))
        div?.text == "Files" && svg != null
    }?.click()
    await { println(currentUrl) ; currentUrl.contains("files") }
}