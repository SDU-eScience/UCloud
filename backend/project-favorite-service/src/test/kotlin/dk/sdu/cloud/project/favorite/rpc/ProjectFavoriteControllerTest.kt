package dk.sdu.cloud.project.favorite.rpc

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.ViewProjectResponse
import api.ListFavoritesRequest
import api.ToggleFavoriteRequest
import dk.sdu.cloud.project.favorite.services.ProjectFavoriteHibernateDAO
import dk.sdu.cloud.project.favorite.services.ProjectFavoriteService
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import org.junit.Test
import kotlin.test.assertTrue

class ProjectFavoriteControllerTest {

    @Test
    fun `list favorite projects`() {
        withKtorTest(
            setup = {
                val micro = initializeMicro()
                micro.install(HibernateFeature)
                val client = ClientMock.authenticatedClient
                val db = micro.hibernateDatabase
                val dao = ProjectFavoriteHibernateDAO()
                val service = ProjectFavoriteService(db, dao, client)
                listOf(ProjectFavoriteController(service))
            },

            test = {
                val response = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/project/favorite/list",
                    user = TestUsers.user,
                    request = ListFavoritesRequest()
                )
                response.assertSuccess()
                val results = defaultMapper.readValue<Page<String>>(response.response.content!!)
                assertTrue(results.items.isEmpty())
            }
        )
    }

    @Test
    fun `toggle and list favorite projects`() {
        val projectID = "project1234"

        withKtorTest(
            setup = {
                val micro = initializeMicro()
                micro.install(HibernateFeature)
                val client = ClientMock.authenticatedClient
                val db = micro.hibernateDatabase
                val dao = ProjectFavoriteHibernateDAO()
                val service = ProjectFavoriteService(db, dao, client)

                ClientMock.mockCallSuccess(
                    Projects.view,
                    ViewProjectResponse(projectID, "title", listOf(ProjectMember(TestUsers.user.username, ProjectRole.ADMIN)))
                )

                listOf(ProjectFavoriteController(service))
            },

            test = {
                run {
                    val response = sendJson(
                        method = HttpMethod.Post,
                        path = "/api/project/favorite/list",
                        user = TestUsers.user,
                        request = ListFavoritesRequest()
                    )
                    response.assertSuccess()
                    val results = defaultMapper.readValue<Page<String>>(response.response.content!!)
                    assertTrue(results.items.isEmpty())
                }

                run {
                    val response = sendJson(
                        method = HttpMethod.Post,
                        path = "/api/project/favorite",
                        user = TestUsers.user,
                        request = ToggleFavoriteRequest(projectID)
                    )
                    response.assertSuccess()
                }
                run {
                    val response = sendJson(
                        method = HttpMethod.Post,
                        path = "/api/project/favorite/list",
                        user = TestUsers.user,
                        request = ListFavoritesRequest()
                    )
                    response.assertSuccess()
                    val results = defaultMapper.readValue<Page<String>>(response.response.content!!)
                    assertTrue(results.items.contains(projectID))
                }
            }
        )
    }
}
