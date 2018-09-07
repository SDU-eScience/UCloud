package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.utils.withDatabase
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.withTransaction
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals

class ApplicationHibernateDaoTest{

    private val user = "user"
    private val normAppDesc = NormalizedApplicationDescription(
        NameAndVersion("name", "2.2"),
        NameAndVersion("name", "2.2"),
        listOf("Authors"),
        "title",
        "app description",
        mockk(relaxed = true),
        mockk(relaxed = true),
        listOf("glob")
    )

    private val normToolDesc = NormalizedToolDescription(
        NameAndVersion("name", "2.2"),
        "container",
        2,
        2,
        SimpleDuration(1,0,0),
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

                val hits = appDAO.findAllByName(it, user, "name", NormalizedPaginationRequest(10,0))

                val result = hits.items.first().description.description
                assertEquals("app description", result)
                assertEquals(1, hits.itemsInTotal)
                val result2 = appDAO.findByNameAndVersion(it, user, "name", "2.2")
                assertEquals("app description", result2.description.description)

                appDAO.updateDescription(it, user, "name", "2.2", "new description")

                val result3 = appDAO.findByNameAndVersion(it, user, "name", "2.2")
                assertEquals("new description", result3.description.description)
                assertEquals("Authors", result3.description.authors.first())

                appDAO.updateDescription(it, user, "name", "2.2", null, listOf("New Authors"))
                val result4 = appDAO.findByNameAndVersion(it, user, "name", "2.2")
                assertEquals("new description", result4.description.description)
                assertEquals("New Authors", result4.description.authors.first())

                //appDAO.listLatestVersion()

            }
        }
    }


    @Test (expected = ApplicationException.AlreadyExists::class)
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

    @Test (expected = ApplicationException.NotAllowed::class)
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

    @Test (expected = ApplicationException.BadToolReference::class)
    fun `Create - bad tool - test`() {
        withDatabase { db ->
            db.withTransaction {

                val appDAO = ApplicationHibernateDAO(ToolHibernateDAO())
                appDAO.create(it, user, normAppDesc)
            }
        }
    }

    @Test (expected = ApplicationException.NotFound::class)
    fun `Find by name - not found - test`() {
        withDatabase { db ->
            db.withTransaction {

                val appDAO = ApplicationHibernateDAO(ToolHibernateDAO())
                appDAO.findByNameAndVersion(it, user, "name", "version")
            }
        }
    }
}