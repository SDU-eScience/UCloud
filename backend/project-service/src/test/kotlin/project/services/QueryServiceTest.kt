package project.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectEvents
import dk.sdu.cloud.project.api.ProjectServiceDescription
import dk.sdu.cloud.project.services.ProjectService
import dk.sdu.cloud.project.services.QueryService
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.EventProducerMock
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.toActor
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class QueryServiceTest {
    companion object {
        lateinit var db: AsyncDBSessionFactory
        lateinit var embDb: EmbeddedPostgres
        lateinit var projectService: ProjectService
        lateinit var serviceClient: AuthenticatedClient

        @BeforeClass
        @JvmStatic
        fun setup() {
            val (db, embDb) = TestDB.from(ProjectServiceDescription)
            this.db = db
            this.embDb = embDb
            this.serviceClient = ClientMock.authenticatedClient
            this.projectService = ProjectService(serviceClient, EventProducerMock(ProjectEvents.events))
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

    fun createTestData() {
        runBlocking {
            val adminActor = TestUsers.admin.toActor()
            val id1 = projectService.create(db, adminActor, "testMain", null, null)
            val id2 = projectService.create(db, adminActor, "testsub1", id1, null)
            val id3 = projectService.create(db, adminActor, "testsub2", id1, null)
            val id4 = projectService.create(db, adminActor, "testsubSub", id3, null)
            val id5 = projectService.create(db, adminActor, "subtoSub", id3, null)
        }
    }

    @BeforeTest
    fun before() {
        truncateProjectDB()
        createTestData()
    }

    @AfterTest
    fun after() {
        truncateProjectDB()
    }

    @Test
    fun `Search Project Paths Test`() {
        val serviceClient = ClientMock.authenticatedClient
        val producer = EventProducerMock<ProjectEvent>(ProjectEvents.events)
        val projectService = ProjectService(serviceClient, producer)
        val queryService = QueryService(projectService)
        runBlocking {
            val results = queryService.searchProjectPaths(db, Actor.System, "est")
            assertEquals(4, results.size)
        }
    }
}
