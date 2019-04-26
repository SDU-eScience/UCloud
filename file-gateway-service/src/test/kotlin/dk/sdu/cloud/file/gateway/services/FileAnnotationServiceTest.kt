package dk.sdu.cloud.file.gateway.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.ownerName
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.favorite.api.FavoriteStatusResponse
import dk.sdu.cloud.file.favorite.api.FileFavoriteDescriptions
import dk.sdu.cloud.file.gateway.api.FileResource
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.initializeMicro
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileAnnotationServiceTest {
    private val service = FileAnnotationService()
    private lateinit var micro: Micro

    private val fileA = StorageFile(
        fileId = "fileA",
        fileType = FileType.FILE,
        path = "/home/user/fileA.txt",
        ownerName = "user"
    )

    private val fileB = StorageFile(
        fileId = "fileB",
        fileType = FileType.FILE,
        path = "/home/user/fileB.txt",
        ownerName = "user"
    )

    @BeforeTest
    fun setupTest() {
        micro = initializeMicro()
    }

    @Test
    fun `test loading of no resources`(): Unit = runBlocking {
        val annotated = service.annotate(emptySet(), listOf(fileA, fileB), ClientMock.authenticatedClient)

        assertThatPropertyEquals(annotated, { it.size }, 2)

        val (firstFile, secondFile) = annotated
        assertEquals(fileA.fileType, firstFile.fileType)
        assertEquals(fileA.path, firstFile.path)
        assertEquals(fileA.ownerName, firstFile.ownerName)
        assertNull(firstFile.favorited)

        assertEquals(fileB.fileType, secondFile.fileType)
        assertEquals(fileB.path, secondFile.path)
        assertEquals(fileB.ownerName, secondFile.ownerName)
        assertNull(secondFile.favorited)
        return@runBlocking
    }

    @Test
    fun `test loading of favorites`(): Unit = runBlocking {
        ClientMock.mockCallSuccess(
            FileFavoriteDescriptions.favoriteStatus,
            FavoriteStatusResponse(
                favorited = mapOf(
                    fileA.fileId to true,
                    fileB.fileId to false
                )
            )
        )

        val annotated =
            service.annotate(setOf(FileResource.FAVORITES), listOf(fileA, fileB), ClientMock.authenticatedClient)

        assertThatPropertyEquals(annotated, { it.size }, 2)

        val (firstFile, secondFile) = annotated
        assertEquals(fileA.fileType, firstFile.fileType)
        assertEquals(fileA.path, firstFile.path)
        assertEquals(fileA.ownerName, firstFile.ownerName)
        assert(firstFile.favorited == true)

        assertEquals(fileB.fileType, secondFile.fileType)
        assertEquals(fileB.path, secondFile.path)
        assertEquals(fileB.ownerName, secondFile.ownerName)
        assert(secondFile.favorited == false)
        return@runBlocking
    }

    @Test
    fun `test loading of favorites with failure`(): Unit = runBlocking {
        val remoteStatusCode = HttpStatusCode.InternalServerError

        ClientMock.mockCallError(
            FileFavoriteDescriptions.favoriteStatus,
            error = null,
            statusCode = remoteStatusCode
        )

        val exception = runCatching {
            service.annotate(setOf(FileResource.FAVORITES), listOf(fileA), ClientMock.authenticatedClient)
        }.exceptionOrNull()

        assertThatInstance(
            exception,
            matcher = { it is RPCException && it.httpStatusCode == remoteStatusCode })
        return@runBlocking
    }
}
