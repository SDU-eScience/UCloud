package dk.sdu.cloud.project.auth.processors

import dk.sdu.cloud.auth.api.CreateSingleUserResponse
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectEvents
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.auth.api.ProjectAuthEvents
import dk.sdu.cloud.project.auth.services.AuthTokenDao
import dk.sdu.cloud.project.auth.services.AuthTokenHibernateDao
import dk.sdu.cloud.project.auth.services.TokenInvalidator
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.authenticatedCloud
import dk.sdu.cloud.service.cloudContext
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.forStream
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.kafka
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.MockedEventConsumerFactory
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.assertThatProperty
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.retrySection
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessorTest {
    lateinit var micro: Micro
    lateinit var db: DBSessionFactory<HibernateSession>
    lateinit var authTokenDao: AuthTokenDao<HibernateSession>
    lateinit var tokenInvalidator: TokenInvalidator<HibernateSession>
    lateinit var processor: ProjectEventProcessor<HibernateSession>
    lateinit var consumers: List<EventConsumer<*>>

    @BeforeTest
    fun beforeTest() {
        micro = initializeMicro().apply {
            install(HibernateFeature)
        }

        db = micro.hibernateDatabase
        authTokenDao = AuthTokenHibernateDao()
        tokenInvalidator = mockk(relaxed = true)
        processor = ProjectEventProcessor(
            micro.authenticatedCloud,
            db,
            authTokenDao,
            tokenInvalidator,
            MockedEventConsumerFactory,
            micro.kafka.producer.forStream(ProjectAuthEvents.events),
            parallelism = 1
        )

        consumers = processor.init()
    }

    @AfterTest
    fun afterTest() {
        consumers.forEach { it.close() }
    }

    private fun send(event: ProjectEvent, await: Boolean = true) {
        println("Sending event: $event")
        MockedEventConsumerFactory.send(ProjectEvents.events, event)
        if (await) Thread.sleep(50)
    }

    private fun createProject(id: String = "projectA") {
        var usersCreated = false
        CloudMock.mockCall(
            UserDescriptions,
            { UserDescriptions.createNewUser },
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
