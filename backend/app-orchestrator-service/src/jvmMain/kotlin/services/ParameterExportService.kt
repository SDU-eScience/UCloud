package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.app.orchestrator.api.JobSpecification
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class ExportedParameters(
    val siteVersion: Int,
    val request: JsonObject,

    // Backwards compatible information
    val machineType: JsonObject,
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
        val exportedParams = defaultMapper.encodeToJsonElement(parameters) as JsonObject
        return ExportedParameters(
            VERSION,
            exportedParams,
            JsonObject(mapOf(
                "cpu" to JsonPrimitive(resolvedProduct.cpu ?: 1),
                "memoryInGigs" to JsonPrimitive(resolvedProduct.memoryInGigs ?: 1),
            ))
        )
    }

    companion object : Loggable {
        override val log = logger()
        const val VERSION = 2
    }
}
