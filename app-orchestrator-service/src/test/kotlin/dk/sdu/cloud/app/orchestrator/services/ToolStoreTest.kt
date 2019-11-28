package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.orchestrator.utils.normTool
import dk.sdu.cloud.app.orchestrator.utils.normToolDesc
import dk.sdu.cloud.app.store.api.ToolStore
import dk.sdu.cloud.service.test.ClientMock
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolStoreTest {

    @Test
    fun `find by name and version - not found`() {
        val service = ToolStoreService(ClientMock.authenticatedClient)

        ClientMock.mockCallError(
            ToolStore.findByNameAndVersion,
            CommonErrorMessage("No tool"),
            HttpStatusCode.NotFound
        )

        runBlocking {
            assertNull(service.findByNameAndVersion(normToolDesc.info.name, normToolDesc.info.version))
        }
    }

    @Test
    fun `find by name and version`() {
        val service = ToolStoreService(ClientMock.authenticatedClient)

        ClientMock.mockCallSuccess(
            ToolStore.findByNameAndVersion,
            normTool
        )

        runBlocking {
            val firstResult = service.findByNameAndVersion(normToolDesc.info.name, normToolDesc.info.version)
            assertEquals(firstResult?.description?.info?.name, normToolDesc.info.name)
            assertEquals(firstResult?.description?.info?.version, normToolDesc.info.version)

            val cachedResult = service.findByNameAndVersion(normToolDesc.info.name, normToolDesc.info.version)
            assertEquals(cachedResult?.description?.info?.name, normToolDesc.info.name)
            assertEquals(cachedResult?.description?.info?.version, normToolDesc.info.version)
        }
    }

}
