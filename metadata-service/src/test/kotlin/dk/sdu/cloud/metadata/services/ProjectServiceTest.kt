package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.project.api.CreateProjectResponse
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.service.authenticatedCloud
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class ProjectServiceTest {

    @Test
    fun `create test`() {
        val micro = initializeMicro()
        val cloud = micro.authenticatedCloud
        val projectService = ProjectService(cloud)
        runBlocking {

            CloudMock.mockCallSuccess(
                ProjectDescriptions,
                { ProjectDescriptions.create },
                CreateProjectResponse("ProjectId")
            )
            val id = projectService.createProject("Title", TestUsers.user.username)
            assertEquals("ProjectId", id)
        }
    }
}
