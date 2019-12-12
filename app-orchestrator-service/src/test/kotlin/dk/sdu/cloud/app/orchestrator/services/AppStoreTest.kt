package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.orchestrator.utils.normAppDesc
import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.ApplicationWithFavoriteAndTags
import dk.sdu.cloud.service.test.ClientMock
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppStoreTest {

    @Test
    fun `find by name and version - Not found`() {
        val client = ClientMock.authenticatedClient
        val service = AppStoreService(client)

        ClientMock.mockCallError(
            AppStore.findByNameAndVersion,
            CommonErrorMessage("No app"),
            HttpStatusCode.NotFound
        )
        runBlocking {
            assertNull(service.findByNameAndVersion("name", "2.2.2"))
        }
    }

    @Test
    fun `find by name and version`() {
        val client = ClientMock.authenticatedClient
        val service = AppStoreService(client)

        ClientMock.mockCallSuccess(
            AppStore.findByNameAndVersion,
            ApplicationWithFavoriteAndTags(normAppDesc.metadata, normAppDesc.invocation, true, emptyList())
        )
        runBlocking {
            val firstRunResult = service.findByNameAndVersion("name", "2.2")
            assertEquals("name", firstRunResult?.metadata?.name)
            assertEquals("2.2", firstRunResult?.metadata?.version)
            val cachedResults = service.findByNameAndVersion("name", "2.2")
            assertEquals("name", cachedResults?.metadata?.name)
            assertEquals("2.2", cachedResults?.metadata?.version)
        }
    }

}
