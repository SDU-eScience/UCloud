package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.BroadcastingStream
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Service responsible for matching proxy configuration to cluster state.
 *
 * Actual configuration is performed by [EnvoyConfigurationService] this service simply makes sure state is up-to-date.
 */
class ApplicationProxyService(
    private val envoyConfigurationService: EnvoyConfigurationService,
    private val jobCache: VerifiedJobCache,
    private val tunnelManager: TunnelManager,
    private val broadcastingStream: BroadcastingStream,
    private val prefix: String = "app-",
    private val domain: String = "cloud.sdu.dk"
) {
    private val entries = HashMap<String, RouteAndCluster>()
    private val lock = Mutex()

    private suspend fun addEntry(tunnel: Tunnel) {
        lock.withLock {
            if (entries.containsKey(tunnel.jobId)) return

            entries[tunnel.jobId] = RouteAndCluster(
                EnvoyRoute(
                    prefix + tunnel.jobId + "." + domain,
                    tunnel.jobId
                ),

                EnvoyCluster(
                    tunnel.jobId,
                    tunnel.ipAddress,
                    tunnel.localPort
                )
            )
        }

        renderConfiguration()
    }

    private suspend fun removeEntry(jobId: String) {
        lock.withLock {
            if (!entries.containsKey(jobId)) return

            entries.remove(jobId)
        }

        renderConfiguration()
    }

    suspend fun initializeListener() {
        broadcastingStream.subscribe(ProxyEvents.events) { event ->
            GlobalScope.launch {
                if (event.shouldCreate) {
                    addEntry(createOrUseExistingTunnel(event.id))
                } else {
                    removeEntry(event.id)
                }
            }
        }
    }

    private suspend fun renderConfiguration() {
        // NOTE: There is definitely a limit to how well this will scale. If we have thousands of applications coming
        // up-and-down every second then this approach won't be able to keep up. In this case we really need to
        // implement the xDS services that Envoy would like. We are not currently using this approach as it appears to
        // be significantly more complex to implement and we are on a bit of a tight schedule.

        val allEntries = lock.withLock { entries.map { it.value } }
        val routes = allEntries.map { it.route }
        val clusters = allEntries.map { it.cluster }
        envoyConfigurationService.configure(routes, clusters)
    }

    private suspend fun createOrUseExistingTunnel(incomingId: String): Tunnel {
        val job = jobCache.findJob(incomingId) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val remotePort = job.application.invocation.web?.port ?: 80
        return tunnelManager.createOrUseExistingTunnel(incomingId, remotePort)
    }
}

private data class RouteAndCluster(val route: EnvoyRoute, val cluster: EnvoyCluster)
