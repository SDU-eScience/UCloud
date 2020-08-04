package dk.sdu.cloud.integration.e2e

import dk.sdu.cloud.app.license.services.LicenseServerTable.address
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.integration.backend.createUser
import dk.sdu.cloud.integration.createDir
import dk.sdu.cloud.integration.createFolder
import dk.sdu.cloud.integration.deleteFolder
import dk.sdu.cloud.integration.selectFolder
import org.junit.Test
import org.openqa.selenium.By

class FilesTest : EndToEndTest() {

    @Test
    fun `Create folder`() = e2e {
        val user = createUser()
        driver.get("$address/app")
        driver.login(user.username, user.password)
        driver.goToFiles()
        driver.createFolder("foobar")
    }

    @Test
    fun `Delete folder`() = e2e {
        val user = createUser()
        val folderName = "foobar"
        createDir("/home/${user.username}/$folderName", user.client)
        driver.get("$address/app")
        driver.login(user.username, user.password)
        driver.goToFiles()

        val element = driver.awaitElement(By.xpath("//*[contains(@data-tag,'$folderName-dropdown')]"))
        println(element.tagName)
        element.click()
        // driver.deleteFolder(folderName)
    }
}