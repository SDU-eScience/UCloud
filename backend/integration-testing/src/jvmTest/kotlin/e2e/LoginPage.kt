package dk.sdu.cloud.integration.e2e

import org.openqa.selenium.By
import org.openqa.selenium.WebDriver

suspend fun WebDriver.login(username: String, password: String) {
    findElement(By.xpath("/html/body/div[1]/div[4]/div/div/div/div/span/div")).click()
    val form = awaitElement(By.tagName("form"))
    form.findElement(By.id("username")).sendKeys(username)
    form.findElement(By.id("password")).sendKeys(password)
    form.findElement(By.tagName("button")).click()
    await { !currentUrl.contains("login") }
}