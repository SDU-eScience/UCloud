package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.file.api.DeleteFileRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.file.api.Quota
import dk.sdu.cloud.file.api.RetrieveQuotaRequest
import dk.sdu.cloud.file.api.SimpleUploadRequest
import dk.sdu.cloud.file.api.TransferQuotaRequest
import dk.sdu.cloud.file.api.UpdateQuotaRequest
import dk.sdu.cloud.file.api.homeDirectory
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.projectHomeDirectory
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.t
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.LeaveProjectRequest
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.TransferPiRoleRequest
import dk.sdu.cloud.service.StaticTimeProvider
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.test.assertThatInstance
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals

suspend fun setPersonalQuota(
    root: String,
    username: String,
    quota: Long
) {
    FileDescriptions.transferQuota.call(
        TransferQuotaRequest(homeDirectory(username), quota),
        serviceClient.withProject(root)
    ).orThrow()
}

suspend fun setProjectQuota(
    projectId: String,
    quota: Long
) {
    FileDescriptions.updateQuota.call(
        UpdateQuotaRequest(
            projectHomeDirectory(projectId),
            quota
        ),
        serviceClient
    ).orThrow()
}

class LimitCheckerTest : IntegrationTest() {
    @Test
    fun `test normal upload with quota and credits`() = t {
        val root = initializeRootProject()
        setProjectQuota(root, 1024 * 1024 * 1024 * 1024L)
        val user = createUser()
        addFundsToPersonalProject(root, user.username, product = sampleStorage.category)
        setPersonalQuota(root, user.username, 1024 * 1024 * 1024)
        MultiPartUploadDescriptions.simpleUpload.call(
            SimpleUploadRequest(
                joinPath(homeDirectory(user.username), "hello.txt"),
                BinaryStream.outgoingFromChannel(ByteReadChannel("Hello, World!"))
            ),
            user.client
        ).orThrow()
    }

    @Test
    fun `test normal upload with no quota`() = t {
        val root = initializeRootProject()
        setProjectQuota(root, 1024 * 1024 * 1024 * 1024L)
        val user = createUser()
        addFundsToPersonalProject(root, user.username, product = sampleStorage.category)
        setPersonalQuota(root, user.username, 0)
        try {
            MultiPartUploadDescriptions.simpleUpload.call(
                SimpleUploadRequest(
                    joinPath(homeDirectory(user.username), "hello.txt"),
                    BinaryStream.outgoingFromChannel(ByteReadChannel("Hello, World!"))
                ),
                user.client
            ).orThrow()
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(HttpStatusCode.PaymentRequired, ex.httpStatusCode)
        }
    }

    @Test
    fun `test upload without credits`() = t {
        val root = initializeRootProject()
        setProjectQuota(root, 1024 * 1024 * 1024 * 1024L)
        val user = createUser()
        try {
            MultiPartUploadDescriptions.simpleUpload.call(
                SimpleUploadRequest(
                    joinPath(homeDirectory(user.username), "hello.txt"),
                    BinaryStream.outgoingFromChannel(ByteReadChannel("Hello, World!"))
                ),
                user.client
            ).orThrow()
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(HttpStatusCode.PaymentRequired, ex.httpStatusCode)
        }
    }

    @Test
    fun `test re-evaluation of quota`() = t {
        Time.provider = StaticTimeProvider
        StaticTimeProvider.time = 0
        val root = initializeRootProject()
        setProjectQuota(root, 1024 * 1024 * 1024 * 1024L)
        val user = createUser()
        addFundsToPersonalProject(root, user.username, product = sampleStorage.category)
        setPersonalQuota(root, user.username, 1024 * 1024)

        // Upload a 1MB file
        val bigFile = "1mb"
        MultiPartUploadDescriptions.simpleUpload.call(
            SimpleUploadRequest(
                joinPath(homeDirectory(user.username), bigFile),
                BinaryStream.outgoingFromChannel(ByteReadChannel(ByteArray(1024 * 1024)))
            ),
            user.client
        ).orThrow()
        StaticTimeProvider.time += 3600 * 1000

        // Check that we cannot upload a new file
        try {
            MultiPartUploadDescriptions.simpleUpload.call(
                SimpleUploadRequest(
                    joinPath(homeDirectory(user.username), "hello.txt"),
                    BinaryStream.outgoingFromChannel(ByteReadChannel("Hello, World!"))
                ),
                user.client
            ).orThrow()
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(HttpStatusCode.PaymentRequired, ex.httpStatusCode)
        }

        // Delete the file
        FileDescriptions.deleteFile.call(
            DeleteFileRequest(joinPath(homeDirectory(user.username), bigFile)),
            user.client
        ).orThrow()

        // No time should be needed to pass between delete and new upload

        // Attempt to upload hello.txt again
        MultiPartUploadDescriptions.simpleUpload.call(
            SimpleUploadRequest(
                joinPath(homeDirectory(user.username), "hello.txt"),
                BinaryStream.outgoingFromChannel(ByteReadChannel("Hello, World!"))
            ),
            user.client
        ).orThrow()
    }

    @Test
    fun `check quota permissions`() = t {
        val root = initializeRootProject()
        setProjectQuota(root, 1024 * 1024 * 1024 * 1024L)

        val project = initializeNormalProject(root)
        val user = createUser()
        val outsideUser = createUser()
        addMemberToProject(project.projectId, project.piClient, user.client, user.username)

        val quota: Long = 1024 * 1024 * 1024
        setProjectQuota(project.projectId, quota)

        val expectedQuota = Quota(quota, quota, 0L)

        assertEquals(
            expectedQuota,
            FileDescriptions.retrieveQuota.call(
                RetrieveQuotaRequest(projectHomeDirectory(project.projectId)),
                project.piClient
            ).orThrow()
        )

        assertEquals(
            expectedQuota,
            FileDescriptions.retrieveQuota.call(
                RetrieveQuotaRequest(projectHomeDirectory(project.projectId)),
                user.client
            ).orThrow()
        )

        assertThatInstance(
            FileDescriptions.retrieveQuota.call(
                RetrieveQuotaRequest(projectHomeDirectory(project.projectId)),
                outsideUser.client
            ),
            "fails"
        ) { it.statusCode.value in 400..499 }
    }

    @Test
    fun `check additive quota grants`() = t {
        val root = initializeRootProject()
        setProjectQuota(root, 1024 * 1024 * 1024 * 1024L)

        val project = initializeNormalProject(root)
        setProjectQuota(project.projectId, 0)
        repeat(5) { i ->
            FileDescriptions.updateQuota.call(
                UpdateQuotaRequest(projectHomeDirectory(project.projectId), 1000, additive = true),
                serviceClient
            ).orThrow()

            assertEquals(
                1000L * (i + 1),
                FileDescriptions.retrieveQuota.call(
                    RetrieveQuotaRequest(projectHomeDirectory(project.projectId)),
                    serviceClient
                ).orThrow().quotaInBytes
            )

            assertEquals(
                1000L * (i + 1),
                FileDescriptions.retrieveQuota.call(
                    RetrieveQuotaRequest(projectHomeDirectory(root)),
                    serviceClient
                ).orThrow().allocated
            )
        }
    }

    @Test
    fun `check transferring without remaining quota`() = t {
        val root = initializeRootProject()
        val quota = 1024L
        setProjectQuota(root, quota)
        val user = createUser()
        try {
            setPersonalQuota(root, user.username, quota * 2)
            assert(false)
        } catch (ex: RPCException) {
            assertThatInstance(ex.httpStatusCode, "user error") { it.value in 400..499 }
        }
    }

    @Test
    fun `allocating without remaining quota`() = t {
        val root = initializeRootProject()
        val quota = 1024L
        setProjectQuota(root, quota)

        val project = initializeNormalProject(root)
        try {
            setProjectQuota(project.projectId, quota * 2)
            assert(false)
        } catch (ex: RPCException) {
            assertThatInstance(ex.httpStatusCode, "user error") { it.value in 400..499 }
        }
    }

    @Test
    fun `test transfer permissions`() = t {
        val root = initializeRootProject()
        val user = createUser()
        try {
            FileDescriptions.transferQuota.call(
                TransferQuotaRequest(homeDirectory(user.username), 1000L),
                user.client
            ).orThrow()
            assert(false)
        } catch (ex: RPCException) {
            assertThatInstance(ex.httpStatusCode, "fails") { it.value in 400..499 }
        }
    }

    @Test
    fun `test transfer permissions of non UCloud admin`() = t {
        val root = initializeRootProject()
        val project = initializeNormalProject(root)
        val user = createUser()
        FileDescriptions.updateQuota.call(
            UpdateQuotaRequest(projectHomeDirectory(project.projectId), 1024 * 1024 * 1024L),
            serviceClient
        ).orThrow()

        FileDescriptions.transferQuota.call(
            TransferQuotaRequest(homeDirectory(user.username), 1000L),
            project.piClient.withProject(project.projectId)
        ).orThrow()

        try {
            FileDescriptions.transferQuota.call(
                TransferQuotaRequest(homeDirectory(user.username), 1000L),
                user.client.withProject(project.projectId)
            ).orThrow()
            assert(false)
        } catch (ex: RPCException) {
            assertThatInstance(ex.httpStatusCode, "user error") { it.value in 401..499 }
        }
    }

    @Test
    fun `view quota as parent project`() = t {
        val root = initializeRootProject()
        val project = initializeNormalProject(root)
        val user = createUser()

        val child = Projects.create.call(
            CreateProjectRequest("Sub-project", project.projectId),
            project.piClient
        ).orThrow()

        val quota = 1024 * 1024 * 1024L
        setProjectQuota(project.projectId, quota)
        FileDescriptions.updateQuota.call(
            UpdateQuotaRequest(projectHomeDirectory(child.id), quota),
            project.piClient.withProject(child.id)
        ).orThrow()

        addMemberToProject(child.id, project.piClient, user.client, user.username)

        Projects.transferPiRole.call(
            TransferPiRoleRequest(user.username),
            project.piClient.withProject(child.id)
        ).orThrow()

        Projects.leaveProject.call(
            LeaveProjectRequest,
            project.piClient.withProject(child.id)
        ).orThrow()

        assertEquals(
            quota,
            FileDescriptions.retrieveQuota.call(
                RetrieveQuotaRequest(projectHomeDirectory(child.id)),
                project.piClient.withProject(project.projectId)
            ).orThrow().quotaInBytes
        )
    }
}
