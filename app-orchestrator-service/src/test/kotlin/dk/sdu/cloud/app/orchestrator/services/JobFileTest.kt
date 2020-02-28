package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.orchestrator.api.ComputationDescriptions
import dk.sdu.cloud.app.orchestrator.api.FileForUploadArchiveType
import dk.sdu.cloud.app.orchestrator.api.ValidatedFileForUpload
import dk.sdu.cloud.app.orchestrator.utils.verifiedJob
import dk.sdu.cloud.app.orchestrator.utils.verifiedJobWithAccessToken
import dk.sdu.cloud.app.orchestrator.utils.verifiedJobWithAccessToken2
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.indexing.api.LookupDescriptions
import dk.sdu.cloud.indexing.api.ReverseLookupResponse
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestUsers
import io.mockk.mockk
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.math.exp
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
            val result = service.jobFolder(verifiedJobWithAccessToken)
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

        val myJob = verifiedJobWithAccessToken2.copy(
            job = verifiedJobWithAccessToken2.job.copy(id = "myJobId")
        )

        runBlocking {
            assertNull(verifiedJobWithAccessToken2.job.folderId)
            assertEquals("/home/Jobs/title/01-01-1970 04.25.45.678", service.jobFolder(verifiedJobWithAccessToken2))
            assertEquals(
                "/home/Jobs/title/myJobId",
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
        val service = JobFileService(authClient, { _, _ -> ClientMock.authenticatedClient }, ParameterExportService())

        ClientMock.mockCallSuccess(
            FileDescriptions.findHomeFolder,
            FindHomeFolderResponse("home")
        )


        ClientMock.mockCallSuccess(
            LookupDescriptions.reverseLookup,
            ReverseLookupResponse(listOf("/home/Jobs/title/testfolder"))
        )

        val myJob = verifiedJobWithAccessToken2.copy(
            job = verifiedJobWithAccessToken2.job.copy(id = "myJobId", folderId = "/path/to/folder")
        )

        runBlocking {
            val result = service.jobFolder(myJob)
            assertEquals("/home/Jobs/title/testfolder", result)
        }
    }

    @Test
    fun `create workspace test`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService(authClient, { _, _ -> ClientMock.authenticatedClient }, ParameterExportService())

        ClientMock.mockCallSuccess(
            WorkspaceDescriptions.create,
            Workspaces.Create.Response("ID", emptyList(), WorkspaceMode.COPY_FILES, null)
        )
        runBlocking {
            service.createWorkspace(verifiedJobWithAccessToken)
        }
    }

    @Test (expected = RPCException::class)
    fun `transfer workspace test - no workspace found`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService(authClient, { _, _ -> ClientMock.authenticatedClient }, ParameterExportService())

        runBlocking {
            service.transferWorkspace(verifiedJobWithAccessToken, false)
        }
    }

    @Test
    fun `transfer workspace test - no workspace found - try replay`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService(authClient, { _, _ -> ClientMock.authenticatedClient }, ParameterExportService())

        runBlocking {
            service.transferWorkspace(verifiedJobWithAccessToken, true)
        }
    }

    @Test
    fun `transfer workspace test`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService(authClient, { _, _ -> ClientMock.authenticatedClient }, ParameterExportService())

        ClientMock.mockCallSuccess(
            WorkspaceDescriptions.transfer,
            Workspaces.Transfer.Response(emptyList())
        )

        runBlocking {
            service.transferWorkspace(verifiedJobWithAccessToken.copy(verifiedJob.copy(workspace = "path/To/workspace")), false)
        }
    }

    //PROBLEMS WITH MOCKCALL TO SUBMITFILE
    /*object TestBackend: ComputationDescriptions("backend")

    @Test
    fun `Transfer files to backend test`() {
        val authClient = ClientMock.authenticatedClient
        val service = JobFileService(authClient, { _, _ -> ClientMock.authenticatedClient }, ParameterExportService())

        ClientMock.mockCallSuccess(
            FileDescriptions.download,
            mockk<BinaryStream>(relaxed = true)
        )

        ClientMock.mockCallSuccess(
            ComputationCallbackDescriptions.submitFile,
            Unit
        )

        runBlocking {
            service.transferFilesToBackend(
                verifiedJobWithAccessToken.copy(
                    verifiedJob.copy(
                        files = listOf(
                            ValidatedFileForUpload(
                                "id",
                                StorageFile(FileType.FILE, "path", ownerName = TestUsers.user.username),
                                "dest",
                                "destPath",
                                "srcPath",
                                FileForUploadArchiveType.ZIP
                            )
                        )
                    )
                ),
                TestBackend
            )
        }
    }*/
}
