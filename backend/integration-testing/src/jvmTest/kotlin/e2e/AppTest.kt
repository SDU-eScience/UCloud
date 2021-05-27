package dk.sdu.cloud.integration.e2e

import dk.sdu.cloud.Role
import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.CreateTagsRequest
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.integration.backend.*
import dk.sdu.cloud.integration.clickAppCard
import dk.sdu.cloud.integration.findAppCard
import dk.sdu.cloud.integration.retrySection
import org.junit.*
import org.openqa.selenium.By

class AppTest : EndToEndTest() {
    @Test
    fun `View apps`() = e2e {
        val user = createUser()
        driver.get("$address/app")
        driver.login(user.username, user.password)
        driver.goToApps()
    }

    @Test
    fun `Find app`() = e2e {
        val admin = createUser(role = Role.ADMIN)
        SampleApplications.create()
        AppStore.createTag.call(CreateTagsRequest(listOf("Featured"), SampleApplications.figlet.name), admin.client)
        driver.get("$address/app")
        driver.login(admin.username, admin.password)
        driver.goToApps()
        driver.findAppCard(SampleApplications.figlet.name.capitalize())
    }

    @Test
    fun `Favorite app in details page`() = e2e {
        val admin = createUser(role = Role.ADMIN)
        SampleApplications.create()
        AppStore.createTag.call(CreateTagsRequest(listOf("Featured"), SampleApplications.figlet.name), admin.client)
        driver.get("$address/app")
        driver.login(admin.username, admin.password)
        driver.goToApps()
        driver.clickAppCard(SampleApplications.figlet.name.capitalize())
        driver.awaitElements(By.xpath("//button[text()='Add to favorites']")).first { it.isDisplayed }.click()
        driver.awaitElements(By.xpath("//button[text()='Remove from favorites']")).first { it.isDisplayed }
    }

    @Test
    fun `Start app`() = e2e {
        UCloudLauncher.requireK8s()
        val admin = createUser(role = Role.ADMIN)
        SampleApplications.create()
        val rootProject = initializeRootProject()
        addFundsToPersonalProject(rootProject, admin.username, sampleStorage.category)
        addFundsToPersonalProject(rootProject, admin.username, sampleCompute.category)
        // setPersonalQuota(rootProject, admin.username, 10.GiB)
        AppStore.createTag.call(CreateTagsRequest(listOf("Featured"), SampleApplications.figlet.name), admin.client)
        driver.get("$address/app")
        driver.login(admin.username, admin.password)
        driver.goToApps()
        driver.clickAppCard(SampleApplications.figlet.name.capitalize())
        driver.awaitElements(By.xpath("//button[text()='Add to favorites']")).first { it.isDisplayed }.click()
        driver.awaitElements(By.xpath("//button[text()='Remove from favorites']")).first { it.isDisplayed }
        driver.goToApps()
        retrySection {
            // At this point, we're back at a page we already visited. Stale elements are a risk here.
            driver.clickAppCard(SampleApplications.figlet.name.capitalize())
        }
        driver.awaitElement(By.xpath("//b[text()='No machine selected']")).click()
        driver.awaitElement(By.xpath("//td[text()='u1-standard-1']")).click()
        driver.awaitElement(
            By.xpath(
                "//div[contains(text(), 'Some text to render with figlet')]/parent::div/parent::label/parent::div/input"
            )
        ).sendKeys("foo bar baz")
        driver.awaitElements(By.xpath("//button[text()='Submit']")).first { it.isDisplayed }.click()
        await { println(driver.currentUrl) ; driver.currentUrl.contains("/applications/results/") }
    }
}
