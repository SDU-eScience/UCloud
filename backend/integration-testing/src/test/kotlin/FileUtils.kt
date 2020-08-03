package dk.sdu.cloud.integration

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.integration.e2e.await
import dk.sdu.cloud.integration.e2e.awaitElement
import dk.sdu.cloud.integration.e2e.awaitElements
import dk.sdu.cloud.integration.e2e.awaitNoElements
import dk.sdu.cloud.service.test.retrySection
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import java.lang.Error

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
    findElement(By.xpath("//button/span[text()='New Folder']")).click()
    awaitElement(By.xpath("//input[@id='default']")).sendKeys(folderName)
    awaitElement(By.xpath("//button[text()='Create']")).click()
    awaitElement(By.xpath("//div[text()='$folderName']")) ?: throw IllegalStateException("Element not found")
}

suspend fun WebDriver.deleteFolder(folderName: String) {
    // awaitElements()
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

suspend fun createDir(directoryPath: String, client: AuthenticatedClient) {
    val foo = FileDescriptions.createDirectory.call(CreateDirectoryRequest(directoryPath), client)
    println(foo.statusCode)
}