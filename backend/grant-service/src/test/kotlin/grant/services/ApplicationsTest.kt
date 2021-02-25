package dk.sdu.cloud.grant.services

import dk.sdu.cloud.accounting.api.Wallets
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.RetrieveQuotaResponse
import dk.sdu.cloud.grant.api.ApplicationStatus
import dk.sdu.cloud.grant.api.GrantRecipient
import dk.sdu.cloud.grant.api.GrantServiceDescription
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.toActor
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.joda.time.LocalDateTime
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class ApplicationsTest {

    companion object {
        lateinit var db: AsyncDBSessionFactory
        lateinit var embDb: EmbeddedPostgres

        @BeforeClass
        @JvmStatic
        fun setup() {
            val (db, embDb) = TestDB.from(GrantServiceDescription)
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

    fun truncateGrantDB() {
        runBlocking {
            db.withSession { session ->
                session.sendPreparedStatement(
                    """
                        TRUNCATE 
                            allow_applications_from,
                            applications,
                            automatic_approval_limits,
                            automatic_approval_users,
                            comments,
                            descriptions,
                            exclude_applications_from,
                            gift_resources,
                            gifts,
                            gifts_claimed,
                            gifts_user_criteria,
                            is_enabled,
                            logos,
                            requested_resources,
                            templates
                    """
                )
            }
        }
    }

    @BeforeTest
    fun before() {
        truncateGrantDB()
    }

    @AfterTest
    fun after() {
        truncateGrantDB()
    }

    private fun createApplicationService(
        client: AuthenticatedClient,
        projectCacheMock: ProjectCache? = null,
        settingsServiceMock: SettingsService? = null,
        notificationServiceMock: NotificationService? = null
    ): ApplicationService {
        val projectCache = projectCacheMock ?: ProjectCache(client)
        val settingsService = settingsServiceMock ?: SettingsService(projectCache)
        val notificationService = notificationServiceMock ?: NotificationService(projectCache, client)
        return ApplicationService(projectCache, settingsService, notificationService, client)
    }

    private fun createApplication(
        dbContext: DBContext,
        requestedBy: String,
        status: ApplicationStatus = ApplicationStatus.IN_PROGRESS,
        statusChangedBy: String? = null
    ) {
        val resourceOwner = "resourceId"
        runBlocking {
            dbContext.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("status", status.name)
                            setParameter("resourceOwnedBy", resourceOwner)
                            setParameter("requestedBy", requestedBy)
                            setParameter("grantRecipient", "title Of Project")
                            setParameter("grantRecipientType", GrantRecipient.NEW_PROJECT_TYPE)
                            setParameter("document", "document")
                            setParameter("createdAt", LocalDateTime.now())
                            setParameter("updatedAt", LocalDateTime.now())
                            setParameter("id", 1L)
                            setParameter("statusChangedBy", statusChangedBy)
                        },
                        """
                            INSERT INTO "grant".applications VALUES 
                            (
                            :status,
                            :resourceOwnedBy,
                            :requestedBy, 
                            :grantRecipient, 
                            :grantRecipientType, 
                            :document, 
                            :createdAt, 
                            :updatedAt, 
                            :id, 
                            :statusChangedBy) 
                        """
                    )
            }
        }
    }

    @Test
    fun testStatusChange() {
        val client = ClientMock.authenticatedClient
        val projectCacheMock = mockk<ProjectCache>()
        val applicationService = createApplicationService(client, projectCacheMock = projectCacheMock)
        val user = TestUsers.user
        createApplication(db, user.username)

        every { projectCacheMock.ancestors } answers {
            val cache = SimpleCache<String, List<Project>> { projectId: String ->
                listOf(Project("test", "title", null, false))
            }
            cache
        }
        every { projectCacheMock.memberStatus } answers {
            val cache = SimpleCache<String, UserStatusResponse> { projectId: String ->
                UserStatusResponse(
                    listOf(UserStatusInProject("test", "title", ProjectMember(user.username, ProjectRole.ADMIN), null)),
                    listOf(UserGroupSummary("test", "group", user.username))
                )
            }
            cache
        }

        ClientMock.mockCallSuccess(
            Wallets.reserveCreditsBulk,
            Unit
        )
        ClientMock.mockCallSuccess(
            Projects.create,
            CreateProjectResponse("idOfNewProject")
        )
        ClientMock.mockCallSuccess(
            FileDescriptions.retrieveQuota,
            RetrieveQuotaResponse(1000, 1000, 0, 0)
        )
        ClientMock.mockCallSuccess(
            Wallets.addToBalanceBulk,
            Unit
        )
        every { projectCacheMock.admins } answers {
            val cache = SimpleCache<String, List<ProjectMember>> { projectId: String ->
                listOf(ProjectMember(user.username, ProjectRole.ADMIN))
            }
            cache
        }
        ClientMock.mockCallSuccess(
            MailDescriptions.sendBulk,
            Unit
        )
        runBlocking {
            applicationService.updateStatus(db, user.toActor(), 1, ApplicationStatus.APPROVED)
        }
    }

    @Test
    fun statusChangeIllegal() {
        val client = ClientMock.authenticatedClient
        val projectCacheMock = mockk<ProjectCache>()
        val applicationService = createApplicationService(client, projectCacheMock = projectCacheMock)
        val user = TestUsers.user
        createApplication(db, user.username, ApplicationStatus.CLOSED)

        every { projectCacheMock.ancestors } answers {
            val cache = SimpleCache<String, List<Project>> { projectId: String ->
                listOf(Project("test", "title", null, false))
            }
            cache
        }
        every { projectCacheMock.memberStatus } answers {
            val cache = SimpleCache<String, UserStatusResponse> { projectId: String ->
                UserStatusResponse(
                    listOf(UserStatusInProject("test", "title", ProjectMember(user.username, ProjectRole.ADMIN), null)),
                    listOf(UserGroupSummary("test", "group", user.username))
                )
            }
            cache
        }
        try {

            runBlocking {
                applicationService.updateStatus(db, user.toActor(), 1, ApplicationStatus.APPROVED)
            }
        } catch (ex: RPCException) {
            assertEquals(ex.httpStatusCode, HttpStatusCode.BadRequest)
        }
    }
}
