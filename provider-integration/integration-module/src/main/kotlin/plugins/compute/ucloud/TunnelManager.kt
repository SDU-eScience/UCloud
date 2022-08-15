package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Suppress("ConstructorParameterNaming")
class Tunnel(
    val jobId: String,
    val rank: Int,
    val ipAddress: String,
    val localPort: Int,
    private val _isAlive: suspend () -> Boolean,
    private val _close: suspend () -> Unit,
) : Closeable {
    suspend fun isAlive() = _isAlive()
    override fun close() = runBlocking { _close() }
}

private data class TunnelWithUsageTracking(val tunnel: Tunnel, var lastUsed: Long)

class TunnelManager(private val k8: K8Dependencies) {
    private data class Key(val jobIdAndRank: JobIdAndRank, val port: Int)
    private val openTunnels = HashMap<Key, TunnelWithUsageTracking>()
    private val mutex = Mutex()
    private val cleanupExecutor = Executors.newSingleThreadScheduledExecutor()
    private val usedPorts = HashSet<Int>()

    fun install() {
        cleanupExecutor.schedule({
            runBlocking {
                mutex.withLock {
                    val iterator = openTunnels.iterator()
                    while (iterator.hasNext()) {
                        val (_, tunnelWithTracking) = iterator.next()
                        if (!tunnelWithTracking.tunnel.isAlive()) {
                            iterator.remove()
                        }
                    }
                }
            }
        }, 30, TimeUnit.SECONDS)
    }

    fun shutdown() {
        cleanupExecutor.shutdownNow()
    }

    suspend fun createOrRecreateTunnel(id: String, remotePort: Int, rank: Int): Tunnel {
        mutex.withLock {
            val key = Key(JobIdAndRank(id, rank), remotePort)
            val existing = openTunnels[key]
            existing?.tunnel?.close()

            val newTunnel = createTunnel(id, remotePort, rank)
            openTunnels[key] = TunnelWithUsageTracking(newTunnel, Time.now())
            return newTunnel
        }
    }

    private suspend fun createTunnel(jobId: String, remotePort: Int, rank: Int): Tunnel {
        val namespace = k8.nameAllocator.namespace()
        val jobName = k8.nameAllocator.jobIdToJobName(jobId)

        val pod = k8.client
            .listResources(
                Pod.serializer(),
                KubernetesResources.pod.withNamespace(namespace),
                mapOf(
                    "labelSelector" to "$VOLCANO_JOB_NAME_LABEL=$jobName"
                )
            )
            .find { it.metadata?.name?.substringAfterLast("-")?.toIntOrNull() == rank }
            ?: throw RPCException("Could not find pod", HttpStatusCode.NotFound)

        val podName = pod.metadata?.name ?: throw RPCException("Pod has no name", HttpStatusCode.InternalServerError)

        val ipAddress =
            pod.status?.podIP ?: throw RPCException("Application not found", HttpStatusCode.NotFound)
        log.trace("Running inside of kubernetes going directly to pod at $ipAddress")
        return Tunnel(
            jobId = jobId,
            ipAddress = ipAddress,
            localPort = remotePort,
            rank = rank,
            _isAlive = {
                runCatching {
                    k8.client.getResource(
                        Pod.serializer(),
                        KubernetesResources.pod.withNameAndNamespace(podName, namespace)
                    )
                }.getOrNull() != null
            },
            _close = { }
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
