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
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.jvm.javaio.*
import io.ktor.utils.io.jvm.javaio.copyTo
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
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

private object LogCache {
    private val emptyBuffer = ByteBuffer.allocateDirect(0)
    private val mutex = Mutex()
    private val buffers = Array(128) { BufferEntry() }
    private const val bufferSize = 1024 * 1024 * 4
    private const val maxAge = 1000L * 60 * 5

    private data class BufferEntry(
        var jobId: Long = -1L,
        var rank: Int = -1,
        var lastUse: Long = 0L,
        var buffer: ByteBuffer = emptyBuffer,
        var copying: Boolean = false,
    )

    suspend fun insertLog(jobId: Long, rank: Int, block: suspend (buffer: ByteBuffer) -> Unit) {
        val entry = mutex.withLock {
            findSlotForEntry(jobId, rank)?.also {
                it.copying = true
            }
        } ?: return
        try {
            block(entry.buffer)
        } finally {
            entry.copying = false
        }
    }

    suspend fun copyLogTo(jobId: Long, rank: Int, out: LinuxOutputStream): Boolean {
        val entry = mutex.withLock {
            buffers.find { it.jobId == jobId && it.rank == rank && !it.copying }
                ?.also { it.copying = true }
        }

        if (entry != null) {
            entry.buffer.flip()
            try {
                while (entry.buffer.hasRemaining()) out.write(entry.buffer)
                return true
            } finally {
                // NOTE(Dan): Don't reset jobId or rank yet, as we still want insertLog() to be cached
                entry.lastUse = 0L
                entry.copying = false
            }
        }
        return false
    }

    private fun clean() {
        val now = Time.now()
        for (entry in buffers) {
            if (!entry.copying && now - entry.lastUse >= maxAge) {
                entry.jobId = -1L
                entry.rank = -1
                entry.lastUse = 0L
                entry.buffer = emptyBuffer
            }
        }
    }

    private fun findSlotForEntry(jobId: Long, rank: Int, canClean: Boolean = true): BufferEntry? {
        fun resetBuffer(entry: BufferEntry) {
            entry.jobId = jobId
            entry.rank = rank
            entry.lastUse = Time.now()
            if (entry.buffer.capacity() == 0) {
                entry.buffer = ByteBuffer.allocateDirect(bufferSize)
            }
            entry.buffer.clear()
        }

        var oldestBuffer: BufferEntry? = null
        for (entry in buffers) {
            if (entry.jobId == jobId && entry.rank == rank) return null
        }

        for (entry in buffers) {
            if (entry.copying) continue

            if (entry.jobId == -1L) {
                resetBuffer(entry)
                return entry
            }

            if (entry.lastUse < (oldestBuffer?.lastUse ?: Long.MAX_VALUE)) {
                oldestBuffer = entry
            }
        }

        if (oldestBuffer != null) {
            resetBuffer(oldestBuffer)
            return oldestBuffer
        }

        if (canClean) {
            clean()
            return findSlotForEntry(jobId, rank, canClean = false)
        }
        return null
    }
}

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

    var volcanoDoesNotExist: Boolean? = null
    var cachedVolcano: VolcanoJob? = null

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

    private suspend fun volcano(): VolcanoJob? {
        val cached = cachedVolcano
        if (cached != null || volcanoDoesNotExist == true) return cached
        cacheMutex.withLock {
            val cached2 = cachedVolcano
            if (cached2 != null || volcanoDoesNotExist == true) return cached2

            try {
                cachedVolcano = k8Client.getResource(
                    VolcanoJob.serializer(),
                    KubernetesResources.volcanoJob.withNameAndNamespace("j-$internalJobId", namespace)
                )
            } catch (ex: Throwable) {
                volcanoDoesNotExist = true
            }
        }

        return cachedVolcano
    }

    override val annotations: Map<String, String>
        get() {
            val initialAnnotations = super.annotations
            if ("volcano.sh/queue-name" in initialAnnotations) {
                // NOTE(Dan): For backwards compatibility, we provide fallback values from Volcano
                return runBlocking {
                    val volcanoAnnotations = (volcano()?.metadata?.annotations ?: JsonObject(emptyMap()))
                        .entries.associate { it.key to it.value.toString() }

                    (volcanoAnnotations + initialAnnotations)
                }
            }
            return initialAnnotations
        }

    override suspend fun cancel(force: Boolean) {
        // NOTE(Dan): Kubernetes is often really slow to delete a pod. As a result, it is not unlikely that the job
        // manager will call this repeatedly because the cancellation is too slow. As a result, all of our code here
        // should be okay with partial failures.
        downloadLogBeforeCancel()

        runCatching {
            k8Client.deleteResource(
                KubernetesResourceLocator.common.pod.withNameAndNamespace(pod().metadata!!.name!!, namespace),
                queryParameters = buildMap {
                    if (force) put("force", "true")
                }
            )
        }

        if (rank == 0) {
            runCatching {
                k8Client.deleteResource(
                    KubernetesResources.networkPolicies.withNameAndNamespace(policyName(jobId), namespace)
                )
            }

            runCatching {
                k8Client.deleteResource(
                    KubernetesResources.services.withNameAndNamespace(serviceName(jobId), namespace)
                )
            }

            runCatching {
                // NOTE(Dan): Backwards compatibility, we always try to delete the matching Volcano job if it exists.
                k8Client.deleteResource(
                    KubernetesResources.volcanoJob.withNameAndNamespace("j-$jobId", namespace)
                )
            }
        }
    }

    private suspend fun downloadLogBeforeCancel() {
        // NOTE(Dan): Often called repeatedly, as a result, the LogCache will not insert our log if we have already
        // done it.
        val podMeta = pod.metadata!!
        val podName = podMeta.name!!
        val namespace = podMeta.namespace!!

        LogCache.insertLog(internalJobId, rank) { output ->
            k8Client.sendRequest(
                HttpMethod.Get,
                KubernetesResources.pod.withNameAndNamespace(podName, namespace),
                mapOf("container" to USER_JOB_CONTAINER),
                "log",
                longRunning = true,
            ).execute { resp ->
                if (resp.status.isSuccess()) {
                    try {
                        val channel = resp.bodyAsChannel()
                        while (output.hasRemaining() && !channel.isClosedForRead) {
                            channel.read { input ->
                                output.put(input)
                            }
                        }
                    } catch (ignored: EOFException) {
                        // Ignored
                    }
                }
            }
        }
    }

    override suspend fun downloadLogs(out: LinuxOutputStream) {
        try {
            if (!LogCache.copyLogTo(internalJobId, rank, out)) {
                downloadLogBeforeCancel()
                LogCache.copyLogTo(internalJobId, rank, out)
            }
        } catch (ex: Throwable) {
            log.info(ex.toReadableStacktrace().toString())
        }
    }

    override suspend fun allowNetworkTo(jobId: String, rank: Int?) {
        k8Client.patchResource(
            KubernetesResources.networkPolicies.withNameAndNamespace(
                policyName(this.jobId),
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
                policyName(this.jobId),
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
            "Running" -> Pair(JobState.RUNNING, "Job has started")
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

    companion object : Loggable {
        override val log = logger()
    }
}

sealed class Pod2Data {
    data class NotYetScheduled(val builder: Pod2ContainerBuilder) : Pod2Data()
    object AlreadyScheduled : Pod2Data()
}

class Pod2Runtime(
    private val k8: K8DependenciesImpl,
    private val namespace: String,
    private val categoryToSelector: Map<String, String>,
    private val fakeIpMount: Boolean,
    private val usePortForwarding: Boolean,
    private val defaultNodeType: String? = null,
) : ContainerRuntime {
    private val k8Client = k8.client
    private val mutex = Mutex()
    private val scheduler = Scheduler<Pod2Data>()

    private var nextNodeScan = 0L
    private var nextPodScan = 0L

    override fun requiresReschedulingOfInQueueJobsOnStartup(): Boolean = true

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
        val scheduledToNodes = HashMap<Long, ArrayList<String>>()
        for (job in scheduledJobs) {
            val data = job.data
            if (data !is Pod2Data.NotYetScheduled) {
                log.warn("We were told to schedule a job which has no builder? $job")
                continue
            }

            scheduledToNodes.getOrPut(job.jobId) { ArrayList() }.add(job.node)

            val pod = data.builder.pod
            pod.metadata!!.name = idAndRankToPodName(job.jobId, job.rank)
            data.builder.podSpec.hostname = idAndRankToPodName(job.jobId, job.rank)
            data.builder.podSpec.nodeName = job.node

            val allContainers = (data.builder.podSpec.initContainers ?: emptyList()) +
                    (data.builder.podSpec.containers ?: emptyList())
            val oldEnv = allContainers.map { c -> c.env?.let { ArrayList(it) } }
            for (container in allContainers) {
                val env = (container.env ?: emptyList()).toMutableList()

                env += Pod.EnvVar(
                    name = "UCLOUD_JOB_ID",
                    value = job.jobId.toString()
                )

                // NOTE(Dan): Used by FeatureMultiNode to create /etc/ucloud/rank.txt
                env += Pod.EnvVar(
                    name = "UCLOUD_RANK",
                    value = job.rank.toString()
                )

                env += Pod.EnvVar(
                    name = "UCLOUD_TASK_COUNT",
                    value = data.builder.replicas.toString()
                )

                // NOTE(Dan): The following bring backwards compatibility with Volcano
                env += Pod.EnvVar(
                    name = "VK_TASK_INDEX", // Ironically, this is probably backwards compatibility within Volcano
                    value = job.rank.toString()
                )

                env += Pod.EnvVar(
                    name = "VC_TASK_INDEX",
                    value = job.rank.toString()
                )

                env += Pod.EnvVar(
                    name = "VC_JOB_NUM",
                    value = data.builder.replicas.toString()
                )

                container.env = env
            }

            k8Client.createResource(
                KubernetesResources.pod.withNamespace(namespace),
                defaultMapper.encodeToString(Pod.serializer(), pod)
            )

            // NOTE(Dan): We must restore the environment since the pod builder itself is shared among all replicas
            allContainers.zip(oldEnv).forEach { (c, env) -> c.env = env }

            // NOTE(Dan): These resources should only be created once, as a result we only do it for rank 0
            if (job.rank == 0) {
                k8Client.createResource(
                    KubernetesResources.networkPolicies.withNamespace(namespace),
                    defaultMapper.encodeToString(NetworkPolicy.serializer(), data.builder.myPolicy)
                )

                k8Client.createResource(
                    KubernetesResources.services.withNamespace(namespace),
                    defaultMapper.encodeToString(Service.serializer(), data.builder.myService)
                )
            }
        }

        for ((jobId, nodeSet) in scheduledToNodes) {
            val compacted = ArrayList<Int>()

            var lastNode: String? = null
            for ((index, node) in nodeSet.withIndex()) {
                if (node != lastNode) {
                    if (index != 0) compacted.add(index)
                    lastNode = node
                }
            }
            compacted.add(nodeSet.size)

            val message = when {
                nodeSet.size == 1 -> {
                    "Assigned to ${nodeSet.single()}"
                }

                compacted.size == 1 -> {
                    "All nodes assigned to ${nodeSet.first()}"
                }

                else -> {
                    buildString {
                        var startRank = 0

                        for (endRank in compacted) {
                            if (startRank == endRank -1) {
                                append("Node ${startRank + 1} assigned to ${nodeSet[startRank]}. ")
                            } else {
                                append("Nodes ${startRank + 1} - ${(endRank - 1) + 1} assigned to ${nodeSet[startRank]}. ")
                            }
                            startRank = endRank
                        }
                    }
                }
            }

            k8.addStatus(
                jobId.toString(),
                message,
                "Job is starting soon"
            )
        }
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
        return Pod2ContainerBuilder(jobId, replicas, k8Client, namespace, categoryToSelector, fakeIpMount)
    }

    override suspend fun schedule(container: ContainerBuilder) {
        mutex.withLock {
            val c = container as Pod2ContainerBuilder

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
            val replica = scheduler.findRunningReplica(jobIdLong, rank, touch = false) ?: return null
            return container(replica)
        }
    }

    override suspend fun list(): List<Container> {
        val result = ArrayList<Container>()
        mutex.withLock {
            for (replica in scheduler.runningReplicas()) {
                val element = container(replica)
                try {
                    // NOTE(Dan): Trigger pod fetch, which might throw if it was recently deleted but not yet removed
                    // from scheduler
                    element.pod

                    result.add(element)
                } catch (ex: Throwable) {
                    // Do nothing
                }
            }
        }
        return result
    }

    private fun container(replica: AllocatedReplica<Pod2Data>): Pod2Container {
        return Pod2Container(
            replica.jobId, replica.rank,
            k8Client,
            idAndRankToPodName(replica.jobId, replica.rank),
            namespace,
            categoryToSelector,
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
                    idAndRankToPodName(jobId.toLong(), rank),
                    namespace,
                )
            )

            val ipAddress =
                pod.status?.podIP ?: throw RPCException.fromStatusCode(dk.sdu.cloud.calls.HttpStatusCode.BadGateway)

            return Tunnel(ipAddress, port, close = {})
        } else {
            val allocatedPort = basePortForward + portAllocator.getAndIncrement()
            val process = k8Client.kubectl(
                listOf(
                    "port-forward",
                    "-n",
                    namespace,
                    idAndRankToPodName(jobId.toLong(), rank),
                    "$allocatedPort:$port"
                )
            )

            return Tunnel("127.0.0.1", allocatedPort, close = { process.destroy() })
        }
    }

    override suspend fun isJobKnown(jobId: String): Boolean {
        val jobIdLong = jobId.toLongOrNull() ?: return false
        mutex.withLock {
            if (scheduler.findRunningReplica(jobIdLong, 0, touch = false) != null) return true
            return scheduler.isJobInQueue(jobIdLong)
        }
    }

    override suspend fun removeJobFromQueue(jobId: String) {
        mutex.withLock {
            scheduler.removeJobFromQueue(jobId.toLongOrNull() ?: return)
        }
    }

    companion object : Loggable {
        override val log = logger()

        private val basePortForward = 30_000
        private val portAllocator = AtomicInteger(0)
    }
}

private fun policyName(jobId: String) = "policy-$jobId"
private fun serviceName(jobId: String) = "j-$jobId"
private fun k8PodSelectorForJob(jobId: String): LabelSelector = LabelSelector(
    matchLabels = JsonObject(
        mapOf(jobIdLabel(jobId))
    )
)

// NOTE(Dan): This is uses the Volcano name to remain backwards compatible
private fun jobIdLabel(jobId: String) = "volcano.sh/job-name" to JsonPrimitive("j-$jobId")
private fun idAndRankToPodName(id: Long, rank: Int): String = "j-$id-job-$rank"
private fun podNameToIdAndRank(podName: String): Pair<Long, Int>? {
    val prefix = "j-"
    if (!podName.startsWith(prefix)) return null
    val withoutPrefix = podName.substring(prefix.length)
    val separatorIndex = withoutPrefix.indexOf('-')
    if (separatorIndex == -1 || separatorIndex == withoutPrefix.lastIndex) return null
    val idPart = withoutPrefix.substring(0, separatorIndex)
    val rankPart = withoutPrefix.substring(separatorIndex + 1, withoutPrefix.length).removePrefix("job-")

    val id = idPart.toLongOrNull() ?: return null
    val rank = rankPart.toIntOrNull() ?: return null
    return Pair(id, rank)
}

class Pod2ContainerBuilder(
    override val jobId: String,
    override val replicas: Int,
    private val k8Client: KubernetesClient,
    val namespace: String,
    private val categoryToSelector: Map<String, String>,
    override val fakeIpMount: Boolean,
) : PodBasedBuilder() {
    val myPolicy: NetworkPolicy
    val myService: Service

    val pod: Pod = Pod(
        metadata = ObjectMeta(
            labels = JsonObject(
                mapOf(
                    jobIdLabel(jobId)
                )
            ),
            annotations = JsonObject(
                mapOf(
                    jobIdLabel(jobId)
                )
            )
        ),
        spec = Pod.Spec(

        )
    )
    override val podSpec: Pod.Spec = pod.spec!!

    init {
        initPodSpec()
        myPolicy = NetworkPolicy(
            metadata = ObjectMeta(name = policyName(this.jobId)),
            spec = NetworkPolicy.Spec(
                egress = ArrayList(),
                ingress = ArrayList(),
                podSelector = k8PodSelectorForJob(this.jobId)
            )
        )

        myService = Service(
            metadata = ObjectMeta(
                name = serviceName(this.jobId),
            ),
            spec = Service.Spec(
                type = "ClusterIP",
                clusterIP = "None",
                selector = JsonObject(mapOf(jobIdLabel(jobId)))
            )
        )

        // NOTE(Dan): These used to be called by Volcano, which is why we are doing them here instead of a feature
        allowNetworkFrom(jobId)
        allowNetworkTo(jobId)

        podSpec.subdomain = "j-$jobId"
    }

    private var productCategoryField: String? = null
    override var productCategoryRequired: String?
        get() = productCategoryField
        set(value) {
            val mapped = categoryToSelector.getOrDefault(value, value)
            productCategoryField = mapped
            podSpec.nodeSelector = JsonObject(
                mapOf(
                    "ucloud.dk/machine" to JsonPrimitive(mapped)
                )
            )
        }

    override var isSidecar: Boolean = false
    override fun supportsSidecar(): Boolean = !isSidecar
    override fun sidecar(name: String, builder: ContainerBuilder.() -> Unit) {
        if (!supportsSidecar()) error("Cannot call sidecar {} in a sidecar container")
        podSpec.initContainers = podSpec.initContainers ?: ArrayList()
        val initContainers = podSpec.initContainers as ArrayList
        val sidecarContainer =
            Pod2ContainerBuilder(jobId, 1, k8Client, namespace, categoryToSelector, fakeIpMount).also(builder).container
        sidecarContainer.name = name
        initContainers.add(sidecarContainer)
    }

    override fun allowNetworkTo(jobId: String, rank: Int?) {
        val ingress = myPolicy.spec!!.ingress as ArrayList

        ingress.add(
            NetworkPolicy.IngressRule(
                from = listOf(NetworkPolicy.Peer(podSelector = k8PodSelectorForJob(jobId)))
            )
        )
    }

    override fun allowNetworkFrom(jobId: String, rank: Int?) {
        val egress = myPolicy.spec!!.egress as ArrayList
        egress.add(
            NetworkPolicy.EgressRule(
                to = listOf(NetworkPolicy.Peer(podSelector = k8PodSelectorForJob(jobId)))
            )
        )
    }

    override fun allowNetworkFromSubnet(subnet: String) {
        val ingress = myPolicy.spec!!.ingress as ArrayList
        ingress.add(
            NetworkPolicy.IngressRule(
                from = listOf(
                    NetworkPolicy.Peer(
                        ipBlock = NetworkPolicy.IPBlock(
                            cidr = subnet
                        )
                    )
                )
            )
        )
    }

    override fun allowNetworkToSubnet(subnet: String) {
        val egress = myPolicy.spec!!.egress as ArrayList
        egress.add(
            NetworkPolicy.EgressRule(
                to = listOf(
                    NetworkPolicy.Peer(
                        ipBlock = NetworkPolicy.IPBlock(
                            cidr = subnet
                        )
                    )
                )
            )
        )
    }

    override fun hostAlias(jobId: String, rank: Int, alias: String) {
        podSpec.hostAliases = podSpec.hostAliases?.toMutableList() ?: ArrayList()
        val aliases = podSpec.hostAliases as MutableList
        val podIp = runBlocking {
            k8Client.getResource(
                Pod.serializer(),
                KubernetesResources.pod.withNameAndNamespace(
                    idAndRankToPodName(jobId.toLong(), rank),
                    namespace
                )
            ).status?.podIP
        } ?: return

        aliases.add(Pod.HostAlias(listOf(alias), podIp))
    }

    override fun upsertAnnotation(key: String, value: String) {
        val annotationEntries = (pod.metadata?.annotations?.entries ?: emptySet())
            .associate { it.key to it.value }
            .toMutableMap()

        annotationEntries[key] = JsonPrimitive(value)

        pod.metadata!!.annotations = JsonObject(annotationEntries)
    }
}
