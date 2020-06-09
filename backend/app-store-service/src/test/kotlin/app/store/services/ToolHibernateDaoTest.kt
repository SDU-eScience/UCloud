package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.app.store.api.AppStoreServiceDescription
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.NormalizedToolDescription
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.ToolBackend
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.withDatabase
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.Ignore
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolHibernateDaoTest {

    private val user = TestUsers.user.copy(username = "user1")
    private val normToolDesc = NormalizedToolDescription(
        NameAndVersion("name", "1.2"),
        "container",
        2,
        2,
        SimpleDuration(1, 0, 0),
        listOf(""),
        listOf("author"),
        "title",
        "description",
        ToolBackend.DOCKER,
        "MIT"
    )

    private val normToolDesc2 = NormalizedToolDescription(
        NameAndVersion("name", "2.2"),
        "container",
        2,
        2,
        SimpleDuration(1, 0, 0),
        listOf(""),
        listOf("author"),
        "title",
        "description",
        ToolBackend.DOCKER,
        "MIT"
    )

    private val normToolDesc3 = NormalizedToolDescription(
        NameAndVersion("anothername", "5.2"),
        "container",
        2,
        2,
        SimpleDuration(1, 0, 0),
        listOf(""),
        listOf("author"),
        "title",
        "description",
        ToolBackend.DOCKER,
        "GNU"
    )

    private val normToolDesc4 = NormalizedToolDescription(
        NameAndVersion("anothername", "8.2"),
        "container",
        2,
        2,
        SimpleDuration(1, 0, 0),
        listOf(""),
        listOf("author"),
        "title",
        "description",
        ToolBackend.DOCKER,
        "GNU"
    )

    private lateinit var embDB: EmbeddedPostgres
    private lateinit var db: AsyncDBSessionFactory

    @BeforeClass
    fun before() {
        val (db,embDB) = TestDB.from(AppStoreServiceDescription)
        this.db = db
        this.embDB = embDB
    }

    @AfterClass
    fun after() {
        runBlocking {
            db.close()
        }
        embDB.close()
    }

    @AfterTest
    fun afterEach() {
        runBlocking {
            db.withSession { session ->
                session.sendPreparedStatement(
                    {},
                    """
                        TRUNCATE 
                            applications, 
                            application_logos, 
                            application_tags, 
                            favorited_by, 
                            permissions,
                            tool_logos,
                            tools
                        RESTART IDENTITY CASCADE 
                    """.trimIndent()
                )
            }
        }
    }

    @Test
    fun `find all by name test - no results`() {
        runBlocking {
            db.withSession { session ->
                val tool = ToolHibernateDAO()
                val results =
                    tool.findAllByName(session, TestUsers.user, "name", NormalizedPaginationRequest(10, 0))
                assertEquals(0, results.itemsInTotal)
            }
        }
    }

    @Test(expected = ToolException.NotFound::class)
    fun `find all by name and version test - no results`() {
        runBlocking {
            db.withSession { session ->
                val tool = ToolHibernateDAO()
                tool.findByNameAndVersion(db.openSession(), TestUsers.user, "name", "2.2")
            }
        }
    }

    @Test
    fun `find latest Version`() {
        runBlocking {
            db.withSession { session ->
                val tool = ToolHibernateDAO()

                tool.create(session, user, normToolDesc)
                Thread.sleep(1000)
                tool.create(session, user, normToolDesc2)
                Thread.sleep(1000)
                tool.create(session, user, normToolDesc3)
                Thread.sleep(1000)
                tool.create(session, user, normToolDesc4)
                Thread.sleep(1000)

                val allListed = tool.listLatestVersion(session, user, NormalizedPaginationRequest(10, 0))
                var previous = ""
                allListed.items.forEach {
                    assert(it.description.info.name >= previous)
                    previous = it.description.info.name
                }

                assertEquals(2, allListed.itemsInTotal)
            }
        }
    }

    @Test
    fun `create Test`() {
        runBlocking {
            db.withSession {
                val tool = ToolHibernateDAO()
                tool.create(it, user, normToolDesc)
                val result =
                    tool.findAllByName(it, user, normToolDesc.info.name, NormalizedPaginationRequest(10, 0))
                assertEquals(1, result.itemsInTotal)
            }
        }
    }

    @Test(expected = ToolException.AlreadyExists::class)
    fun `create Test - duplicate`() {
        runBlocking {
            db.withSession {
                val tool = ToolHibernateDAO()
                tool.create(it, user, normToolDesc)
                tool.create(it, user, normToolDesc)
            }
        }
    }

    @Test(expected = ToolException.NotAllowed::class)
    fun `create Test - not the owner`() {
        runBlocking {
            db.withSession {
                val tool = ToolHibernateDAO()
                tool.create(it, user, normToolDesc)
                tool.create(it, TestUsers.user5, normToolDesc)
            }
        }
    }

    @Test
    fun `update description Test`() {
        runBlocking {
            db.withSession {
                val tool = ToolHibernateDAO()
                tool.create(it, user, normToolDesc)
                val hits = tool.findAllByName(it, user, normToolDesc.info.name, NormalizedPaginationRequest(10, 0))
                val description = hits.items.first().description.description
                assertEquals(normToolDesc.description, description)
                tool.updateDescription(
                    it,
                    user,
                    normToolDesc.info.name,
                    normToolDesc.info.version,
                    "This is a new description"
                )
                val newHits =
                    tool.findAllByName(it, user, normToolDesc.info.name, NormalizedPaginationRequest(10, 0))
                val newDescription = newHits.items.first().description.description
                assertEquals("This is a new description", newDescription)
            }
        }
    }

    @Test
    fun `update author Test`() {
        runBlocking {
            db.withSession {
                val tool = ToolHibernateDAO()
                tool.create(it, user, normToolDesc)
                val hits = tool.findAllByName(it, user, normToolDesc.info.name, NormalizedPaginationRequest(10, 0))
                assertFalse(hits.items.first().description.authors.contains("new Author"))
                tool.updateDescription(
                    it,
                    user,
                    normToolDesc.info.name,
                    normToolDesc.info.version,
                    null,
                    listOf("new Author")
                )
                val newHits =
                    tool.findAllByName(it, user, normToolDesc.info.name, NormalizedPaginationRequest(10, 0))
                val newAuthorList = newHits.items.first().description.authors
                assertTrue(newAuthorList.contains("new Author"))
            }
        }
    }

    @Test(expected = ToolException.NotAllowed::class)
    fun `update description Test - not same user`() {
        runBlocking {
            db.withSession {
                val tool = ToolHibernateDAO()
                tool.create(it, user, normToolDesc)
                tool.updateDescription(
                    it,
                    TestUsers.user5,
                    normToolDesc.info.name,
                    normToolDesc.info.version,
                    "This is a new description",
                    listOf("new Author")
                )
            }
        }
    }

    @Test(expected = ToolException.NotFound::class)
    fun `update description Test - description not found`() {
        runBlocking {
            db.withSession {
                val tool = ToolHibernateDAO()
                tool.updateDescription(
                    it,
                    TestUsers.user5,
                    normToolDesc.info.name,
                    normToolDesc.info.version,
                    "This is a new description",
                    listOf("new Author")
                )
            }
        }
    }
}
