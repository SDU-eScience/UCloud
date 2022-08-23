package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.SimpleCache
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class K8Pod(
    override val k8Client: KubernetesClient,
    override val pod: Pod,
    override val jobId: String,
    override val rank: Int,
) : PodBasedContainer() {
    private val namespace = pod.metadata!!.namespace!!

    override suspend fun cancel() {
        k8Client.deleteResource(
            KubernetesResourceLocator.common.pod.withNameAndNamespace(pod.metadata!!.name!!, namespace,)
        )

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
        return (pod.spec?.nodeSelector?.get("ucloud.dk/machine") as? JsonPrimitive)?.content
    }
}

class K8PodRuntime(
    private val k8Client: KubernetesClient,
    private val namespace: String,
) : ContainerRuntime {
    private val canReadNodes = SimpleCache<Unit, Boolean>(SimpleCache.DONT_EXPIRE) {
        try {
            k8Client.listResources(
                Node.serializer(),
                KubernetesResourceLocator.common.node
            )
            true
        } catch (ex: Throwable) {
            false
        }
    }

    override fun builder(jobId: String, replicas: Int, block: ContainerBuilder.() -> Unit): ContainerBuilder {
        return K8PodContainerBuilder(jobId, replicas, k8Client, namespace).also(block)
    }

    override suspend fun scheduleGroup(group: List<ContainerBuilder>) {
        // TODO(Dan): For a more robust implementation, we would need to check if all the jobs in the group can be
        //  scheduled together immediately. The current use-case is meant only for a proof-of-concept, as a result, we
        //  don't worry too much about this right now.
        for (container in group) {
            if (container !is K8PodContainerBuilder) error("This runtime only accepts K8PodContainerBuilder")

            if (container.replicas > 1) {
                // TODO(Dan): Pending proper support in FeatureMultiNode for exporting information
                throw RPCException(
                    "Multiple nodes per job is not supported by this provider",
                    dk.sdu.cloud.calls.HttpStatusCode.BadRequest
                )
            }

            repeat(container.replicas) { rank ->
                container.pod.metadata!!.name = idAndRankToPodName(container.jobId, rank)

                k8Client.createResource(
                    KubernetesResourceLocator.common.pod.withNamespace(namespace),
                    defaultMapper.encodeToString(Pod.serializer(), container.pod)
                )
            }

            val networkPolicy = container.myPolicy
            for (attempt in 1..5) {
                try {
                    k8Client.createResource(
                        KubernetesResourceLocator.common.networkPolicies.withNamespace(namespace),
                        defaultMapper.encodeToString(NetworkPolicy.serializer(), networkPolicy)
                    )
                    break
                } catch (ex: KubernetesException) {
                    if (ex.statusCode == HttpStatusCode.Conflict) {
                        k8Client.deleteResource(
                            KubernetesResources.networkPolicies.withNameAndNamespace(
                                networkPolicy.metadata!!.name!!,
                                namespace,
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
            val pod = k8Client.getResource(
                Pod.serializer(),
                KubernetesResourceLocator.common.pod.withNameAndNamespace(
                    idAndRankToPodName(jobId, rank),
                    namespace
                )
            )

            return K8Pod(k8Client, pod, jobId, rank)
        } catch (ex: KubernetesException) {
            if (ex.statusCode == HttpStatusCode.NotFound) {
                return null
            }
            throw ex
        }
    }

    override suspend fun list(): List<Container> {
        return k8Client
            .listResources(
                Pod.serializer(),
                KubernetesResourceLocator.common.pod.withNamespace(namespace)
            )
            .items
            .asSequence()
            .filter { it.metadata?.annotations?.get(podAnnotation) != null }
            .mapNotNull {
                runCatching {
                    val (jobId, rank) = podNameToIdAndRank(it.metadata!!.name!!)
                    K8Pod(k8Client, it, jobId, rank)
                }.getOrNull()
            }
            .toList()
    }

    override suspend fun listNodes(): List<ComputeNode> {
        if (canReadNodes.get(Unit) != true) return emptyList()

        val namespace = k8Client.getResource(
            Namespace.serializer(),
            KubernetesResources.namespaces.withName(namespace)
        )
        val computeAnnotation =
            (namespace.metadata?.annotations?.get("scheduler.alpha.kubernetes.io/node-selector") as? JsonPrimitive)
                ?.content

        val allNodes = k8Client.listResources(Node.serializer(), KubernetesResources.node.withNamespace(NAMESPACE_ANY))
            .items
            .filter { node ->
                computeAnnotation != "${NameAllocator.nodeLabel}=true" ||
                    (node.metadata?.labels?.get(NameAllocator.nodeLabel) as? JsonPrimitive)?.content == "true"
            }

        return allNodes.map { KubernetesNode(it) }
    }

    companion object {
        const val podAnnotation = "ucloud.dk/user-job"

        fun idAndRankToPodName(id: String, rank: Int): String = "j-$id-$rank"
        fun podNameToIdAndRank(podName: String): Pair<String, Int> {
            val withoutPrefix = podName.removePrefix("j-")
            val rankPart = withoutPrefix.substringAfterLast('-')
            val idPart = withoutPrefix.substringBeforeLast('-')
            return Pair(idPart, rankPart.toInt())
        }
    }
}

class K8PodContainerBuilder(
    override val jobId: String,
    override val replicas: Int,
    private val k8Client: KubernetesClient,
    private val namespace: String,
) : PodBasedBuilder() {
    val myPolicy: NetworkPolicy

    val pod: Pod = Pod(
        metadata = ObjectMeta(
            labels = JsonObject(mapOf(
                K8_JOB_NAME_LABEL to JsonPrimitive(jobId)
            )),
            annotations = JsonObject(mapOf(
                K8PodRuntime.podAnnotation to JsonPrimitive(true)
            ))
        ),
        spec = Pod.Spec(

        )
    )
    override val podSpec: Pod.Spec = pod.spec!!

    init {
        initPodSpec()
        myPolicy = NetworkPolicy(
            metadata = ObjectMeta(name = K8_POD_NETWORK_POLICY_PREFIX + this.jobId),
            spec = NetworkPolicy.Spec(
                egress = ArrayList(),
                ingress = ArrayList(),
                podSelector = k8PodSelectorForJob(this.jobId)
            )
        )
    }

    override var productCategoryRequired: String?
        get() = error("read not supported")
        set(value) {
            podSpec.nodeSelector = JsonObject(
                mapOf(
                    "ucloud.dk/machine" to JsonPrimitive(value)
                )
            )
        }

    override var isSidecar: Boolean = false
    override fun supportsSidecar(): Boolean = !isSidecar
    override fun sidecar(name: String, builder: ContainerBuilder.() -> Unit) {
        if (!supportsSidecar()) error("Cannot call sidecar {} in a sidecar container")
        podSpec.initContainers = podSpec.initContainers ?: ArrayList()
        val initContainers = podSpec.initContainers as ArrayList
        val sidecarContainer = K8PodContainerBuilder(jobId, 1, k8Client, namespace).also(builder).container
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
                    K8PodRuntime.idAndRankToPodName(jobId, rank),
                    namespace
                )
            ).status?.podIP
        } ?: return

        aliases.add(Pod.HostAlias(listOf(alias), podIp))
    }

    override fun upsertAnnotation(key: String, value: String) {
        val annotationEntries = (pod.metadata?.annotations?.entries ?: emptySet())
            .associate { it.key to JsonPrimitive(it.value.toString()) }
            .toMutableMap()

        annotationEntries[key] = JsonPrimitive(value)

        pod.metadata!!.annotations = JsonObject(annotationEntries)
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
