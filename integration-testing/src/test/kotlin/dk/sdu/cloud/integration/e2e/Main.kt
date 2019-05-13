package dk.sdu.cloud.integration.e2e

import dk.sdu.cloud.service.test.retrySection
import org.openqa.selenium.By
import org.openqa.selenium.Keys
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.firefox.FirefoxDriver
import java.io.File
import java.nio.file.Files
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

const val HOST = "https://cloud.sdu.dk"
lateinit var driver: WebDriver

class EndToEndTest {
    @Test
    fun runTest() {
        val (username, password) = File("${System.getProperty("user.home")}/cloud-test.txt").readLines()
        driver = FirefoxDriver()
        with(driver) {
            try {
                get("$HOST/app/dashboard")
                await { driver.currentUrl.contains("login") }

                login(username, password)
                goToFiles()

                val folderName = UUID.randomUUID().toString()
                createDirectory(folderName)
                openFolder(folderName)

                uploadFile()
                uploadArchive()
                createAndThrowAway()
                testSensitivity()
                createCopyInOwnDirectory()
                renameFiles()
                favoriteFiles()
            } finally {
                close()
            }
        }
    }

    private fun WebDriver.login(username: String, password: String) {
        findElement(By.id("username")).sendKeys(username)
        findElement(By.id("password")).sendKeys(password)
        findElement(By.tagName("button")).click()
        await { !currentUrl.contains("login") }
    }

    private fun WebDriver.goToFiles() {
        get("$HOST/app/files")
        val selector = byTag("fileRow")
        println(selector)
        awaitElements(selector)
    }

    private fun WebDriver.createDirectory(name: String) {
        findElement(byTag("newFolder")).click()
        val inputField = awaitElement(byTag("renameField"))
        inputField.sendKeys(name + Keys.ENTER)
    }

    private fun WebDriver.openFolder(name: String) {
        retrySection {
            val rows = findElements(byTag("fileRow"))
            val row = FileRow(rows.find { it.findElementOrNull(byTag("fileName"))?.text == name }!!)
            val findElement = row.path?.findElement(By.tagName("div"))
            // Correct element will for some reason not be clicked
            findElement?.click()
        }
    }

    private fun WebDriver.uploadFile() {
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

    private fun WebDriver.uploadArchive() {
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

    private fun WebDriver.createAndThrowAway() {
        val folderName2 = "Other"
        createDirectory(folderName2)
        retrySection {
            FileTable.byName(folderName2)!!.clickDropdownOption(DropdownOption.MOVE_TO_TRASH)
        }

        retrySection {
            SwalDialog.confirm()
        }

        retrySection {
            assertNull(FileTable.byName(folderName2))
        }
    }

    private fun WebDriver.testSensitivity() {
        val sensitiveFile = "Sensitive"
        createDirectory(sensitiveFile)
        lateinit var fileRow: FileRow
        retrySection {
            fileRow = FileTable.byName(sensitiveFile)!!
            fileRow.clickDropdownOption(DropdownOption.SENSITIVITY)
        }

        retrySection {
            SwalDialog.selectOption("SENSITIVE")
            SwalDialog.confirm()
        }

        retrySection {
            assertEquals("S", fileRow.sensitivityBadge.text.trim())
        }
    }

    private fun WebDriver.createCopyInOwnDirectory() {
        val copy = "Copy"
        createDirectory(copy)

        lateinit var fileRow: FileRow
        retrySection {
            fileRow = FileTable.byName(copy)!!
            fileRow.clickDropdownOption(DropdownOption.COPY)
        }

        retrySection {
            val thisFolder = FileTable.rows.find { it.fileName.text.contains("Current folder", ignoreCase = true) }!!
            thisFolder.actionButton!!.click()
        }

        retrySection {
            SwalDialog.selectOption("RENAME")
            SwalDialog.confirm()
        }

        retrySection {
            FileTable.byName("$copy(1)")!!
        }
    }

    private fun WebDriver.renameFiles() {
        val before = "Before"
        val after = "After"
        createDirectory(before)

        lateinit var fileRow: FileRow
        retrySection {
            fileRow = FileTable.byName(before)!!
            fileRow.clickDropdownOption(DropdownOption.RENAME)
        }

        retrySection {
            fileRow.renameField!!.run {
                clear()
                sendKeys(after + Keys.ENTER)
            }
        }

        retrySection {
            FileTable.byName(after)!!
            assertNull(FileTable.byName(before))
        }
    }

    private fun WebDriver.favoriteFiles() {
        val file = "Favorite"
        createDirectory(file)

        lateinit var fileRow: FileRow
        retrySection {
            fileRow = FileTable.byName(file)!!
        }

        retrySection {
            fileRow.favorite!!.click()
        }
    }
}

fun byTag(tag: String): By = By.cssSelector("[data-tag=\"$tag\"]")

object SwalDialog {
    val isOpen: Boolean
        get() = driver.findElementOrNull(By.cssSelector(".swal2-actions"))?.isDisplayed == true

    fun confirm() {
        driver.findElement(By.cssSelector(".swal2-confirm")).click()
    }

    fun canel() {
        driver.findElement(By.cssSelector(".swal2-cancel")).click()
    }

    fun selectOption(value: String) {
        val select = driver.findElementOrNull(By.cssSelector(".swal2-select"))?.takeIf { it.isDisplayed }
        if (select != null) {
            select.sendKeys(value)
        } else {
            driver
                .findElement(By.cssSelector(".swal2-content"))
                .findElement(By.tagName("select"))
                .sendKeys(value)
        }
    }
}

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
        driver.get(driver.currentUrl)
        await { !isOpen }
    }
}

object FileTable {
    val rows: List<FileRow>
        get() = driver.findElements(byTag("fileRow")).map { FileRow(it) }

    fun byName(name: String): FileRow? {
        return rows.find { it.fileName?.text == name }
    }
}

class FileRow(private val element: WebElement) {
    val checkBox: WebElement?
        get() = element.findElementOrNull(By.tagName("label"))

    val path: WebElement
        get() = element.findElement(By.tagName("a"))

    val leftSortingRow: WebElement?
        get() = element.findElementOrNull(byTag("sortingLeft"))

    val rightSortingRow: WebElement?
        get() = element.findElementOrNull(byTag("sortingRight"))

    private val dropdown: WebElement?
        get() = element.findElementOrNull(byTag("dropdown"))

    val favorite: WebElement?
        get() = element.findElementOrNull(byTag(("fileFavorite")))

    val fileName: WebElement
        get() = element.findElement(byTag("fileName"))

    val sensitivityBadge: WebElement
        get() = element.findElement(byTag("sensitivityBadge"))

    val actionButton: WebElement?
        get() = element.findElement(By.tagName("button"))

    val renameField: WebElement?
        get() = element.findElementOrNull(byTag("renameField"))

    val isDropdownOpen: Boolean
        get() = dropdown?.findElement(By.tagName("div"))?.isDisplayed ?: false

    private fun openDropdown() {
        if (isDropdownOpen) return

        if (dropdown == null) {
            checkBox!!.click()
        }



        retrySection {
            dropdown!!.click()
        }
    }

    fun clickDropdownOption(option: DropdownOption) {
        openDropdown()
        val dropdownOptions = dropdown!!.findElement(By.tagName("div"))!!.findElements(By.tagName("div"))
        val find = dropdownOptions.find { el ->
            el.findElement(By.tagName("span"))?.text == option.option
        }
        find?.click()
    }
}

enum class DropdownOption(val option: String) {
    RENAME("Rename"),
    SHARE("Share"),
    DOWNLOAD("Download"),
    SENSITIVITY("Sensitivity"),
    COPY("Copy"),
    MOVE("Move"),
    MOVE_TO_TRASH("Move to Trash"),
    PROPERTIES("Properties")
}

class UploadRow(private val element: WebElement) {
    private val startButton: WebElement?
        get() = element.findElementOrNull(byTag("startUpload"))

    private val removeButton: WebElement?
        get() = element.findElementOrNull(byTag("removeUpload"))

    private val cancelButton: WebElement?
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

