package dk.sdu.cloud.file.trash.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.FindHomeFolderResponse
import dk.sdu.cloud.file.api.LongRunningResponse
import dk.sdu.cloud.file.trash.storageFile
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.authenticatedCloud
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.BeforeTest

class TrashTest {
    private val user = TestUsers.user
    private lateinit var service: TrashService
    private lateinit var micro: Micro
    private lateinit var cloud: AuthenticatedCloud

    private fun mockStat(returnDirectory: Boolean = false) {
        CloudMock.mockCallSuccess(
            FileDescriptions,
            { FileDescriptions.stat },
            if (returnDirectory) {storageFile.copy(fileType = FileType.DIRECTORY)}
            else { storageFile}
        )
    }

    private fun mockFailingStat() {
        CloudMock.mockCallError(
            FileDescriptions,
            { FileDescriptions.stat },
            CommonErrorMessage("ERROR"),
            HttpStatusCode.NotFound
        )
    }

    private fun mockMove() {
        CloudMock.mockCallSuccess(
            FileDescriptions,
            { FileDescriptions.move },
            LongRunningResponse.Result(item = Unit)
        )
    }

    private fun mockCreateDir() {
        CloudMock.mockCallSuccess(
            FileDescriptions,
            { FileDescriptions.createDirectory },
            LongRunningResponse.Result(item = Unit)
        )
    }

    private fun mockDelete() {
        CloudMock.mockCallSuccess(
            FileDescriptions,
            { FileDescriptions.deleteFile },
            LongRunningResponse.Result(item = Unit)
        )
    }

    private fun mockHomeFolder() {
        CloudMock.mockCallSuccess(
            FileDescriptions,
            {FileDescriptions.findHomeFolder},
            FindHomeFolderResponse("/home/user/")
        )
    }

    @BeforeTest
    fun initTest() {
        micro = initializeMicro()
        service = TrashService()
        cloud = micro.authenticatedCloud
    }

    @Test
    fun `Empty Trash -file - Test`() {
        mockStat()
        mockMove()
        mockCreateDir()
        mockDelete()
        mockHomeFolder()

        runBlocking {
            service.emptyTrash(user.username, cloud)
        }
    }

    @Test
    fun `Empty Trash - directory - Test`() {
        mockStat(returnDirectory = true)
        mockDelete()
        mockHomeFolder()

        runBlocking {
            service.emptyTrash(user.username, cloud)
        }
    }

    @Test
    fun `Empty Trash - Trash Bin does not exists - Test`() {
        mockFailingStat()
        mockCreateDir()
        mockDelete()
        mockHomeFolder()

        runBlocking {
            service.emptyTrash(user.username, cloud)
        }
    }

    @Test (expected = RPCException::class)
    fun `Empty Trash - creation fails - Test`() {
        mockFailingStat()
        mockHomeFolder()
        CloudMock.mockCallError(
            FileDescriptions,
            { FileDescriptions.createDirectory },
            CommonErrorMessage("ERROR"),
            HttpStatusCode.InternalServerError
        )

        runBlocking {
            service.emptyTrash(user.username, cloud)
        }
    }

    @Test
    fun `move to trash test`() {
        mockStat(returnDirectory = true)
        mockMove()
        mockHomeFolder()
        runBlocking {
            val returnList = service.moveFilesToTrash(listOf("file1", "file2"), user.username, cloud)
            println(returnList)
        }
    }
}
