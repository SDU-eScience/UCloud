package dk.sdu.cloud.app.kubernetes.services.proxy

import dk.sdu.cloud.app.kubernetes.services.JobIdAndRank
import dk.sdu.cloud.app.kubernetes.services.K8Dependencies
import dk.sdu.cloud.app.kubernetes.services.volcano.VOLCANO_JOB_NAME_LABEL
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.k8.KubernetesResources
import dk.sdu.cloud.service.k8.Pod
import dk.sdu.cloud.service.k8.getResource
import dk.sdu.cloud.service.k8.listResources
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
    private data class Key(val jobIdAndRank: JobIdAndRank, val port: Int)
    private val openTunnels = HashMap<Key, TunnelWithUsageTracking>()
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

    suspend fun createOrUseExistingTunnel(id: String, remotePort: Int, rank: Int): Tunnel {
        mutex.withLock {
            val key = Key(JobIdAndRank(id, rank), remotePort)
            val existing = openTunnels[key]
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
                    return@run 1
                }

                val newTunnel = createTunnel(id, localPort, remotePort, rank)
                openTunnels[key] = TunnelWithUsageTracking(newTunnel, Time.now())
                newTunnel
            }
        }
    }

    private suspend fun createTunnel(jobId: String, localPortSuggestion: Int, remotePort: Int, rank: Int): Tunnel {
        val namespace = k8.nameAllocator.jobIdToNamespace(jobId)
        val jobName = k8.nameAllocator.jobIdToJobName(jobId)

        val pod = k8.client
            .listResources<Pod>(
                KubernetesResources.pod.withNamespace(namespace),
                mapOf(
                    "labelSelector" to "$VOLCANO_JOB_NAME_LABEL=$jobName"
                )
            )
            .find { it.metadata?.name?.substringAfterLast("-")?.toIntOrNull() == rank }
            ?: throw RPCException("Could not find pod", HttpStatusCode.NotFound)

        val podName = pod.metadata?.name ?: throw RPCException("Pod has no name", HttpStatusCode.InternalServerError)

        if (!isRunningInsideKubernetes) {
            @Suppress("BlockingMethodInNonBlockingContext")
            val k8sTunnel = run {
                // Using kubectl port-forward appears to be a lot more reliable than using the alternatives.
                ProcessBuilder().apply {
                    val cmd = listOf(
                        "kubectl",
                        "port-forward",
                        "-n",
                        namespace,
                        podName,
                        "$localPortSuggestion:$remotePort"
                    )
                    log.trace("Running command: $cmd")
                    command(cmd)
                }.start()
            }

            // Consume first line (wait for process to be ready)
            val bufferedReader = k8sTunnel.inputStream.bufferedReader()
            @Suppress("BlockingMethodInNonBlockingContext")
            bufferedReader.readLine()

            val job = k8.scope.launch {
                // Read remaining lines to avoid buffer filling up
                bufferedReader.lineSequence().forEach { _ ->
                    // Discard line
                }
            }

            log.trace("Port forwarding $jobId to $localPortSuggestion")
            return Tunnel(
                jobId = jobId,
                ipAddress = "127.0.0.1",
                localPort = localPortSuggestion,
                rank = rank,
                _isAlive = {
                    k8sTunnel.isAlive
                },
                _close = {
                    k8sTunnel.destroyForcibly()
                    job.cancel()
                },
            )
        } else {
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
    }

    companion object : Loggable {
        override val log = logger()
    }
}
