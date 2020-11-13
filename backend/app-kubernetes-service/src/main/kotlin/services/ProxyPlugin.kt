package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.proxy.ProxyEvent
import dk.sdu.cloud.app.kubernetes.services.proxy.ProxyEvents
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.service.BroadcastingStream

/**
 * A plugin which sends [ProxyEvents.events] to notify this and other instances of app-kubernetes to configure the
 * proxy server
 */
class ProxyPlugin(
    private val broadcastingStream: BroadcastingStream,
) : JobManagementPlugin {
    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        broadcastingStream.broadcast(ProxyEvent(job.id, true), ProxyEvents.events)
    }

    override suspend fun JobManagement.onCleanup(jobId: String) {
        broadcastingStream.broadcast(ProxyEvent(jobId, false), ProxyEvents.events)
    }
}