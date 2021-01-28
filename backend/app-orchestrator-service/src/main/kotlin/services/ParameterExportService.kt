package dk.sdu.cloud.app.orchestrator.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.app.orchestrator.api.JobSpecification
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import io.ktor.http.*

data class ExportedParameters(
    val siteVersion: Int,
    val request: JsonNode,

    // Backwards compatible information
    val machineType: Map<String, Any?>,
)

class ParameterExportService(
    private val productCache: ProductCache,
) {
    suspend fun exportParameters(parameters: JobSpecification): ExportedParameters {
        val resolvedProduct = productCache.find<Product.Compute>(
            parameters.product.provider,
            parameters.product.id,
            parameters.product.category
        ) ?: throw RPCException("Unknown machine reservation", HttpStatusCode.BadRequest)

        // Exported to a json node in case we need to modify for compatibility
        val exportedParams = defaultMapper.valueToTree<ObjectNode>(parameters)
        return ExportedParameters(
            VERSION,
            exportedParams,
            mapOf(
                "cpu" to (resolvedProduct.cpu ?: 1),
                "memoryInGigs" to (resolvedProduct.memoryInGigs ?: 1),
            )
        )
    }

    companion object : Loggable {
        override val log = logger()
        const val VERSION = 2
    }
}
