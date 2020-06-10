package dk.sdu.cloud.app.store.services

import app.store.services.ApplicationLogoAsyncDAO
import app.store.services.ApplicationPublicAsyncDAO
import app.store.services.ApplicationSearchAsyncDAO
import app.store.services.ApplicationTagsAsyncDAO
import app.store.services.FavoriteAsyncDAO
import dk.sdu.cloud.app.store.api.AppStoreServiceDescription
import dk.sdu.cloud.app.store.api.ApplicationInvocationDescription
import dk.sdu.cloud.app.store.api.ApplicationMetadata
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.services.acl.AclHibernateDao
import dk.sdu.cloud.app.store.util.normAppDesc
import dk.sdu.cloud.app.store.util.normToolDesc
import dk.sdu.cloud.app.store.util.truncate
import dk.sdu.cloud.app.store.util.withNameAndVersion
import dk.sdu.cloud.app.store.util.withNameAndVersionAndTitle
import dk.sdu.cloud.app.store.util.withTool
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.initializeMicro
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.hibernate.exception.GenericJDBCException
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
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDAO = ApplicationPublicAsyncDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDAO)
                appDAO.create(it, user, normAppDesc)

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

                appDAO.updateDescription(it, user, "name", "2.2", "new description")

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
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)
                appDAO.create(it, user, normAppDesc)

                run {
                    // Load from specific version
                    val loadedApp = appDAO.findByNameAndVersionForUser(it, user, null, emptyList(), "name", "2.2")
                    assertEquals("app description", loadedApp.metadata.description)
                }

                appDAO.updateDescription(it, user, "name", "2.2", "new description")

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
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()
                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)
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

                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)
                appDAO.create(it, user, version1)
                Thread.sleep(1000) // Wait a bit to make sure they get different createdAt
                appDAO.create(it, user, version2)

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
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()

                toolDAO.create(it, user, normToolDesc)

                val applicationA = normAppDesc.withNameAndVersionAndTitle("name1", "1", "AAA")
                val applicationB = normAppDesc.withNameAndVersionAndTitle("name2", "1", "BBB")

                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)
                appDAO.create(it, user, applicationA)
                appDAO.create(it, user, applicationB)

                val appSearchDAO = ApplicationSearchAsyncDAO(appDAO)
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
                appDAO.create(it, user, applicationANewVersion)
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
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)
                appDAO.create(it, user, normAppDesc)
                appDAO.create(it, user, normAppDesc)
            }
        }
    }

    @Test(expected = ApplicationException.NotAllowed::class)
    fun `Create - Not Allowed - test`() {
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)
                appDAO.create(it, user, normAppDesc)
                appDAO.create(it, TestUsers.user5, normAppDesc)
            }
        }
    }

    @Test(expected = ApplicationException.BadToolReference::class)
    fun `Create - bad tool - test`() {
        runBlocking {
            db.withTransaction {
                val appDAO = AppStoreAsyncDAO(ToolHibernateDAO(), AclHibernateDao(), ApplicationPublicAsyncDAO())
                appDAO.create(it, user, normAppDesc)
            }
        }
    }

    @Test(expected = ApplicationException.NotFound::class)
    fun `Find by name - not found - test`() {
        runBlocking {
            db.withTransaction {
                val appDAO = AppStoreAsyncDAO(ToolHibernateDAO(), AclHibernateDao(), ApplicationPublicAsyncDAO())
                appDAO.findByNameAndVersion(it, user, null, emptyList(), "name", "version")
            }
        }
    }

    //@Ignore // Code only works in postgres
    @Test
    fun `tagSearch test`() {
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()
                toolDAO.create(it, user, normToolDesc)

                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)
                val tagsDAO = ApplicationTagsAsyncDAO()
                val searchDAO = ApplicationSearchAsyncDAO(appDAO)
                val commonTag = "common"
                val appA = normAppDesc.withNameAndVersionAndTitle("A", "1", "Atitle")
                val appB = normAppDesc.withNameAndVersionAndTitle("B", "1", "Btitle")

                appDAO.create(it, user, appA)
                appDAO.create(it, user, appB)

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
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()
                toolDAO.create(it, user, normToolDesc)

                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)
                val favoriteDao = FavoriteAsyncDAO(publicDao, aclDao)

                val userA = TestUsers.user.copy(username = "userA")
                val userB = TestUsers.user.copy(username = "userB")

                val aVersion1 = normAppDesc.withNameAndVersion("A", "v1")
                val aVersion2 = normAppDesc.withNameAndVersion("A", "v2")
                val bVersion1 = normAppDesc.withNameAndVersion("B", "v1")

                appDAO.create(it, user, aVersion1)
                Thread.sleep(100) // Ensure different createdAt
                appDAO.create(it, user, aVersion2)
                appDAO.create(it, user, bVersion1)

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
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()
                val favoriteDao = FavoriteAsyncDAO(publicDao, aclDao)

                favoriteDao.toggleFavorite(it, user, null, emptyList(), "App1", "1.4")
            }
        }
    }

    @Test
    fun `create and delete tags`() {
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()

                toolDAO.create(it, user, normToolDesc)

                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)
                val tagsDAO = ApplicationTagsAsyncDAO()


                val appA = normAppDesc.withNameAndVersion("A", "1")

                appDAO.create(it, user, appA)
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
                val tagsDao = ApplicationTagsAsyncDAO()
                tagsDao.createTags(it, user, "notAnApp", listOf("A3"))
            }
        }
    }

    @Test(expected = RPCException::class)
    fun `delete tag for invalid app`() {
        runBlocking {
            db.withTransaction {
                val tagsDao = ApplicationTagsAsyncDAO()
                tagsDao.deleteTags(it, user, "notAnApp", listOf("A3"))
            }
        }
    }

    @Test
    fun `find latest by tool`() {
        val toolDao = ToolHibernateDAO()
        val aclDao = AclHibernateDao()
        val publicDao = ApplicationPublicAsyncDAO()
        val appDao = AppStoreAsyncDAO(toolDao, aclDao, publicDao)
        val t1 = "tool1"
        val t2 = "tool2"
        val version = "1"

        runBlocking {
            db.withTransaction { session ->
                toolDao.create(session, TestUsers.admin, normToolDesc.copy(NameAndVersion(t1, version)))
                toolDao.create(session, TestUsers.admin, normToolDesc.copy(NameAndVersion(t2, version)))

                appDao.create(session, TestUsers.admin, normAppDesc.withNameAndVersion("a", "1").withTool(t1, version))
                Thread.sleep(250)
                appDao.create(session, TestUsers.admin, normAppDesc.withNameAndVersion("a", "2").withTool(t1, version))

                appDao.create(session, TestUsers.admin, normAppDesc.withNameAndVersion("b", "1").withTool(t2, version))
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
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()
                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)

                toolDAO.create(
                    it,
                    TestUsers.admin,
                    normToolDesc
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(invocation = normAppDesc.invocation.copy(fileExtensions = listOf("exe", "cpp")))
                )

                try {
                    appDAO.findBySupportedFileExtension(
                        it,
                        TestUsers.admin,
                        null,
                        emptyList(),
                        setOf("kt")
                    )
                } catch (ex: Exception) {
                    //Do nothing
                }
            }
        }
    }

    @Test
    fun `Prepare page for user - no user test`() {
        runBlocking {
            db.withTransaction {
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()
                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)

                toolDAO.create(
                    it,
                    TestUsers.admin,
                    normToolDesc
                )
                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(invocation = normAppDesc.invocation.copy(fileExtensions = listOf("exe", "cpp")))
                )
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
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()
                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)
                val appLogoDao = ApplicationLogoAsyncDAO(appDAO)

                runBlocking {
                    val logo = appLogoDao.fetchLogo(it, "name")
                    assertNull(logo)
                }

                toolDAO.create(
                    it,
                    TestUsers.admin,
                    normToolDesc
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(invocation = normAppDesc.invocation.copy(fileExtensions = listOf("exe", "cpp")))
                )

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
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()
                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)
                val appLogoDao = ApplicationLogoAsyncDAO(appDAO)

                toolDAO.create(
                    it,
                    TestUsers.admin,
                    normToolDesc
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(invocation = normAppDesc.invocation.copy(fileExtensions = listOf("exe", "cpp")))
                )

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
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()
                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)
                val appLogoDao = ApplicationLogoAsyncDAO(appDAO)

                toolDAO.create(
                    it,
                    TestUsers.admin,
                    normToolDesc
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.copy(invocation = normAppDesc.invocation.copy(fileExtensions = listOf("exe", "cpp")))
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
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()
                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)
                val appLogoDao = ApplicationLogoAsyncDAO(appDAO)

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
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()
                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)
                val appLogoDao = ApplicationLogoAsyncDAO(appDAO)

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
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()
                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)

                toolDAO.create(
                    it,
                    TestUsers.admin,
                    normToolDesc
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.withNameAndVersion("anothername", "1.1")
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc
                )
println(1)
                appDAO.getAllApps(it, TestUsers.admin).forEach { app -> println(app.getField(ApplicationTable.idName)) }
println(11)
                val ids1 = appDAO.findAllByID(
                    it,
                    TestUsers.admin,
                    null,
                    emptyList(),
                    listOf(EmbeddedNameAndVersion("anothername", "1.1")),
                    NormalizedPaginationRequest(10, 0)
                )

                assertEquals(1, ids1.size)
                println(2)

                val ids2 = appDAO.findAllByID(
                    it,
                    TestUsers.admin,
                    null,
                    emptyList(),
                    listOf(EmbeddedNameAndVersion("name", "2.2"), EmbeddedNameAndVersion("anothername", "1.1")),
                    NormalizedPaginationRequest(10, 0)
                )

                assertEquals(2, ids2.size)
                println(3)

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
                val toolDAO = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val publicDao = ApplicationPublicAsyncDAO()
                val appDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDao)

                toolDAO.create(
                    it,
                    TestUsers.admin,
                    normToolDesc
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc.withNameAndVersion("anotherName", "1.1")
                )

                appDAO.create(
                    it,
                    TestUsers.admin,
                    normAppDesc
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
