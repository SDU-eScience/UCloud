package dk.sdu.cloud.integration

import dk.sdu.cloud.integration.e2e.awaitElement
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver

suspend fun WebDriver.findAppCard(name: String) {
    awaitElement(By.xpath("//div[text()='$name']"))
}

suspend fun WebDriver.clickAppCard(name: String) {
    awaitElement(By.xpath("//a/div/div/div/div[text()='$name']")).click()
}