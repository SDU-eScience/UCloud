package dk.sdu.cloud.share

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.LookupUsersResponse
import dk.sdu.cloud.auth.api.OptionalAuthenticationTokens
import dk.sdu.cloud.auth.api.TokenExtensionResponse
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.auth.api.UserLookup
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.FindHomeFolderResponse
import dk.sdu.cloud.file.api.LongRunningResponse
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.indexing.api.LookupDescriptions
import dk.sdu.cloud.indexing.api.ReverseLookupResponse
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.notification.api.FindByNotificationId
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.assertCollectionHasItem
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.share.api.CreateShareRequest
import dk.sdu.cloud.share.api.ShareId
import dk.sdu.cloud.share.api.ShareState
import dk.sdu.cloud.share.services.InternalShare
import dk.sdu.cloud.share.services.ShareException
import dk.sdu.cloud.share.services.ShareHibernateDAO
import dk.sdu.cloud.share.services.ShareService
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareServiceTest {
    lateinit var service: ShareService<HibernateSession>
    lateinit var micro: Micro

    @BeforeTest
    fun initializeService() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)

        service = ShareService(
            serviceCloud = ClientMock.authenticatedClient,
            db = micro.hibernateDatabase,
            shareDao = ShareHibernateDAO(),
            userCloudFactory = { ClientMock.authenticatedClient }
        )
        this.micro = micro
    }

    private data class CreateMock(
        val owner: SecurityPrincipal = TestUsers.user,
        val recipient: SecurityPrincipal = TestUsers.user2,
        val sharePath: String = joinPath("/home/${owner.username}", "to_share"),
        val rights: Set<AccessRight> = setOf(AccessRight.READ),

        val statResult: TestCallResult<StorageFile, CommonErrorMessage> = TestCallResult.Ok(
            StorageFile(
                FileType.FILE,
                sharePath,
                ownerName = owner.username
            )
        ),

        val lookupResult: TestCallResult<LookupUsersResponse, CommonErrorMessage> = TestCallResult.Ok(
            LookupUsersResponse(mapOf(recipient.username to UserLookup(recipient.username, 0L, recipient.role)))
        ),

        val extensionResult: TestCallResult<OptionalAuthenticationTokens, CommonErrorMessage> = TestCallResult.Ok(
            OptionalAuthenticationTokens(TokenValidationMock.createTokenForPrincipal(owner), "csrf", "refresh")
        ),

        val notificationResult: TestCallResult<FindByNotificationId, CommonErrorMessage> = TestCallResult.Ok(
            FindByNotificationId(1)
        )
    ) {
        companion object {
            val default = CreateMock()
        }
    }

    private suspend fun CreateMock.sendCreateRequest(): ShareId {
        val bearerToken = TokenValidationMock.createTokenForPrincipal(
            owner
        )
        return service.create(
            owner.username,
            CreateShareRequest(recipient.username, sharePath, rights),
            bearerToken,
            ClientMock.authenticatedClient.withoutAuthentication().bearerAuth(bearerToken)
        )
    }

    private inline fun <T> CreateMock.mock(consumer: CreateMock.() -> T = { Unit as T }): T {
        ClientMock.mockCall(
            FileDescriptions.stat,
            { statResult }
        )

        ClientMock.mockCall(
            UserDescriptions.lookupUsers,
            { lookupResult }
        )

        ClientMock.mockCall(
            AuthDescriptions.tokenExtension,
            { extensionResult }
        )

        ClientMock.mockCall(
            NotificationDescriptions.create,
            { notificationResult }
        )

        return consumer(this)
    }

    private data class AcceptMock(
        val owner: SecurityPrincipal = TestUsers.user,
        val recipient: SecurityPrincipal = TestUsers.user2,
        val sharePath: String = joinPath("/home/${owner.username}", "to_share"),

        val extensionResult: TestCallResult<OptionalAuthenticationTokens, CommonErrorMessage> = TestCallResult.Ok(
            OptionalAuthenticationTokens(TokenValidationMock.createTokenForPrincipal(owner), "csrf", "refresh")
        ),

        val createLinkResult: TestCallResult<StorageFile, CommonErrorMessage> = TestCallResult.Ok(
            StorageFile(
                FileType.DIRECTORY,
                path = joinPath("/home/${owner.username}", "to_share"),
                link = true,
                ownerName = recipient.username
            )
        ),

        val updateAclResult: TestCallResult<Unit, CommonErrorMessage> = TestCallResult.Ok(Unit)
    )

    private inline fun <T> AcceptMock.mock(consumer: AcceptMock.() -> T = { Unit as T }): T {
        ClientMock.mockCall(
            AuthDescriptions.tokenExtension,
            { extensionResult }
        )

        ClientMock.mockCall(
            FileDescriptions.createLink,
            { createLinkResult }
        )

        ClientMock.mockCall(
            FileDescriptions.updateAcl,
            { updateAclResult }
        )

        return consumer(this)
    }

    private suspend fun AcceptMock.sendAcceptRequest(shareId: ShareId) {
        val bearerToken = TokenValidationMock.createTokenForPrincipal(
            recipient
        )
        service.acceptShare(
            recipient.username,
            shareId,
            bearerToken,
            ClientMock.authenticatedClient.withoutAuthentication().bearerAuth(bearerToken)
        )
    }

    private data class DeleteMock(
        val owner: SecurityPrincipal = TestUsers.user,
        val recipient: SecurityPrincipal = TestUsers.user2,
        val sharePath: String = joinPath("/home/${owner.username}", "to_share"),

        val updateAclResult: TestCallResult<Unit, CommonErrorMessage> = TestCallResult.Ok(Unit),

        val linkStatResult: TestCallResult<StorageFile, CommonErrorMessage> = TestCallResult.Ok(
            StorageFile(FileType.FILE, sharePath, ownerName = owner.username, link = true)
        ),

        val linkLookup: TestCallResult<ReverseLookupResponse, CommonErrorMessage> = TestCallResult.Ok(
            ReverseLookupResponse(listOf(joinPath("/home/${owner.username}", "to_share")))
        ),

        val deleteFileResult: TestCallResult<LongRunningResponse<Unit>, CommonErrorMessage> = TestCallResult.Ok(
            LongRunningResponse.Result(Unit)
        )
    )

    private fun <T> DeleteMock.mock(consumer: DeleteMock.() -> T = { Unit as T }): T {
        ClientMock.mockCall(
            FileDescriptions.updateAcl,
            { updateAclResult }
        )

        ClientMock.mockCall(
            FileDescriptions.deleteFile,
            { deleteFileResult }
        )

        ClientMock.mockCall(
            FileDescriptions.stat,
            { linkStatResult }
        )

        ClientMock.mockCall(
            LookupDescriptions.reverseLookup,
            { linkLookup }
        )

        ClientMock.mockCallSuccess(
            AuthDescriptions.logout,
            Unit
        )

        return consumer(this)
    }

    @Test
    fun `test successful create and list`() {
        runBlocking {
            CreateMock().mock {
                sendCreateRequest()

                val page = service.list(owner.username)
                assertEquals(1, page.itemsInTotal)
                assertEquals(1, page.items.size)
                assertCollectionHasItem(page.items) { it.sharedByMe && it.path == sharePath }
                assertCollectionHasItem(page.items.first().shares) {
                    it.sharedWith == recipient.username && it.rights == rights && it.state == ShareState.REQUEST_SENT
                }
            }
        }
    }

    @Test(expected = ShareException.NotFound::class)
    fun `test create with missing file`() {
        runBlocking {
            CreateMock(
                statResult = TestCallResult.Error(null, HttpStatusCode.NotFound)
            ).mock {
                sendCreateRequest()
            }
        }
    }

    @Test(expected = ShareException.NotAllowed::class)
    fun `test create missing permissions`() {
        runBlocking {
            val mockData = CreateMock(
                statResult = TestCallResult.Ok(
                    StorageFile(
                        FileType.FILE,
                        CreateMock.default.sharePath,
                        ownerName = "somebody"
                    )
                )
            )

            mockData.mock<Unit>()
            mockData.sendCreateRequest()
        }
    }

    @Test(expected = ShareException.BadRequest::class)
    fun `test create share with missing user`() {
        runBlocking {
            val mockData = CreateMock(
                lookupResult = TestCallResult.Ok(
                    LookupUsersResponse(
                        mapOf(
                            CreateMock.default.recipient.username to null
                        )
                    )
                )
            )

            mockData.mock<Unit>()
            mockData.sendCreateRequest()
        }
    }

    @Test(expected = RPCException::class)
    fun `test create with token extension failure`() {
        runBlocking {
            val mockData = CreateMock(
                extensionResult = TestCallResult.Error(null, HttpStatusCode.InternalServerError)
            )

            mockData.mock<Unit>()
            mockData.sendCreateRequest()
        }
    }

    @Test
    fun `test create with no notification service`() {
        runBlocking {
            val mockData = CreateMock(
                notificationResult = TestCallResult.Error(null, HttpStatusCode.InternalServerError)
            )

            mockData.mock {
                sendCreateRequest()

                val page = service.list(owner.username)
                assertEquals(1, page.itemsInTotal)
                assertEquals(1, page.items.size)
                assertCollectionHasItem(page.items) { it.sharedByMe && it.path == sharePath }
                assertCollectionHasItem(page.items.first().shares) {
                    it.sharedWith == recipient.username && it.rights == rights && it.state == ShareState.REQUEST_SENT
                }
            }
        }
    }

    @Test
    fun `create, accept, and delete`() {
        ClientMock.mockCallSuccess(
            FileDescriptions.findHomeFolder,
            FindHomeFolderResponse("/home/user/")
        )
        runBlocking {
            CreateMock().mock {
                val shareId = sendCreateRequest()

                AcceptMock().mock {
                    sendAcceptRequest(shareId)
                }

                val ownerPage = service.list(owner.username)
                assertEquals(1, ownerPage.itemsInTotal)
                assertEquals(1, ownerPage.items.size)
                assertCollectionHasItem(ownerPage.items) { it.sharedByMe && it.path == sharePath }
                assertCollectionHasItem(ownerPage.items.first().shares) {
                    it.sharedWith == recipient.username && it.rights == rights && it.state == ShareState.ACCEPTED
                }

                val recipientPage = service.list(recipient.username)
                assertEquals(1, recipientPage.itemsInTotal)
                assertEquals(1, recipientPage.items.size)
                assertCollectionHasItem(recipientPage.items) { !it.sharedByMe && it.path == sharePath }
                assertCollectionHasItem(recipientPage.items.first().shares) {
                    it.sharedWith == recipient.username && it.rights == rights && it.state == ShareState.ACCEPTED
                }

                DeleteMock().mock<Unit>()
                service.deleteShare(owner.username, shareId)

                val ownerPage2 = service.list(owner.username)
                assertEquals(0, ownerPage2.itemsInTotal)

                val recipientPage2 = service.list(recipient.username)
                assertEquals(0, recipientPage2.itemsInTotal)
            }
        }
    }

    @Test
    fun `create and accept with missing owner permissions`() {
        runBlocking {
            CreateMock().mock {
                // For the initial request the owner will have permissions
                val shareId = sendCreateRequest()

                val acceptMock = AcceptMock(
                    updateAclResult = TestCallResult.Error(null, HttpStatusCode.Forbidden)
                )

                acceptMock.mock<Unit>()
                val acceptResult = runCatching {
                    acceptMock.sendAcceptRequest(shareId)
                }

                assertTrue(acceptResult.isFailure)

                val ownerPage = service.list(owner.username)
                assertEquals(1, ownerPage.itemsInTotal)
                assertEquals(1, ownerPage.items.size)
                assertCollectionHasItem(ownerPage.items) { it.sharedByMe && it.path == sharePath }
                assertCollectionHasItem(ownerPage.items.first().shares) {
                    it.sharedWith == recipient.username && it.rights == rights && it.state == ShareState.REQUEST_SENT
                }

                val recipientPage = service.list(recipient.username)
                assertEquals(1, recipientPage.itemsInTotal)
                assertEquals(1, recipientPage.items.size)
                assertCollectionHasItem(recipientPage.items) { !it.sharedByMe && it.path == sharePath }
                assertCollectionHasItem(recipientPage.items.first().shares) {
                    it.sharedWith == recipient.username && it.rights == rights && it.state == ShareState.REQUEST_SENT
                }
            }
        }
    }

    @Test (expected = RPCException::class)
    fun `Cleanup on failed accept test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)

        val shareDao = mockk<ShareHibernateDAO>()
        val shareService = ShareService(
            ClientMock.authenticatedClient,
            micro.hibernateDatabase,
            shareDao,
            { ClientMock.authenticatedClient }
        )

        every { shareDao.findById(any(), any(), any()) } answers {
            InternalShare(
                22,
                TestUsers.user.username,
                TestUsers.user2.username,
                ShareState.REQUEST_SENT,
                "path",
                setOf(AccessRight.READ, AccessRight.WRITE),
                "fileId",
                "ownerToken",
                "recipientToken",
                "linkId")
        }

        ClientMock.mockCallSuccess(
            FileDescriptions.updateAcl,
            Unit
        )

        ClientMock.mockCallSuccess(
            LookupDescriptions.reverseLookup,
            ReverseLookupResponse(listOf("path/to/link"))
        )

        ClientMock.mockCallSuccess(
            FileDescriptions.deleteFile,
            LongRunningResponse.Result(Unit)
        )

        ClientMock.mockCallSuccess(
            AuthDescriptions.tokenExtension,
            TokenExtensionResponse("accessToken", "csrfToken", "refreshtoken")
        )

        ClientMock.mockCallSuccess(
            FileDescriptions.findHomeFolder,
            FindHomeFolderResponse("/home/user")
        )

        ClientMock.mockCallError(
            FileDescriptions.createLink,
            null,
            HttpStatusCode.BadRequest
        )
        runBlocking {
            shareService.acceptShare(
                TestUsers.user.username,
                22L,
                "userToken",
                ClientMock.authenticatedClient
            )
        }
    }
}
