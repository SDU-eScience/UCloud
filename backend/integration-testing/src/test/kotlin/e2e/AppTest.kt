package dk.sdu.cloud.integration.e2e

import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.integration.backend.ApplicationTestData
import dk.sdu.cloud.integration.backend.UCloudProvider
import dk.sdu.cloud.integration.backend.initializeResourceTestContext
import dk.sdu.cloud.integration.retrySection
import dk.sdu.cloud.service.test.assertThatInstance
import org.openqa.selenium.By
import kotlin.test.assertEquals

suspend fun E2EIntegrationContext<*>.createApplication(
    applicationTitle: String,
    jobName: String? = null,
    hours: Int? = null,
    minutes: Int? = null,
    submit: Boolean = true,
) {
    clickSidebarOption(SidebarOption.Apps)

    retrySection {
        AppStoreComponent(driver).cards()
            .find { it.title() == applicationTitle }
            ?.navigate()
            ?: error("Could not find desired application")
    }

    await { driver.currentUrl.contains("/jobs/create") }

    retrySection {
        val jobCreation = JobCreationComponent(driver)
        if (jobName != null) {
            jobCreation.jobNameInput().apply {
                clear()
                sendKeys(jobName)
            }
        }

        if (hours != null) {
            jobCreation.hoursInput().apply {
                clear()
                sendKeys(hours.toString())
            }
        }

        jobCreation.openMachineTypes()
        jobCreation.machineTypes().first().select()
        val parameters = jobCreation.findParameters()
        for (param in parameters) {
            if (!param.isOpen()) continue
            when (param) {
                is InputParameter.Bool -> param.setValue(true)
                is InputParameter.Text -> param.input().sendKeys("Testing")
                is InputParameter.TextArea -> param.input().sendKeys("Testing")
                else -> TODO()
            }
        }
        if (submit) jobCreation.submit()
    }
}

class AppTest : EndToEndTest() {
    override fun defineTests() {
        UCloudProvider.globalInitialize(UCloudLauncher.micro)

        test<Unit, Unit>("Basic Application Usage") {
            executeE2E(E2EDrivers.CHROME) {
                UCloudProvider.testInitialize(UCloudLauncher.serviceClient)
                ApplicationTestData.create()

                with(initializeResourceTestContext(UCloudProvider.products, emptyList())) {
                    login(piUsername, piPassword)
                    switchToProjectByTitle(projectTitle)

                    val jobName = "My Job"
                    createApplication(
                        ApplicationTestData.figletBatch.metadata.title,
                        jobName,
                        hours = 5,
                        minutes = 30
                    )

                    // Allow application to run for up to 120 seconds
                    retrySection(attempts = 2 * 120, delay = 500) {
                        require(JobPropertiesComponent(driver).isComplete())
                    }

                    clickSidebarOption(SidebarOption.Runs)

                    retrySection {
                        val row = ListComponent.inMainContainer(driver).rows().find { it.title().contains(jobName) }
                            ?: error("Could not find the job we just created")

                        val stats = row.importantStats() ?: error("Could not find stats")
                        assertThatInstance(stats.text, "should be successful") {
                            it.contains("success", ignoreCase = true)
                        }
                    }

                    search(jobName)

                    retrySection {
                        val row = ListComponent.inMainContainer(driver).rows().find { it.title().contains(jobName) }
                            ?: error("Could not find the job we just created")

                        val stats = row.importantStats() ?: error("Could not find stats")
                        assertThatInstance(stats.text, "should be successful") {
                            it.contains("success", ignoreCase = true)
                        }
                    }

                    search("Something else")

                    retrySection {
                        require(
                            ListComponent.inMainContainer(driver).rows().find { it.title().contains(jobName) } == null
                        )
                    }

                    search("")

                    retrySection {
                        ListComponent.inMainContainer(driver).rows().find { it.title().contains(jobName) }?.select()
                            ?: error("Could not find the job we just created")

                        driver.clickSidebarOperation("Properties")
                    }

                    retrySection { require(JobPropertiesComponent(driver).isComplete()) }

                    retrySection {
                        val rows = ListComponent.inMainContainer(driver).rows()
                        require(rows.any { it.title() == "JobParameters.json" }) { "Could not find job parameters" }
                        require(rows.any { it.title() == "stdout.txt" }) { "Could not find stdout.txt" }
                    }

                    repeat(3) {
                        createApplication(ApplicationTestData.figletLongRunning.metadata.title, hours = 1)
                    }

                    clickSidebarOption(SidebarOption.Runs)

                    retrySection {
                        val rows = ListComponent.inMainContainer(driver).rows()
                        val targetRows = rows.filter {
                            it.importantStats()?.text?.contains("Success", ignoreCase = true) == false
                        }

                        targetRows.forEach { row ->
                            row.select()
                        }

                        driver.clickSidebarOperation("Stop", hold = true)
                    }

                    retrySection(attempts = 30) {
                        reload()
                        val rows = ListComponent.inMainContainer(driver).rows()
                        assertEquals(4, rows.size)
                        assertThatInstance(rows, "should all be successful") { row ->
                            row.all { it.importantStats()!!.text.contains("Success", ignoreCase = true) }
                        }
                    }
                }
            }

            case("-") {
                input(Unit)
                check { }
            }
        }

        test<Unit, Unit>("Test Application with Resources") {
            executeE2E(E2EDrivers.CHROME) {
                UCloudProvider.testInitialize(UCloudLauncher.serviceClient)
                ApplicationTestData.create()
                with(initializeResourceTestContext(UCloudProvider.products, emptyList())) {
                    login(piUsername, piPassword)
                    switchToProjectByTitle(projectTitle)

                    val myFolder = "My Folder"

                    run {
                        clickSidebarOption(SidebarOption.Files)
                        retrySection {
                            ListComponent.inMainContainer(driver).rows().find { it.title().startsWith("Member Files:") }
                                ?.navigate() ?: error("Could not navigate to collection")
                        }
                        retrySection { driver.clickUniqueButton("Create folder") }
                        retrySection { ListComponent.inMainContainer(driver).sendInput("${myFolder}\n") }
                        retrySection {
                            assertThatInstance(
                                ListComponent.inMainContainer(driver).rows().map { it.title() },
                                "Has our new folder",
                                matcher = { rows -> rows.any { it == myFolder } }
                            )
                        }
                    }

                    val myPublicLink = "myingress"
                    run {
                        clickSidebarOption(SidebarOption.Resources)
                        retrySection {
                            driver.findElements(By.tagName("div")).find { it.text == "Public Links" }?.click()
                                ?: error("Could not find public links button")
                        }

                        retrySection { driver.clickSidebarOperation("Create public link") }
                        retrySection {
                            ListComponent.inMainContainer(driver).findProductSelector()?.click()
                                ?: error("Could not find product selector")
                        }
                        retrySection {
                            ListComponent.inMainContainer(driver).products().first().select()
                        }
                        retrySection { ListComponent.inMainContainer(driver).sendInput("${myPublicLink}\n") }
                        retrySection {
                            assertThatInstance(
                                ListComponent.inMainContainer(driver).rows().map { it.title() },
                                "Has our new link",
                                matcher = { rows -> rows.any { it.contains(myPublicLink) } }
                            )
                        }
                    }

                    retrySection {
                        createApplication(
                            ApplicationTestData.figletBatch.metadata.title,
                            submit = false
                        )
                    }

                    retrySection {
                        val jobCreationComponent = JobCreationComponent(driver)
                        jobCreationComponent.addFolder()
                        jobCreationComponent.addPublicLink()
                    }

                    retrySection {
                        val jobCreationComponent = JobCreationComponent(driver)
                        jobCreationComponent.findParameters().filterIsInstance<InputParameter.File>().first().open()
                    }

                    retrySection {
                        ListComponent.inModal(driver).rows().find { it.title() == myFolder }?.use()
                            ?: error("Could not find folder")
                    }

                    retrySection {
                        val jobCreationComponent = JobCreationComponent(driver)
                        jobCreationComponent.findParameters().filterIsInstance<InputParameter.Ingress>().first().open()
                    }

                    retrySection {
                        ListComponent.inModal(driver).rows().find { it.title().contains(myPublicLink) }?.use()
                            ?: error("Could not find folder")
                    }

                    retrySection {
                        JobCreationComponent(driver).submit()
                    }

                    retrySection(attempts = 2 * 60, delay = 500) {
                        require(JobPropertiesComponent(driver).isComplete())
                    }
                }
            }

            case("-") {
                input(Unit)
                check { }
            }
        }
    }
}
