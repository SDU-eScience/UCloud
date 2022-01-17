package dk.sdu.cloud.app.aau.rpc

import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.app.aau.ClientHolder
import dk.sdu.cloud.app.aau.services.ResourceCache
import dk.sdu.cloud.app.kubernetes.api.AauCompute
import dk.sdu.cloud.app.kubernetes.api.AauComputeMaintenance
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.ToolBackend
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.validateAndDecodeOrNull
import dk.sdu.cloud.slack.api.SendSupportRequest
import dk.sdu.cloud.slack.api.SlackDescriptions
import io.ktor.http.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

class ComputeController(
    private val client: ClientHolder,
    private val resourceCache: ResourceCache,
    private val serviceClient: AuthenticatedClient,
    private val devMode: Boolean,
    private val tokenValidation: TokenValidation<*>,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(AauCompute.create) {
            request.items.forEach { req ->
                val resources = resourceCache.findResources(req)
                val application = resources.application
                val tool = application.invocation.tool.tool!!

                if (tool.description.backend != ToolBackend.VIRTUAL_MACHINE) {
                    throw RPCException("Unsupported application", HttpStatusCode.BadRequest)
                }
            }

            request.items.forEach { req ->
                val resources = resourceCache.findResources(req)
                val application = resources.application
                val tool = application.invocation.tool.tool!!

                sendMessage(
                    req.id,
                    buildString {
                        appendLine("AAU VM creation request: ")
                        appendLine(
                            defaultMapper.encodeToString(
                                JsonObject(
                                    mapOf(
                                        "request" to JsonPrimitive("creation"),
                                        "job_id" to JsonPrimitive(req.id),
                                        "owner_username" to JsonPrimitive(req.owner.createdBy),
                                        "owner_project" to JsonPrimitive(req.owner.project),
                                        "base_image" to JsonPrimitive(tool.description.image),
                                        "machine_template" to JsonPrimitive(resources.product.name),
                                        "total_grant_allocation" to JsonPrimitive("Ask SDU if needed, no longer available in the temporary solution"),
                                        "request_parameters" to defaultMapper.encodeToJsonElement(req.specification.parameters)
                                    )
                                )
                            )
                        )
                    }
                )
            }

            JobsControl.update.call(
                bulkRequestOf(
                    request.items.map { req ->
                        ResourceUpdateAndId(
                            req.id,
                            JobUpdate(
                                JobState.IN_QUEUE,
                                status = "A request has been submitted and is now awaiting approval by a system administrator. " +
                                    "This might take a few business days."
                            )
                        )
                    }
                ),
                client.client
            ).orThrow()

            ok(BulkResponse(request.items.map { null }))
        }

        implement(AauCompute.terminate) {
            request.items.forEach { req ->
                val resources = resourceCache.findResources(req)
                val application = resources.application
                val tool = application.invocation.tool.tool!!

                sendMessage(
                    req.id,
                    buildString {
                        appendLine("AAU VM deletion request: ")
                        appendLine(
                            defaultMapper.encodeToString(
                                JsonObject(
                                    mapOf(
                                        "request" to JsonPrimitive("deletion"),
                                        "job_id" to JsonPrimitive(req.id),
                                        "owner_username" to JsonPrimitive(req.owner.createdBy),
                                        "owner_project" to JsonPrimitive(req.owner.project),
                                        "base_image" to JsonPrimitive(tool.description.image),
                                        "machine_template" to JsonPrimitive(resources.product.name),
                                        "total_grant_allocation" to JsonPrimitive("Ask SDU if needed, no longer available in the temporary solution"),
                                        "request_parameters" to defaultMapper.encodeToJsonElement(req.specification.parameters)
                                    )
                                )
                            )
                        )
                    }
                )
            }

            JobsControl.update.call(
                bulkRequestOf(
                    request.items.map { req ->
                        ResourceUpdateAndId(
                            req.id,
                            JobUpdate(
                                JobState.IN_QUEUE,
                                status = "A request for deletion has been submitted and is now awaiting action by a system administrator. " +
                                    "This might take a few business days."
                            )
                        )
                    }
                ),
                client.client
            ).orThrow()

            ok(BulkResponse(request.items.map { Unit }))
        }

        implement(AauComputeMaintenance.sendUpdate) {
            withContext<HttpCall> {
                val bearer = ctx.context.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                val principal = tokenValidation.validateAndDecodeOrNull(bearer)?.principal
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                if (principal.role !in Roles.PRIVILEGED) throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)

                JobsControl.update.call(
                    bulkRequestOf(request.items.map { req ->
                        ResourceUpdateAndId(
                            req.id,
                            JobUpdate(req.newState, status = req.update)
                        )
                    }),
                    client.client
                ).orThrow()

                ok(Unit)
            }
        }

        implement(AauComputeMaintenance.retrieve) {
            withContext<HttpCall> {
                val bearer = ctx.context.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                val principal = tokenValidation.validateAndDecodeOrNull(bearer)?.principal
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                if (principal.role !in Roles.PRIVILEGED) throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)

                ok(
                    JobsControl.retrieve.call(
                        ResourceRetrieveRequest(
                            JobIncludeFlags(includeProduct = true, includeApplication = true),
                            request.id
                        ),
                        client.client
                    ).orThrow()
                )
            }
        }

        implement(AauCompute.retrieveProducts) {
            ok(retrieveProductsTemporary())
        }

        implement(AauCompute.follow) {
            sendWSMessage(JobsProviderFollowResponse("id", -1, null, null))
            sendWSMessage(
                JobsProviderFollowResponse(
                    "id",
                    0,
                    "Please see the 'Messages' panel for how to access your machine",
                    null
                )
            )
            while (currentCoroutineContext().isActive) {
                delay(1000)
            }
            ok(
                JobsProviderFollowResponse(
                    "",
                    0,
                    "Please see the 'Messages' panel for how to access your machine",
                    null
                )
            )
        }

        implement(AauCompute.extend) {
            ok(BulkResponse(request.items.map { Unit }))
        }

        implement(AauCompute.openInteractiveSession) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        }

        implement(AauCompute.retrieveUtilization) {
            ok(JobsProviderUtilizationResponse(CpuAndMemory(100.0, 100L), CpuAndMemory(0.0, 0L), QueueStatus(0, 0)))
        }

        implement(AauCompute.verify) {
            ok(Unit)
        }

        implement(AauCompute.suspend) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        }

        return@with
    }

    private suspend fun sendMessage(id: String, message: String) {
        if (devMode) {
            println(message)
        } else {
            SlackDescriptions.sendSupport.call(
                SendSupportRequest(
                    id,
                    SecurityPrincipal(
                        username = "_UCloud",
                        role = Role.SERVICE,
                        firstName = "UCloud",
                        lastName = "",
                        uid = 0L
                    ),
                    "UCloud",
                    "AAU Virtual Machine [${id.substringBefore('-').toUpperCase()}]",
                    message
                ),
                serviceClient
            ).orThrow()
        }
    }

    private val productCache = SimpleCache<Unit, List<Product.Compute>>(lookup = {
        Products.browse.call(
            ProductsBrowseRequest(filterProvider = "aau", filterArea = ProductType.COMPUTE),
            serviceClient
        ).orThrow().items.filterIsInstance<Product.Compute>()
    })

    suspend fun retrieveProductsTemporary(): BulkResponse<ComputeSupport> {
        return BulkResponse(productCache.get(Unit)?.map {
            ComputeSupport(
                ProductReference(it.name, it.category.name, it.category.provider),
                ComputeSupport.Docker(
                    enabled = false,
                ),
                ComputeSupport.VirtualMachine(
                    enabled = true,
                )
            )
        } ?: emptyList())
    }
}
