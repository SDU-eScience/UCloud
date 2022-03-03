package dk.sdu.cloud.integration.e2e

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.integration.backend.UCloudProvider
import dk.sdu.cloud.integration.backend.initializeResourceTestContext
import dk.sdu.cloud.integration.retrySection
import dk.sdu.cloud.project.api.AddGroupMemberRequest
import dk.sdu.cloud.project.api.ProjectGroups
import dk.sdu.cloud.provider.api.AclEntity
import dk.sdu.cloud.service.test.assertThatInstance
import kotlinx.coroutines.delay
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StorageTest : EndToEndTest() {
    override fun defineTests() {
        UCloudProvider.globalInitialize(UCloudLauncher.micro)
        testFilter = { title, subtitle -> title.contains("Sorting") }

        /*
            Tests:
                - Folder creation
                - Folder navigation
                - Uploading of files
                - Rename a file
                - Copy a file
                - Move to trash
                - Start renaming, but cancel
                - Move a file
                - Navigate using breadcrumbs
                - Change sensitivity
                - View properties
                - Check preview of file correctly matches
                - Empty trash

            Note(Jonas): I may have missed one or two.
         */
        test<Unit, Unit>("Basic File Management") {
            executeE2E(E2EDrivers.CHROME) {
                val temporaryDirectory = Files.createTempDirectory("").toFile()
                val fileContents = "Hello, World!"
                val fileToUpload = File(temporaryDirectory, "file.txt").also { it.writeText(fileContents) }

                UCloudProvider.testInitialize(UCloudLauncher.serviceClient)

                with(initializeResourceTestContext(UCloudProvider.products, emptyList())) {
                    login(piUsername, piPassword)
                    switchToProjectByTitle(projectTitle)
                    clickSidebarOption(SidebarOption.Files)

                    retrySection {
                        ListComponent.inMainContainer(driver).rows().find { it.title().startsWith("Member Files:") }
                            ?.navigate()
                            ?: error("Could not find the member files")
                    }

                    driver.clickUniqueButton("Create folder")
                    retrySection { ListComponent.inMainContainer(driver).sendInput("My folder\n") }

                    retrySection {
                        val urlBefore = driver.currentUrl
                        val c = ListComponent.inMainContainer(driver)
                        val newFolder = c.rows().find { it.title() == "My folder" } ?: error("New folder not found!")
                        newFolder.navigate()
                        await { driver.currentUrl != urlBefore }

                        // NOTE(Dan): The UI doesn't currently like this going too fast. This is technically an issue
                        // but such low priority that we are working around it.
                        delay(500)
                    }

                    driver.clickUniqueButton("Upload files")

                    retrySection {
                        val uploadInput =
                            driver.findElementOrNull(By.id("fileUploadBrowse")) ?: error("Could not find uploader")
                        uploadInput.sendKeys(fileToUpload.absolutePath)
                        delay(500) // Wait for file to upload
                        driver.findElementOrNull(By.cssSelector("body"))!!.click()
                    }

                    retrySection {
                        val c = ListComponent.inMainContainer(driver)
                        val file = c.rows().find { it.title() == fileToUpload.name }
                            ?: error("Could not find freshly uploaded file")

                        file.openOperations(true)
                        file.selectOperation("Rename")
                    }

                    retrySection {
                        ListComponent.inMainContainer(driver).sendInput("MyNewFile.txt\n", clear = true)
                    }

                    retrySection {
                        val c = ListComponent.inMainContainer(driver)
                        val file = c.rows().find { it.title() == "MyNewFile.txt" }
                            ?: error("Could not find freshly renamed file")

                        file.openOperations(false)
                        file.selectOperation("Copy to...")
                    }

                    driver.clickUniqueButton("Use this folder")

                    retrySection {
                        val c = ListComponent.inMainContainer(driver)
                        val file = c.rows().find { it.title() == "MyNewFile(1).txt" }
                            ?: error("Could not find freshly copied file")

                        file.openOperations(false)
                        file.selectOperation("Move to trash", true)
                    }

                    await {
                        val c = ListComponent.inMainContainer(driver)
                        c.rows().find { it.title() == "MyNewFile(1).txt" } == null
                    }

                    driver.clickUniqueButton("Create folder")
                    retrySection {
                        ListComponent.inMainContainer(driver).cancelInput()
                    }

                    driver.clickUniqueButton("Create folder")
                    retrySection {
                        ListComponent.inMainContainer(driver).sendInput("Nested\n")
                    }

                    retrySection {
                        val c = ListComponent.inMainContainer(driver)
                        val file = c.rows().find { it.title() == "MyNewFile.txt" }
                            ?: error("Could not find freshly renamed file")

                        file.openOperations(true)
                        file.selectOperation("Move to...")
                    }

                    retrySection {
                        val c = ListComponent.inModal(driver)
                        val file = c.rows().find { it.title() == "Nested" }
                            ?: error("Could not find freshly created directory")
                        file.row.clickUniqueButton("Use")
                    }

                    retrySection {
                        val c = ListComponent.inMainContainer(driver)
                        require(c.rows().find { it.title() == "MyNewFile.txt" } == null)
                    }

                    retrySection {
                        val startUrl = driver.currentUrl
                        val c = ListComponent.inMainContainer(driver)
                        val file = c.rows().find { it.title() == "Nested" }
                            ?: error("Could not find freshly created directory")
                        file.navigate()
                        await { driver.currentUrl != startUrl }
                    }

                    retrySection {
                        val c = ListComponent.inMainContainer(driver)
                        require(c.rows().find { it.title() == "MyNewFile.txt" } != null)
                    }

                    retrySection {
                        val startUrl = driver.currentUrl
                        val crumb = driver.findComponents("crumb").find { it.text == "My folder" }
                            ?: error("Could not find breadcrumb")

                        crumb.click()
                        await { driver.currentUrl != startUrl }
                    }

                    retrySection {
                        val file = ListComponent.inMainContainer(driver).rows().find { it.title() == "Nested" }
                            ?: error("Could not find freshly created directory")
                        file.openOperations(true)
                        file.selectOperation("Rename")
                    }

                    retrySection {
                        ListComponent.inMainContainer(driver).sendInput("Nested2\n", clear = true)
                    }

                    retrySection {
                        val startUrl = driver.currentUrl
                        ListComponent.inMainContainer(driver).rows().find { it.title() == "Nested2" }?.navigate()
                            ?: error("Could not find renamed folder")
                        await { driver.currentUrl != startUrl }
                    }

                    retrySection {
                        ListComponent.inMainContainer(driver).rows().find { it.title() == "MyNewFile.txt" }
                            ?: error("Could not find file after directory rename")
                    }

                    retrySection {
                        val startUrl = driver.currentUrl
                        val crumb = driver.findComponents("crumb").find { it.text == "My folder" }
                            ?: error("Could not find breadcrumb")

                        crumb.click()
                        await { driver.currentUrl != startUrl }
                    }

                    retrySection {
                        val folder = ListComponent.inMainContainer(driver).rows().find { it.title() == "Nested2" }
                            ?: error("Could not find renamed folder")
                        folder.openOperations(false)
                        folder.selectOperation("Change sensitivity")
                    }

                    retrySection {
                        val dialog = driver.findElementOrNull(By.cssSelector("#sensitivityDialog"))
                            ?: error("Could not find sensitivity dialog")

                        val valueSelector = dialog.findElementOrNull(By.cssSelector("#sensitivityDialogValue"))
                            ?: error("Could not find value selector")

                        val reason = dialog.findElementOrNull(By.cssSelector("#sensitivityDialogReason"))
                            ?: error("Could not find reason input")

                        val confirmButton = dialog.findElementOrNull(By.cssSelector("button[type=submit]"))
                            ?: error("Could not find confirm button")

                        valueSelector.sendKeys("SENSITIVE")
                        reason.sendKeys("We are changing the sensitivity for testing reasons.")
                        confirmButton.click()
                    }

                    retrySection {
                        val folder = ListComponent.inMainContainer(driver).rows().find { it.title() == "Nested2" }
                            ?: error("Could not find renamed folder")
                        val stats = folder.importantStats() ?: error("Could not find important stats")
                        require(stats.text == "S") { "Sensitivity has not changed ('${stats.text}')" }

                        folder.openOperations(true)
                        folder.selectOperation("Copy to...")
                    }

                    driver.clickUniqueButton("Use this folder")

                    retrySection {
                        val startUrl = driver.currentUrl
                        ListComponent.inMainContainer(driver).rows().find { it.title() == "Nested2(1)" }?.navigate()
                            ?: error("Could not find copied folder")
                        await { driver.currentUrl != startUrl }
                    }

                    retrySection {
                        val startUrl = driver.currentUrl
                        val file = ListComponent.inMainContainer(driver).rows().find { it.title() == "MyNewFile.txt" }
                            ?: error("File not found")
                        file.openOperations(true)
                        file.selectOperation("Properties")
                        await { driver.currentUrl != startUrl }
                    }

                    retrySection {
                        val preview = driver.findElementOrNull(By.cssSelector(".text-preview"))
                            ?: error("Could not find preview")

                        assertEquals(fileContents.trim(), preview.text.trim())
                    }

                    clickSidebarOption(SidebarOption.Files)
                    retrySection {
                        ListComponent.inMainContainer(driver).rows()
                            .find { it.title().startsWith("Member Files:") }
                            ?.navigate() ?: error("Could not find member files")
                    }

                    retrySection {
                        val trash = ListComponent.inMainContainer(driver).rows().find { it.title() == "Trash" }
                            ?: error("Could not find trash")
                        trash.openOperations(true)
                        trash.selectOperation("Empty Trash", hold = true)
                    }
                }
            }

            case("No input") {
                input(Unit)
                check {}
            }
        }

        test<Unit, Unit>("Sorting") {
            // TODO
            //     - Create folders A in drive Foo
            //     - Upload file B in drive Foo
            //     - Set sort by name, set sort order as ascending, ensure A comes first.
            //     - Set sort order as descending, ensure B comes first.
            //     - Set sort by modified at, set sort order as ascending, ensure B comes first
            //     - Set sort order as descending, ensure A comes first
            //     - Set sort by size, set sort order as ascending, ensure A comes first
            //     - Set sort order as descending, ensure B comes first

            executeE2E(E2EDrivers.CHROME) {
                val temporaryDirectory = Files.createTempDirectory("").toFile()
                val fileContents = "Hello, World!"
                val fileToUpload = File(temporaryDirectory, "B").also { it.writeText(fileContents) }
                val driveName = "Some Drive"
                val folder = "A"

                // Note(Jonas): Might not be correct.
                UCloudProvider.testInitialize(UCloudLauncher.serviceClient)

                with(initializeResourceTestContext(UCloudProvider.products, emptyList())) {
                    login(piUsername, piPassword)
                    switchToProjectByTitle(projectTitle)
                    clickSidebarOption(SidebarOption.Files)

                    driver.clickUniqueButton("Create drive")

                    retrySection {
                        ListComponent.inMainContainer(driver).findProductSelector()?.click()
                            ?: error("Could not open product selector")
                    }

                    retrySection {
                        ListComponent.inMainContainer(driver).products().first().select()
                    }

                    retrySection {
                        ListComponent.inMainContainer(driver).sendInput("${driveName}\n")
                    }

                    retrySection {
                        ListComponent.inMainContainer(driver).rows().find { it.title() == driveName }?.navigate()
                            ?: error("Drive named $driveName not found")
                    }

                    await {
                        var result = false
                        driver.findElements(By.tagName("button")).forEach {
                            result = result.or(it.text == "Create folder")
                        }
                        result
                    }

                    if (ListComponent.inMainContainer(driver).rows().find { it.title() == "A" } == null) {
                        retrySection { driver.clickUniqueButton("Create folder") }
                        retrySection { ListComponent.inMainContainer(driver).sendInput("A\n") }
                        retrySection {
                            assertThatInstance(
                                ListComponent.inMainContainer(driver).rows().map { it.title() },
                                "Has our new folder",
                                matcher = { rows -> rows.any { it == folder } }
                            )
                        }
                    }

                    if (ListComponent.inMainContainer(driver).rows().find { it.title() == "B" } == null) {
                        retrySection { driver.clickUniqueButton("Upload files") }

                        retrySection {
                            val uploadInput =
                                driver.findElementOrNull(By.id("fileUploadBrowse")) ?: error("Could not find uploader")
                            uploadInput.sendKeys(fileToUpload.absolutePath)
                            delay(500) // Wait for file to upload
                            driver.findElementOrNull(By.cssSelector("body"))!!.click()
                        }
                    }

                    retrySection {
                        clickSortDirectionOption(driver, "Ascending")
                    }

                    val rowsAscending = ListComponent.inMainContainer(driver).rows()
                    assertEquals("A", rowsAscending.first().title())
                    assertEquals("B", rowsAscending[1].title())

                    retrySection {
                        clickSortDirectionOption(driver, "Descending")
                    }

                    delay(500)
                    val rowsDescending = ListComponent.inMainContainer(driver).rows()
                    assertEquals("B", rowsDescending.first().title())
                    assertEquals("A", rowsDescending[1].title())

                    delay(2000)

                    val options = listOf("Date created", "Created by", "Name")

                    var currentSortBy = "Sort By"
                    var sortDirection = "Ascending"

                    fun assertRows(driver: WebDriver, first: String, second: String) {
                        retrySection {
                            val rows = ListComponent.inMainContainer(driver).rows()
                            assert(rows.first().title() == first)
                            assert(rows[1].title() == second)
                        }
                    }

                    (0..1).forEach { _ ->
                        clickSortDirectionOption(driver, sortDirection)
                        options.forEach { option ->
                            clickSortByOption(driver, currentSortBy, option)
                            currentSortBy = option

                            when (currentSortBy) {
                                "Date created" -> if (sortDirection == "Ascending") {
                                    assertRows(driver, "A", "B")
                                } else {
                                    assertRows(driver, "B", "A")
                                }
                                // Created by are equal
                                "Created by" -> if (sortDirection == "Ascending") {
                                    assertRows(driver, "A", "B")
                                } else {
                                    assertRows(driver, "B", "A")
                                }
                                "Name" -> if (sortDirection == "Ascending") {
                                    assertRows(driver, "A", "B")
                                } else {
                                    assertRows(driver, "B", "A")
                                }
                            }

                        }
                        sortDirection = if (sortDirection == "Ascending") "Descending" else "Ascending"
                    }
                }
            }

            case("No input") {
                input(Unit)
                check {}
            }
        }

        test<Unit, Unit>("Permissions") {
            executeE2E(E2EDrivers.CHROME) {
                UCloudProvider.testInitialize(UCloudLauncher.serviceClient)

                val driveName = "Drive"
                val group1 = "G1"
                val group2 = "G2"
                with(initializeResourceTestContext(UCloudProvider.products, listOf(group1, group2))) {
                    ProjectGroups.addGroupMember.call(
                        AddGroupMemberRequest(
                            groupNamesToId.getValue(group2),
                            memberUsername
                        ),
                        piClient.withProject(project)
                    ).orThrow()

                    run {
                        // Create a drive
                        login(piUsername, piPassword)
                        switchToProjectByTitle(projectTitle)
                        clickSidebarOption(SidebarOption.Files)

                        driver.clickUniqueButton("Create drive")

                        retrySection {
                            ListComponent.inMainContainer(driver).findProductSelector()?.click()
                                ?: error("Could not open product selector")
                        }

                        retrySection {
                            ListComponent.inMainContainer(driver).products().first().select()
                        }

                        retrySection {
                            ListComponent.inMainContainer(driver).sendInput("${driveName}\n")
                        }
                    }

                    run {
                        // Confirm that the drive is not visible as a normal member
                        logout()
                        login(memberUsername, memberPassword)
                        switchToProjectByTitle(projectTitle)
                        clickSidebarOption(SidebarOption.Files)
                        retrySection {
                            assertNull(
                                ListComponent.inMainContainer(driver).rows().find { it.title() == driveName }
                            )
                        }
                    }

                    run {
                        // Update the permissions of the drive
                        logout()
                        login(piUsername, piPassword)
                        switchToProjectByTitle(projectTitle)
                        clickSidebarOption(SidebarOption.Files)
                        retrySection {
                            val row = ListComponent.inMainContainer(driver).rows().find { it.title() == driveName }
                                ?: error("Could not find drive")
                            row.openOperations(true)
                            row.selectOperation("Permissions")
                        }

                        retrySection {
                            val component = PermissionComponent(driver)
                            component.findRows().find { it.groupTitle() == group2 }
                                ?.select("Read")
                                ?: error("Found no entry")

                            component.close()
                        }
                    }

                    run {
                        // Verify that we have the appropriate permissions as the member user
                        logout()
                        login(memberUsername, memberPassword)
                        switchToProjectByTitle(projectTitle)
                        clickSidebarOption(SidebarOption.Files)
                        retrySection {
                            ListComponent.inMainContainer(driver).rows().find { it.title() == driveName }
                                ?.navigate() ?: error("Could not find shared drive!")
                        }

                        retrySection {
                            val button = driver.findElements(By.tagName("button")).find { it.text == "Upload files" }
                                ?: error("Could not find upload files button")

                            assertThatInstance(button, "is disabled") { it.getAttribute("disabled") != null }
                        }

                        retrySection {
                            val button = driver.findElements(By.tagName("button")).find { it.text == "Create folder" }
                                ?: error("Could not find create folder button")

                            assertThatInstance(button, "is disabled") { it.getAttribute("disabled") != null }
                        }
                    }
                }
            }

            case("-") {
                input(Unit)
                check {}
            }
        }
    }
}

fun clickSortByOption(driver: WebDriver, currentSortBy: String, nextSortBy: String) {
    driver.findElements(By.tagName("b")).find {
        it.text == currentSortBy
    }?.click()
    retrySection {
        driver.findElements(By.className("row-left-content")).find {
            it.text == nextSortBy
        }?.click()
    }
}

fun clickSortDirectionOption(driver: WebDriver, sortDirection: String) {
    driver.findElements(By.tagName("b")).find {
        it.text == "Sort Direction"
    }?.click() ?: error("Could not find sort direction")
    retrySection {
        driver.findElements(By.className("row-left-content")).find {
            it.text == sortDirection
        }?.click() ?: error("$sortDirection field not found")
    }
}