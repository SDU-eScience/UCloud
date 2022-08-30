package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.proxy.ProxyEvent
import dk.sdu.cloud.app.kubernetes.services.proxy.ProxyEvents
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.service.BroadcastingStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A plugin which sends [ProxyEvents.events] to notify this and other instances of app-kubernetes to configure the
 * proxy server
 */
class ProxyPlugin(
    private val broadcastingStream: BroadcastingStream,
    private val ingressService: IngressService?,
) : JobManagementPlugin {
    private val hasInitializedLock = Mutex()
    private var hasInitialized = false

    override suspend fun JobManagement.onJobStart(jobId: String, jobFromServer: VolcanoJob) {
        initializeIngress(jobId, jobFromServer)
    }

    override suspend fun JobManagement.onCleanup(jobId: String) {
        broadcastingStream.broadcast(ProxyEvent(jobId, false), ProxyEvents.events)
    }

    override suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<VolcanoJob>) {
        if (hasInitialized) return
        hasInitializedLock.withLock {
            if (!hasInitialized) {
                hasInitialized = true
                for (jobFromServer in jobBatch) {
                    val name = jobFromServer.metadata?.name ?: continue
                    val jobId = k8.nameAllocator.jobNameToJobId(name)

                    initializeIngress(jobId, jobFromServer)
                }
            }
        }
    }

    private suspend fun initializeIngress(jobId: String, jobFromServer: VolcanoJob) {
        val domains = ingressService?.retrieveDomainsByJobId(jobId) ?: emptyList()
        broadcastingStream.broadcast(
            ProxyEvent(jobId, true, domains, jobFromServer.spec?.minAvailable ?: 1),
            ProxyEvents.events
        )
    }
}
