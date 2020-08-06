package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.app.store.api.AppStoreServiceDescription
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.services.acl.AclAsyncDao
import dk.sdu.cloud.app.store.util.normAppDesc
import dk.sdu.cloud.app.store.util.normToolDesc
import dk.sdu.cloud.app.store.util.truncate
import dk.sdu.cloud.app.store.util.withNameAndVersion
import dk.sdu.cloud.app.store.util.withNameAndVersionAndTitle
import dk.sdu.cloud.app.store.util.withTool
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.lang.Exception
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicationHibernateDaoTest {
    private val user = TestUsers.user
    companion object {
        private lateinit var embDB: EmbeddedPostgres
        private lateinit var db: AsyncDBSessionFactory

        @BeforeClass
        @JvmStatic
        fun before() {
            val (db,embDB) = TestDB.from(AppStoreServiceDescription)
            this.db = db
            this.embDB = embDB
        }

        @AfterClass
        @JvmStatic
        fun after() {
            runBlocking {
                db.close()
            }
            embDB.close()
        }
    }

    @BeforeTest
    fun beforeEach() {
        truncate(db)
    }

    @AfterTest
    fun afterEach() {
        truncate(db)
    }

    @Test
    fun `create, find, update test`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDAO = ApplicationPublicAsyncDao()

                toolDao.create(it, user, normToolDesc, "original")

                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDAO)
                appDAO.create(it, user, normAppDesc, "original")

                run {
                    // Load from page
                    val hits = appDAO.findAllByName(it, user, null, emptyList(), "name", NormalizedPaginationRequest(10, 0))
                    val loadedApp = hits.items.first().metadata.description

                    assertEquals("app description", loadedApp)
                    assertEquals(1, hits.itemsInTotal)
                }

                run {
                    // Load from specific version
                    val loadedApp = appDAO.findByNameAndVersion(it, user, null, emptyList(), "name", "2.2")
                    assertEquals("app description", loadedApp.metadata.description)
                }

                appDAO.updateDescription(it, user, "name", "2.2", "new description", null)

                run {
                    // Load from specific version after update
                    val loadedApp = appDAO.findByNameAndVersion(it, user, null, emptyList(), "name", "2.2")
                    assertEquals("new description", loadedApp.metadata.description)
                    assertEquals("Authors", loadedApp.metadata.authors.first())
                }

                appDAO.updateDescription(it, user, "name", "2.2", null, listOf("New Authors"))

                run {
                    // Load from specific version after another update
                    val loadedApp = appDAO.findByNameAndVersion(it, user, null, emptyList(), "name", "2.2")
                    assertEquals("new description", loadedApp.metadata.description)
                    assertEquals("New Authors", loadedApp.metadata.authors.first())
                }
            }
        }
    }

    @Test
    fun `test find by name and version user`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()

                toolDao.create(it, user, normToolDesc, "original")

                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)
                appDAO.create(it, user, normAppDesc, "original")

                run {
                    // Load from specific version
                    val loadedApp = appDAO.findByNameAndVersionForUser(it, user, null, emptyList(), "name", "2.2")
                    assertEquals("app description", loadedApp.metadata.description)
                }

                appDAO.updateDescription(it, user, "name", "2.2", "new description", null)

                run {
                    // Load from specific version after update
                    val loadedApp = appDAO.findByNameAndVersionForUser(it, user, null, emptyList(), "name", "2.2")
                    assertEquals("new description", loadedApp.metadata.description)
                    assertEquals("Authors", loadedApp.metadata.authors.first())
                }
            }
        }
    }

    @Test(expected = ApplicationException.NotFound::class)
    fun `test find by name and version user - notfound`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()
                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)
                run {
                    // Load from specific version
                    val loadedApp = appDAO.findByNameAndVersionForUser(it, user, null, emptyList(), "name", "2.2")
                    assertEquals("app description", loadedApp.metadata.description)
                }
            }
        }
    }

    @Test
    fun `test creating different versions`() {
        runBlocking {
            db.withTransaction {
                val version1 = normAppDesc.withNameAndVersion("app", "v1")
                val version2 = normAppDesc.withNameAndVersion("app", "v2")

                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()

                toolDao.create(it, user, normToolDesc, "original")

                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)
                appDAO.create(it, user, version1, "original")
                Thread.sleep(1000) // Wait a bit to make sure they get different createdAt
                appDAO.create(it, user, version2, "original")

                val allListed = appDAO.listLatestVersion(it, user, null, emptyList(), NormalizedPaginationRequest(10, 0))
                assertEquals(1, allListed.itemsInTotal)
                assertThatPropertyEquals(allListed.items.single(), { it.metadata.version }, version2.metadata.version)
            }
        }
    }

    @Test
    fun `search test`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()

                toolDao.create(it, user, normToolDesc, "original")

                val applicationA = normAppDesc.withNameAndVersionAndTitle("name1", "1", "AAA")
                val applicationB = normAppDesc.withNameAndVersionAndTitle("name2", "1", "BBB")

                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)
                appDAO.create(it, user, applicationA, "original")
                appDAO.create(it, user, applicationB, "original")

                val appSearchDAO = ApplicationSearchAsyncDao(appDAO)
                println(1)
                run {
                    val searchResult = appSearchDAO.search(it, user, null, emptyList(), "A", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.single().metadata.name)
                }
                println(2)

                run {
                    val searchResult = appSearchDAO.search(it, user, null, emptyList(), "AA", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.single().metadata.name)
                }
                println(3)

                run {
                    val searchResult = appSearchDAO.search(it, user, null, emptyList(), "AAA", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.single().metadata.name)
                }
                println(4)

                run {
                    val searchResult = appSearchDAO.search(it, user, null, emptyList(), "notPossible", NormalizedPaginationRequest(10, 0))

                    assertEquals(0, searchResult.itemsInTotal)
                }
                println(5)

                //Spacing searches
                run {
                    val searchResult = appSearchDAO.search(it, user, null, emptyList(), "AA   ", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.first().metadata.name)
                }
                println(6)

                run {
                    val searchResult = appSearchDAO.search(it, user, null, emptyList(), "   AA", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.first().metadata.name)
                }
                println(7)

                run {
                    val searchResult =
                        appSearchDAO.search(it, user, null, emptyList(), "multiple one found AA", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.first().metadata.name)
                }
                println(8)

                run {
                    val searchResult =
                        appSearchDAO.search(it, user, null, emptyList(), "   AA  A Extra    spacing   ", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.first().metadata.name)
                }
                println(9)

                run {
                    val searchResult = appSearchDAO.search(it, user, null, emptyList(), "AA BB", NormalizedPaginationRequest(10, 0))

                    assertEquals(2, searchResult.itemsInTotal)
                    assertEquals(applicationA.metadata.name, searchResult.items.first().metadata.name)
                    assertEquals(applicationB.metadata.name, searchResult.items.last().metadata.name)

                }
                println(10)

                run {
                    val searchResult = appSearchDAO.search(it, user, null, emptyList(), "  ", NormalizedPaginationRequest(10, 0))

                    assertEquals(0, searchResult.itemsInTotal)
                }

                //multiversion search
                val applicationANewVersion = normAppDesc.withNameAndVersionAndTitle("name1", "2", "AAA")
                appDAO.create(it, user, applicationANewVersion, "original")
                println(12)

                run {
                    val searchResult = appSearchDAO.search(it, user, null, emptyList(), "AA", NormalizedPaginationRequest(10, 0))

                    assertEquals(1, searchResult.itemsInTotal)
                    assertEquals(applicationANewVersion.metadata.title, searchResult.items.first().metadata.title)
                    assertEquals(applicationANewVersion.metadata.version, searchResult.items.first().metadata.version)
                }
                println(13)

                run {
                    val searchResult = appSearchDAO.search(it, user, null, emptyList(), "AA BB", NormalizedPaginationRequest(10, 0))

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
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()

                toolDao.create(it, user, normToolDesc, "original")

                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)
                appDAO.create(it, user, normAppDesc, "original")
                appDAO.create(it, user, normAppDesc, "original")
            }
        }
    }

    @Test(expected = ApplicationException.NotAllowed::class)
    fun `Create - Not Allowed - test`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()

                toolDao.create(it, user, normToolDesc, "original")

                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)
                appDAO.create(it, user, normAppDesc, "original")
                appDAO.create(it, TestUsers.user5, normAppDesc, "original")
            }
        }
    }

    @Test(expected = ApplicationException.BadToolReference::class)
    fun `Create - bad tool - test`() {
        runBlocking {
            db.withTransaction {
                val appDAO = AppStoreAsyncDao(ToolAsyncDao(), AclAsyncDao(), ApplicationPublicAsyncDao())
                appDAO.create(it, user, normAppDesc, "original")
            }
        }
    }

    @Test(expected = ApplicationException.NotFound::class)
    fun `Find by name - not found - test`() {
        runBlocking {
            db.withTransaction {
                val appDAO = AppStoreAsyncDao(ToolAsyncDao(), AclAsyncDao(), ApplicationPublicAsyncDao())
                appDAO.findByNameAndVersion(it, user, null, emptyList(), "name", "version")
            }
        }
    }

    //@Ignore // Code only works in postgres
    @Test
    fun `tagSearch test`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()
                toolDao.create(it, user, normToolDesc, "original")

                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)
                val tagsDAO = ApplicationTagsAsyncDao()
                val searchDAO = ApplicationSearchAsyncDao(appDAO)
                val commonTag = "common"
                val appA = normAppDesc.withNameAndVersionAndTitle("A", "1", "Atitle")
                val appB = normAppDesc.withNameAndVersionAndTitle("B", "1", "Btitle")

                appDAO.create(it, user, appA, "original")
                appDAO.create(it, user, appB, "original")

                tagsDAO.createTags(it, user, appA.metadata.name, listOf(commonTag, "A1", "A2"))
                tagsDAO.createTags(it, user, appB.metadata.name, listOf(commonTag, "B1", "B2"))

                run {
                    // Search for no hits
                    val hits = searchDAO.searchByTags(it, user, null, emptyList(), listOf("tag20"), NormalizedPaginationRequest(10, 0))

                    assertEquals(0, hits.itemsInTotal)
                }

                run {
                    // Search for one hit tag
                    val hits = searchDAO.searchByTags(it, user, null, emptyList(), listOf("A1"), NormalizedPaginationRequest(10, 0))

                    val result = hits.items.single().metadata

                    assertEquals(1, hits.itemsInTotal)
                    assertEquals(appA.metadata.name, result.name)
                    assertEquals(appA.metadata.version, result.version)
                }

                run {
                    // Search for multiple hit tag
                    val hits = searchDAO.searchByTags(it, user, null, emptyList(), listOf(commonTag), NormalizedPaginationRequest(10, 0))

                    assertEquals(2, hits.itemsInTotal)
                    assertEquals(appA.metadata.name, hits.items[0].metadata.name)
                    assertEquals(appB.metadata.name, hits.items[1].metadata.name)
                }

                run {
                    // Search for empty tag. Should be empty since it is not a wildcard search
                    val hits = searchDAO.searchByTags(it, user, null, emptyList(), listOf(""), NormalizedPaginationRequest(10, 0))

                    assertEquals(0, hits.itemsInTotal)
                }
            }
        }
    }

    @Test
    fun `Favorite test`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()
                toolDao.create(it, user, normToolDesc, "original")

                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)
                val favoriteDao = FavoriteAsyncDao(publicDao, aclDao)

                val userA = TestUsers.user.copy(username = "userA")
                val userB = TestUsers.user.copy(username = "userB")

                val aVersion1 = normAppDesc.withNameAndVersion("A", "v1")
                val aVersion2 = normAppDesc.withNameAndVersion("A", "v2")
                val bVersion1 = normAppDesc.withNameAndVersion("B", "v1")

                appDAO.create(it, user, aVersion1, "original")
                Thread.sleep(100) // Ensure different createdAt
                appDAO.create(it, user, aVersion2, "original")
                appDAO.create(it, user, bVersion1, "original")

                listOf(userA, userB).forEach { currentUser ->
                    run {
                        val favorites = favoriteDao.retrieveFavorites(it, currentUser, null, emptyList(), NormalizedPaginationRequest(10, 0))
                        assertEquals(0, favorites.itemsInTotal)
                    }

                    run {
                        favoriteDao.toggleFavorite(it, currentUser, null, emptyList(), aVersion1.metadata.name, aVersion1.metadata.version)
                        val favorites = favoriteDao.retrieveFavorites(it, currentUser, null, emptyList(), NormalizedPaginationRequest(10, 0))
                        assertEquals(1, favorites.itemsInTotal)
                    }

                    run {
                        favoriteDao.toggleFavorite(it, currentUser, null, emptyList(), aVersion2.metadata.name, aVersion2.metadata.version)
                        val favorites = favoriteDao.retrieveFavorites(it, currentUser, null, emptyList(), NormalizedPaginationRequest(10, 0))
                        assertEquals(2, favorites.itemsInTotal)
                    }
                }
            }
        }
    }

    @Test(expected = ApplicationException.BadApplication::class)
    fun `Favorite test - Not an app`() {
        runBlocking {
            db.withTransaction {
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()
                val favoriteDao = FavoriteAsyncDao(publicDao, aclDao)

                favoriteDao.toggleFavorite(it, user, null, emptyList(), "App1", "1.4")
            }
        }
    }

    @Test
    fun `create and delete tags`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()

                toolDao.create(it, user, normToolDesc, "original")

                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)
                val tagsDAO = ApplicationTagsAsyncDao()


                val appA = normAppDesc.withNameAndVersion("A", "1")

                appDAO.create(it, user, appA, "original")
                tagsDAO.createTags(it, user, appA.metadata.name, listOf("A1", "A2"))
                run {
                    val tag1 = tagsDAO.findTag(it, appA.metadata.name, "A1")
                    val tag2 = tagsDAO.findTag(it, appA.metadata.name, "A2")
                    val nottag = tagsDAO.findTag(it, appA.metadata.name, "A3")

                    assertNotNull(tag1)
                    assertNotNull(tag2)
                    assertNull(nottag)

                    val tags = tagsDAO.findTagsForApp(it, appA.metadata.name)
                    assertEquals(2, tags.size)
                    assertEquals("A1", tags.first().getField(TagTable.tag))
                    assertEquals("A2", tags.last().getField(TagTable.tag))
                }

                tagsDAO.createTags(it, user, appA.metadata.name, listOf("A3"))

                run {
                    val tag1 = tagsDAO.findTag(it, appA.metadata.name, "A1")
                    val tag2 = tagsDAO.findTag(it, appA.metadata.name, "A2")
                    val tag3 = tagsDAO.findTag(it, appA.metadata.name, "A3")

                    assertNotNull(tag1)
                    assertNotNull(tag2)
                    assertNotNull(tag3)
                }

                tagsDAO.deleteTags(it, user, appA.metadata.name, listOf("A1", "A3"))
                run {
                    val tag1 = tagsDAO.findTag(it, appA.metadata.name, "A1")
                    val tag2 = tagsDAO.findTag(it, appA.metadata.name, "A2")
                    val tag3 = tagsDAO.findTag(it, appA.metadata.name, "A3")

                    assertNull(tag1)
                    assertNotNull(tag2)
                    assertNull(tag3)
                }
            }
        }
    }

    @Test(expected = RPCException::class)
    fun `create tag for invalid app`() {
        runBlocking {
            db.withTransaction {
                val tagsDao = ApplicationTagsAsyncDao()
                tagsDao.createTags(it, user, "notAnApp", listOf("A3"))
            }
        }
    }

    @Test(expected = RPCException::class)
    fun `delete tag for invalid app`() {
        runBlocking {
            db.withTransaction {
                val tagsDao = ApplicationTagsAsyncDao()
                tagsDao.deleteTags(it, user, "notAnApp", listOf("A3"))
            }
        }
    }

    @Test
    fun `find latest by tool`() {
        val toolDao = ToolAsyncDao()
        val aclDao = AclAsyncDao()
        val publicDao = ApplicationPublicAsyncDao()
        val appDao = AppStoreAsyncDao(toolDao, aclDao, publicDao)
        val t1 = "tool1"
        val t2 = "tool2"
        val version = "1"

        runBlocking {
            db.withTransaction { session ->
                toolDao.create(session, TestUsers.admin, normToolDesc.copy(NameAndVersion(t1, version)), "original")
                toolDao.create(session, TestUsers.admin, normToolDesc.copy(NameAndVersion(t2, version)), "original")

                appDao.create(session, TestUsers.admin, normAppDesc.withNameAndVersion("a", "1").withTool(t1, version), "original")
                Thread.sleep(250)
                appDao.create(session, TestUsers.admin, normAppDesc.withNameAndVersion("a", "2").withTool(t1, version), "original")

                appDao.create(session, TestUsers.admin, normAppDesc.withNameAndVersion("b", "1").withTool(t2, version), "original")
            }
        }

        runBlocking {
            db.withTransaction { session ->
                val page = appDao.findLatestByTool(session, TestUsers.admin, null, emptyList(), t1, PaginationRequest().normalize())

                assertThatInstance(page) { it.itemsInTotal == 1 }
                assertThatInstance(page) { it.items.single().metadata.name == "a" }
                assertThatInstance(page) { it.items.single().metadata.version == "2" }
            }
        }

        runBlocking {
            db.withTransaction { session ->
                val page = appDao.findLatestByTool(session, TestUsers.admin, null, emptyList(), t2, PaginationRequest().normalize())

                assertThatInstance(page) { it.itemsInTotal == 1 }
                assertThatInstance(page) { it.items.single().metadata.name == "b" }
                assertThatInstance(page) { it.items.single().metadata.version == "1" }
            }
        }

        runBlocking {
            db.withTransaction { session ->
                val page = appDao.findLatestByTool(session, TestUsers.admin, null, emptyList(), "tool3", PaginationRequest().normalize())

                assertThatInstance(page) { it.itemsInTotal == 0 }
            }
        }
    }

    @Test
    fun `Find by supported file ext test CC only`() {
        runBlocking {
            val toolDao = ToolAsyncDao()
            val aclDao = AclAsyncDao()
            val publicDao = ApplicationPublicAsyncDao()
            val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)
            val favorite = FavoriteAsyncDao(publicDao, aclDao)
            db.withSession {
                toolDao.create(
                    it,
                    TestUsers.admin,
                    normToolDesc,
                    "original"
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(invocation = normAppDesc.invocation.copy(fileExtensions = listOf(".exe", ".cpp"))),
                    "original"
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(
                        metadata = normAppDesc.metadata.copy(version = "2.6"),
                        invocation = normAppDesc.invocation.copy(fileExtensions = listOf(".json", ".exe"))
                    ),
                    "original"
                )
            }
            db.withSession {
                favorite.toggleFavorite(
                    it,
                    TestUsers.admin,
                    null,
                    emptyList(),
                    normAppDesc.metadata.name,
                    normAppDesc.metadata.version
                )
                favorite.toggleFavorite(
                    it,
                    TestUsers.admin,
                    null,
                    emptyList(),
                    normAppDesc.metadata.name,
                    "2.6"
                )
            }
            db.withSession {
                val notFound = appDAO.findBySupportedFileExtension(
                        it,
                        TestUsers.admin,
                        null,
                        emptyList(),
                        setOf("kt")
                    )
                assertEquals(0, notFound.size)

                val found = appDAO.findBySupportedFileExtension(
                    it,
                    TestUsers.admin,
                    null,
                    emptyList(),
                    setOf(".cpp")
                )

                assertEquals(1, found.size)
                assertEquals(normAppDesc.metadata.version, found.first().metadata.version)

                val foundMulti = appDAO.findBySupportedFileExtension(
                    it,
                    TestUsers.admin,
                    null,
                    emptyList(),
                    setOf(".exe")
                )
                assertEquals(2, foundMulti.size)

                val foundDiffExt = appDAO.findBySupportedFileExtension(
                    it,
                    TestUsers.admin,
                    null,
                    emptyList(),
                    setOf(".json", ".cpp")
                )
                assertEquals(2, foundDiffExt.size)

                favorite.toggleFavorite(
                    it,
                    TestUsers.admin,
                    null,
                    emptyList(),
                    normAppDesc.metadata.name,
                    "2.6"
                )
            }
            db.withSession {
                val foundDiffExtAfterToggle = appDAO.findBySupportedFileExtension(
                    it,
                    TestUsers.admin,
                    null,
                    emptyList(),
                    setOf(".json", ".cpp")
                )
                assertEquals(1, foundDiffExtAfterToggle.size)
            }
        }
    }

    @Test
    fun `Prepare page for user - no user test`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()
                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)

                toolDao.create(
                    it,
                    TestUsers.admin,
                    normToolDesc,
                    "original"
                )
                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(invocation = normAppDesc.invocation.copy(fileExtensions = listOf("exe", "cpp"))),
                    "original")
                val page = appDAO.findLatestByTool(
                    it,
                    TestUsers.admin,
                    null,
                    emptyList(),
                    normToolDesc.info.name,
                    NormalizedPaginationRequest(10, 0)
                )

                val preparedPage = appDAO.preparePageForUser(it, null, page)

                assertEquals(1, preparedPage.itemsInTotal)
            }
        }
    }

    @Test
    fun `Create and Delete Logo test`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()
                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)
                val appLogoDao = ApplicationLogoAsyncDao(appDAO)

                runBlocking {
                    val logo = appLogoDao.fetchLogo(it, "name")
                    assertNull(logo)
                }

                toolDao.create(
                    it,
                    TestUsers.admin,
                    normToolDesc,
                    "original"
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(invocation = normAppDesc.invocation.copy(fileExtensions = listOf("exe", "cpp"))),
                    "original")

                runBlocking {
                    val logo = appLogoDao.fetchLogo(it, "name")
                    assertNull(logo)
                }

                appLogoDao.createLogo(it, TestUsers.admin, "name", ByteArray(1024))

                runBlocking {
                    val logo = appLogoDao.fetchLogo(it, "name")
                    assertNotNull(logo)
                }

                appLogoDao.clearLogo(it, TestUsers.admin, "name")

                runBlocking {
                    val logo = appLogoDao.fetchLogo(it, "name")
                    assertNull(logo)
                }
            }
        }
    }

    @Test
    fun `Create Logo - forbidden`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()
                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)
                val appLogoDao = ApplicationLogoAsyncDao(appDAO)

                toolDao.create(
                    it,
                    TestUsers.admin,
                    normToolDesc,
                    "original"
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(invocation = normAppDesc.invocation.copy(fileExtensions = listOf("exe", "cpp"))),
                    "original")

                try {
                    appLogoDao.createLogo(it, TestUsers.user, "name", ByteArray(1024))
                } catch (ex: RPCException) {
                    if (ex.httpStatusCode.value != 403) {
                        assertTrue(false)
                    } else {
                        assertTrue(true)
                    }
                }
            }
        }
    }

    @Test
    fun `Delete Logo - forbidden`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()
                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)
                val appLogoDao = ApplicationLogoAsyncDao(appDAO)

                toolDao.create(
                    it,
                    TestUsers.admin,
                    normToolDesc,
                    "original"
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(invocation = normAppDesc.invocation.copy(fileExtensions = listOf("exe", "cpp"))),
                    "original"
                )

                try {
                    appLogoDao.clearLogo(it, TestUsers.user, "name")
                } catch (ex: RPCException) {
                    if (ex.httpStatusCode.value != 403) {
                        assertTrue(false)
                    } else {
                        assertTrue(true)
                    }
                }
            }
        }
    }

    @Test
    fun `Create Logo - NotFound`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()
                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)
                val appLogoDao = ApplicationLogoAsyncDao(appDAO)

                try {
                    appLogoDao.createLogo(it, TestUsers.user, "name", ByteArray(1024))
                } catch (ex: RPCException) {
                    if (ex.httpStatusCode.value != 404) {
                        assertTrue(false)
                    } else {
                        assertTrue(true)
                    }
                }
            }
        }
    }

    @Test
    fun `Delete Logo - NotFound`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()
                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)
                val appLogoDao = ApplicationLogoAsyncDao(appDAO)

                try {
                    appLogoDao.clearLogo(it, TestUsers.user, "name")
                } catch (ex: RPCException) {
                    if (ex.httpStatusCode.value != 404) {
                        assertTrue(false)
                    } else {
                        assertTrue(true)
                    }
                }
            }
        }
    }

    @Test
    fun `Find all by ID test`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()
                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)

                toolDao.create(
                    it,
                    TestUsers.admin,
                    normToolDesc,
                    "original"
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.withNameAndVersion("anothername", "1.1"),
                    "original"
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc,
                    "original"
                )
             
                appDAO.getAllApps(it, TestUsers.admin).forEach { app -> println(app.getField(ApplicationTable.idName)) }
              
                val ids1 = appDAO.findAllByID(
                    it,
                    TestUsers.admin,
                    null,
                    emptyList(),
                    listOf(EmbeddedNameAndVersion("anothername", "1.1")),
                    NormalizedPaginationRequest(10, 0)
                )

                assertEquals(1, ids1.size)

                val ids2 = appDAO.findAllByID(
                    it,
                    TestUsers.admin,
                    null,
                    emptyList(),
                    listOf(EmbeddedNameAndVersion("name", "2.2"), EmbeddedNameAndVersion("anothername", "1.1")),
                    NormalizedPaginationRequest(10, 0)
                )

                assertEquals(2, ids2.size)

                val ids3 = appDAO.findAllByID(
                    it,
                    TestUsers.admin,
                    null,
                    emptyList(),
                    listOf(EmbeddedNameAndVersion("name", "WRONG"), EmbeddedNameAndVersion("anothername", "1.1")),
                    NormalizedPaginationRequest(10, 0)
                )

                assertEquals(1, ids3.size)
            }
        }
    }

    @Test
    fun `find all by IDs - no ids given`() {
        runBlocking {
            db.withTransaction {
                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val publicDao = ApplicationPublicAsyncDao()
                val appDAO = AppStoreAsyncDao(toolDao, aclDao, publicDao)

                toolDao.create(
                    it,
                    TestUsers.admin,
                    normToolDesc,
                    "original"
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.withNameAndVersion("anotherName", "1.1"),
                    "original"
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc,
                    "original"
                )

                val results = appDAO.findAllByID(
                    it,
                    TestUsers.admin,
                    null,
                    emptyList(),
                    emptyList(),
                    NormalizedPaginationRequest(10, 0)
                )

                assertTrue(results.isEmpty())
            }
        }
    }
}
