package dk.sdu.cloud.app.kubernetes.services

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Service responsible for matching proxy configuration to cluster state.
 *
 * Actual configuration is performed by [EnvoyConfigurationService] this service simply makes sure state is up-to-date.
 */
class ApplicationProxyService (
    private val envoyConfigurationService: EnvoyConfigurationService,
    private val prefix: String = "app-",
    private val domain: String = "cloud.sdu.dk"
){
    private val entries = HashMap<String, RouteAndCluster>()
    private val lock = Mutex()

    suspend fun addEntry(tunnel: Tunnel) {
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

    suspend fun removeEntry(jobId: String) {
        lock.withLock {
            if (!entries.containsKey(jobId)) return

            entries.remove(jobId)
        }

        renderConfiguration()
    }

    private suspend fun renderConfiguration() {
        // NOTE: There is definitely a limit to how well this will scale. If we have thousands of applications coming
        // up-and-down every second then this approach won't be able to keep up. In this case we really need to
        // implement the xDS services that Envoy would like. We are not currently using this approach as it appears to
        // be significantly more complex to implement and we are on a bit of a tight schedule.

        val allEntries = lock.withLock { entries.map { it.value } }
        val routes = allEntries.map { it.route }
        val clusters = allEntries.map { it.cluster }
        envoyConfigurationService.configure(EnvoyResources(routes), EnvoyResources(clusters))
    }
}

private data class RouteAndCluster(val route: EnvoyRoute, val cluster: EnvoyCluster)
