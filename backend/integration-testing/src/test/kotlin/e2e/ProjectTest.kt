package dk.sdu.cloud.integration.e2e

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.grant.api.AutomaticApprovalSettings
import dk.sdu.cloud.grant.api.Grants
import dk.sdu.cloud.grant.api.UploadRequestSettingsRequest
import dk.sdu.cloud.grant.api.UserCriteria
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.backend.createUser
import dk.sdu.cloud.integration.backend.initializeRootProject
import dk.sdu.cloud.integration.goToProjects
import org.junit.Test
import org.openqa.selenium.By


class ProjectTest : EndToEndTest() {

    @Test
    fun `Go to projects`() = e2e {
        val user = createUser()
        driver.get("$address/app")
        driver.login(user.username, user.password)
        driver.goToProjects()
    }

    @Test
    fun `Create project application`() = e2e {
        val root = initializeRootProject()
        Grants.uploadRequestSettings.call(
            UploadRequestSettingsRequest(
                AutomaticApprovalSettings(emptyList(), emptyList()), listOf(
                    UserCriteria.Anyone()
                )
            ), serviceClient.withProject(root)
        )
        val user = createUser()

        driver.get("$address/app")
        driver.login(user.username, user.password)
        driver.goToProjects()
        // FIXME: Find other way of selecting. Preferably test tag.
        driver.awaitElements(By.xpath("//button[text()='Create Project Application']")).first { it.isDisplayed }.click()
        driver.awaitElement(By.xpath("//h3[text()='UCloud']")).click()
        driver.awaitElement(By.xpath("//label[text()='Title']")).click()
        driver.switchTo().activeElement().sendKeys("Foo bar baz")
        driver.awaitElement(By.xpath("//input[@data-target='cephfs/ucloud']")).sendKeys("10")
        driver.awaitElement(By.xpath("//input[@data-target='quota-cephfs/ucloud']")).sendKeys("50")
        driver.awaitElement(By.xpath("//input[@data-target='standard/ucloud']")).sendKeys("70")
        driver.awaitElement(By.xpath("//textarea")).sendKeys((65..90).map { it.toChar() }.joinToString(""))
        // FIXME: Find other way of selecting. Preferably test tag.
        driver.awaitElement(By.xpath("//button[text()='Submit request']")).click()
        // FIXME: Find other way of selecting. Preferably test tag.
        driver.awaitElement(By.xpath("//button[text()='Withdraw']"))
    }
}