package dk.sdu.cloud.file.stats.services

import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderResponse
import dk.sdu.cloud.file.stats.storageFile
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.QueryResponse
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.TestUsers
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class RecentFilesTest {

    @Test
    fun `Recent files test`() {
        CloudMock.mockCallSuccess(
            QueryDescriptions,
            { QueryDescriptions.query },
            QueryResponse(
                2,
                10,
                0,
                listOf(
                    storageFile,
                    storageFile.copy(fileId = "id2")
                )
            )
        )

        CloudMock.mockCallSuccess(
            FileDescriptions,
            {FileDescriptions.findHomeFolder},
            FindHomeFolderResponse("/home/user")
        )


        val recentFilesService = RecentFilesService(mockk(relaxed = true))
        runBlocking {
            val results = recentFilesService.queryRecentFiles(TestUsers.user.username)
            assertEquals(2, results.size)
            assertEquals("id", results.first().fileId)
            assertEquals("id2", results.last().fileId)
        }
    }

}
