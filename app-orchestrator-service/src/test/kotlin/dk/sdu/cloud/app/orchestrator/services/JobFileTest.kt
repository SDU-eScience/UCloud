package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.utils.verifiedJob
import dk.sdu.cloud.app.orchestrator.utils.verifiedJobWithAccessToken
import dk.sdu.cloud.app.orchestrator.utils.verifiedJobWithAccessToken2
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.indexing.api.LookupDescriptions
import dk.sdu.cloud.indexing.api.ReverseLookupResponse
import dk.sdu.cloud.service.test.ClientMock
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JobFileTest{

    @Test
    fun `initialize Result folder test`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService(authClient,  { _, _ -> ClientMock.authenticatedClient }, ParameterExportService())

        ClientMock.mockCallSuccess(
            FileDescriptions.stat,
            StorageFile(
                FileType.DIRECTORY,
                "/home/Jobs/title/somefolder",
                12345678,
                1234567,
                "user",
                7891234,
                emptyList(),
                SensitivityLevel.PRIVATE,
                emptySet(), "123",
                "user",
                SensitivityLevel.PRIVATE)
        )

        ClientMock.mockCallSuccess(
            FileDescriptions.findHomeFolder,
            FindHomeFolderResponse("home")
        )
        ClientMock.mockCallSuccess(
            FileDescriptions.createDirectory,
            LongRunningResponse.Result(item = Unit)
        )

        runBlocking {
            service.initializeResultFolder(verifiedJobWithAccessToken)
        }
    }

    @Test
    fun `test accept File - no extract also absolute path`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService(authClient, { _, _ -> authClient }, ParameterExportService())

        ClientMock.mockCallSuccess(
            FileDescriptions.findHomeFolder,
            FindHomeFolderResponse("home")
        )

        ClientMock.mockCallSuccess(
            MultiPartUploadDescriptions.simpleUpload,
            Unit
        )

        runBlocking {
            service.acceptFile(
                verifiedJobWithAccessToken,
                "/filepath",
                2000,
                ByteReadChannel.Empty,
                false
            )

        }
    }

    @Test
    fun `test accept File - with extract`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService(authClient, { _, _ -> authClient }, ParameterExportService())

        ClientMock.mockCallSuccess(
            FileDescriptions.findHomeFolder,
            FindHomeFolderResponse("home")
        )

        ClientMock.mockCallSuccess(
            MultiPartUploadDescriptions.simpleUpload,
            Unit
        )

        ClientMock.mockCallSuccess(
            FileDescriptions.extract,
            Unit
        )

        runBlocking {
            service.acceptFile(
                verifiedJobWithAccessToken,
                "filepath",
                2000,
                ByteReadChannel.Empty,
                true
            )

        }
    }

    @Test
    fun `jobFolder test`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService(authClient, { _, _ -> ClientMock.authenticatedClient }, ParameterExportService())

        ClientMock.mockCallSuccess(
            FileDescriptions.findHomeFolder,
            FindHomeFolderResponse("home")
        )

        runBlocking {
            val result = service.jobFolder(verifiedJob)
            assertTrue(result.startsWith("/home/Jobs/title"))
        }
    }

    @Test
    fun `jobFolder test2`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService(authClient, { _, _ -> ClientMock.authenticatedClient }, ParameterExportService())

        ClientMock.mockCallSuccess(
            FileDescriptions.findHomeFolder,
            FindHomeFolderResponse("home")
        )

        ClientMock.mockCallSuccess(
            FileDescriptions.createDirectory,
            LongRunningResponse.Result(Unit)
        )

        ClientMock.mockCallSuccess(
            FileDescriptions.stat,
            StorageFile(
                FileType.DIRECTORY,
                "/home/Jobs/title/testfolder",
                12345678,
                1234567,
                "user",
                7891234,
                emptyList(),
                SensitivityLevel.PRIVATE,
                emptySet(), "1234",
                "user",
                SensitivityLevel.PRIVATE)
        )

        ClientMock.mockCallSuccess(
            LookupDescriptions.reverseLookup,
            ReverseLookupResponse(listOf("/home/Jobs/title/testfolder"))
        )

        runBlocking {
            assertNull(verifiedJobWithAccessToken2.job.folderId)
            assertEquals("/home/Jobs/title/01-01-1970 04:25:45.678", service.jobFolder(verifiedJobWithAccessToken2.job))
            assertEquals("/home/Jobs/title/verifiedId", service.jobFolder(verifiedJobWithAccessToken2.job, true))
        }

        runBlocking {
            service.initializeResultFolder(verifiedJobWithAccessToken2)
        }

        runBlocking {
            assertEquals("1234", verifiedJobWithAccessToken2.job.folderId)
            assertEquals("/home/Jobs/title/testfolder", service.jobFolder(verifiedJobWithAccessToken2.job))
        }
    }
}
