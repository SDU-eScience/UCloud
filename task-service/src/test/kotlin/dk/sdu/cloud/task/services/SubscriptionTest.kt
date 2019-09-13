package dk.sdu.cloud.task.services

import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.retrySection
import dk.sdu.cloud.task.api.PostStatusResponse
import dk.sdu.cloud.task.api.TaskUpdate
import dk.sdu.cloud.task.api.Tasks
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SubscriptionTest {
    private lateinit var micro: Micro
    private lateinit var db: HibernateSessionFactory
    private lateinit var subscriptionService: SubscriptionService<HibernateSession>
    private lateinit var subscriptionDao: SubscriptionHibernateDao
    private lateinit var callHandler: CallHandler<*, TaskUpdate, *>
    private lateinit var wsCall: WSCall

    private val localPort = 40000
    private val remotePort = 50000

    private val dummyId = "foobar"
    private val dummyUpdate = TaskUpdate(dummyId)

    @BeforeTest
    fun beforeTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)

        db = micro.hibernateDatabase

        callHandler = mockk(relaxed = true)
        wsCall = mockk(relaxed = true)
        every { callHandler.ctx } returns wsCall

        mockkStatic("dk.sdu.cloud.calls.server.IngoingWebSocketInterceptorKt")
        coEvery { callHandler.sendWSMessage(any()) } just Runs

        subscriptionDao = SubscriptionHibernateDao()
        subscriptionService = SubscriptionService(
            HostInfo("localhost", port = localPort),
            ClientMock.authenticatedClient,
            micro.hibernateDatabase,
            subscriptionDao,
            ClientMock.authenticatedClient
        )
    }

    @Test
    fun `test that local connections are handled correctly`() = runBlocking {
        subscriptionService.onConnection(TestUsers.user.username, callHandler)
        subscriptionService.onTaskUpdate(TestUsers.user.username, dummyId, dummyUpdate)
        coVerify { callHandler.sendWSMessage(dummyUpdate) }
    }

    @Test
    fun `test that remote connections are handled correctly`() = runBlocking {
        var postStatusCall = 0
        db.withTransaction { session ->
            subscriptionDao.open(session, TestUsers.user.username, "localhost", remotePort)
        }

        ClientMock.mockCall(Tasks.postStatus) {
            postStatusCall++
            TestCallResult.Ok(PostStatusResponse)
        }

        subscriptionService.onTaskUpdate(TestUsers.user.username, dummyId, dummyUpdate)
        coVerify(exactly = 0) { callHandler.sendWSMessage(dummyUpdate) }

        retrySection {
            assertEquals(1, postStatusCall, "expected Tasks.postStatus to be called!")
        }
    }

    @Test
    fun `test that invalid connections are handled correctly`() = runBlocking {
        subscriptionService.onTaskUpdate(TestUsers.user.username, dummyId, dummyUpdate)
        coVerify(exactly = 0) { callHandler.sendWSMessage(dummyUpdate) }
    }
}
