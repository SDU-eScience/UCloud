package dk.sdu.cloud.integration.e2e

import dk.sdu.cloud.integration.createUser
import kotlinx.coroutines.delay
import org.junit.Test
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

class ExampleE2ETest : EndToEndTest() {
    // Note: This requires an already running webpack dev server

    @Test
    fun `testing`() = e2e {
        val user = createUser()
        driver.get("$address/app")
        driver.login(user.username, user.password)
        driver.goToFiles()
    }
}
