package dk.sdu.cloud.integration.e2e

import org.openqa.selenium.By
import org.openqa.selenium.WebDriver

class JobPropertiesComponent(val driver: WebDriver) {
    fun isComplete(): Boolean {
        return driver.findElements(By.tagName("h2")).any {
            it.text.contains("job has completed")
        }
    }
}