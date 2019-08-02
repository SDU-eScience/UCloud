package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.app.store.util.normAppDesc
import dk.sdu.cloud.app.store.util.normToolDesc
import dk.sdu.cloud.app.store.util.withNameAndVersion
import dk.sdu.cloud.app.store.util.withNameAndVersionAndTitle
import dk.sdu.cloud.app.store.util.withTags
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.withDatabase
import io.ktor.http.HttpStatusCode
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ApplicationHibernateDaoTest {
    private val user = "user"

    @Test
    fun `create, find, update test`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO)
                appDAO.create(it, user, normAppDesc)

                run {
                    // Load from page
                    val hits = appDAO.findAllByName(it, user, "name", NormalizedPaginationRequest(10, 0))
                    val loadedApp = hits.items.first().metadata.description

                    assertEquals("app description", loadedApp)
                    assertEquals(1, hits.itemsInTotal)
                }

                run {
                    // Load from specific version
                    val loadedApp = appDAO.findByNameAndVersion(it, user, "name", "2.2")
                    assertEquals("app description", loadedApp.metadata.description)
                }

                appDAO.updateDescription(it, user, "name", "2.2", "new description")

                run {
                    // Load from specific version after update
                    val loadedApp = appDAO.findByNameAndVersion(it, user, "name", "2.2")
                    assertEquals("new description", loadedApp.metadata.description)
                    assertEquals("Authors", loadedApp.metadata.authors.first())
                }

                appDAO.updateDescription(it, user, "name", "2.2", null, listOf("New Authors"))

                run {
                    // Load from specific version after another update
                    val loadedApp = appDAO.findByNameAndVersion(it, user, "name", "2.2")
                    assertEquals("new description", loadedApp.metadata.description)
                    assertEquals("New Authors", loadedApp.metadata.authors.first())
                }
            }
        }
    }

    @Test
    fun `test find by name and version user`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO)
                appDAO.create(it, user, normAppDesc)


                run {
                    // Load from specific version
                    val loadedApp = appDAO.findByNameAndVersionForUser(it, user, "name", "2.2")
                    assertEquals("app description", loadedApp.metadata.description)
                }

                appDAO.updateDescription(it, user, "name", "2.2", "new description")

                run {
                    // Load from specific version after update
                    val loadedApp = appDAO.findByNameAndVersionForUser(it, user, "name", "2.2")
                    assertEquals("new description", loadedApp.metadata.description)
                    assertEquals("Authors", loadedApp.metadata.authors.first())
                }
            }
        }
    }

    @Test(expected = ApplicationException.NotFound::class)
    fun `test find by name and version user - notfound`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val appDAO = ApplicationHibernateDAO(toolDAO)
                run {
                    // Load from specific version
                    val loadedApp = appDAO.findByNameAndVersionForUser(it, user, "name", "2.2")
                    assertEquals("app description", loadedApp.metadata.description)
                }
            }
        }
    }

    @Test
    fun `test creating different versions`() {
        withDatabase { db ->
            db.withTransaction {
                val version1 = normAppDesc.withNameAndVersion("app", "v1")
                val version2 = normAppDesc.withNameAndVersion("app", "v2")

                val toolDAO = ToolHibernateDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO)
                appDAO.create(it, user, version1)
                Thread.sleep(1000) // Wait a bit to make sure they get different createdAt
                appDAO.create(it, user, version2)

                val allListed = appDAO.listLatestVersion(it, user, NormalizedPaginationRequest(10, 0))
                assertEquals(1, allListed.itemsInTotal)
                assertThatPropertyEquals(allListed.items.single(), { it.metadata.version }, version2.metadata.version)
            }
        }
    }

    @Test
    fun `search test`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                toolDAO.create(it, user, normToolDesc)

                val applicationA = normAppDesc.withNameAndVersionAndTitle("name1", "1", "AAA")
                val applicationB = normAppDesc.withNameAndVersionAndTitle("name2", "1", "BBB")

                val appDAO = ApplicationHibernateDAO(toolDAO)
                appDAO.create(it, user, applicationA)
                appDAO.create(it, user, applicationB)

                run {
                    val searchResult = appDAO.search(it, user, "A", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.single().metadata.name)
                }

                run {
                    val searchResult = appDAO.search(it, user, "AA", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.single().metadata.name)
                }

                run {
                    val searchResult = appDAO.search(it, user, "AAA", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.single().metadata.name)
                }

                run {
                    val searchResult = appDAO.search(it, user, "notPossible", NormalizedPaginationRequest(10, 0))

                    assertEquals(0, searchResult.itemsInTotal)
                }
                //Spacing searches
                run {
                    val searchResult = appDAO.search(it, user, "AA   ", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.first().metadata.name)
                }
                run {
                    val searchResult = appDAO.search(it, user, "   AA", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.first().metadata.name)
                }
                run {
                    val searchResult =
                        appDAO.search(it, user, "multiple one found AA", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.first().metadata.name)
                }

                run {
                    val searchResult =
                        appDAO.search(it, user, "   AA  A Extra    spacing   ", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.first().metadata.name)
                }

                run {
                    val searchResult = appDAO.search(it, user, "AA BB", NormalizedPaginationRequest(10, 0))

                    assertEquals(2, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.first().metadata.name)
                    assertEquals(applicationB.metadata.name, searchResult.items.last().metadata.name)

                }

                run {
                    val searchResult = appDAO.search(it, user, "  ", NormalizedPaginationRequest(10, 0))

                    assertEquals(0, searchResult.itemsInTotal)
                }

                //multiversion search
                val applicationANewVersion = normAppDesc.withNameAndVersionAndTitle("name1", "2", "AAA")
                appDAO.create(it, user, applicationANewVersion)

                run {
                    val searchResult = appDAO.search(it, user, "AA", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationANewVersion.metadata.title, searchResult.items.first().metadata.title)
                    assertEquals(applicationANewVersion.metadata.version, searchResult.items.first().metadata.version)
                }

                run {
                    val searchResult = appDAO.search(it, user, "AA BB", NormalizedPaginationRequest(10, 0))

                    assertEquals(2, searchResult.itemsInTotal)
                    assertEquals(applicationANewVersion.metadata.title, searchResult.items.first().metadata.title)
                    assertEquals(applicationANewVersion.metadata.version, searchResult.items.first().metadata.version)
                    assertEquals(applicationB.metadata.title, searchResult.items.last().metadata.title)
                    assertEquals(applicationB.metadata.version, searchResult.items.last().metadata.version)
                }
            }
        }
    }

    @Test(expected = ApplicationException.AlreadyExists::class)
    fun `Create - already exists - test`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO)
                appDAO.create(it, user, normAppDesc)
                appDAO.create(it, user, normAppDesc)

            }
        }
    }

    @Test(expected = ApplicationException.NotAllowed::class)
    fun `Create - Not Allowed - test`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO)
                appDAO.create(it, user, normAppDesc)
                appDAO.create(it, "Not the user", normAppDesc)

            }
        }
    }

    @Test(expected = ApplicationException.BadToolReference::class)
    fun `Create - bad tool - test`() {
        withDatabase { db ->
            db.withTransaction {

                val appDAO = ApplicationHibernateDAO(ToolHibernateDAO())
                appDAO.create(it, user, normAppDesc)
            }
        }
    }

    @Test(expected = ApplicationException.NotFound::class)
    fun `Find by name - not found - test`() {
        withDatabase { db ->
            db.withTransaction {

                val appDAO = ApplicationHibernateDAO(ToolHibernateDAO())
                appDAO.findByNameAndVersion(it, user, "name", "version")
            }
        }
    }

    //@Ignore // Code only works in postgres
    @Test
    fun `tagSearch test`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO)

                val commonTag = "common"
                val appA = normAppDesc.withNameAndVersionAndTitle("A", "1","Atitle").withTags(listOf("A1", "A2", commonTag))
                val appB = normAppDesc.withNameAndVersionAndTitle("B", "1", "Btitle").withTags(listOf("B1", "B2", commonTag))

                appDAO.create(it, user, appA)
                appDAO.create(it, user, appB)

                run {
                    // Search for no hits
                    val hits = appDAO.searchTags(it, user, listOf("tag20"), NormalizedPaginationRequest(10, 0))

                    assertEquals(0, hits.itemsInTotal)
                }

                run {
                    // Search for one hit tag
                    val hits = appDAO.searchTags(it, user, listOf("A1"), NormalizedPaginationRequest(10, 0))

                    val result = hits.items.single().metadata

                    assertEquals(1, hits.itemsInTotal)
                    assertEquals(appA.metadata.name, result.name)
                    assertEquals(appA.metadata.version, result.version)
                }

                run {
                    // Search for multiple hit tag
                    val hits = appDAO.searchTags(it, user, listOf(commonTag), NormalizedPaginationRequest(10, 0))

                    assertEquals(2, hits.itemsInTotal)
                    assertEquals(appA.metadata.name, hits.items[0].metadata.name)
                    assertEquals(appB.metadata.name, hits.items[1].metadata.name)
                }

                run {
                    // Search for empty tag. Should be empty since it is not a wildcard search
                    val hits = appDAO.searchTags(it, user, listOf(""), NormalizedPaginationRequest(10, 0))

                    assertEquals(0, hits.itemsInTotal)
                }
            }
        }
    }

    @Test
    fun `Favorite test`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO)

                val userA = "userA"
                val userB = "userB"

                val aVersion1 = normAppDesc.withNameAndVersion("A", "v1")
                val aVersion2 = normAppDesc.withNameAndVersion("A", "v2")
                val bVersion1 = normAppDesc.withNameAndVersion("B", "v1")

                appDAO.create(it, user, aVersion1)
                Thread.sleep(100) // Ensure different createdAt
                appDAO.create(it, user, aVersion2)
                appDAO.create(it, user, bVersion1)

                listOf(userA, userB).forEach { currentUser ->
                    run {
                        val favorites = appDAO.retrieveFavorites(it, currentUser, NormalizedPaginationRequest(10, 0))
                        assertEquals(0, favorites.itemsInTotal)
                    }

                    run {
                        appDAO.toggleFavorite(it, currentUser, aVersion1.metadata.name, aVersion1.metadata.version)
                        val favorites = appDAO.retrieveFavorites(it, currentUser, NormalizedPaginationRequest(10, 0))
                        assertEquals(1, favorites.itemsInTotal)
                    }

                    run {
                        appDAO.toggleFavorite(it, currentUser, aVersion2.metadata.name, aVersion2.metadata.version)
                        val favorites = appDAO.retrieveFavorites(it, currentUser, NormalizedPaginationRequest(10, 0))
                        assertEquals(2, favorites.itemsInTotal)
                    }
                }
            }
        }
    }

    @Test(expected = ApplicationException.BadApplication::class)
    fun `Favorite test - Not an app`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO)

                appDAO.toggleFavorite(it, user, "App1", "1.4")
            }
        }
    }

    @Test
    fun `create and delete tags`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO)
                val appA = normAppDesc.withNameAndVersion("A", "1").withTags(listOf("A1", "A2"))

                appDAO.create(it, user, appA)
                run {
                    val tag1 = appDAO.findTag(it, appA.metadata.name, appA.metadata.version, "A1")
                    val tag2 = appDAO.findTag(it, appA.metadata.name, appA.metadata.version, "A2")
                    val nottag = appDAO.findTag(it, appA.metadata.name, appA.metadata.version, "A3")

                    assertNotNull(tag1)
                    assertNotNull(tag2)
                    assertNull(nottag)
                }

                appDAO.createTags(it, listOf("A3"), appA.metadata.name, appA.metadata.version, user)

                run {
                    val tag1 = appDAO.findTag(it, appA.metadata.name, appA.metadata.version, "A1")
                    val tag2 = appDAO.findTag(it, appA.metadata.name, appA.metadata.version, "A2")
                    val tag3 = appDAO.findTag(it, appA.metadata.name, appA.metadata.version, "A3")

                    assertNotNull(tag1)
                    assertNotNull(tag2)
                    assertNotNull(tag3)
                }

                appDAO.deleteTags(it, listOf("A1", "A3"), appA.metadata.name, appA.metadata.version, user)
                run {
                    val tag1 = appDAO.findTag(it, appA.metadata.name, appA.metadata.version, "A1")
                    val tag2 = appDAO.findTag(it, appA.metadata.name, appA.metadata.version, "A2")
                    val tag3 = appDAO.findTag(it, appA.metadata.name, appA.metadata.version, "A3")

                    assertNull(tag1)
                    assertNotNull(tag2)
                    assertNull(tag3)
                }
            }
        }
    }

    @Test (expected = RPCException::class)
    fun `create tag for invalid app`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val appDAO = ApplicationHibernateDAO(toolDAO)
                appDAO.createTags(it, listOf("A3"), "notAnApp", "NotVersion", "user")
            }
        }
    }

    @Test (expected = RPCException::class)
    fun `delete tag for invalid app`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val appDAO = ApplicationHibernateDAO(toolDAO)
                appDAO.deleteTags(it, listOf("A3"), "notAnApp", "NotVersion", "user")
            }
        }
    }
}
