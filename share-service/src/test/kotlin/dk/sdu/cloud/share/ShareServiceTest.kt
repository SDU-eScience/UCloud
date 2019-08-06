package dk.sdu.cloud.share

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.LookupUsersResponse
import dk.sdu.cloud.auth.api.TokenExtensionResponse
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.auth.api.UserLookup
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.ClientAndBackend
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.BackgroundJobs
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.FindHomeFolderResponse
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.indexing.api.LookupDescriptions
import dk.sdu.cloud.indexing.api.ReverseLookupResponse
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.notification.api.FindByNotificationId
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.retrySection
import dk.sdu.cloud.share.ShareServiceTest.Companion.owner
import dk.sdu.cloud.share.ShareServiceTest.Companion.recipient
import dk.sdu.cloud.share.ShareServiceTest.Companion.sharedFile
import dk.sdu.cloud.share.api.ShareState
import dk.sdu.cloud.share.api.Shares
import dk.sdu.cloud.share.services.ShareHibernateDAO
import dk.sdu.cloud.share.services.ShareQueryService
import dk.sdu.cloud.share.services.ShareService
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

data class MockConfiguration(
    val reverseLookupPath: String? = "/home/$recipient/$sharedFile",
    val findHomeSuccess: Boolean = true,
    val allowTokenExtension: Boolean = true,
    val allowTokenRevoke: Boolean = true,
    val statOwner: String? = owner,
    val allowUserLookup: HttpStatusCode = HttpStatusCode.OK,
    val allowAclUpdate: Boolean = true
)

class ShareServiceTest {
    lateinit var shareService: ShareService<HibernateSession>
    lateinit var shareQueryService: ShareQueryService<HibernateSession>
    lateinit var micro: Micro

    @BeforeTest
    fun initializeTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)

        val shareDao = ShareHibernateDAO()

        shareService = ShareService(
            ClientMock.authenticatedClient,
            micro.hibernateDatabase,
            shareDao,
            { ClientAndBackend(ClientMock.client, OutgoingHttpCall).bearerAuth(it) },
            micro.eventStreamService,
            devMode = false
        )

        shareQueryService = ShareQueryService(micro.hibernateDatabase, shareDao, ClientMock.authenticatedClient)

        shareService.initializeJobQueue()
    }

    fun initializeMocks(config: MockConfiguration) {
        with(config) {
            initializeLinkLookupMocks()
            initializeNotificationMock()
            initializeTokenMocks()
            initializeVerificationMocks()
            initializeACLMock()
        }
    }

    fun MockConfiguration.initializeLinkLookupMocks() {
        ClientMock.mockCall(LookupDescriptions.reverseLookup) {
            if (reverseLookupPath == null) {
                TestCallResult.Error(null, HttpStatusCode.InternalServerError)
            } else {
                TestCallResult.Ok(ReverseLookupResponse(listOf(reverseLookupPath)))
            }
        }

        ClientMock.mockCall(FileDescriptions.findHomeFolder) {
            if (findHomeSuccess) TestCallResult.Ok(FindHomeFolderResponse("/home/${it.username}"))
            else TestCallResult.Error(null, HttpStatusCode.InternalServerError)
        }
    }

    fun MockConfiguration.initializeNotificationMock() {
        ClientMock.mockCallSuccess(NotificationDescriptions.create, FindByNotificationId(1))
    }

    fun MockConfiguration.initializeTokenMocks() {
        ClientMock.mockCall(AuthDescriptions.tokenExtension) {
            if (allowTokenExtension) {
                TestCallResult.Ok(TokenExtensionResponse("accessToken", "csrfToken", "refreshToken"))
            } else {
                TestCallResult.Error(null, HttpStatusCode.InternalServerError)
            }
        }

        ClientMock.mockCall(AuthDescriptions.logout) {
            if (allowTokenRevoke) {
                TestCallResult.Ok(Unit)
            } else {
                TestCallResult.Error(null, HttpStatusCode.InternalServerError)
            }
        }
    }

    fun MockConfiguration.initializeVerificationMocks() {
        ClientMock.mockCall(FileDescriptions.stat) {
            if (statOwner == null) {
                TestCallResult.Error(null, HttpStatusCode.NotFound)
            } else {
                TestCallResult.Ok(
                    StorageFile(
                        fileType = FileType.DIRECTORY,
                        path = "/home/$owner/$sharedFile",
                        ownerName = statOwner
                    )
                )
            }
        }

        ClientMock.mockCall(UserDescriptions.lookupUsers) {
            if (allowUserLookup == HttpStatusCode.OK) {
                TestCallResult.Ok(
                    LookupUsersResponse(
                        it.users.map { user ->
                            user to UserLookup(user, 10, Role.USER)
                        }.toMap()
                    )
                )
            } else if (allowUserLookup == HttpStatusCode.NotFound) {
                TestCallResult.Ok(
                    LookupUsersResponse(
                        it.users.map { user ->
                            user to null
                        }.toMap()
                    )
                )
            } else {
                TestCallResult.Error<LookupUsersResponse, CommonErrorMessage>(null, allowUserLookup)
            }
        }
    }

    fun MockConfiguration.initializeACLMock() {
        ClientMock.mockCall(FileDescriptions.updateAcl) {
            if (allowAclUpdate) {
                TestCallResult.Ok(Unit)
            } else {
                TestCallResult.Error(null, HttpStatusCode.InternalServerError)
            }
        }
    }

    private suspend fun createShare(
        owner: String = ShareServiceTest.owner,
        recipient: String = ShareServiceTest.recipient
    ): Int {
        val path = "/home/$owner/$sharedFile"
        var statusCode = 0
        try {
            shareService.create(
                owner,
                Shares.Create.Request(recipient, path, setOf(AccessRight.READ)),
                "token",
                ClientMock.authenticatedClient
            )
        } catch (ex: RPCException) {
            statusCode = ex.httpStatusCode.value
        }
        return statusCode
    }

    @Test
    fun `test normal creation`() = runBlocking {
        initializeMocks(MockConfiguration())

        createShare()

        assertEquals(1, shareQueryService.list(owner, true).items.size)
        assertEquals(1, shareQueryService.list(recipient, false).items.size)

        return@runBlocking
    }

    @Test
    fun `test creation user not found`() = runBlocking {
        initializeMocks(MockConfiguration(allowUserLookup = HttpStatusCode.NotFound))

        val statusCode = createShare()
        assertEquals(HttpStatusCode.BadRequest.value, statusCode)

        assertEquals(0, shareQueryService.list(owner, true).items.size)
        assertEquals(0, shareQueryService.list(recipient, false).items.size)

        return@runBlocking
    }

    @Test
    fun `test creation file not found`() = runBlocking {
        initializeMocks(MockConfiguration(statOwner = null))

        val statusCode = createShare()

        assertEquals(HttpStatusCode.NotFound.value, statusCode)

        assertEquals(0, shareQueryService.list(owner, true).items.size)
        assertEquals(0, shareQueryService.list(recipient, false).items.size)

        return@runBlocking
    }

    @Test
    fun `test creation file not owned`() = runBlocking {
        initializeMocks(MockConfiguration(statOwner = "notme"))

        val statusCode = createShare()

        assertEquals(HttpStatusCode.Forbidden.value, statusCode)

        assertEquals(0, shareQueryService.list(owner, true).items.size)
        assertEquals(0, shareQueryService.list(recipient, false).items.size)

        return@runBlocking
    }

    @Test
    fun `test creation extension failure`() = runBlocking {
        initializeMocks(MockConfiguration(allowTokenExtension = false))

        val statusCode = createShare()

        assertEquals(HttpStatusCode.InternalServerError.value, statusCode)

        assertEquals(0, shareQueryService.list(owner, true).items.size)
        assertEquals(0, shareQueryService.list(recipient, false).items.size)

        return@runBlocking
    }

    private suspend fun acceptShare(expectedState: ShareState = ShareState.ACCEPTED) {
        shareService.acceptShare(
            recipient,
            1L,
            "token"
        )

        assertEquals(1, shareQueryService.list(owner, true).items.size)
        assertEquals(1, shareQueryService.list(recipient, false).items.size)

        retrySection {
            assertEquals(expectedState, shareQueryService.list(owner, true).items.single().shares.single().state)
            assertEquals(expectedState, shareQueryService.list(recipient, false).items.single().shares.single().state)
        }
    }

    @Test
    fun `test normal accept`() = runBlocking {
        initializeMocks(MockConfiguration())
        assertEquals(0, createShare())

        acceptShare()
        return@runBlocking
    }

    @Test
    fun `test accepting twice`() = runBlocking {
        initializeMocks(MockConfiguration())
        assertEquals(0, createShare())
        acceptShare()
        assertException<RPCException> { acceptShare() }
        return@runBlocking
    }

    @Test
    fun `test accepting with token failure`() = runBlocking {
        initializeMocks(MockConfiguration())
        assertEquals(0, createShare())

        initializeMocks(MockConfiguration(allowTokenExtension = false))
        assertStatusCode(HttpStatusCode.InternalServerError) { acceptShare() }
        return@runBlocking
    }

    @Test
    fun `test accepting with updateAcl failure`() = runBlocking {
        initializeMocks(MockConfiguration())
        assertEquals(0, createShare())

        initializeMocks(MockConfiguration(allowAclUpdate = false))
        Thread {
            Thread.sleep(2000)
            initializeMocks(MockConfiguration())
        }.start()

        acceptShare(ShareState.FAILURE)
        return@runBlocking
    }

    private suspend fun updateShare(expectedState: ShareState = ShareState.ACCEPTED) {
        shareService.updateRights(owner, 1L, setOf(AccessRight.READ, AccessRight.WRITE))

        retrySection {
            assertEquals(expectedState, shareQueryService.list(owner, true).items.single().shares.single().state)
            assertEquals(expectedState, shareQueryService.list(recipient, false).items.single().shares.single().state)
        }
    }

    @Test
    fun `test updating with accepted share`() = runBlocking {
        initializeMocks(MockConfiguration())
        assertEquals(0, createShare())
        acceptShare()
        updateShare()
        return@runBlocking
    }

    @Test
    fun `test updating with accepted share and updateAcl failure`() = runBlocking {
        initializeMocks(MockConfiguration())
        assertEquals(0, createShare())
        acceptShare()
        initializeMocks(MockConfiguration(allowAclUpdate = false))

        Thread {
            Thread.sleep(2000)
            initializeMocks(MockConfiguration())
        }.start()

        updateShare(ShareState.FAILURE)
        return@runBlocking
    }

    @Test
    fun `test updating`() = runBlocking {
        initializeMocks(MockConfiguration())
        assertEquals(0, createShare())
        updateShare(ShareState.REQUEST_SENT)
        return@runBlocking
    }

    @Test
    fun `test deleting not accepted (owner)`() = runBlocking {
        initializeMocks(MockConfiguration())
        assertEquals(0, createShare())
        shareService.deleteShare(owner, 1L)
        assertEquals(0, shareQueryService.list(owner, true).items.size)
        assertEquals(0, shareQueryService.list(recipient, false).items.size)
        return@runBlocking
    }

    @Test
    fun `test deleting not accepted (recipient)`() = runBlocking {
        initializeMocks(MockConfiguration())
        assertEquals(0, createShare())
        shareService.deleteShare(recipient, 1L)
        assertEquals(0, shareQueryService.list(owner, true).items.size)
        assertEquals(0, shareQueryService.list(recipient, false).items.size)
        return@runBlocking
    }

    @Test
    fun `test deleting not accepted (bad user)`() = runBlocking {
        initializeMocks(MockConfiguration())
        assertEquals(0, createShare())
        acceptShare()
        assertStatusCode(HttpStatusCode.NotFound) { shareService.deleteShare("notme", 1L) }
        assertEquals(1, shareQueryService.list(owner, true).items.size)
        assertEquals(1, shareQueryService.list(recipient, false).items.size)
        return@runBlocking
    }

    @Test
    fun `test deleting accepted (owner)`() = runBlocking {
        initializeMocks(MockConfiguration())
        assertEquals(0, createShare())
        acceptShare()
        shareService.deleteShare(owner, 1L)
        assertEquals(0, shareQueryService.list(owner, true).items.size)
        assertEquals(0, shareQueryService.list(recipient, false).items.size)
        return@runBlocking
    }

    @Test
    fun `test deleting accepted (recipient)`() = runBlocking {
        initializeMocks(MockConfiguration())
        assertEquals(0, createShare())
        acceptShare()
        shareService.deleteShare(recipient, 1L)
        assertEquals(0, shareQueryService.list(owner, true).items.size)
        assertEquals(0, shareQueryService.list(recipient, false).items.size)
        return@runBlocking
    }

    @Test
    fun `test deleting accepted (bad user)`() = runBlocking {
        initializeMocks(MockConfiguration())
        assertEquals(0, createShare())
        assertStatusCode(HttpStatusCode.NotFound) { shareService.deleteShare("notme", 1L) }
        assertEquals(1, shareQueryService.list(owner, true).items.size)
        assertEquals(1, shareQueryService.list(recipient, false).items.size)
        return@runBlocking
    }

    @Test
    fun `test sharing with multiple`() = runBlocking {
        initializeMocks(MockConfiguration())
        val user2 = "user2"
        assertEquals(0, createShare())
        assertEquals(0, createShare(recipient = user2))

        assertThatPropertyEquals(
            shareQueryService.findSharesForPath(recipient, "/home/$owner/$sharedFile", "accesstoken"),
            { it.shares.size },
            1
        )

        assertThatPropertyEquals(
            shareQueryService.findSharesForPath(user2, "/home/$owner/$sharedFile", "accesstoken"),
            { it.shares.size },
            1
        )

        assertThatPropertyEquals(
            shareQueryService.findSharesForPath(owner, "/home/$owner/$sharedFile", "accesstoken"),
            { it.shares.size },
            2
        )

        assertStatusCode(HttpStatusCode.NotFound) {
            shareQueryService.findSharesForPath("unrelated", "/home/$owner/$sharedFile", "accesstoken")
        }
    }

    private inline fun <reified E : Throwable> assertException(block: () -> Unit) {
        try {
            block()
        } catch (ex: Exception) {
            assertTrue(E::class.java.isInstance(ex))
            return
        }

        assert(false)
    }

    private inline fun assertStatusCode(code: HttpStatusCode, block: () -> Unit) {
        var returnCode = HttpStatusCode.OK
        try {
            block()
        } catch (ex: RPCException) {
            returnCode = ex.httpStatusCode
        } catch (ex: Throwable) {
            returnCode = HttpStatusCode.InternalServerError
        }

        assertEquals(code, returnCode)
    }

    companion object {
        const val owner = "owner"
        const val recipient = "recipient"
        val ownerUser = TestUsers.user.copy(username = owner)
        val recipientUser = TestUsers.user.copy(username = recipient)
        const val sharedFile = "shared"
    }
}
