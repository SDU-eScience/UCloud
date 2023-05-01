package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.debugSystem
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.logThrowable
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.toReadableStacktrace
import dk.sdu.cloud.utils.LinuxOutputStream
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private data class Pod2Container(
    val internalJobId: Long,
    override val rank: Int,

    override val k8Client: KubernetesClient,
    private val name: String,
    private val namespace: String,
    private val categoryToSelector: Map<String, String>,
) : PodBasedContainer() {
    // This object contains a reference to a job which might not be running yet, but most likely has a pod available.
    // The only scenario in which the pod is gone, is if the job has terminated.
    override val jobId: String = internalJobId.toString()
    private var cachedPod: Pod? = null
    private val cacheMutex = Mutex()

    override val pod: Pod
        get() = runBlocking { pod() }

    private suspend fun pod(): Pod {
        val cached = cachedPod
        if (cached != null) return cached
        cacheMutex.withLock {
            val cached2 = cachedPod
            if (cached2 != null) return cached2

            val pod = k8Client.getResource(
                Pod.serializer(),
                KubernetesResources.pod.withNameAndNamespace(name, namespace)
            )

            cachedPod = pod
            return pod
        }
    }

    override suspend fun cancel(force: Boolean) {
        try {
            k8Client.deleteResource(
                KubernetesResourceLocator.common.pod.withNameAndNamespace(pod().metadata!!.name!!, namespace,),
                queryParameters = buildMap {
                    if (force) put("force", "true")
                }
            )
        } catch (ex: KubernetesException) {
            if (ex.statusCode == HttpStatusCode.NotFound) {
                // This is OK
            } else {
                throw ex
            }
        }

        if (rank == 0) {
            runCatching {
                k8Client.deleteResource(
                    KubernetesResources.networkPolicies.withNameAndNamespace(
                        K8_POD_NETWORK_POLICY_PREFIX + this.jobId,
                        namespace,
                    )
                )
            }
        }
    }

    override suspend fun allowNetworkTo(jobId: String, rank: Int?) {
        k8Client.patchResource(
            KubernetesResources.networkPolicies.withNameAndNamespace(
                K8_POD_NETWORK_POLICY_PREFIX + this.jobId,
                namespace
            ),
            defaultMapper.encodeToString(
                ListSerializer(JsonObject.serializer()),
                listOf(
                    JsonObject(
                        mapOf(
                            "op" to JsonPrimitive("add"),
                            "path" to JsonPrimitive("/spec/egress/-"),
                            "value" to defaultMapper.encodeToJsonElement(
                                NetworkPolicy.EgressRule.serializer(),
                                NetworkPolicy.EgressRule().apply {
                                    to = listOf(NetworkPolicy.Peer().apply {
                                        podSelector = k8PodSelectorForJob(jobId)
                                    })
                                }
                            )
                        )
                    ),
                )
            ),
            ContentType("application", "json-patch+json")
        )
    }

    override suspend fun allowNetworkFrom(jobId: String, rank: Int?) {
        k8Client.patchResource(
            KubernetesResources.networkPolicies.withNameAndNamespace(
                K8_POD_NETWORK_POLICY_PREFIX + this.jobId,
                namespace
            ),
            defaultMapper.encodeToString(
                ListSerializer(JsonObject.serializer()),
                listOf(
                    JsonObject(
                        mapOf(
                            "op" to JsonPrimitive("add"),
                            "path" to JsonPrimitive("/spec/ingress/-"),
                            "value" to defaultMapper.encodeToJsonElement(
                                NetworkPolicy.IngressRule.serializer(),
                                NetworkPolicy.IngressRule().apply {
                                    from = listOf(NetworkPolicy.Peer().apply {
                                        podSelector = k8PodSelectorForJob(jobId)
                                    })
                                }
                            )
                        )
                    ),
                )
            ),
            ContentType("application", "json-patch+json")
        )
    }

    override fun stateAndMessage(): Pair<JobState, String> {
        val pod = runBlocking { pod() }
        return when (val phase = pod.status?.phase ?: "Unknown") {
            "Pending" -> Pair(JobState.IN_QUEUE, "Job is currently in the queue")
            "Running" -> Pair(JobState.RUNNING, "Job is now running")
            "Succeeded" -> Pair(JobState.SUCCESS, "Job has terminated")
            "Failed" -> Pair(JobState.SUCCESS, "Job has terminated with a non-zero exit code")
            "Unknown" -> Pair(JobState.FAILURE, "Job has failed")
            else -> Pair(JobState.FAILURE, "Unexpected state, assuming job failure: $phase")
        }
    }

    override suspend fun productCategory(): String? {
        val name = (pod().spec?.nodeSelector?.get("ucloud.dk/machine") as? JsonPrimitive)?.contentOrNull
        return categoryToSelector.entries.find { it.value == name }?.key ?: name
    }
}

sealed class Pod2Data {
    data class NotYetScheduled(val builder: PodBasedBuilder) : Pod2Data()
    object AlreadyScheduled : Pod2Data()
}

class Pod2Runtime(
    private val k8Client: KubernetesClient,
    private val namespace: String,
    private val categoryToSelector: Map<String, String>,
    private val fakeIpMount: Boolean,
    private val usePortForwarding: Boolean,
    private val defaultNodeType: String? = null,
) : ContainerRuntime {
    private val mutex = Mutex()
    private val scheduler = Scheduler<Pod2Data>()

    private var nextNodeScan = 0L
    private var nextPodScan = 0L

    fun start() {
        ProcessingScope.launch {
            while (isActive) {
                try {
                    mutex.withLock { scheduleLoop() }
                    delay(20)
                } catch (ex: Throwable) {
                    debugSystem.logThrowable("Error while running scheduleLoop", ex)
                    log.warn(ex.toReadableStacktrace().toString())
                }
            }
        }
    }

    private suspend fun scheduleLoop() {
        val now = Time.now()
        if (now >= nextNodeScan) {
            val allNodes = k8Client.listResources(
                Node.serializer(),
                KubernetesResources.node.withNamespace(NAMESPACE_ANY)
            ).items

            fun label(node: Node, label: String): String? =
                (node.metadata?.labels?.get(label) as? JsonPrimitive)?.contentOrNull

            for (node in allNodes) {
                val machineType = label(node, "ucloud.dk/machine") ?: defaultNodeType ?: continue
                val capacity = node.status?.capacity ?: continue

                // NOTE(Dan): We do not respect the "pods" capacity. The provider must ensure that we can schedule a
                // full machine using the smallest product configuration without hitting this limit.
                // TODO(Dan): Remove system reserved resources
                scheduler.registerNode(
                    node.metadata!!.name!!,
                    machineType,
                    cpuStringToMilliCpus(capacity.cpu),
                    memoryStringToBytes(capacity.memory),
                    gpuStringToFullGpus(capacity.nvidiaGpu)
                )
            }

            scheduler.pruneNodes()
            nextNodeScan = now + (1000 * 60 * 15L)
        }

        if (now >= nextPodScan) {
            val allPods = k8Client.listResources(
                Pod.serializer(),
                KubernetesResources.pod.withNamespace(namespace)
            ).items

            for (pod in allPods) {
                val podName = pod.metadata!!.name!!
                val jobAndRank = podNameToIdAndRank(podName)
                if (jobAndRank == null) {
                    log.warn("Killing unknown pod: $pod")
                    k8Client.deleteResource(KubernetesResources.pod.withNameAndNamespace(podName, namespace))
                    continue
                }

                val (jobId, rank) = jobAndRank
                if (scheduler.findRunningReplica(jobId, rank, touch = true) == null) {
                    val podLimits = pod.spec?.containers?.firstOrNull()?.resources?.limits
                    if (podLimits == null) {
                        log.warn("Pod without resource limits: $pod")
                        k8Client.deleteResource(KubernetesResources.pod.withNameAndNamespace(podName, namespace))
                        continue
                    }

                    fun limit(name: String): String? = (podLimits.get(name) as? JsonPrimitive)?.contentOrNull

                    scheduler.addRunningReplica(
                        jobId,
                        rank,
                        cpuStringToMilliCpus(limit("cpu")),
                        memoryStringToBytes(limit("memory")),
                        gpuStringToFullGpus(limit("nvidia.com/gpu")),
                        pod.spec!!.nodeName!!,
                        Pod2Data.AlreadyScheduled
                    )
                }
            }

            scheduler.pruneJobs()
            nextPodScan = now + (1000 * 30L)
        }

        val scheduledJobs = scheduler.schedule()
        for (job in scheduledJobs) {
            val data = job.data
            if (data !is Pod2Data.NotYetScheduled) {
                log.warn("We were told to schedule a job which has no builder? $job")
                continue
            }

            data.builder.podSpec.nodeName = job.node

            val pod = Pod(
                metadata = ObjectMeta(name = idAndRankToPodName(job.jobId, job.rank)),
                spec = data.builder.podSpec
            )

            k8Client.createResource(
                KubernetesResources.pod.withNamespace(namespace),
                defaultMapper.encodeToString(Pod.serializer(), pod)
            )

            // TODO create network policy and service
        }
    }

    private fun idAndRankToPodName(id: Long, rank: Int): String = "j-$id-$rank"
    private fun podNameToIdAndRank(podName: String): Pair<Long, Int>? {
        val prefix = "j-"
        if (!podName.startsWith(prefix)) return null
        val withoutPrefix = podName.substring(prefix.length)
        val separatorIndex = withoutPrefix.indexOf('-')
        if (separatorIndex == -1 || separatorIndex == withoutPrefix.lastIndex) return null
        val idPart = withoutPrefix.substring(0, separatorIndex)
        val rankPart = withoutPrefix.substring(separatorIndex + 1, withoutPrefix.length)

        val id = idPart.toLongOrNull() ?: return null
        val rank = rankPart.toIntOrNull() ?: return null
        return Pair(id, rank)
    }

    private val notNumbers = Regex("[^0-9]")

    private fun cpuStringToMilliCpus(cpuString: String?): Int {
        return when {
            cpuString.isNullOrBlank() -> 0
            cpuString.endsWith("m") -> notNumbers.replace(cpuString, "").toInt()
            else -> notNumbers.replace(cpuString, "").toInt() * 1000
        }
    }

    private fun gpuStringToFullGpus(gpuString: String?): Int {
        return when {
            gpuString.isNullOrBlank() -> 0
            gpuString.endsWith("m") -> notNumbers.replace(gpuString, "").toInt() / 1000
            else -> notNumbers.replace(gpuString, "").toInt()
        }
    }

    private fun memoryStringToBytes(memory: String?): Long {
        val ki = 1024L
        val mi = ki * 1024L
        val gi = mi * 1024L
        val ti = gi * 1024L
        val pi = ti * 1024L

        return when {
            memory.isNullOrBlank() -> 0
            memory.contains("Ki") -> notNumbers.replace(memory, "").toLong() * ki
            memory.contains("Mi") -> notNumbers.replace(memory, "").toLong() * mi
            memory.contains("Gi") -> notNumbers.replace(memory, "").toLong() * gi
            memory.contains("Ti") -> notNumbers.replace(memory, "").toLong() * ti
            memory.contains("Pi") -> notNumbers.replace(memory, "").toLong() * pi
            else -> notNumbers.replace(memory, "").toLong()
        }
    }

    override fun builder(jobId: String, replicas: Int, block: ContainerBuilder.() -> Unit): ContainerBuilder {
        return K8PodContainerBuilder(jobId, replicas, k8Client, namespace, categoryToSelector, fakeIpMount)
    }

    override suspend fun schedule(container: ContainerBuilder) {
        mutex.withLock {
            val c = container as K8PodContainerBuilder

            scheduler.addJobToQueue(
                c.jobId.toLong(),
                c.productCategoryRequired ?: defaultNodeType ?: "",
                c.vCpuMillis,
                c.memoryMegabytes * 1024 * 1024L,
                c.gpus,
                c.replicas,
                Pod2Data.NotYetScheduled(c)
            )
        }
    }

    override suspend fun retrieve(jobId: String, rank: Int): Container? {
        mutex.withLock {
            val jobIdLong = jobId.toLongOrNull() ?: return null
            if (scheduler.findRunningReplica(jobIdLong, rank, touch = false) == null) return null
            return container(jobIdLong, rank)
        }
    }

    override suspend fun list(): List<Container> {
        val result = ArrayList<Container>()
        mutex.withLock {
            for (replica in scheduler.runningReplicas()) {
                result.add(container(replica.jobId, replica.rank))
            }
        }
        return result
    }

    private fun container(id: Long, rank: Int): Pod2Container {
        return Pod2Container(
            id, rank,
            k8Client,
            idAndRankToPodName(id, rank),
            namespace,
            categoryToSelector
        )
    }

    override suspend fun listNodes(): List<ComputeNode> {
        return emptyList()
    }

    override suspend fun openTunnel(jobId: String, rank: Int, port: Int): Tunnel {
        if (!usePortForwarding) {
            val pod = k8Client.getResource(
                Pod.serializer(),
                KubernetesResourceLocator.common.pod.withNameAndNamespace(
                    K8PodRuntime.idAndRankToPodName(jobId, rank),
                    namespace,
                )
            )

            val ipAddress =
                pod.status?.podIP ?: throw RPCException.fromStatusCode(dk.sdu.cloud.calls.HttpStatusCode.BadGateway)

            return Tunnel(ipAddress, port, close = {})
        } else {
            val allocatedPort = basePortForward + portAllocator.getAndIncrement()
            val process = k8Client.kubectl(listOf(
                "port-forward",
                "-n",
                namespace,
                K8PodRuntime.idAndRankToPodName(jobId, rank),
                "$allocatedPort:$port"
            ))

            return Tunnel("127.0.0.1", allocatedPort, close = { process.destroy() })
        }
    }

    companion object : Loggable {
        override val log = logger()

        private val basePortForward = 30_000
        private val portAllocator = AtomicInteger(0)
    }
}

private const val K8_POD_NETWORK_POLICY_PREFIX = "policy-"
private const val K8_JOB_NAME_LABEL = "k8-job-id"
private fun k8PodSelectorForJob(jobId: String): LabelSelector = LabelSelector(
    matchLabels = JsonObject(
        mapOf(
            K8_JOB_NAME_LABEL to JsonPrimitive(jobId)
        )
    )
)
