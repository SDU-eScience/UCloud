package dk.sdu.cloud.file.favorite.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.file.favorite.api.FavoriteStatusRequest
import dk.sdu.cloud.file.favorite.api.FavoriteStatusResponse
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteRequest
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteResponse
import dk.sdu.cloud.file.favorite.services.FileFavoriteService
import dk.sdu.cloud.file.favorite.storageFile
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.CloudContextMock
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.parseSuccessful
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileFavoriteControllerTest{
    private val service = mockk<FileFavoriteService<HibernateSession>>()
    private val cloud = CloudContextMock
    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        listOf(FileFavoriteController(service, cloud))
    }

    @Test
    fun `Toggle test`() {
        withKtorTest(
            setup,
            test = {
                coEvery { service.toggleFavorite(any(), any(), any()) } answers {
                    emptyList()
                }

                val request = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/files/favorite",
                    user = TestUsers.user,
                    request = ToggleFavoriteRequest(
                        listOf(
                            "/home/user/1",
                            "/home/user/2"
                        )
                    )
                )

                val response = request.parseSuccessful<ToggleFavoriteResponse>()
                assertTrue(response.failures.isEmpty())
            }
        )
    }

    @Test
    fun `Toggle test - failed instances`() {
        withKtorTest(
            setup,
            test = {
                coEvery { service.toggleFavorite(any(), any(), any()) } answers {
                    listOf("/home/user/1")
                }

                val request = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/files/favorite",
                    user = TestUsers.user,
                    request = ToggleFavoriteRequest(
                        listOf(
                            "/home/user/1",
                            "/home/user/2"
                        )
                    )
                )

                request.assertSuccess()
                val response = defaultMapper.readValue<ToggleFavoriteResponse>(request.response.content!!)
                assertEquals(1, response.failures.size)
                assertEquals("/home/user/1", response.failures.first())
            }
        )
    }

    @Test
    fun `isFavorite test - failed instances`() {
        withKtorTest(
            setup,
            test = {
                coEvery { service.getFavoriteStatus(any(), any()) } answers {
                    mapOf(
                        "fileId" to true,
                        "fileId2" to false
                    )
                }

                val request = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/files/favorite/status",
                    user = TestUsers.user,
                    request = FavoriteStatusRequest(
                        listOf(
                            storageFile,
                            storageFile.copy(path = "/home/user/5", fileId = "fileId2")
                        )
                    )
                )

                request.assertSuccess()
                val response = defaultMapper.readValue<FavoriteStatusResponse>(request.response.content!!)
                assertEquals(true, response.favorited["fileId"])
                assertEquals(false, response.favorited["fileId2"])
            }
        )
    }
}
