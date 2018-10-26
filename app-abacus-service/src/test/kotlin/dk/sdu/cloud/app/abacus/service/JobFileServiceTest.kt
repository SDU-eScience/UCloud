package dk.sdu.cloud.app.abacus.service

import com.jcraft.jsch.SftpATTRS
import dk.sdu.cloud.app.abacus.services.JobFileException
import dk.sdu.cloud.app.abacus.services.JobFileService
import dk.sdu.cloud.app.abacus.services.ssh.LSWithGlobResult
import dk.sdu.cloud.app.abacus.services.ssh.SSHConnection
import dk.sdu.cloud.app.abacus.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.abacus.services.ssh.createZipFileOfDirectory
import dk.sdu.cloud.app.abacus.services.ssh.lsWithGlob
import dk.sdu.cloud.app.abacus.services.ssh.mkdir
import dk.sdu.cloud.app.abacus.services.ssh.rm
import dk.sdu.cloud.app.abacus.services.ssh.scpDownload
import dk.sdu.cloud.app.abacus.services.ssh.scpUpload
import dk.sdu.cloud.app.abacus.services.ssh.stat
import dk.sdu.cloud.app.abacus.services.ssh.unzip
import dk.sdu.cloud.app.api.Application
import dk.sdu.cloud.app.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.api.FileForUploadArchiveType
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedApplicationDescription
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.Tool
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.app.api.WordInvocationParameter
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.objectMockk
import io.mockk.staticMockk
import io.mockk.verify
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.BeforeTest

class JobFileServiceTest {
    private val connectionPool: SSHConnectionPool = mockk(relaxed = true)
    private val connection: SSHConnection = mockk(relaxed = true)
    private val cloud: RefreshingJWTAuthenticatedCloud
    private val service: JobFileService
    private val workingDirectory = "/work/"
    private val tool = Tool(
        "appOwner",
        System.currentTimeMillis(),
        System.currentTimeMillis(),
        NormalizedToolDescription(
            NameAndVersion("tool", "1"),
            "container",
            1,
            1,
            SimpleDuration(1, 0, 0),
            emptyList(),
            listOf("Author"),
            "tool",
            "tool",
            ToolBackend.SINGULARITY
        )
    )
    private val application = Application(
        "appOwner",
        System.currentTimeMillis(),
        System.currentTimeMillis(),
        NormalizedApplicationDescription(
            NameAndVersion("app", "1"),
            tool.description.info,
            listOf("Author"),
            "app",
            "appDescription",
            listOf(WordInvocationParameter("test")),
            emptyList(),
            listOf("stdout.txt", "stderr.txt"),
            emptyList()
        ),
        tool
    )
    private val job: VerifiedJob = VerifiedJob(
        application,
        emptyList(),
        "someId",
        "someOwner",
        1,
        1,
        SimpleDuration(1, 0, 0),
        VerifiedJobInput(emptyMap()),
        "abacus",
        JobState.TRANSFER_SUCCESS
    )

    init {
        every { connectionPool.borrowConnection() } returns Pair(0, connection)

        cloud = mockk(relaxed = true)

        service = JobFileService(connectionPool, cloud, workingDirectory)

        staticMockk("dk.sdu.cloud.app.abacus.services.ssh.SFTPKt").mock()
        staticMockk("dk.sdu.cloud.app.abacus.services.ssh.SCPKt").mock()
        staticMockk("dk.sdu.cloud.app.abacus.services.ssh.ZIPKt").mock()

        objectMockk(ComputationCallbackDescriptions).mock()
    }

    @BeforeTest
    fun before() {
        val scopes = listOf(
            staticMockk("dk.sdu.cloud.app.abacus.services.ssh.SFTPKt"),
            staticMockk("dk.sdu.cloud.app.abacus.services.ssh.SCPKt"),
            staticMockk("dk.sdu.cloud.app.abacus.services.ssh.ZIPKt"),
            objectMockk(ComputationCallbackDescriptions)
        )

        scopes.forEach { it.unmock() }
        scopes.forEach { it.mock() }
    }

    @Test
    fun `test job initialization`() {
        every { connection.execWithOutputAsText(match { it.startsWith("mkdir") }) } returns Pair(0, "")
        service.initializeJob("testing")
    }

    @Test(expected = JobFileException.UnableToCreateFile::class)
    fun `test job initialization with failure`() {
        every { connection.execWithOutputAsText(match { it.startsWith("mkdir") }) } returns Pair(1, "")
        service.initializeJob("testing")
    }

    @Test
    fun `test upload file`() {
        every { connection.mkdir(any(), any()) } returns 0
        every { connection.scpUpload(any(), any(), any(), any(), any()) } returns 0
        val fileBody = "Hello"
        service.uploadFile("jobId", "./foo", 42L, null, ByteArrayInputStream(fileBody.toByteArray()))
    }

    @Test(expected = JobFileException.ErrorDuringTransfer::class)
    fun `test upload file with failure (status)`() {
        every { connection.mkdir(any(), any()) } returns 0
        every { connection.scpUpload(any(), any(), any(), any(), any()) } returns 1
        val fileBody = "Hello"
        service.uploadFile("jobId", "./foo", 42L, null, ByteArrayInputStream(fileBody.toByteArray()))
    }

    @Test(expected = JobFileException.ErrorDuringTransfer::class)
    fun `test upload file with failure (exception)`() {
        every { connection.mkdir(any(), any()) } returns 0
        every { connection.scpUpload(any(), any(), any(), any(), any()) } throws IOException("BAD!")
        val fileBody = "Hello"
        service.uploadFile("jobId", "./foo", 42L, null, ByteArrayInputStream(fileBody.toByteArray()))
    }

    @Test
    fun `test upload file with extraction`() {
        every { connection.mkdir(any(), any()) } returns 0
        every { connection.scpUpload(any(), any(), any(), any(), any()) } returns 0
        every { connection.unzip(any(), any()) } returns 0
        val fileBody = "Hello"
        service.uploadFile(
            "jobId",
            "./foo",
            42L,
            FileForUploadArchiveType.ZIP,
            ByteArrayInputStream(fileBody.toByteArray())
        )

        verify { connection.unzip(any(), any()) }
    }

    @Test(expected = JobFileException.CouldNotExtractArchive::class)
    fun `test upload file with extraction and failure`() {
        every { connection.mkdir(any(), any()) } returns 0
        every { connection.scpUpload(any(), any(), any(), any(), any()) } returns 0
        every { connection.unzip(any(), any()) } returns 5
        val fileBody = "Hello"
        service.uploadFile(
            "jobId",
            "./foo",
            42L,
            FileForUploadArchiveType.ZIP,
            ByteArrayInputStream(fileBody.toByteArray())
        )

        verify { connection.unzip(any(), any()) }
    }

    @Test
    fun `test cleanup`() {
        every { connection.rm(any(), any(), any()) } returns 0
        service.cleanup("jobId")

        verify { connection.rm(any(), any(), any()) }
    }

    @Test
    fun `test cleanup with failure`() {
        // We don't handle failure in any special way
        every { connection.rm(any(), any(), any()) } returns 1
        service.cleanup("jobId")

        verify { connection.rm(any(), any(), any()) }
    }

    @Test
    fun `test transfer compute results (no results)`() {
        every { connection.lsWithGlob(any(), any()) } returns emptyList()
        service.transferForJob(job)
    }

    @Test
    fun `test transfer compute results (single file)`() {
        every { connection.lsWithGlob(any(), any()) } returns listOf(
            LSWithGlobResult("/work/someId/files/stdout.txt", 10L)
        )

        val attrs = mockk<SftpATTRS>(relaxed = true)
        every { attrs.isDir } returns false

        every { connection.stat(any()) } returns attrs

        val lambda = CapturingSlot<(InputStream) -> Unit>()
        every { connection.scpDownload(any(), capture(lambda)) } answers {
            lambda.captured(ByteArrayInputStream("text".toByteArray()))
            0
        }

        coEvery {
            ComputationCallbackDescriptions.submitFile.call(
                any(),
                any()
            )
        } returns RESTResponse.Ok(mockk(relaxed = true), Unit)

        service.transferForJob(job)

        coVerify { ComputationCallbackDescriptions.submitFile.call(any(), any()) }
    }

    @Test
    fun `test transfer compute results (single directory)`() {
        every { connection.lsWithGlob(any(), any()) } returns listOf(
            LSWithGlobResult("/work/someId/files/directory", 10L)
        )

        val attrs = mockk<SftpATTRS>(relaxed = true)
        every { attrs.isDir } returns true

        every { connection.stat(any()) } returns attrs

        every { connection.createZipFileOfDirectory(any(), any()) } returns 0

        val lambda = CapturingSlot<(InputStream) -> Unit>()
        every { connection.scpDownload(any(), capture(lambda)) } answers {
            lambda.captured(ByteArrayInputStream("text".toByteArray()))
            0
        }

        coEvery {
            ComputationCallbackDescriptions.submitFile.call(
                any(),
                any()
            )
        } returns RESTResponse.Ok(mockk(relaxed = true), Unit)

        service.transferForJob(job)
        verify { connection.createZipFileOfDirectory(any(), any()) }
        coVerify { ComputationCallbackDescriptions.submitFile.call(any(), any()) }
    }

    @Test(expected = JobFileException.ArchiveCreationFailed::class)
    fun `test transfer compute results (single directory - zip failure)`() {
        every { connection.lsWithGlob(any(), any()) } returns listOf(
            LSWithGlobResult("/work/someId/files/directory", 10L)
        )

        val attrs = mockk<SftpATTRS>(relaxed = true)
        every { attrs.isDir } returns true

        every { connection.stat(any()) } returns attrs

        every { connection.createZipFileOfDirectory(any(), any()) } returns 1

        val lambda = CapturingSlot<(InputStream) -> Unit>()
        every { connection.scpDownload(any(), capture(lambda)) } answers {
            lambda.captured(ByteArrayInputStream("text".toByteArray()))
            0
        }

        coEvery {
            ComputationCallbackDescriptions.submitFile.call(
                any(),
                any()
            )
        } returns RESTResponse.Ok(mockk(relaxed = true), Unit)

        service.transferForJob(job)
        verify { connection.createZipFileOfDirectory(any(), any()) }
        coVerify { ComputationCallbackDescriptions.submitFile.call(any(), any()) }
    }

    @Test(expected = JobFileException.UploadToCloudFailed::class)
    fun `test transfer compute results (single file - scpDownload error)`() {
        every { connection.lsWithGlob(any(), any()) } returns listOf(
            LSWithGlobResult("/work/someId/files/stdout.txt", 10L)
        )

        val attrs = mockk<SftpATTRS>(relaxed = true)
        every { attrs.isDir } returns false

        every { connection.stat(any()) } returns attrs

        val lambda = CapturingSlot<(InputStream) -> Unit>()
        every { connection.scpDownload(any(), capture(lambda)) } answers {
            1
        }

        coEvery {
            ComputationCallbackDescriptions.submitFile.call(
                any(),
                any()
            )
        } returns RESTResponse.Ok(mockk(relaxed = true), Unit)

        service.transferForJob(job)
    }

    @Test
    fun `test transfer compute results (single file - stat error)`() {
        every { connection.lsWithGlob(any(), any()) } returns listOf(
            LSWithGlobResult("/work/someId/files/stdout.txt", 10L)
        )

        val attrs = mockk<SftpATTRS>(relaxed = true)
        every { attrs.isDir } returns false

        every { connection.stat(any()) } returns null
        service.transferForJob(job)
    }
}
