package dk.sdu.cloud.project.services

import dk.sdu.cloud.auth.api.LookupUsersResponse
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.auth.api.UserLookup
import dk.sdu.cloud.contact.book.api.ContactBookDescriptions
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.notification.api.FindByNotificationId
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectEvents
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.ProjectServiceDescription
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.EventProducerMock
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertThatProperty
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.mockk
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Ignore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectDaoTest {
    companion object {
        lateinit var db: AsyncDBSessionFactory
        lateinit var embDb: EmbeddedPostgres

        @BeforeClass
        @JvmStatic
        fun setup() {
            val (db, embDb) = TestDB.from(ProjectServiceDescription)
            this.db = db
            this.embDb = embDb
        }

        @AfterClass
        @JvmStatic
        fun close() {
            runBlocking { db.close() }
            embDb.close()
        }
    }

    fun truncateProjectDB() {
        runBlocking {
            db.withSession { session ->
                session.sendPreparedStatement(
                    """
                        TRUNCATE 
                            cooldowns,
                            group_members,
                            groups,
                            invites,
                            project_favorite,
                            project_members,
                            project_membership_verification,
                            projects
                    """
                )
            }
        }
    }

    @BeforeTest
    fun before() {
        truncateProjectDB()
    }

    @AfterTest
    fun after() {
        truncateProjectDB()
    }

    @Test
    fun `get PI and Admin test`() {
        val client = ClientMock.authenticatedClient
        val micro = initializeMicro()
        val eventStreamService = micro.eventStreamService
        val eventProducer = eventStreamService.createProducer(ProjectEvents.events)
        val projectService = ProjectService(client, eventProducer)

        ClientMock.mockCallSuccess(
            UserDescriptions.lookupUsers,
            LookupUsersResponse(
                mapOf(
                    TestUsers.admin.username to
                            UserLookup(TestUsers.admin.username, TestUsers.admin.uid, TestUsers.admin.role),
                    TestUsers.admin2.username to
                            UserLookup(TestUsers.admin2.username, TestUsers.admin2.uid, TestUsers.admin2.role)
                )
            )
        )

        ClientMock.mockCallSuccess(
            ContactBookDescriptions.insert,
            Unit
        )

        ClientMock.mockCallSuccess(
            NotificationDescriptions.create,
            FindByNotificationId(1L)
        )
        runBlocking {
            val id = projectService.create(db, TestUsers.admin, "Test Project", null)
            val id2 = projectService.create(db, TestUsers.admin2, "Another Test Project", null)
            val pi = projectService.getPIOfProject(db, id)
            assertEquals(TestUsers.admin.username, pi)

            projectService.inviteMember(db, TestUsers.admin2.username, id2, setOf(TestUsers.admin.username))
            val (pi2, admins) = projectService.getPIAndAdminsOfProject(db, id2)
            assertEquals(TestUsers.admin2.username, pi2)
            assertTrue(admins.isEmpty())
            projectService.acceptInvite(db, TestUsers.admin.username, id2)
            projectService.changeRoleOfMember(
                db,
                TestUsers.admin2.username,
                id2,
                TestUsers.admin.username,
                ProjectRole.ADMIN
            )

            val (pi2AfterUpdate, adminsAfterUpdate) = projectService.getPIAndAdminsOfProject(db, id2)
            assertEquals(TestUsers.admin2.username, pi2AfterUpdate)
            assertTrue(adminsAfterUpdate.isNotEmpty())
            assertEquals(1, adminsAfterUpdate.size)
            assertEquals(TestUsers.admin.username, adminsAfterUpdate.first())
        }
    }

    /*private lateinit var micro: Micro
    private val projectDao = ProjectDao()
    private lateinit var db: DBSessionFactory<AsyncDBConnection>

    @BeforeTest
    fun initializeTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)
        db = TODO()
    }

    @Test
    fun `create and list projects`(): Unit = runBlocking {
        val principalInvestigator = "guy1"
        db.withTransaction { session ->
            val id = "id"
            val title = "title"
            projectDao.create(session, id, title, principalInvestigator)
            val list = projectDao.listProjectsForUser(session, PaginationRequest().normalize(), principalInvestigator)
            assertThatProperty(
                list,
                { it.items },
                matcher = {
                    it.single().projectId == id && it.single().title == title && it.single().whoami.role == ProjectRole.PI
                }
            )
        }
    }*/
}
