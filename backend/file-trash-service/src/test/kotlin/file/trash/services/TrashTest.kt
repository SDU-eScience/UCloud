package dk.sdu.cloud.file.trash.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.LongRunningResponse
import dk.sdu.cloud.file.trash.storageFile
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.test.ClientMock
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
    private lateinit var cloud: AuthenticatedClient
    private lateinit var backgroundScope: BackgroundScope

    private fun mockStat(returnDirectory: Boolean = false) {
        ClientMock.mockCallSuccess(
            FileDescriptions.stat,
            if (returnDirectory) {
                storageFile.copy(fileTypeOrNull = FileType.DIRECTORY)
            } else {
                storageFile
            }
        )
    }

    private fun mockFailingStat() {
        ClientMock.mockCallError(
            FileDescriptions.stat,
            CommonErrorMessage("ERROR"),
            HttpStatusCode.NotFound
        )
    }

    private fun mockMove() {
        ClientMock.mockCallSuccess(
            FileDescriptions.move,
            LongRunningResponse.Result(item = Unit)
        )
    }

    private fun mockCreateDir() {
        ClientMock.mockCallSuccess(
            FileDescriptions.createDirectory,
            LongRunningResponse.Result(item = Unit)
        )
    }

    private fun mockDelete() {
        ClientMock.mockCallSuccess(
            FileDescriptions.deleteFile,
            LongRunningResponse.Result(item = Unit)
        )
    }

    @BeforeTest
    fun initTest() {
        backgroundScope = BackgroundScope()
        backgroundScope.init()

        micro = initializeMicro()
        service = TrashService(
            TrashDirectoryService(),
            ClientMock.authenticatedClient,
            backgroundScope
        )
        cloud = ClientMock.authenticatedClient
    }

    @Test
    fun `Empty Trash -file - Test`() {
        mockStat()
        mockMove()
        mockCreateDir()
        mockDelete()

        runBlocking {
            service.emptyTrash(user.username, cloud, null)
        }
    }

    @Test
    fun `Empty Trash - directory - Test`() {
        mockStat(returnDirectory = true)
        mockDelete()

        runBlocking {
            service.emptyTrash(user.username, cloud, null)
        }
    }

    @Test
    fun `Empty Trash - Trash Bin does not exists - Test`() {
        mockFailingStat()
        mockCreateDir()
        mockDelete()

        runBlocking {
            service.emptyTrash(user.username, cloud, null)
        }
    }

    @Test(expected = RPCException::class)
    fun `Empty Trash - creation fails - Test`() {
        mockFailingStat()
        ClientMock.mockCallError(
            FileDescriptions.createDirectory,
            CommonErrorMessage("ERROR"),
            HttpStatusCode.InternalServerError
        )

        runBlocking {
            service.emptyTrash(user.username, cloud, null)
        }
    }

    @Test
    fun `move to trash test`() {
        mockStat(returnDirectory = true)
        mockMove()
        runBlocking {
            val returnList = service.moveFilesToTrash(listOf("file1", "file2"), user.username, cloud)
            println(returnList)
        }
    }
}
