package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.utils.withDatabase
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.withTransaction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolHibernateDaoTest{

    private val user = "user1"
    private val normToolDesc = NormalizedToolDescription(
        NameAndVersion("name", "1.2"),
        "container",
        2,
        2,
        SimpleDuration(1,0,0),
        listOf(""),
        listOf("author"),
        "title",
        "description",
        ToolBackend.UDOCKER
    )

    private val normToolDesc2 = NormalizedToolDescription(
        NameAndVersion("name", "2.2"),
        "container",
        2,
        2,
        SimpleDuration(1,0,0),
        listOf(""),
        listOf("author"),
        "title",
        "description",
        ToolBackend.UDOCKER
    )

    private val normToolDesc3 = NormalizedToolDescription(
        NameAndVersion("anothername", "5.2"),
        "container",
        2,
        2,
        SimpleDuration(1,0,0),
        listOf(""),
        listOf("author"),
        "title",
        "description",
        ToolBackend.UDOCKER
    )

    private val normToolDesc4 = NormalizedToolDescription(
        NameAndVersion("anothername", "8.2"),
        "container",
        2,
        2,
        SimpleDuration(1,0,0),
        listOf(""),
        listOf("author"),
        "title",
        "description",
        ToolBackend.UDOCKER
    )

    @Test
    fun `find all by name test - no results`() {
        withDatabase { db ->
            val tool = ToolHibernateDAO()
            val results = tool.findAllByName(db.openSession(), "user", "name", NormalizedPaginationRequest(10,0))
            assertEquals(0, results.itemsInTotal)
        }
    }

    @Test (expected = ToolException.NotFound::class)
    fun `find all by name and version test - no results`() {
        withDatabase { db ->
            val tool = ToolHibernateDAO()
            tool.findByNameAndVersion(db.openSession(), "user", "name", "2.2")
        }
    }

    @Test
    fun `find latest Version`() {
        withDatabase { db ->
            db.withTransaction {
                val tool = ToolHibernateDAO()

                tool.create(it, user, normToolDesc)
                Thread.sleep(1000)
                tool.create(it, user, normToolDesc2)
                Thread.sleep(1000)
                tool.create(it, user, normToolDesc3)
                Thread.sleep(1000)
                tool.create(it, user, normToolDesc4)
                Thread.sleep(1000)

                val allListed = tool.listLatestVersion(it, "user", NormalizedPaginationRequest(10, 0))
                var previous = ""
                allListed.items.forEach {
                    if (it.description.info.name < previous)
                        assert(false)
                    previous = it.description.info.name
                }

                assertEquals(2, allListed.itemsInTotal)
            }
        }
    }

    @Test
    fun `create Test`() {
        withDatabase { db ->
            db.withTransaction {
                val tool = ToolHibernateDAO()
                tool.create(it, user, normToolDesc)
                val result = tool.findAllByName(it, user, normToolDesc.info.name, NormalizedPaginationRequest(10,0))
                assertEquals(1, result.itemsInTotal)
            }
        }
    }

    @Test (expected = ToolException.AlreadyExists::class)
    fun `create Test - duplicate`() {
        withDatabase { db ->
            db.withTransaction {
                val tool = ToolHibernateDAO()
                tool.create(it, user, normToolDesc)
                tool.create(it, user, normToolDesc)
            }
        }
    }

    @Test (expected = ToolException.NotAllowed::class)
    fun `create Test - not the owner`() {
        withDatabase { db ->
            db.withTransaction {
                val tool = ToolHibernateDAO()
                tool.create(it, user, normToolDesc)
                tool.create(it, "notTheUser", normToolDesc)
            }
        }
    }

    @Test
    fun `update description Test`() {
        withDatabase { db ->
            db.withTransaction {
                val tool = ToolHibernateDAO()
                tool.create(it, user, normToolDesc)
                val hits = tool.findAllByName(it, user, normToolDesc.info.name, NormalizedPaginationRequest(10,0))
                val description = hits.items.first().description.description
                assertEquals(normToolDesc.description, description)
                tool.updateDescription(
                    it,
                    user,
                    normToolDesc.info.name,
                    normToolDesc.info.version,
                    "This is a new description"
                )
                val newHits = tool.findAllByName(it, user, normToolDesc.info.name, NormalizedPaginationRequest(10,0))
                val newDescription = newHits.items.first().description.description
                assertEquals("This is a new description", newDescription)
            }
        }
    }

    @Test
    fun `update author Test`() {
        withDatabase { db ->
            db.withTransaction {
                val tool = ToolHibernateDAO()
                tool.create(it, user, normToolDesc)
                val hits = tool.findAllByName(it, user, normToolDesc.info.name, NormalizedPaginationRequest(10,0))
                assertFalse(hits.items.first().description.authors.contains("new Author"))
                tool.updateDescription(
                    it,
                    user,
                    normToolDesc.info.name,
                    normToolDesc.info.version,
                    null,
                    listOf("new Author")
                )
                val newHits = tool.findAllByName(it, user, normToolDesc.info.name, NormalizedPaginationRequest(10,0))
                val newAuthorList = newHits.items.first().description.authors
                assertTrue(newAuthorList.contains("new Author"))
            }
        }
    }

    @Test (expected = ToolException.NotAllowed::class)
    fun `update description Test - not same user`() {
        withDatabase { db ->
            db.withTransaction {
                val tool = ToolHibernateDAO()
                tool.create(it, user, normToolDesc)
                tool.updateDescription(
                    it,
                    "NotTheUserWhoCreated",
                    normToolDesc.info.name,
                    normToolDesc.info.version,
                    "This is a new description",
                    listOf("new Author")
                )
            }
        }
    }

    @Test (expected = ToolException.NotFound::class)
    fun `update description Test - description not found`() {
        withDatabase { db ->
            db.withTransaction {
                val tool = ToolHibernateDAO()
                tool.updateDescription(
                    it,
                    "NotTheUserWhoCreated",
                    normToolDesc.info.name,
                    normToolDesc.info.version,
                    "This is a new description",
                    listOf("new Author")
                )
            }
        }
    }
}