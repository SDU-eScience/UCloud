package dk.sdu.cloud.plugins

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.RequestContext
import kotlinx.coroutines.channels.ReceiveChannel

interface ComputePlugin : ResourcePlugin<Product.Compute, ComputeSupport, Job, ConfigSchema.Plugins.Jobs> {
    suspend fun RequestContext.extendBulk(request: JobsProviderExtendRequest): JobsExtendResponse {
        return BulkResponse(request.items.map { extend(it) })
    }

    suspend fun RequestContext.extend(request: JobsProviderExtendRequestItem)

    suspend fun RequestContext.suspendBulk(request: JobsProviderSuspendRequest): JobsProviderSuspendResponse {
        request.items.forEach { suspendJob(it) }
        return BulkResponse(request.items.map { Unit })
    }

    suspend fun RequestContext.suspendJob(request: JobsProviderSuspendRequestItem)

    suspend fun RequestContext.terminateBulk(request: BulkRequest<Job>): BulkResponse<Unit?> {
        return BulkResponse(request.items.map { terminate(it) })
    }

    suspend fun RequestContext.terminate(resource: Job)

    suspend fun FollowLogsContext.follow(job: Job)

    class FollowLogsContext(
        delegate: RequestContext,
        val isActive: () -> Boolean,
        val emitStdout: suspend (rank: Int, message: String) -> Unit,
        val emitStderr: suspend (rank: Int, message: String) -> Unit,
    ) : RequestContext by delegate

    suspend fun RequestContext.verify(jobs: List<Job>) {}

    suspend fun RequestContext.retrieveClusterUtilization(): JobsProviderUtilizationResponse

    suspend fun RequestContext.openInteractiveSessionBulk(
        request: JobsProviderOpenInteractiveSessionRequest
    ): JobsProviderOpenInteractiveSessionResponse {
        return JobsProviderOpenInteractiveSessionResponse(request.items.map { openInteractiveSession(it) })
    }

    suspend fun RequestContext.openInteractiveSession(job: JobsProviderOpenInteractiveSessionRequestItem): OpenSession {
        throw RPCException("Interactive sessions are not supported by this cluster", HttpStatusCode.BadRequest)
    }

    override suspend fun RequestContext.delete(resource: Job) {
        // Not supported by compute plugins
    }

    suspend fun RequestContext.canHandleShellSession(request: ShellRequest.Initialize): Boolean {
        return false
    }

    class ShellContext(
        delegate: RequestContext,
        val isActive: () -> Boolean,
        val receiveChannel: ReceiveChannel<ShellRequest>,
        val emitData: suspend (data: String) -> Unit,
    ) : RequestContext by delegate

    suspend fun ShellContext.handleShellSession(request: ShellRequest.Initialize) {
        // Do nothing
    }
}

abstract class EmptyComputePlugin : ComputePlugin {
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()

    override suspend fun RequestContext.extendBulk(request: JobsProviderExtendRequest): JobsExtendResponse = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.extend(request: JobsProviderExtendRequestItem) = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.suspendBulk(request: JobsProviderSuspendRequest): JobsProviderSuspendResponse = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.suspendJob(request: JobsProviderSuspendRequestItem) = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.terminateBulk(request: BulkRequest<Job>): BulkResponse<Unit?> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.terminate(resource: Job) = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun ComputePlugin.FollowLogsContext.follow(job: Job) = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.retrieveClusterUtilization(): JobsProviderUtilizationResponse = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.openInteractiveSessionBulk(request: JobsProviderOpenInteractiveSessionRequest): JobsProviderOpenInteractiveSessionResponse = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.openInteractiveSession(job: JobsProviderOpenInteractiveSessionRequestItem): OpenSession = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.delete(resource: Job) = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.canHandleShellSession(request: ShellRequest.Initialize): Boolean = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun ComputePlugin.ShellContext.handleShellSession(request: ShellRequest.Initialize) = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.createBulk(request: BulkRequest<Job>): BulkResponse<FindByStringId?> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.create(resource: Job): FindByStringId? = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.deleteBulk(request: BulkRequest<Job>): BulkResponse<Unit?> = throw RPCException("Not supported", HttpStatusCode.BadRequest)

    override suspend fun RequestContext.verify(jobs: List<Job>) {}
    override suspend fun PluginContext.runMonitoringLoop() {}

    override suspend fun RequestContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<ComputeSupport> {
        return BulkResponse(knownProducts.map { ComputeSupport(it) })
    }
}
