package dk.sdu.cloud.project.favorite.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.ViewProjectResponse
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue

class ProjectFavoriteServiceTest {

    @Test
    fun `toggle favorite test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        val dao = ProjectFavoriteHibernateDAO()
        val client = ClientMock.authenticatedClient
        val service = ProjectFavoriteService(db, dao, client)
        ClientMock.mockCallSuccess(
            Projects.view,
            ViewProjectResponse(
                "project",
                "title",
                listOf(ProjectMember(TestUsers.user.username, ProjectRole.ADMIN))
            )
        )

        runBlocking {
            service.toggleFavorite("project", TestUsers.user)

            val favorites = service.listFavorites(TestUsers.user, NormalizedPaginationRequest(10, 0))
            assertTrue(favorites.items.contains("project"))
        }
        runBlocking {
            service.toggleFavorite("project", TestUsers.user)
            val favorites  = service.listFavorites(TestUsers.user, NormalizedPaginationRequest(10, 0))
            assertTrue(favorites.items.isEmpty())
        }
    }

    @Test (expected = RPCException::class)
    fun `Not a project test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        val dao = ProjectFavoriteHibernateDAO()
        val client = ClientMock.authenticatedClient
        val service = ProjectFavoriteService(db, dao, client)
        ClientMock.mockCallError(
            Projects.view,
            CommonErrorMessage("not found"),
            HttpStatusCode.NotFound
        )

        runBlocking {
            service.toggleFavorite("project", TestUsers.user)
        }
    }

    @Test
    fun `list favorites test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        val dao = ProjectFavoriteHibernateDAO()
        val client = ClientMock.authenticatedClient
        val service = ProjectFavoriteService(db, dao, client)
        runBlocking {
            val favorites = service.listFavorites(TestUsers.user, NormalizedPaginationRequest(10, 0))
            assertTrue(favorites.items.isEmpty())
        }
    }
}
