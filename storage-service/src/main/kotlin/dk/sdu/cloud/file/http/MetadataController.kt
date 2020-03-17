package dk.sdu.cloud.file.http

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.FindMetadataResponse
import dk.sdu.cloud.file.api.MetadataDescriptions
import dk.sdu.cloud.file.api.MetadataUpdate
import dk.sdu.cloud.file.services.MetadataRecoveryService
import dk.sdu.cloud.file.services.acl.Metadata
import dk.sdu.cloud.file.services.acl.MetadataService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode

class MetadataController(
    private val metadataService: MetadataService,
    private val metadataRecovery: MetadataRecoveryService<*>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(MetadataDescriptions.findMetadata) {
            ok(
                FindMetadataResponse(
                    metadataService
                        .listMetadata(
                            if (request.path == null) null else listOf(request.path!!),
                            request.username,
                            request.type
                        ).values.flatten()
                        .map {
                            MetadataUpdate(it.path, it.type, it.username, defaultMapper.writeValueAsString(it.payload))
                        }
                )
            )
        }

        implement(MetadataDescriptions.findByPrefix) {
            ok(
                FindMetadataResponse(
                    metadataService
                        .findByPrefix(
                            request.pathPrefix,
                            request.username,
                            request.type
                        )
                        .map {
                            MetadataUpdate(it.path, it.type, it.username, defaultMapper.writeValueAsString(it.payload))
                        }
                )
            )
        }

        implement(MetadataDescriptions.removeMetadata) {
            ok(metadataService.removeEntries(request.updates))
        }

        implement(MetadataDescriptions.updateMetadata) {
            ok(metadataService.updateMetadataBulk(request.updates.map {
                Metadata(
                    it.path,
                    it.type,
                    it.username,
                    runCatching { defaultMapper.readTree(it.jsonPayload) }.getOrElse { ex ->
                        log.debug(ex.stackTraceToString())
                        throw RPCException("Bad JSON payload", HttpStatusCode.BadRequest)
                    },
                    null
                )
            }))
        }

        implement(MetadataDescriptions.createMetadata) {
            ok(metadataService.createMetadataBulk(request.updates.map {
                Metadata(
                    it.path,
                    it.type,
                    it.username,
                    runCatching { defaultMapper.readTree(it.jsonPayload) }.getOrElse { ex ->
                        log.debug(ex.stackTraceToString())
                        throw RPCException("Bad JSON payload", HttpStatusCode.BadRequest)
                    },
                    null
                )
            }))
        }

        implement(MetadataDescriptions.verify) {
            ok(metadataRecovery.verify(request.paths))
        }

        Unit
    }

    companion object : Loggable {
        override val log = logger()
    }
}
