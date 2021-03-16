package dk.sdu.cloud.app.aau.rpc

import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.api.RetrieveAllFromProviderRequest
import dk.sdu.cloud.app.aau.ClientHolder
import dk.sdu.cloud.app.aau.services.ResourceCache
import dk.sdu.cloud.app.kubernetes.api.AauCompute
import dk.sdu.cloud.app.kubernetes.api.AauComputeMaintenance
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.ToolBackend
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.SimpleCache
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
    private val devMode: Boolean,
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
                            defaultMapper.encodeToString(JsonObject(
                                mapOf(
                                    "request" to JsonPrimitive("creation"),
                                    "job_id" to JsonPrimitive(req.id),
                                    "owner_username" to JsonPrimitive(req.owner.createdBy),
                                    "owner_project" to JsonPrimitive(req.owner.project),
                                    "base_image" to JsonPrimitive(tool.description.image),
                                    "machine_template" to JsonPrimitive(resources.product.id),
                                    "total_grant_allocation" to JsonPrimitive("${(req.billing.__creditsAllocatedToWalletDoNotDependOn__ / 1_000_000)} DKK"),
                                    "request_parameters" to defaultMapper.encodeToJsonElement(req.specification.parameters)
                                )
                            )
                        ))
                    }
                )
            }

            JobsControl.update.call(
                bulkRequestOf(
                    request.items.map { req ->
                        JobsControlUpdateRequestItem(
                            req.id,
                            JobState.IN_QUEUE,
                            "A request has been submitted and is now awaiting approval by a system administrator. " +
                                "This might take a few business days."
                        )
                    }
                ),
                client.client
            ).orThrow()

            ok(Unit)
        }

        implement(AauCompute.delete) {
            request.items.forEach { req ->
                val resources = resourceCache.findResources(req)
                val application = resources.application
                val tool = application.invocation.tool.tool!!

                sendMessage(
                    req.id,
                    buildString {
                        appendLine("AAU VM deletion request: ")
                        appendLine(
                            defaultMapper.encodeToString(JsonObject(
                                mapOf(
                                    "request" to JsonPrimitive("creation"),
                                    "job_id" to JsonPrimitive(req.id),
                                    "owner_username" to JsonPrimitive(req.owner.createdBy),
                                    "owner_project" to JsonPrimitive(req.owner.project),
                                    "base_image" to JsonPrimitive(tool.description.image),
                                    "machine_template" to JsonPrimitive(resources.product.id),
                                    "total_grant_allocation" to JsonPrimitive("${(req.billing.__creditsAllocatedToWalletDoNotDependOn__ / 1_000_000)} DKK"),
                                    "request_parameters" to defaultMapper.encodeToJsonElement(req.specification.parameters)
                                )
                            )
                        ))
                    }
                )
            }

            JobsControl.update.call(
                bulkRequestOf(
                    request.items.map { req ->
                        JobsControlUpdateRequestItem(
                            req.id,
                            JobState.IN_QUEUE,
                            "A request for deletion has been submitted and is now awaiting action by a system administrator. " +
                                "This might take a few business days."
                        )
                    }
                ),
                client.client
            ).orThrow()

            ok(Unit)
        }

        implement(AauComputeMaintenance.sendUpdate) {
            JobsControl.update.call(
                bulkRequestOf(request.items.map { req ->
                    JobsControlUpdateRequestItem(req.id,
                        req.newState,
                        req.update)
                }),
                client.client
            ).orThrow()

            ok(Unit)
        }

        implement(AauComputeMaintenance.retrieve) {
            ok(JobsControl.retrieve.call(
                JobsControlRetrieveRequest(request.id, includeProduct = true, includeApplication = true),
                client.client
            ).orThrow())
        }

        implement(AauCompute.retrieveProducts) {
            ok(retrieveProductsTemporary())
        }

        implement(AauCompute.follow) {
            sendWSMessage(JobsProviderFollowResponse("id", -1, null, null))
            sendWSMessage(JobsProviderFollowResponse(
                "id",
                0,
                "Please see the 'Messages' panel for how to access your machine",
                null
            ))
            while (currentCoroutineContext().isActive) {
                delay(1000)
            }
            ok(JobsProviderFollowResponse("", 0, "Please see the 'Messages' panel for how to access your machine", null))
        }

        implement(AauCompute.extend) {
            ok(Unit)
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
                client.client
            ).orThrow()
        }
    }

    private val productCache = SimpleCache<Unit, List<Product.Compute>>(lookup = {
        Products.retrieveAllFromProvider.call(
            RetrieveAllFromProviderRequest("aau"),
            client.client
        ).orThrow().filterIsInstance<Product.Compute>()
    })

    suspend fun retrieveProductsTemporary(): JobsProviderRetrieveProductsResponse {
        return JobsProviderRetrieveProductsResponse(productCache.get(Unit)?.map {
            ComputeProductSupport(
                ProductReference(it.id, it.category.id, it.category.provider),
                ComputeSupport(
                    ComputeSupport.Docker(
                        enabled = false,
                    ),
                    ComputeSupport.VirtualMachine(
                        enabled = true,
                    )
                )
            )
        } ?: emptyList())
    }
}
