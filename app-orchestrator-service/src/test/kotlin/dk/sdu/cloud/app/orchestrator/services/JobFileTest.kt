package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.utils.verifiedJob
import dk.sdu.cloud.app.orchestrator.utils.verifiedJobWithAccessToken
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderResponse
import dk.sdu.cloud.file.api.LongRunningResponse
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.service.test.ClientMock
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue

class JobFileTest{

    @Test
    fun `initialize Result folder test`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService(authClient,  { _, _ -> ClientMock.authenticatedClient }, ParameterExportService())

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
}
