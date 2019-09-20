package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.app.store.api.ContainerDescription
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.buildEnvironmentValue
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.api.LINUX_FS_USER_UID
import dk.sdu.cloud.service.BroadcastingStream
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource
import io.fabric8.kubernetes.api.model.EnvVar
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource
import io.fabric8.kubernetes.api.model.PodSecurityContext
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.ResourceRequirements
import io.fabric8.kubernetes.api.model.VolumeMount
import io.fabric8.kubernetes.client.KubernetesClientException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch

const val INPUT_DIRECTORY = "/input"
const val WORKING_DIRECTORY = "/work"
const val MULTI_NODE_DIRECTORY = "/etc/sducloud"
const val DATA_STORAGE = "workspace-storage"
const val MULTI_NODE_STORAGE = "multi-node-config"
const val MULTI_NODE_CONTAINER = "init"

/**
 * Creates user jobs in Kubernetes.
 */
class K8JobCreationService(
    private val k8: K8Dependencies,
    private val k8JobMonitoringService: K8JobMonitoringService,
    private val networkPolicyService: NetworkPolicyService,
    private val sharedFileSystemMountService: SharedFileSystemMountService,
    private val broadcastingStream: BroadcastingStream,
    private val hostAliasesService: HostAliasesService
) {
    fun create(verifiedJob: VerifiedJob) {
        log.info("Creating new job with name: ${verifiedJob.id}")
        createJobs(verifiedJob)

        k8.scope.launch {
            k8JobMonitoringService.runPostCreateHandlers(verifiedJob) {
                runPostSubmissionHandlers(verifiedJob)
            }
        }
    }

    suspend fun cleanup(requestId: String) {
        try {
            broadcastingStream.broadcast(ProxyEvent(requestId, shouldCreate = false), ProxyEvents.events)
        } catch (ignored: Throwable) {
            // Ignored
        }

        try {
            networkPolicyService.deletePolicy(requestId)
        } catch (ex: KubernetesClientException) {
            // Ignored
            if (ex.status?.code !in setOf(400, 404)) {
                log.warn(ex.stackTraceToString())
            }
        }
    }

    @Suppress("LongMethod", "ComplexMethod") // Just a DSL
    private fun createJobs(verifiedJob: VerifiedJob) {
        // We need to create and prepare some other resources as well
        val (sharedVolumes, sharedMounts) = sharedFileSystemMountService.createVolumesAndMounts(verifiedJob)
        networkPolicyService.createPolicy(verifiedJob.id, verifiedJob.peers.map { it.jobId })
        val hostAliases = hostAliasesService.findAliasesForPeers(verifiedJob.peers)

        // Create a kubernetes job for each node in our job
        repeat(verifiedJob.nodes) { rank ->
            val podName = k8.nameAllocator.jobName(verifiedJob.id, rank)
            k8.client.batch().jobs().inNamespace(k8.nameAllocator.namespace).createNew()
                .metadata {
                    withName(podName)
                    withNamespace(k8.nameAllocator.namespace)

                    withLabels(
                        mapOf(
                            K8NameAllocator.ROLE_LABEL to k8.nameAllocator.appRole,
                            K8NameAllocator.RANK_LABEL to rank.toString(),
                            K8NameAllocator.JOB_ID_LABEL to verifiedJob.id
                        )
                    )
                }
                .spec {
                    val containerConfig = verifiedJob.application.invocation.container ?: ContainerDescription()

                    val reservation = verifiedJob.reservation
                    val resourceRequirements =
                        if (reservation.cpu != null && reservation.memoryInGigs != null) {
                            ResourceRequirements(
                                mapOf(
                                    "memory" to Quantity("${reservation.memoryInGigs}Gi"),
                                    "cpu" to Quantity("${reservation.cpu!! * 1000}m")
                                ),
                                mapOf(
                                    "memory" to Quantity("${reservation.memoryInGigs}Gi"),
                                    "cpu" to Quantity("${reservation.cpu!! * 1000}m")
                                )
                            )
                        } else {
                            null
                        }

                    val deadline = verifiedJob.maxTime.toSeconds()
                    withActiveDeadlineSeconds(deadline)
                    withBackoffLimit(1)
                    withParallelism(1)

                    withTemplate(
                        PodTemplateSpecBuilder().apply {
                            metadata {
                                withName(podName)
                                withNamespace(k8.nameAllocator.namespace)

                                withLabels(
                                    mapOf(
                                        K8NameAllocator.ROLE_LABEL to k8.nameAllocator.appRole,
                                        K8NameAllocator.RANK_LABEL to rank.toString(),
                                        K8NameAllocator.JOB_ID_LABEL to verifiedJob.id
                                    )
                                )
                            }

                            spec {
                                if (verifiedJob.nodes > 1) {
                                    // Insert multi-node data
                                    //
                                    // Each pod creates a single shared volume between the init container and the
                                    // work container. This shared volume can use `emptyDir`. This directory will be
                                    // mounted at `/etc/sducloud` and will contain metadata about the other nodes.

                                    withInitContainers(
                                        container {
                                            withName(MULTI_NODE_CONTAINER)
                                            withImage("alpine:latest")
                                            withRestartPolicy("Never")
                                            withCommand(
                                                "sh",
                                                "-c",
                                                "while [ ! -f $MULTI_NODE_DIRECTORY/job_id.txt ]; do sleep 0.5; done;"
                                            )
                                            withAutomountServiceAccountToken(false)

                                            if (resourceRequirements != null) {
                                                withResources(resourceRequirements)
                                            }

                                            withVolumeMounts(
                                                VolumeMount(
                                                    MULTI_NODE_DIRECTORY,
                                                    null,
                                                    MULTI_NODE_STORAGE,
                                                    false,
                                                    null
                                                )
                                            )
                                        }
                                    )
                                }

                                withContainers(
                                    container {
                                        val uid = LINUX_FS_USER_UID.toLong()
                                        val app = verifiedJob.application.invocation
                                        val tool = verifiedJob.application.invocation.tool.tool!!.description
                                        val givenParameters =
                                            verifiedJob.jobInput.asMap().mapNotNull { (paramName, value) ->
                                                if (value != null) {
                                                    app.parameters.find { it.name == paramName }!! to value
                                                } else {
                                                    null
                                                }
                                            }.toMap()

                                        val command =
                                            app.invocation.flatMap { it.buildInvocationList(givenParameters) }

                                        log.debug("Container is: ${tool.container}")
                                        log.debug("Executing command: $command")

                                        withName("user-job")
                                        withImage(tool.container)
                                        withRestartPolicy("Never")
                                        withCommand(command)
                                        withAutomountServiceAccountToken(false)

                                        if (resourceRequirements != null) {
                                            withResources(resourceRequirements)
                                        }

                                        run {
                                            val envVars = ArrayList<EnvVar>()

                                            val builtInVars = mapOf(
                                                "CLOUD_UID" to uid.toString()
                                            )

                                            builtInVars.forEach { (t, u) ->
                                                envVars.add(EnvVar(t, u, null))
                                            }

                                            verifiedJob.application.invocation.environment?.forEach { (name, value) ->
                                                if (name !in builtInVars) {
                                                    val resolvedValue = value.buildEnvironmentValue(givenParameters)
                                                    if (resolvedValue != null) {
                                                        envVars.add(EnvVar(name, resolvedValue, null))
                                                    }
                                                }
                                            }

                                            withEnv(envVars)
                                        }

                                        if (containerConfig.changeWorkingDirectory) {
                                            withWorkingDir(WORKING_DIRECTORY)
                                        }

                                        if (containerConfig.runAsRoot) {
                                            withSecurityContext(
                                                PodSecurityContext(
                                                    0,
                                                    0,
                                                    false,
                                                    0,
                                                    null,
                                                    emptyList(),
                                                    emptyList()
                                                )
                                            )
                                        } else if (containerConfig.runAsRealUser) {
                                            withSecurityContext(
                                                PodSecurityContext(
                                                    uid,
                                                    uid,
                                                    false,
                                                    uid,
                                                    null,
                                                    emptyList(),
                                                    emptyList()
                                                )
                                            )
                                        }

                                        withVolumeMounts(
                                            VolumeMount(
                                                WORKING_DIRECTORY,
                                                null,
                                                DATA_STORAGE,
                                                false,
                                                verifiedJob.workspace
                                                    ?.removePrefix("/")
                                                    ?.removeSuffix("/")
                                                    ?.let { it + "/output" }
                                                    ?: throw RPCException(
                                                        "No workspace found",
                                                        HttpStatusCode.BadRequest
                                                    )
                                            ),

                                            VolumeMount(
                                                INPUT_DIRECTORY,
                                                null,
                                                DATA_STORAGE,
                                                true,
                                                verifiedJob.workspace
                                                    ?.removePrefix("/")
                                                    ?.removeSuffix("/")
                                                    ?.let { it + "/input" }
                                                    ?: throw RPCException(
                                                        "No workspace found",
                                                        HttpStatusCode.BadRequest
                                                    )
                                            ),

                                            VolumeMount(
                                                MULTI_NODE_DIRECTORY,
                                                null,
                                                MULTI_NODE_STORAGE,
                                                true,
                                                null
                                            ),

                                            *sharedMounts.toTypedArray()
                                        )

                                        withHostAliases(*hostAliases.toTypedArray())
                                    }
                                )

                                withVolumes(
                                    volume {
                                        withName(DATA_STORAGE)
                                        withPersistentVolumeClaim(
                                            PersistentVolumeClaimVolumeSource(
                                                "cephfs",
                                                false
                                            )
                                        )
                                    },

                                    volume {
                                        withName(MULTI_NODE_STORAGE)
                                        withEmptyDir(EmptyDirVolumeSource())
                                    },

                                    *sharedVolumes.toTypedArray()
                                )
                            }
                        }.build()
                    )
                }
                .done()
        }
    }

    private suspend fun runPostSubmissionHandlers(verifiedJob: VerifiedJob) {
        if (verifiedJob.nodes > 1) {
            k8.addStatus(
                verifiedJob.id,
                "Your job is currently waiting to be scheduled. This step might take a while."
            )

            awaitCatching(retries = 36_000, time = 100) {
                val pods = k8.nameAllocator.listPods(verifiedJob.id)
                pods.all { pod ->
                    // Note: We are awaiting the init containers
                    val state = pod.status.initContainerStatuses.first().state
                    state.running != null || state.terminated != null
                }
            }

            k8.addStatus(verifiedJob.id, "Configuring multi-node application")

            // Now we initialize the files stored in MULTI_NODE_CONFIG
            val findPods = k8.nameAllocator.listPods(verifiedJob.id)

            log.debug("Found the following pods: ${findPods.map { it.metadata.name }}")

            val podsWithIp = findPods.map {
                val rankLabel = it.metadata.labels[K8NameAllocator.RANK_LABEL]!!.toInt()
                rankLabel to it.status.podIP
            }

            log.debug(podsWithIp.toString())

            findPods.forEach { pod ->
                val podResource = k8.client.pods()
                    .inNamespace(k8.nameAllocator.namespace)
                    .withName(pod.metadata.name)

                fun writeFile(path: String, contents: String) {
                    val (_, _, ins) = podResource.execWithDefaultListener(
                        listOf("sh", "-c", "cat > $path"),
                        attachStdout = true,
                        attachStdin = true,
                        container = MULTI_NODE_CONTAINER
                    )

                    ins!!.use {
                        it.write(contents.toByteArray())
                    }
                }

                podsWithIp.forEach { (rank, ip) ->
                    writeFile("$MULTI_NODE_DIRECTORY/node-$rank.txt", ip + "\n")
                }

                writeFile("$MULTI_NODE_DIRECTORY/rank.txt", pod.metadata.labels[K8NameAllocator.RANK_LABEL]!! + "\n")
                writeFile("$MULTI_NODE_DIRECTORY/number_of_nodes.txt", verifiedJob.nodes.toString() + "\n")
                writeFile("$MULTI_NODE_DIRECTORY/job_id.txt", verifiedJob.id + "\n")
            }

            log.debug("Multi node configuration written to all nodes for ${verifiedJob.id}")
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

private fun SimpleDuration.toSeconds(): Long {
    return (hours * 3600L) + (minutes * 60) + seconds
}
