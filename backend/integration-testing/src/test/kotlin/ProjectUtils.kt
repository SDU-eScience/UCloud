package dk.sdu.cloud.integration

import dk.sdu.cloud.integration.e2e.await
import dk.sdu.cloud.integration.e2e.awaitElement
import dk.sdu.cloud.integration.e2e.clickSidebarOption
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver

suspend fun WebDriver.goToProjects() {
    if (findElement(By.xpath("//div[text()='Personal Project']")) != null) {
        awaitElement(By.xpath("//div[text()='Personal Project']")).click()
        awaitElement(By.xpath("//div[text()='Manage projects']")).click()
    } else {
        clickSidebarOption("Projects", "app/projects")
    }
    await { currentUrl.endsWith("/app/projects")}
}