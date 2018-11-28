package dk.sdu.cloud.file.stats.services

import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.file.stats.storageFile
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.QueryResponse
import dk.sdu.cloud.service.test.TestUsers
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class RecentFilesTest {

    @Test
    fun `Recent files test`() {
        mockkObject(QueryDescriptions) {

            coEvery{ QueryDescriptions.query.call(any(), any())} answers {
                RESTResponse.Ok(
                    mockk(relaxed = true),
                    QueryResponse(
                        2,
                        10,
                        0,
                        listOf(
                            storageFile,
                            storageFile.copy(id = "id2")
                        )
                    )
                )
            }

            val recentFilesService = RecentFilesService(mockk(relaxed = true))
            runBlocking {
                val results = recentFilesService.queryRecentFiles(TestUsers.user.username)
                assertEquals(2, results.size)
                assertEquals("id", results.first().fileId)
                assertEquals("id2", results.last().fileId)
            }
        }
    }
}
