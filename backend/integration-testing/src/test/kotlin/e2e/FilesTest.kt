package dk.sdu.cloud.integration.e2e

import dk.sdu.cloud.file.api.FileDescriptions.transferQuota
import dk.sdu.cloud.integration.*
import dk.sdu.cloud.integration.backend.*
import org.junit.Test
import org.openqa.selenium.By

class FilesTest : EndToEndTest() {

    @Test
    fun `Create folder`() = e2e {
        val user = createUser()
        driver.get("$address/app")
        driver.login(user.username, user.password)
        driver.goToFiles()
        driver.createFolder("foo")
    }

    @Test
    fun `Delete folder`() = e2e {
        val user = createUser()
        val folderName = "bar"
        createDir("/home/${user.username}/$folderName", user)
        driver.get("$address/app")
        driver.login(user.username, user.password)
        driver.goToFiles()
        driver.deleteFolder(folderName)
    }

    @Test
    fun `Rename folder`() = e2e {
        val user = createUser()
        val folderName = "baz"
        createDir("/home/${user.username}/$folderName", user)
        driver.get("$address/app")
        driver.login(user.username, user.password)
        driver.goToFiles()
        driver.renameFile(folderName)
    }

    @Test
    fun `Upload file`() = e2e {
        val user = createUser()
        val rootProject = initializeRootProject()
        addFundsToPersonalProject(rootProject, user.username, sampleStorage.category)
        setProjectQuota(rootProject, 1024 * 1024 * 1024 * 1024L)
        setPersonalQuota(rootProject, user.username, 50_000_000)
        driver.get("$address/app")
        driver.login(user.username, user.password)
        driver.goToFiles()
        driver.uploadFile("foo.txt")
    }

    @Test
    fun `Folder navigation`() = e2e {
        val user = createUser()
        val folder1 = "foo"
        val folder2 = "bar"
        val folder3 = "baz"
        createDir("/home/${user.username}/$folder1", user)
        createDir("/home/${user.username}/$folder1/$folder2", user)
        createDir("/home/${user.username}/$folder1/$folder2/$folder3", user)
        driver.get("$address/app")
        driver.login(user.username, user.password)
        driver.goToFiles()
        driver.navigateThroughFolders(listOf(folder1, folder2, folder3))
    }
}