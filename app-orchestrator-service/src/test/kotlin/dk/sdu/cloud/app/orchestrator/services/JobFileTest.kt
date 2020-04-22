package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.utils.verifiedJobWithAccessToken
import dk.sdu.cloud.app.orchestrator.utils.verifiedJobWithAccessToken2
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.service.test.ClientMock
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JobFileTest {

    @Test
    fun `initialize Result folder test`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService(
            { _, _ -> ClientMock.authenticatedClient },
            ParameterExportService(),
            ClientMock.authenticatedClient
        )

        ClientMock.mockCallSuccess(
            FileDescriptions.stat,
            StorageFile(
                fileType = FileType.DIRECTORY,
                path = "/home/Jobs/title/somefolder",
                createdAt = 12345678,
                modifiedAt = 1234567,
                ownerName = "user",
                size = 7891234,
                acl = emptyList(),
                sensitivityLevel = SensitivityLevel.PRIVATE,
                ownSensitivityLevel = SensitivityLevel.PRIVATE
            )
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
        val service = JobFileService({ _, _ -> authClient }, ParameterExportService(), ClientMock.authenticatedClient)

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
                ByteReadChannel.Empty
            )

        }
    }

    @Test
    fun `test accept File - with extract`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService({ _, _ -> authClient }, ParameterExportService(), ClientMock.authenticatedClient)

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
                ByteReadChannel.Empty
            )

        }
    }

    @Test
    fun `jobFolder test`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService(
            { _, _ -> ClientMock.authenticatedClient },
            ParameterExportService(),
            ClientMock.authenticatedClient
        )

        runBlocking {
            val result = service.jobFolder(verifiedJobWithAccessToken)
            assertTrue(result.startsWith("/home/${verifiedJobWithAccessToken.job.owner}/Jobs/title"))
        }
    }

    @Test
    fun `jobFolder test2`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService(
            { _, _ -> ClientMock.authenticatedClient },
            ParameterExportService(),
            ClientMock.authenticatedClient
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
                SensitivityLevel.PRIVATE
            )
        )

        val myJob = verifiedJobWithAccessToken2.copy(
            job = verifiedJobWithAccessToken2.job.copy(id = "myJobId")
        )

        runBlocking {
            assertEquals(
                "/home/${verifiedJobWithAccessToken2.job.owner}/Jobs/title/01-01-1970 04.25.45.678",
                service.jobFolder(verifiedJobWithAccessToken2)
            )
            assertEquals(
                "/home/${verifiedJobWithAccessToken2.job.owner}/Jobs/title/myJobId",
                service.jobFolder(
                    myJob,
                    true
                )
            )
        }

        runBlocking {
            service.initializeResultFolder(verifiedJobWithAccessToken2)
        }
    }

    @Test
    fun `jobFolder test - folder not null`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService(
            { _, _ -> ClientMock.authenticatedClient },
            ParameterExportService(),
            ClientMock.authenticatedClient
        )

        ClientMock.mockCallSuccess(
            FileDescriptions.findHomeFolder,
            FindHomeFolderResponse("home")
        )


        val myJob = verifiedJobWithAccessToken2.copy(
            job = verifiedJobWithAccessToken2.job.copy(id = "myJobId", outputFolder = "/home/Jobs/title/testfolder")
        )

        runBlocking {
            val result = service.jobFolder(myJob)
            assertEquals("/home/Jobs/title/testfolder", result)
        }
    }
}
