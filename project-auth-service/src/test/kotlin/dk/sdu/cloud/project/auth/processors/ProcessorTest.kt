package dk.sdu.cloud.project.auth.processors

import dk.sdu.cloud.auth.api.CreateSingleUserResponse
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectEvents
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.auth.services.AuthTokenDao
import dk.sdu.cloud.project.auth.services.AuthTokenHibernateDao
import dk.sdu.cloud.project.auth.services.TokenInvalidator
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.EventServiceMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.assertThatProperty
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.retrySection
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ProcessorTest {
    lateinit var micro: Micro
    lateinit var db: DBSessionFactory<HibernateSession>
    lateinit var authTokenDao: AuthTokenDao<HibernateSession>
    lateinit var tokenInvalidator: TokenInvalidator<HibernateSession>
    lateinit var processor: ProjectEventProcessor<HibernateSession>

    @BeforeTest
    fun beforeTest() {
        micro = initializeMicro().apply {
            install(HibernateFeature)
        }

        db = micro.hibernateDatabase
        authTokenDao = AuthTokenHibernateDao()
        tokenInvalidator = mockk(relaxed = true)
        processor = ProjectEventProcessor(
            ClientMock.authenticatedClient,
            db,
            authTokenDao,
            tokenInvalidator,
            EventServiceMock
        )
        processor.init()
    }

    @AfterTest
    fun afterTest() {
        EventServiceMock.reset()
    }

    private fun send(event: ProjectEvent, await: Boolean = true) {
        println("Sending event: $event")
        EventServiceMock.produceEvents(ProjectEvents.events, listOf(event))
        if (await) Thread.sleep(500)
    }

    private fun createProject(id: String = "projectA") {
        var usersCreated = false
        ClientMock.mockCall(
            UserDescriptions.createNewUser,
            {
                usersCreated = true
                TestCallResult.Ok(it.map { CreateSingleUserResponse("access", "refresh", "csrf") })
            }
        )
        send(ProjectEvent.Created(Project(id, "project")))

        retrySection {
            assertTrue(usersCreated)
            val tokens = db.withTransaction { authTokenDao.tokensForProject(it, id) }
            assertThatProperty(tokens, { it.size }, matcher = { it == ProjectRole.values().size })
        }
    }

    @Test
    fun `test creating a project`() {
        createProject()
    }

    @Test
    fun `test deleting a project`() = runBlocking {
        val id = "projectA-${UUID.randomUUID()}"
        createProject(id)

        send(ProjectEvent.Deleted(Project(id, "project")))

        retrySection {
            coVerify { tokenInvalidator.invalidateTokensForProject(id) }
        }
    }
}
