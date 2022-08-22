package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.defaultMapper
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class VolcanoRuntime(
    private val k8: K8Dependencies,
) : ContainerRuntime<VolcanoContainer, VolcanoContainerBuilder> {
    override fun builder(
        jobId: String,
        replicas: Int,
        block: VolcanoContainerBuilder.() -> Unit
    ): VolcanoContainerBuilder {
        return VolcanoContainerBuilder(jobId, replicas, k8.nameAllocator, k8).also(block)
    }

    override suspend fun scheduleGroup(group: List<VolcanoContainerBuilder>) {
        for (c in group) {
            k8.client.createResource(
                KubernetesResourceLocator.common.volcanoJob,
                defaultMapper.encodeToString(VolcanoJob.serializer(), c.job)
            )
        }
    }

    override suspend fun retrieve(jobId: String, rank: Int): VolcanoContainer? {
        try {
            val pod = k8.client.getResource(
                Pod.serializer(),
                KubernetesResourceLocator.common.pod.withNameAndNamespace(
                    k8.nameAllocator.jobIdAndRankToPodName(jobId, rank),
                    k8.nameAllocator.namespace()
                )
            )

            val job = k8.client.getResource(
                VolcanoJob.serializer(),
                KubernetesResourceLocator.common.volcanoJob.withNameAndNamespace(
                    k8.nameAllocator.jobIdToJobName(jobId),
                    k8.nameAllocator.namespace()
                )
            )

            return VolcanoContainer(jobId, rank, job, pod, k8)
        } catch (ex: Throwable) {
            return null
        }
    }

    override suspend fun list(): List<VolcanoContainer> {
        val jobs = k8.client.listResources(
            VolcanoJob.serializer(),
            KubernetesResourceLocator.common.volcanoJob.withNamespace(k8.nameAllocator.namespace()),
        )

        val pods = k8.client.listResources(
            Pod.serializer(),
            KubernetesResourceLocator.common.pod.withNamespace(k8.nameAllocator.namespace())
        )

        val result = ArrayList<VolcanoContainer>()

        for (job in jobs) {
            val jobId = k8.nameAllocator.jobNameToJobId(job.metadata?.name ?: "")
            val myPods = pods.filter { k8.nameAllocator.jobIdFromPodName(it.metadata?.name ?: "") == jobId }

            for (child in myPods) {
                result.add(
                    VolcanoContainer(
                        jobId,
                        k8.nameAllocator.rankFromPodName(child.metadata?.name ?: ""),
                        job,
                        child,
                        k8
                    )
                )
            }
        }
        return result
    }
}

class VolcanoContainer(
    override val jobId: String,
    override val rank: Int,
    val volcanoJob: VolcanoJob,
    override val pod: Pod,
    private val k8: K8Dependencies,
) : PodBasedContainer() {
    override val k8Client: KubernetesClient get() = k8.client
    override val annotations: Map<String, String>
        get() {
            val annotationEntries = pod.metadata?.annotations?.entries ?: emptySet()
            return annotationEntries.associate { it.key to it.value.toString() }
        }

    override suspend fun upsertAnnotation(key: String, value: String) {
        val shouldInsert = key !in annotations
        val metadata = volcanoJob.metadata ?: error("no metadata")

        k8.client.patchResource(
            KubernetesResources.volcanoJob.withNameAndNamespace(
                metadata.name ?: error("no name"),
                metadata.namespace ?: error("no namespace")
            ),
            defaultMapper.encodeToString(
                ListSerializer(JsonObject.serializer()),
                // http://jsonpatch.com/
                listOf(
                    JsonObject(
                        mapOf(
                            // https://tools.ietf.org/html/rfc6901#section-3
                            "op" to JsonPrimitive(if (shouldInsert) "add" else "replace"),
                            "path" to JsonPrimitive("/metadata/annotations/${key.replace("/", "~1")}"),
                            "value" to JsonPrimitive(value)
                        )
                    )
                )
            ),
            ContentType("application", "json-patch+json")
        )
    }

    override suspend fun cancel() {
        k8.client.deleteResource(
            KubernetesResourceLocator.common.volcanoJob.withNameAndNamespace(
                volcanoJob.metadata!!.name!!,
                volcanoJob.metadata!!.namespace!!,
            )
        )
    }

    override suspend fun allowNetworkTo(jobId: String, rank: Int?) {
        // TODO("Not yet implemented")
        repeat(10) { println("allowNetworkTo($jobId, $rank) not yet implemented" )}
    }

    override suspend fun allowNetworkFrom(jobId: String, rank: Int?) {
        // TODO("Not yet implemented")
        repeat(10) { println("allowNetworkFrom($jobId, $rank) not yet implemented" )}
    }
}

class VolcanoContainerBuilder(
    override val jobId: String,
    override val replicas: Int,
    private val nameAllocator: NameAllocator,
    private val k8: K8Dependencies,
    override val isSidecar: Boolean = false
) : PodBasedBuilder<VolcanoContainerBuilder>() {
    override var shouldAllowRoot: Boolean = false
    override var workingDirectory: String = "/work"

    override var productCategoryRequired: String? = null

    override fun supportsSidecar(): Boolean = !isSidecar

    override val sidecars = ArrayList<VolcanoContainerBuilder>()

    val job: VolcanoJob = VolcanoJob(
        metadata = ObjectMeta(nameAllocator.jobIdToJobName(jobId), nameAllocator.namespace()),
        spec = VolcanoJob.Spec(
            minAvailable = replicas,
            maxRetry = 0,
            queue = DEFAULT_QUEUE,
            policies = emptyList(),
            plugins = JsonObject(
                mapOf(
                    "env" to JsonArray(emptyList()),
                    "svc" to JsonArray(emptyList()),
                )
            ),
            tasks = listOf(
                VolcanoJob.TaskSpec(
                    name = "job",
                    replicas = replicas,
                    policies = emptyList(),
                    template = Pod.SpecTemplate(
                        metadata = ObjectMeta(name = "job-${jobId}"),
                        spec = Pod.Spec(),
                    )
                )
            )
        )
    )

    private val spec: VolcanoJob.Spec get() = job.spec!!
    private val task: VolcanoJob.TaskSpec get() = spec.tasks!![0]
    override val podSpec: Pod.Spec get() = task.template!!.spec!!

    init {
        initPodSpec()
    }

    private var myPolicy: NetworkPolicy? = null

    private fun initNetworkPolicyIfNeeded(): NetworkPolicy {
        return myPolicy ?: NetworkPolicy(
            metadata = ObjectMeta(name = "policy-${this.jobId}"),
            spec = NetworkPolicy.Spec(
                egress = ArrayList(),
                ingress = ArrayList(),
                podSelector = podSelectorForJob(this.jobId)
            )
        ).also { myPolicy = it }
    }

    private fun podSelectorForJob(jobId: String): LabelSelector = LabelSelector(
        matchLabels = JsonObject(
            mapOf(
                VOLCANO_JOB_NAME_LABEL to JsonPrimitive(nameAllocator.jobIdToJobName(jobId))
            )
        )
    )

    override fun allowNetworkTo(jobId: String, rank: Int?) {
        val policy = initNetworkPolicyIfNeeded()
        val ingress = policy.spec!!.ingress as ArrayList

        ingress.add(
            NetworkPolicy.IngressRule(
                from = listOf(NetworkPolicy.Peer(podSelector = podSelectorForJob(jobId)))
            )
        )
    }

    override fun allowNetworkFrom(jobId: String, rank: Int?) {
        val policy = initNetworkPolicyIfNeeded()
        val egress = policy.spec!!.egress as ArrayList
        egress.add(
            NetworkPolicy.EgressRule(
                to = listOf(NetworkPolicy.Peer(podSelector = podSelectorForJob(jobId)))
            )
        )
    }

    override fun hostAlias(jobId: String, rank: Int, alias: String) {
        podSpec.hostAliases = podSpec.hostAliases ?: ArrayList()
        val aliases = podSpec.hostAliases as ArrayList
        val podIp = runBlocking {
            k8.client.getResource(
                Pod.serializer(),
                KubernetesResources.pod.withNameAndNamespace(
                    k8.nameAllocator.jobIdAndRankToPodName(jobId, rank),
                    k8.nameAllocator.namespace()
                )
            ).status?.podIP
        } ?: return

        aliases.add(Pod.HostAlias(listOf(alias), podIp))
    }

    override fun sidecar(builder: VolcanoContainerBuilder.() -> Unit) {
        if (!supportsSidecar()) error("Cannot call sidecar {} in a sidecar container")
        podSpec.initContainers = podSpec.initContainers ?: ArrayList()
        val initContainers = podSpec.initContainers as ArrayList
        initContainers.add(
            VolcanoContainerBuilder(jobId, 1, nameAllocator, k8).also(builder).container
        )
    }
}
