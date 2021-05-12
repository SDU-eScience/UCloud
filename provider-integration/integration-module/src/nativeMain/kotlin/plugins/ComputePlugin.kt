package dk.sdu.cloud.plugins

import dk.sdu.cloud.app.orchestrator.api.*

interface ComputePlugin : Plugin {
    fun PluginContext.createBulk(request: JobsProviderCreateRequest): Unit {
        request.items.forEach { create(it) }
    }

    fun PluginContext.create(job: Job)

    fun PluginContext.deleteBulk(request: JobsProviderDeleteRequest) {
        request.items.forEach { delete(it) }
    }

    fun PluginContext.delete(job: Job)

    fun PluginContext.extendBulk(request: JobsProviderExtendRequest) {
        request.items.forEach { extend(it) }
    }

    fun PluginContext.extend(request: JobsProviderExtendRequestItem)

    fun PluginContext.suspendBulk(request: JobsProviderSuspendRequest) {
        request.items.forEach { suspendJob(it) }
    }

    fun PluginContext.suspendJob(request: JobsProviderSuspendRequestItem)

    fun FollowLogsContext.followLogs(job: Job)

    class FollowLogsContext(
        delegate: PluginContext,
        val isActive: () -> Boolean,
        val emitStdout: (rank: Int, message: String) -> Unit,
        val emitStderr: (rank: Int, message: String) -> Unit,
    ) : PluginContext by delegate
}
