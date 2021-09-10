package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.app.orchestrator.api.ExportedParameters
import dk.sdu.cloud.app.orchestrator.api.ExportedParametersRequest
import dk.sdu.cloud.app.orchestrator.api.JobSpecification
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

class ParameterExportService(
    private val db: DBContext,
) {
    private val productCache = SimpleCache<ProductReference, Product.Compute>(
        maxAge = 60_000 * 15L,
        lookup = { reference ->
            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("provider", reference.provider)
                        setParameter("category", reference.category)
                        setParameter("name", reference.id)
                    },
                    """
                        select accounting.product_to_json(p, pc, null)
                        from
                            accounting.products p join
                            accounting.product_categories pc on p.category = pc.id
                        where
                            pc.provider = :provider and
                            pc.category = :category and
                            p.name = :name
                        order by p.version desc
                        limit 1
                    """
                )
            }.rows.singleOrNull()?.let {
                runCatching {
                    defaultMapper.decodeFromString<Product.Compute>(it.getString(0)!!)
                }.getOrNull()
            }
        }
    )

    suspend fun exportParameters(parameters: JobSpecification): ExportedParameters {
        val resolvedProduct = productCache.get(parameters.product)
            ?: throw RPCException("Unknown machine reservation", HttpStatusCode.BadRequest)

        return ExportedParameters(
            VERSION,
            ExportedParametersRequest(
                parameters.application,
                parameters.product,
                parameters.name,
                parameters.replicas,
                defaultMapper.encodeToJsonElement(parameters.parameters) as JsonObject,
                parameters.resources?.map {
                    defaultMapper.encodeToJsonElement(it) as JsonObject
                } ?: emptyList(),
                parameters.timeAllocation
            ),
            JsonObject(mapOf(
                "cpu" to JsonPrimitive(resolvedProduct.cpu ?: 1),
                "memoryInGigs" to JsonPrimitive(resolvedProduct.memoryInGigs ?: 1),
            ))
        )
    }

    companion object : Loggable {
        override val log = logger()
        const val VERSION = 3
    }
}
