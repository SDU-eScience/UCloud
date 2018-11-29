package dk.sdu.cloud.file.trash.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.file.trash.api.TrashRequest
import dk.sdu.cloud.file.trash.api.TrashResponse
import dk.sdu.cloud.file.trash.services.TrashService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.authenticatedCloud
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertTrue

class FileTrashTest() {

    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        val micro = initializeMicro()
        val cloud = micro.authenticatedCloud
        val trashService = mockk<TrashService>()

        coEvery { trashService.moveFilesToTrash(any(), any(), any())} answers {
            emptyList()
        }
        coEvery { trashService.emptyTrash(any(), any())} just Runs

        listOf(FileTrashController(cloud, trashService))
    }

    @Test
    fun `test move to trash`() {
        withKtorTest(
            setup,
            test = {
                val request = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/files/trash",
                    user = TestUsers.user,
                    request = TrashRequest(listOf("file1", "file2"))
                )
                request.assertSuccess()
                val response = defaultMapper.readValue<TrashResponse>(request.response.content!!)
                assertTrue(response.failures.isEmpty())
            }
        )
    }

    @Test
    fun `test clear trash`() {
        withKtorTest(
            setup,
            test = {
                sendRequest(
                    method = HttpMethod.Post,
                    path = "/api/files/trash/clear",
                    user = TestUsers.user
                ).assertSuccess()
            }
        )
    }
}
