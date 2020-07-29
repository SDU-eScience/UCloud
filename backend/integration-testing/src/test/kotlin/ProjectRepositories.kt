package dk.sdu.cloud.integration

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.service.test.retrySection
import org.junit.Test

class ProjectRepositories : IntegrationTest() {
    @Test
    fun `test that personal repository is automatically created and accessible`() = t {
        val project = initializeNormalProject(initializeRootProject())
        // This sections need to retry because repository creation is asynchronous
        retrySection {
            FileDescriptions.listAtPath.call(
                ListDirectoryRequest(
                    joinPath(projectHomeDirectory(project.projectId), PERSONAL_REPOSITORY, project.piUsername)
                ),
                project.piClient
            ).orThrow()
        }
    }
}
