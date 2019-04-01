package dk.sdu.cloud.app.abacus.service

import dk.sdu.cloud.app.abacus.services.JobFileException
import dk.sdu.cloud.app.abacus.services.JobFileService
import dk.sdu.cloud.app.abacus.services.ssh.SSHConnection
import dk.sdu.cloud.app.abacus.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.abacus.services.ssh.mkdir
import dk.sdu.cloud.app.abacus.services.ssh.rm
import dk.sdu.cloud.app.abacus.services.ssh.scpUpload
import dk.sdu.cloud.app.abacus.services.ssh.unzip
import dk.sdu.cloud.app.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.api.FileForUploadArchiveType
import dk.sdu.cloud.app.api.ValidatedFileForUpload
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.service.test.ClientMock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.BeforeTest

class JobFileServiceTest {
    private val connectionPool: SSHConnectionPool = mockk(relaxed = true)
    private val connection: SSHConnection = mockk(relaxed = true)
    private val service: JobFileService
    private val workingDirectory = "/work/"

    init {
        val cloud = ClientMock.authenticatedClient
        every { connectionPool.borrowConnection() } returns Pair(0, connection)

        service = JobFileService(connectionPool, cloud, workingDirectory)
    }

    @BeforeTest
    fun before() {
        mockkStatic("dk.sdu.cloud.app.abacus.services.ssh.SFTPKt")
        mockkStatic("dk.sdu.cloud.app.abacus.services.ssh.SCPKt")
        mockkStatic("dk.sdu.cloud.app.abacus.services.ssh.ZIPKt")
        mockkObject(ComputationCallbackDescriptions)
    }

    @Test
    fun `test job initialization`() = runBlocking {
        coEvery { connection.execWithOutputAsText(match { it.startsWith("mkdir") }) } returns Pair(0, "")
        service.initializeJob("testing")
    }

    @Test(expected = JobFileException.UnableToCreateFile::class)
    fun `test job initialization with failure`() = runBlocking {
        coEvery { connection.execWithOutputAsText(match { it.startsWith("mkdir") }) } returns Pair(1, "")
        service.initializeJob("testing")
    }

    @Test
    fun `test upload file`() = runBlocking {
        coEvery { connection.mkdir(any(), any()) } returns 0
        coEvery { connection.scpUpload(any(), any(), any(), any(), any()) } returns 0
        val paramName = "bar"
        ClientMock.mockCallSuccess(
            ComputationCallbackDescriptions.lookup, JobData.job.copy(
                files = listOf(
                    ValidatedFileForUpload(
                        paramName,
                        StorageFile(FileType.FILE, "/home/foo/bar", ownerName = "foo"),
                        "foo",
                        "./foo",
                        "./foo",
                        null
                    )
                )
            )
        )

        service.uploadFile(
            "jobId",
            paramName,
            null,
            ByteReadChannel("Hello")
        )
    }

    /*
        @Ignore("Mockk 1.8.13kotlin13 bytecode verification issue")
        @Test(expected = JobFileException.ErrorDuringTransfer::class)
        fun `test upload file with failure (status)`() = runBlocking {
            coEvery { connection.mkdir(any(), any()) } returns 0
            coEvery { connection.scpUpload(any(), any(), any(), any(), any()) } returns 1

            val channel = ByteReadChannel("Hello")
            service.uploadFile(
                "jobId",
                "./foo",
                42L,
                null,
                channel
            )
            Unit
        }

        @Ignore("Mockk 1.8.13kotlin13 bytecode verification issue")
        @Test(expected = JobFileException.ErrorDuringTransfer::class)
        fun `test upload file with failure (exception)`() = runBlocking {
            coEvery { connection.mkdir(any(), any()) } returns 0
            coEvery { connection.scpUpload(any(), any(), any(), any(), any()) } throws IOException("BAD!")
            val channel = ByteReadChannel("Hello")
            service.uploadFile(
                "jobId",
                "./foo",
                42L,
                null,
                channel
            )
        }
    */
    @Test
    fun `test upload file with extraction`() = runBlocking {
        coEvery { connection.mkdir(any(), any()) } returns 0
        coEvery { connection.rm(any(), any()) } returns 0
        coEvery { connection.scpUpload(any(), any(), any(), any(), any()) } returns 0
        coEvery { connection.unzip(any(), any()) } returns 0

        val paramName = "bar"
        ClientMock.mockCallSuccess(
            ComputationCallbackDescriptions.lookup, JobData.job.copy(
                files = listOf(
                    ValidatedFileForUpload(
                        paramName,
                        StorageFile(FileType.FILE, "/home/foo/bar", ownerName = "foo"),
                        "foo",
                        "./foo",
                        "./foo",
                        FileForUploadArchiveType.ZIP
                    )
                )
            )
        )

        service.uploadFile(
            "jobId",
            paramName,
            null,
            ByteReadChannel("Hello")
        )

        coVerify { connection.unzip(any(), any()) }
    }

    @Test(expected = JobFileException.CouldNotExtractArchive::class)
    fun `test upload file with extraction and failure`() = runBlocking {
        coEvery { connection.mkdir(any(), any()) } returns 0
        coEvery { connection.scpUpload(any(), any(), any(), any(), any()) } returns 0
        coEvery { connection.unzip(any(), any()) } returns 5

        val paramName = "bar"
        ClientMock.mockCallSuccess(
            ComputationCallbackDescriptions.lookup, JobData.job.copy(
                files = listOf(
                    ValidatedFileForUpload(
                        paramName,
                        StorageFile(FileType.FILE, "/home/foo/bar", ownerName = "foo"),
                        "foo",
                        "./foo",
                        "./foo",
                        FileForUploadArchiveType.ZIP
                    )
                )
            )
        )

        service.uploadFile(
            "jobId",
            paramName,
            null,
            ByteReadChannel("Hello")
        )

        coVerify { connection.unzip(any(), any()) }
    }

    @Test
    fun `test cleanup`() = runBlocking {
        coEvery { connection.rm(any(), any(), any()) } returns 0
        service.cleanup("jobId")

        coVerify { connection.rm(any(), any(), any()) }
    }

    @Test
    fun `test cleanup with failure`() = runBlocking {
        // We don't handle failure in any special way
        coEvery { connection.rm(any(), any(), any()) } returns 1
        service.cleanup("jobId")

        coVerify { connection.rm(any(), any(), any()) }
    }
/*
    @Ignore("Mockk 1.8.13kotlin13 bytecode verification issue")
    @Test
    fun `test transfer compute results (no results)`() = runBlocking {
        coEvery { connection.lsWithGlob(any(), any()) } returns emptyList()
        service.transferForJob(job)
    }

    @Ignore("Mockk 1.8.13kotlin13 bytecode verification issue")
    @Test
    fun `test transfer compute results (single file)`() = runBlocking {
        coEvery { connection.lsWithGlob(any(), any()) } returns listOf(
            LSWithGlobResult("/work/someId/files/stdout.txt", 10L)
        )

        val attrs = mockk<SftpATTRS>(relaxed = true)
        every { attrs.isDir } returns false

        every { connection.stat(any()) } returns attrs

        val lambda = CapturingSlot<suspend (InputStream) -> Unit>()
        coEvery { connection.scpDownload(any(), capture(lambda)) } coAnswers {
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

    @Ignore("Mockk 1.8.13kotlin13 bytecode verification issue")
    @Test
    fun `test transfer compute results (single directory)`() = runBlocking {
        coEvery { connection.lsWithGlob(any(), any()) } returns listOf(
            LSWithGlobResult("/work/someId/files/directory", 10L)
        )

        val attrs = mockk<SftpATTRS>(relaxed = true)
        every { attrs.isDir } returns true

        every { connection.stat(any()) } returns attrs

        coEvery { connection.createZipFileOfDirectory(any(), any()) } returns 0

        val lambda = CapturingSlot<suspend (InputStream) -> Unit>()
        coEvery { connection.scpDownload(any(), capture(lambda)) } coAnswers {
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
        coVerify { connection.createZipFileOfDirectory(any(), any()) }
        coVerify { ComputationCallbackDescriptions.submitFile.call(any(), any()) }
    }

    @Ignore("Mockk 1.8.13kotlin13 bytecode verification issue")
    @Test(expected = JobFileException.ArchiveCreationFailed::class)
    fun `test transfer compute results (single directory - zip failure)`() = runBlocking {
        coEvery { connection.lsWithGlob(any(), any()) } returns listOf(
            LSWithGlobResult("/work/someId/files/directory", 10L)
        )

        val attrs = mockk<SftpATTRS>(relaxed = true)
        every { attrs.isDir } returns true

        every { connection.stat(any()) } returns attrs

        coEvery { connection.createZipFileOfDirectory(any(), any()) } returns 1

        val lambda = CapturingSlot<suspend (InputStream) -> Unit>()
        coEvery { connection.scpDownload(any(), capture(lambda)) } coAnswers {
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
        coVerify { connection.createZipFileOfDirectory(any(), any()) }
        coVerify { ComputationCallbackDescriptions.submitFile.call(any(), any()) }
    }

    @Ignore("Mockk 1.8.13kotlin13 bytecode verification issue")
    @Test(expected = JobFileException.UploadToCloudFailed::class)
    fun `test transfer compute results (single file - scpDownload error)`() = runBlocking {
        coEvery { connection.lsWithGlob(any(), any()) } returns listOf(
            LSWithGlobResult("/work/someId/files/stdout.txt", 10L)
        )

        val attrs = mockk<SftpATTRS>(relaxed = true)
        every { attrs.isDir } returns false

        every { connection.stat(any()) } returns attrs

        val lambda = CapturingSlot<suspend (InputStream) -> Unit>()
        coEvery { connection.scpDownload(any(), capture(lambda)) } answers {
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

    @Ignore("Mockk 1.8.13kotlin13 bytecode verification issue")
    @Test
    fun `test transfer compute results (single file - stat error)`() = runBlocking {
        coEvery { connection.lsWithGlob(any(), any()) } returns listOf(
            LSWithGlobResult("/work/someId/files/stdout.txt", 10L)
        )

        val attrs = mockk<SftpATTRS>(relaxed = true)
        every { attrs.isDir } returns false

        every { connection.stat(any()) } returns null
        service.transferForJob(job)
    }
*/
}
