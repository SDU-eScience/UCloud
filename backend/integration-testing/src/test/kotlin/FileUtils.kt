package dk.sdu.cloud.integration

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.integration.backend.CreatedUser

import dk.sdu.cloud.integration.e2e.await
import dk.sdu.cloud.integration.e2e.awaitElement
import dk.sdu.cloud.integration.e2e.awaitElements
import dk.sdu.cloud.integration.e2e.awaitNoElements
import dk.sdu.cloud.service.test.retrySection
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import java.io.File

fun uploadFile(homeFolder: String, fileName: String) {
    //MultiPartUploadDescriptions.simpleUpload.call(
    //    SimpleUploadRequest(
    //        joinPath(homeFolder, testId, FileTesting.Companion.SmallFileUpload.DIR, FileTesting.Companion.SmallFileUpload.NAME),
    //        BinaryStream.outgoingFromChannel(fileToUpload.readChannel(), fileToUpload.length())
    //    ),
    //    client
    //).orThrow()
}

suspend fun WebDriver.createFolder(folderName: String) {
    retrySection {
        awaitElement(By.xpath("//div[@data-tag='sidebar']/div/div/button[@data-tag='New Folder-action']/span")).click()
    }
    awaitElement(By.xpath("//input[@id='default']")).sendKeys(folderName)
    awaitElement(By.xpath("//button[text()='Create']")).click()
    awaitElement(By.xpath("//div[text()='$folderName']"))
}

suspend fun WebDriver.openFileDropdown(folderName: String) {
    awaitElement(By.xpath("//*[contains(@data-tag, '$folderName-dropdown')]/../..")).click()
}

suspend fun WebDriver.deleteFolder(folderName: String) {
    openFileDropdown(folderName)
    awaitElement(By.xpath("//div[@data-tag='Move to Trash-action']")).click()
    awaitElement(By.xpath("//button[text()='Move files']")).click()
    awaitNoElements(By.xpath("//div[text()='$folderName']"))
}

suspend fun WebDriver.renameFile(fileName: String) {
    openFileDropdown(fileName)
    awaitElement(By.xpath("//div[@data-tag='Rename-action']")).click()
    val element = switchTo().activeElement()
    element.clear()
    element.sendKeys("$fileName-2")
    awaitElement(By.xpath("//button[text()='Create']")).click()
    awaitNoElements(By.xpath("//input[text()='$fileName-2']"))
    awaitElements(By.xpath("//div[text()='$fileName-2']"))
}

suspend fun WebDriver.navigateThroughFolders(folders: List<String>) {
    folders.forEach { path ->
        navigateToFolder(path)
    }
}

suspend fun WebDriver.navigateToFolder(folderName: String) {
    awaitElement(By.xpath("//div[text()='$folderName']")).click()
}

suspend fun WebDriver.uploadFile(fileName: String) {
    retrySection {
        // This is for some reason prone to being stale.
        awaitElement(By.xpath("//div[@data-tag='sidebar']/div/div/button[@data-tag='Upload Files-action']/span")).click()
    }
    val file = File(fileName)
    file.writeText("FooBarBaz")
    // TODO: find better selector criteria
    awaitElement(By.xpath("/html/body/div[4]/div/div/div/div[2]/div/input")).sendKeys(file.absolutePath)
    println(awaitElement(By.xpath("//button[text()='Upload']")).size)
    awaitElement(By.xpath("//button[text()='Upload']")).click()
    awaitElement(By.xpath("//button[text()='Clear finished uploads']"))
    awaitElement(By.xpath("//*[@data-tag='modalCloseButton']")).click()
    awaitNoElements(By.xpath("//button[text()='Clear finished uploads']"))
    awaitElement(By.xpath("//div[text()='$fileName']"))
}

suspend fun WebDriver.selectFolder(folderName: String) {
    await {
        findElements(By.tagName("a")).find {
            it.text == folderName
        } != null
    }

    findElements(By.tagName("a")).find {
        it.text == folderName && it.isDisplayed
    }?.click()
}

suspend fun createDir(directoryPath: String, user: CreatedUser) {
    val foo = FileDescriptions.createDirectory.call(CreateDirectoryRequest(directoryPath, user.username), user.client)
    println("-----------------------------------")
    println(foo.statusCode)
    println("-----------------------------------")
}