package dk.sdu.cloud.project.favorite.services

import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectFavoriteHibernateTest {

    private val projectID = "projectID1234"
    private val projectID2 = "test"

    @Test
    fun `Test insert, list, delete favorite`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        val dao = ProjectFavoriteHibernateDAO()
        runBlocking {
            db.withTransaction { session ->
                run {
                    val favorites = dao.listFavorites(session, TestUsers.user, NormalizedPaginationRequest(10, 0))

                    assertEquals(0, favorites.itemsInTotal)
                    assertTrue(favorites.items.isEmpty())
                    assertEquals(0, favorites.pagesInTotal)
                    assertEquals(0, favorites.pageNumber)
                }

                dao.toggleFavorite(session, TestUsers.user, projectID)

                run {
                    val favorites = dao.listFavorites(session, TestUsers.user, NormalizedPaginationRequest(10, 0))

                    assertEquals(1, favorites.itemsInTotal)
                    assertEquals(projectID, favorites.items.first())
                    assertEquals(1, favorites.pagesInTotal)
                    assertEquals(0, favorites.pageNumber)
                }

                dao.toggleFavorite(session, TestUsers.user, projectID2)

                run {
                    val favorites = dao.listFavorites(session, TestUsers.user, NormalizedPaginationRequest(10, 0))

                    assertEquals(2, favorites.itemsInTotal)
                    assertTrue(favorites.items.contains(projectID))
                    assertTrue(favorites.items.contains(projectID2))
                    assertEquals(1, favorites.pagesInTotal)
                    assertEquals(0, favorites.pageNumber)
                }

                dao.toggleFavorite(session, TestUsers.user2, projectID)

                run {
                    val favorites = dao.listFavorites(session, TestUsers.user2, NormalizedPaginationRequest(10, 0))

                    assertEquals(1, favorites.itemsInTotal)
                    assertEquals(projectID, favorites.items.first())
                    assertEquals(1, favorites.pagesInTotal)
                    assertEquals(0, favorites.pageNumber)
                }

                run {
                    val favorites = dao.listFavorites(session, TestUsers.user, NormalizedPaginationRequest(10, 0))

                    assertEquals(2, favorites.itemsInTotal)
                    assertTrue(favorites.items.contains(projectID))
                    assertTrue(favorites.items.contains(projectID2))
                    assertEquals(1, favorites.pagesInTotal)
                    assertEquals(0, favorites.pageNumber)
                }

                dao.toggleFavorite(session, TestUsers.user, projectID)
                dao.toggleFavorite(session, TestUsers.user, projectID2)

                run {
                    val favorites = dao.listFavorites(session, TestUsers.user, NormalizedPaginationRequest(10, 0))

                    assertEquals(0, favorites.itemsInTotal)
                    assertTrue(favorites.items.isEmpty())
                    assertEquals(0, favorites.pagesInTotal)
                    assertEquals(0, favorites.pageNumber)
                }

                run {
                    val favorites = dao.listFavorites(session, TestUsers.user2, NormalizedPaginationRequest(10, 0))

                    assertEquals(1, favorites.itemsInTotal)
                    assertEquals(projectID, favorites.items.first())
                    assertEquals(1, favorites.pagesInTotal)
                    assertEquals(0, favorites.pageNumber)
                }
            }
        }
    }
}
