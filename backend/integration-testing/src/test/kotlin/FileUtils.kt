package dk.sdu.cloud.integration

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.e2e.await
import dk.sdu.cloud.integration.e2e.awaitElement
import dk.sdu.cloud.integration.e2e.awaitElements
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver

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
    awaitElements(By.tagName("button")).find {
        it.findElement(By.tagName("span")).text == "New Folder"
    }?.click()

    val inputs = awaitElements(By.tagName("input"))

    inputs.filter { it.isEnabled && it.isDisplayed }.forEach {
        it.sendKeys(folderName)
    }

    awaitElements(By.tagName("button")).find {
        it.text == "Create"
    }?.click()

    await {
        findElements(By.tagName("div")).any {
            it.text == folderName
        }
    }
}

suspend fun WebDriver.deleteFolder(folderName: String) {
    // awaitElements()
}

suspend fun createDir(directoryPath: String) {
    FileDescriptions.createDirectory.call(CreateDirectoryRequest(directoryPath), serviceClient)
}