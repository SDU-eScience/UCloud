package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private data class TunnelWithUsageTracking(val tunnel: Tunnel, var lastUsed: Long)

class TunnelManager(private val k8: K8Dependencies) {
    private val openTunnels = HashMap<String, TunnelWithUsageTracking>()
    private val mutex = Mutex()
    private val cleanupExecutor = Executors.newSingleThreadScheduledExecutor()
    private val usedPorts = HashSet<Int>()

    private val isRunningInsideKubernetes: Boolean by lazy {
        runCatching {
            File("/var/run/secrets/kubernetes.io").exists()
        }.getOrNull() == true
    }

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

    suspend fun createOrUseExistingTunnel(id: String, remotePort: Int, urlId: String?): Tunnel {
        mutex.withLock {
            val existing = openTunnels[id]
            val alive = existing?.tunnel?.isAlive() ?: false
            if (existing != null && !alive) {
                existing.tunnel.close()
            }

            return if (existing != null && alive) {
                existing.lastUsed = Time.now()
                existing.tunnel
            } else {
                val localPort: Int = run {
                    while (true) {
                        val localPort = Random.nextInt(30000, 64000)
                        if (localPort !in usedPorts) {
                            return@run localPort
                        }
                    }

                    // Needed to compile code
                    @Suppress("UNREACHABLE_CODE")
                    return@run -1
                }

                val newTunnel = createTunnel(id, localPort, remotePort, urlId)
                openTunnels[id] = TunnelWithUsageTracking(newTunnel, Time.now())
                newTunnel
            }
        }
    }

    private fun createTunnel(jobId: String, localPortSuggestion: Int, remotePort: Int, urlId: String?): Tunnel {
        val pod =
            k8.nameAllocator.listPods(jobId).firstOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        fun findPodResource() = k8.nameAllocator.findPodByName(pod.metadata.name)
        val podResource = findPodResource()

        if (!isRunningInsideKubernetes) {
            val k8sTunnel = run {
                podResource.portForward(remotePort)
                // Using kubectl port-forward appears to be a lot more reliable than using the built-in.
                ProcessBuilder().apply {
                    val cmd = listOf(
                        "kubectl",
                        "port-forward",
                        "-n",
                        k8.nameAllocator.namespace,
                        pod.metadata.name,
                        "$localPortSuggestion:$remotePort"
                    )
                    log.debug("Running command: $cmd")
                    command(cmd)
                }.start()
            }

            // Consume first line (wait for process to be ready)
            val bufferedReader = k8sTunnel.inputStream.bufferedReader()
            bufferedReader.readLine()

            val job = k8.scope.launch {
                // Read remaining lines to avoid buffer filling up
                bufferedReader.lineSequence().forEach {
                    // Discard line
                }
            }

            log.info("Port forwarding $jobId to $localPortSuggestion")
            return Tunnel(
                jobId = jobId,
                ipAddress = "127.0.0.1",
                localPort = localPortSuggestion,
                urlId = urlId,
                _isAlive = {
                    k8sTunnel.isAlive
                },
                _close = {
                    k8sTunnel.destroyForcibly()
                    job.cancel()
                }
            )
        } else {
            val ipAddress =
                podResource.get().status.podIP ?: throw RPCException("Application not found", HttpStatusCode.NotFound)
            log.debug("Running inside of kubernetes going directly to pod at $ipAddress")
            return Tunnel(
                jobId = jobId,
                ipAddress = ipAddress,
                localPort = remotePort,
                urlId = urlId,
                _isAlive = { runCatching { findPodResource()?.get() }.getOrNull() != null },
                _close = { }
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
