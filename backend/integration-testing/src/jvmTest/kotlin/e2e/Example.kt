package dk.sdu.cloud.integration.e2e

import dk.sdu.cloud.integration.backend.createUser
import org.junit.Ignore
import org.junit.Test

class ExampleE2ETest : EndToEndTest() {
    // Note: This requires an already running webpack dev server

    @Test
    fun `testing`() = e2e {
        val user = createUser()
        driver.get("$address/app")
        driver.login(user.username, user.password)
        driver.goToFiles()
    }

    @Test
    fun `testing 2`() = e2e {
        val user = createUser()
        driver.get("$address/app")
        driver.login(user.username, user.password)
        driver.goToFiles()
    }
}
