package dk.sdu.cloud.integration.e2e

import dk.sdu.cloud.service.test.retrySection
import org.openqa.selenium.By
import org.openqa.selenium.Keys
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.interactions.Actions
import java.io.File
import java.nio.file.Files
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

const val HOST = "http://localhost:9000"
lateinit var driver: WebDriver

class EndToEndTest {
    @Test
    fun runTest() {
        val (username, password) = File("/tmp/cloud-test.txt").readLines()
        driver = FirefoxDriver()
        with(driver) {
            get(HOST)
            await { driver.currentUrl.contains("login") }

            login(username, password)
            goToFiles()

            val folderName = UUID.randomUUID().toString()
            createDirectory(folderName)
            openFolder(folderName)
            uploadFile()
            uploadArchive()
        }
    }

    fun WebDriver.login(username: String, password: String) {
        findElement(By.id("username")).sendKeys(username)
        findElement(By.id("password")).sendKeys(password)
        findElement(By.tagName("button")).click()
        await { !currentUrl.contains("login") }
    }

    fun WebDriver.goToFiles() {
        get("$HOST/app/files")
        val selector = byTag("fileRow")
        println(selector)
        awaitElements(selector)
    }

    fun WebDriver.createDirectory(name: String) {
        findElement(byTag("newFolder")).click()
        val inputField = awaitElement(byTag("renameField"))
        inputField.sendKeys(name + Keys.ENTER)
    }

    fun WebDriver.openFolder(name: String) {
        retrySection {
            val rows = findElements(byTag("fileRow"))
            val row = rows.find { it.findElementOrNull(byTag("fileName"))?.text == name }!!
            val findElement = row.findElement(By.tagName("a")).findElement(By.tagName("div"))
            // Correct element will for some reason not be clicked
            findElement.click()
        }
    }

    fun WebDriver.uploadFile() {
        with(UploadDialog) {
            open()
            val flag = Files.createTempFile("file", ".txt").toFile().also { it.writeText("Hi!") }
            selectFile(flag.absolutePath)

            val uploadRow = UploadDialog.rows.single()
            uploadRow.start()
            uploadRow.awaitComplete()
            uploadRow.remove()
            assertEquals(0, UploadDialog.rows.size)

            close()
        }
    }

    fun WebDriver.uploadArchive() {
        val file = Files.createTempFile("archive", ".tar.gz").toFile()
        file.outputStream().use { outs ->
            javaClass.classLoader.getResourceAsStream("complex.tar.gz").use { it.copyTo(outs) }
        }

        with(UploadDialog) {
            open()
            selectFile(file.absolutePath)

            val uploadRow = rows.single()
            uploadRow.toggleExtractArchive()
            uploadRow.start()
            uploadRow.awaitComplete()
            uploadRow.remove()
            close()
        }
    }

}

fun byTag(tag: String): By = By.cssSelector("[data-tag=\"$tag\"]")

object UploadDialog {
    val modal: WebElement?
        get() = driver.findElementOrNull(byTag("uploadModal"))

    val rows: List<UploadRow>
        get() = driver.findElements(byTag("uploadRow")).map { UploadRow(it) }

    val isOpen: Boolean
        get() = modal?.isDisplayed == true

    fun open(): Unit = with(driver) {
        findElement(byTag("uploadButton")).click()
        await { isOpen }
    }

    fun selectFile(path: String): Unit = with(driver) {
        val modal = modal!!
        val input = modal.findElement(By.tagName("input"))
        val countBefore = rows.size

        input.sendKeys(path)
        await { rows.size > countBefore }
    }

    fun close() {
        driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE)
        await { !isOpen }
    }
}

class UploadRow(val element: WebElement) {
    val startButton: WebElement?
        get() = element.findElementOrNull(byTag("startUpload"))

    val removeButton: WebElement?
        get() = element.findElementOrNull(byTag("removeUpload"))

    val cancelButton: WebElement?
        get() = element.findElementOrNull(byTag("cancelButton"))

    fun start() {
        startButton!!.click()
    }

    fun awaitComplete() {
        await { startButton == null && cancelButton == null && removeButton != null }
    }

    fun remove() {
        removeButton!!.click()
    }

    fun toggleExtractArchive() {
        element.findElement(byTag("extractArchive")).findElement(By.tagName("span")).click()
    }
}

