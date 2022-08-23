package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.CpuAndMemory
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.utils.forEachGraal
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class KubernetesNode(private val node: Node) : ComputeNode {
    override suspend fun productCategory(): String? =
        (node.metadata?.labels?.get("ucloud.dk/machine") as? JsonPrimitive)?.contentOrNull

    override suspend fun retrieveCapacity(): CpuAndMemory {
        val vCpu = node.status?.allocatable?.cpu?.toDouble() ?: 0.0
        val memory = memoryStringToBytes(node.status?.allocatable?.memory)
        return CpuAndMemory(vCpu, memory)
    }

    companion object {
        private fun memoryStringToBytes(memory: String?): Long {
            val numbersOnly = Regex("[^0-9]")
            val ki = 1024
            val mi = 1048576
            val gi = 1073741824
            val ti = 1099511627776
            val pi = 1125899906842624

            return if (memory.isNullOrBlank()) {
                0
            } else (when {
                memory.contains("Ki") -> {
                    numbersOnly.replace(memory, "").toLong() * ki
                }

                memory.contains("Mi") -> {
                    numbersOnly.replace(memory, "").toLong() * mi
                }

                memory.contains("Gi") -> {
                    numbersOnly.replace(memory, "").toLong() * gi
                }

                memory.contains("Ti") -> {
                    numbersOnly.replace(memory, "").toLong() * ti
                }

                memory.contains("Pi") -> {
                    numbersOnly.replace(memory, "").toLong() * pi
                }

                else -> {
                    numbersOnly.replace(memory, "").toLong()
                }
            })
        }
    }
}

class VolcanoRuntime(
    private val k8: K8DependenciesImpl,
) : ContainerRuntime {
    override fun builder(
        jobId: String,
        replicas: Int,
        block: ContainerBuilder.() -> Unit
    ): ContainerBuilder {
        return VolcanoContainerBuilder(jobId, replicas, k8.nameAllocator, k8).also(block)
    }

    override suspend fun scheduleGroup(group: List<ContainerBuilder>) {
        group.forEachGraal { c ->
            if (c !is VolcanoContainerBuilder) error("This runtime only accepts volcano jobs")

            k8.client.createResource(
                KubernetesResourceLocator.common.volcanoJob.withNamespace(k8.nameAllocator.namespace()),
                defaultMapper.encodeToString(VolcanoJob.serializer(), c.job)
            )

            val networkPolicy = c.myPolicy
            for (attempt in 1..5) {
                try {
                    k8.client.createResource(
                        KubernetesResourceLocator.common.networkPolicies.withNamespace(k8.nameAllocator.namespace()),
                        defaultMapper.encodeToString(NetworkPolicy.serializer(), networkPolicy)
                    )
                    break
                } catch (ex: KubernetesException) {
                    if (ex.statusCode == HttpStatusCode.Conflict) {
                        k8.client.deleteResource(
                            KubernetesResources.networkPolicies.withNameAndNamespace(
                                networkPolicy.metadata!!.name!!,
                                k8.nameAllocator.namespace()
                            )
                        )
                    }
                    delay(500)
                }
            }
        }
    }

    override suspend fun retrieve(jobId: String, rank: Int): Container? {
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

    override suspend fun list(): List<Container> {
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

    override suspend fun listNodes(): List<ComputeNode> {
        val namespace = k8.client.getResource(
            Namespace.serializer(),
            KubernetesResources.namespaces.withName(k8.nameAllocator.namespace())
        )
        val computeAnnotation =
            (namespace.metadata?.annotations?.get("scheduler.alpha.kubernetes.io/node-selector") as? JsonPrimitive)
                ?.content

        val allNodes = k8.client.listResources(Node.serializer(), KubernetesResources.node.withNamespace(NAMESPACE_ANY))
            .items
            .filter { node ->
                computeAnnotation != "${NameAllocator.nodeLabel}=true" ||
                    (node.metadata?.labels?.get(NameAllocator.nodeLabel) as? JsonPrimitive)?.content == "true"
            }

        return allNodes.map { KubernetesNode(it) }
    }
}

class VolcanoContainer(
    override val jobId: String,
    override val rank: Int,
    val volcanoJob: VolcanoJob,
    override val pod: Pod,
    private val k8: K8DependenciesImpl,
) : PodBasedContainer() {
    override val k8Client: KubernetesClient get() = k8.client
    override val annotations: Map<String, String>
        get() {
            val annotationEntries = pod.metadata?.annotations?.entries ?: emptySet()
            return annotationEntries.associate { it.key to it.value.toString() }
        }

    override fun stateAndMessage(): Pair<JobState, String> {
        return when (val phase = volcanoJob.status?.state?.phase) {
            VolcanoJobPhase.Pending -> {
                Pair(JobState.IN_QUEUE, "Job is waiting in the queue")
            }

            VolcanoJobPhase.Running -> {
                Pair(JobState.RUNNING, "Job is now running")
            }

            VolcanoJobPhase.Restarting -> {
                Pair(JobState.IN_QUEUE, "Job is restarting")
            }

            VolcanoJobPhase.Failed -> {
                Pair(JobState.SUCCESS, "Job is terminating (exit code ≠ 0 or terminated by UCloud/compute)")
            }

            VolcanoJobPhase.Terminated, VolcanoJobPhase.Terminating,
            VolcanoJobPhase.Completed, VolcanoJobPhase.Completing,
            VolcanoJobPhase.Aborted, VolcanoJobPhase.Aborting,
            -> {
                Pair(JobState.SUCCESS, "Job is terminating")
            }

            else -> Pair(JobState.FAILURE, "Unknown state $phase")
        }
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
        if (rank != 0) return // Only perform deletions on the root job

        runCatching {
            k8.client.deleteResource(
                KubernetesResourceLocator.common.volcanoJob.withNameAndNamespace(
                    volcanoJob.metadata!!.name!!,
                    volcanoJob.metadata!!.namespace!!,
                )
            )
        }

        runCatching {
            k8.client.deleteResource(
                KubernetesResources.networkPolicies.withNameAndNamespace(
                    VOLCANO_NETWORK_POLICY_PREFIX + this.jobId,
                    k8.nameAllocator.namespace()
                )
            )
        }
    }

    override suspend fun allowNetworkTo(jobId: String, rank: Int?) {
        k8.client.patchResource(
            KubernetesResources.networkPolicies.withNameAndNamespace(
                VOLCANO_NETWORK_POLICY_PREFIX + this.jobId,
                k8.nameAllocator.namespace()
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
                                        podSelector = volcanoPodSelectorForJob(jobId, k8.nameAllocator)
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
        k8.client.patchResource(
            KubernetesResources.networkPolicies.withNameAndNamespace(
                VOLCANO_NETWORK_POLICY_PREFIX + this.jobId,
                k8.nameAllocator.namespace()
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
                                        podSelector = volcanoPodSelectorForJob(jobId, k8.nameAllocator)
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

    override suspend fun productCategory(): String? {
        return (volcanoJob.spec?.tasks?.get(0)?.template?.spec
            ?.nodeSelector?.get("ucloud.dk/machine") as? JsonPrimitive)?.content
    }
}

class VolcanoContainerBuilder(
    override val jobId: String,
    override val replicas: Int,
    private val nameAllocator: NameAllocator,
    private val k8: K8DependenciesImpl,
    override val isSidecar: Boolean = false
) : PodBasedBuilder() {
    override var productCategoryRequired: String?
        get() = error("read not supported")
        set(value) {
            podSpec.nodeSelector = JsonObject(
                mapOf(
                    "ucloud.dk/machine" to JsonPrimitive(value)
                )
            )
        }

    override fun supportsSidecar(): Boolean = !isSidecar

    val myPolicy: NetworkPolicy
    val job: VolcanoJob = VolcanoJob(
        metadata = ObjectMeta(nameAllocator.jobIdToJobName(jobId), nameAllocator.namespace()),
        spec = VolcanoJob.Spec(
            schedulerName = "volcano",
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
        myPolicy = NetworkPolicy(
            metadata = ObjectMeta(name = VOLCANO_NETWORK_POLICY_PREFIX + this.jobId),
            spec = NetworkPolicy.Spec(
                egress = ArrayList(),
                ingress = ArrayList(),
                podSelector = volcanoPodSelectorForJob(this.jobId, nameAllocator)
            )
        )
    }

    override fun allowNetworkTo(jobId: String, rank: Int?) {
        val ingress = myPolicy.spec!!.ingress as ArrayList

        ingress.add(
            NetworkPolicy.IngressRule(
                from = listOf(NetworkPolicy.Peer(podSelector = volcanoPodSelectorForJob(jobId, nameAllocator)))
            )
        )
    }

    override fun allowNetworkFrom(jobId: String, rank: Int?) {
        val egress = myPolicy.spec!!.egress as ArrayList
        egress.add(
            NetworkPolicy.EgressRule(
                to = listOf(NetworkPolicy.Peer(podSelector = volcanoPodSelectorForJob(jobId, nameAllocator)))
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
            k8.client.getResource(
                Pod.serializer(),
                KubernetesResources.pod.withNameAndNamespace(
                    k8.nameAllocator.jobIdAndRankToPodName(jobId, rank),
                    k8.nameAllocator.namespace()
                )
            ).status?.podIP
        } ?: run {
            return
        }

        aliases.add(Pod.HostAlias(listOf(alias), podIp))
    }

    override fun sidecar(name: String, builder: ContainerBuilder.() -> Unit) {
        if (!supportsSidecar()) error("Cannot call sidecar {} in a sidecar container")
        podSpec.initContainers = podSpec.initContainers ?: ArrayList()
        val initContainers = podSpec.initContainers as ArrayList
        val sidecarContainer = VolcanoContainerBuilder(jobId, 1, nameAllocator, k8).also(builder).container
        sidecarContainer.name = name
        initContainers.add(sidecarContainer)
    }

    override fun upsertAnnotation(key: String, value: String) {
        val annotationEntries = (job.metadata?.annotations?.entries ?: emptySet())
            .associate { it.key to JsonPrimitive(it.value.toString()) }
            .toMutableMap()

        annotationEntries[key] = JsonPrimitive(value)

        job.metadata!!.annotations = JsonObject(annotationEntries)
    }
}

private const val VOLCANO_NETWORK_POLICY_PREFIX = "policy-"

private fun volcanoPodSelectorForJob(jobId: String, nameAllocator: NameAllocator): LabelSelector = LabelSelector(
    matchLabels = JsonObject(
        mapOf(
            VOLCANO_JOB_NAME_LABEL to JsonPrimitive(nameAllocator.jobIdToJobName(jobId))
        )
    )
)
