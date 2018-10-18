package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedApplicationDescription
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.utils.withDatabase
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.withTransaction
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationHibernateDaoTest {

    private val user = "user"
    private val normAppDesc = NormalizedApplicationDescription(
        NameAndVersion("name", "2.2"),
        NameAndVersion("name", "2.2"),
        listOf("Authors"),
        "title",
        "app description",
        mockk(relaxed = true),
        mockk(relaxed = true),
        listOf("glob"),
        listOf("tag1", "tag2")
    )
    private val normAppDesc2 = NormalizedApplicationDescription(
        NameAndVersion("name", "1.2"),
        NameAndVersion("name", "2.2"),
        listOf("Authors"),
        "title",
        "app description",
        mockk(relaxed = true),
        mockk(relaxed = true),
        listOf("glob"),
        listOf()
    )
    private val normAppDesc3 = NormalizedApplicationDescription(
        NameAndVersion("app", "3.2"),
        NameAndVersion("name", "2.2"),
        listOf("Authors"),
        "title",
        "app description",
        mockk(relaxed = true),
        mockk(relaxed = true),
        listOf("glob"),
        listOf()
    )
    private val normAppDesc4 = NormalizedApplicationDescription(
        NameAndVersion("app", "4.2"),
        NameAndVersion("name", "2.2"),
        listOf("Authors"),
        "title",
        "app description",
        mockk(relaxed = true),
        mockk(relaxed = true),
        listOf("glob"),
        listOf()
    )

    private val normToolDesc = NormalizedToolDescription(
        NameAndVersion("name", "2.2"),
        "container",
        2,
        2,
        SimpleDuration(1, 0, 0),
        listOf(""),
        listOf("auther"),
        "title",
        "description",
        ToolBackend.UDOCKER
    )

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
                    val loadedApp = hits.items.first().description.description

                    assertEquals("app description", loadedApp)
                    assertEquals(1, hits.itemsInTotal)
                }

                run {
                    // Load from specific version
                    val loadedApp = appDAO.findByNameAndVersion(it, user, "name", "2.2")
                    assertEquals("app description", loadedApp.description.description)
                }

                appDAO.updateDescription(it, user, "name", "2.2", "new description")

                run {
                    // Load from specific version after update
                    val loadedApp = appDAO.findByNameAndVersion(it, user, "name", "2.2")
                    assertEquals("new description", loadedApp.description.description)
                    assertEquals("Authors", loadedApp.description.authors.first())
                }

                appDAO.updateDescription(it, user, "name", "2.2", null, listOf("New Authors"))

                run {
                    // Load from specific version after another update
                    val loadedApp = appDAO.findByNameAndVersion(it, user, "name", "2.2")
                    assertEquals("new description", loadedApp.description.description)
                    assertEquals("New Authors", loadedApp.description.authors.first())
                }
            }
        }
    }

    @Test
    fun `list all`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO)
                appDAO.create(it, user, normAppDesc2)
                Thread.sleep(1000)
                appDAO.create(it, user, normAppDesc)
                Thread.sleep(1000)
                appDAO.create(it, user, normAppDesc3)
                Thread.sleep(1000)
                appDAO.create(it, user, normAppDesc4)

                appDAO.toggleFavorite(it, user, normAppDesc.info.name, normAppDesc.info.version)

                val allListed = appDAO.listLatestVersion(it, user, NormalizedPaginationRequest(10, 0))

                var previous = ""
                allListed.items.forEach {
                    if (it.application.description.info.name < previous)
                        assert(false)
                    previous = it.application.description.info.name
                }

                assertEquals(2, allListed.itemsInTotal)
            }
        }
    }

    @Test
    fun `search test`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO)
                appDAO.create(it, user, normAppDesc2)
                Thread.sleep(1000)
                appDAO.create(it, user, normAppDesc)
                Thread.sleep(1000)
                appDAO.create(it, user, normAppDesc3)
                Thread.sleep(1000)
                appDAO.create(it, user, normAppDesc4)

                appDAO.toggleFavorite(it, user, normAppDesc.info.name, normAppDesc.info.version)

                run {
                    val searchResult = appDAO.search(it, user, "nam", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals("name", searchResult.items.first().application.description.info.name)
                }

                run {
                    val searchResult = appDAO.search(it, user, "ap", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals("app", searchResult.items.first().application.description.info.name)
                }

                run {
                    val searchResult = appDAO.search(it, user, "a", NormalizedPaginationRequest(10, 0))

                    assertEquals(2, searchResult.itemsInTotal)
                    assertEquals("app", searchResult.items[0].application.description.info.name)
                    assertEquals("name", searchResult.items[1].application.description.info.name)

                    assertFalse(searchResult.items[0].favorite)
                    assertTrue(searchResult.items[1].favorite)
                }

                run {
                    val searchResult = appDAO.search(it, user, "", NormalizedPaginationRequest(10, 0))

                    assertEquals(2, searchResult.itemsInTotal)
                    assertEquals("app", searchResult.items[0].application.description.info.name)
                    assertEquals("name", searchResult.items[1].application.description.info.name)
                }

                run {
                    val searchResult = appDAO.search(it, user, "notPossible", NormalizedPaginationRequest(10, 0))

                    assertEquals(0, searchResult.itemsInTotal)
                }
            }
        }
    }

    @Test
    fun `list all - same date test`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO)

                val tool = toolDAO.internalByNameAndVersion(it, normAppDesc.tool.name, normAppDesc.tool.version)

                val date = Date()
                it.save(
                    ApplicationEntity(
                        user,
                        date,
                        date,
                        normAppDesc,
                        "",
                        tool!!,
                        EmbeddedNameAndVersion(normAppDesc.info.name, normAppDesc.info.version)
                    )
                )

                it.save(
                    ApplicationEntity(
                        user,
                        date,
                        date,
                        normAppDesc4,
                        "",
                        tool,
                        EmbeddedNameAndVersion(normAppDesc4.info.name, normAppDesc4.info.version)
                    )
                )


                val allListed = appDAO.listLatestVersion(it, user, NormalizedPaginationRequest(10, 0))
                var previous = ""
                allListed.items.forEach {
                    if (it.application.description.info.name < previous)
                        assert(false)
                    previous = it.application.description.info.name
                }

                assertEquals(2, allListed.itemsInTotal)
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

    @Test
    fun `tagSearch test`() {
        withDatabase { db ->
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = ApplicationHibernateDAO(toolDAO)
                appDAO.create(it, user, normAppDesc)
                appDAO.create(it, user, normAppDesc3)
                appDAO.create(
                    it, user, normAppDesc.copy(
                        info = NameAndVersion("App2", "3.4"),
                        tags = listOf("tag2", "tag5")
                    )
                )
                appDAO.create(
                    it, user, normAppDesc.copy(
                        info = NameAndVersion("App1", "1.4"),
                        tags = listOf("tag2", "tag4")
                    )
                )

                appDAO.toggleFavorite(it, user, "App1", "1.4")
                appDAO.toggleFavorite(it, user, "App2", "3.4")

                run {
                    // Search for no hits
                    val hits = appDAO.searchTags(it, user, "tag20", NormalizedPaginationRequest(10, 0))
                    println(hits)

                    assertEquals(0, hits.itemsInTotal)
                }

                run {
                    // Search for one hit tag
                    val hits = appDAO.searchTags(it, user, "tag1", NormalizedPaginationRequest(10, 0))
                    println(hits)
                    val loadedApp = hits.items.first().application.description.description

                    assertEquals("app description", loadedApp)
                    assertEquals(1, hits.itemsInTotal)
                }

                run {
                    // Search for multiple hit tag
                    val hits = appDAO.searchTags(it, user, "tag2", NormalizedPaginationRequest(10, 0))

                    var previous = ""
                    hits.items.forEach {
                        if (it.application.description.info.name < previous)
                            assert(false)
                        previous = it.application.description.info.name
                    }

                    assertEquals(3, hits.itemsInTotal)
                    assertEquals("App1", hits.items[0].application.description.info.name)
                    assertEquals("App2", hits.items[1].application.description.info.name)
                    assertEquals("name", hits.items[2].application.description.info.name)

                    assertTrue(hits.items[0].favorite)
                    assertTrue(hits.items[1].favorite)
                    assertFalse(hits.items[2].favorite)
                }

                run {
                    // Search for empty tag. Should be empty since it is not a wildcard search
                    val hits = appDAO.searchTags(it, user, "", NormalizedPaginationRequest(10, 0))

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
                appDAO.create(it, user, normAppDesc)
                appDAO.create(it, user, normAppDesc3)
                appDAO.create(
                    it, user, normAppDesc.copy(
                        info = NameAndVersion("App2", "3.4")
                    )
                )
                appDAO.create(
                    it, user, normAppDesc.copy(
                        info = NameAndVersion("App1", "1.4")
                    )
                )

                run {
                    val favorites = appDAO.retrieveFavorites(it, user, NormalizedPaginationRequest(10, 0))
                    assertEquals(0, favorites.itemsInTotal)
                }

                run {
                    appDAO.toggleFavorite(it, user, "App2", "3.4")
                    val favorites = appDAO.retrieveFavorites(it, user, NormalizedPaginationRequest(10, 0))
                    assertEquals(1, favorites.itemsInTotal)
                }

                run {
                    appDAO.toggleFavorite(it, user, "App1", "1.4")
                    val favorites = appDAO.retrieveFavorites(it, user, NormalizedPaginationRequest(10, 0))
                    assertEquals(2, favorites.itemsInTotal)
                }

                run {
                    appDAO.toggleFavorite(it, "AnotherUser", "App1", "1.4")
                    val favorites = appDAO.retrieveFavorites(it, "AnotherUser", NormalizedPaginationRequest(10, 0))
                    assertEquals(1, favorites.itemsInTotal)
                }

                run {
                    appDAO.toggleFavorite(it, user, "App1", "1.4")
                    val favorites = appDAO.retrieveFavorites(it, user, NormalizedPaginationRequest(10, 0))
                    assertEquals(1, favorites.itemsInTotal)
                }

                run {
                    appDAO.toggleFavorite(it, "AnotherUser", "App1", "1.4")
                    val favorites = appDAO.retrieveFavorites(it, "AnotherUser", NormalizedPaginationRequest(10, 0))
                    assertEquals(0, favorites.itemsInTotal)
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
}
