package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.TolerationKeyAndValue
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.app.store.api.ContainerDescription
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.buildEnvironmentValue
import dk.sdu.cloud.service.BroadcastingStream
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.dsl.PodResource
import kotlinx.coroutines.launch

const val WORKING_DIRECTORY = "/work"
const val MULTI_NODE_DIRECTORY = "/etc/ucloud"
const val MULTI_NODE_STORAGE = "multi-node-config"
const val MULTI_NODE_CONTAINER = "init"
const val USER_CONTAINER = "user-job"

/**
 * Creates user jobs in Kubernetes.
 */
class K8JobCreationService(
    private val k8: K8Dependencies,
    private val k8JobMonitoringService: K8JobMonitoringService,
    private val networkPolicyService: NetworkPolicyService,
    private val broadcastingStream: BroadcastingStream,
    private val hostAliasesService: HostAliasesService,
    private val workspaceService: WorkspaceService,
    private val toleration: TolerationKeyAndValue? = null,
    private val devMode: Boolean = false
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

        try {
            k8.nameAllocator.findJobs(requestId).delete()
        } catch (ex: Throwable) {
            val isExceptionExpected = ex is KubernetesClientException && ex.status?.code in setOf(400, 404)
            if (!isExceptionExpected) {
                log.warn("Caught exception while deleting jobs")
                log.warn(ex.stackTraceToString())
            }
        }

        try {
            k8.nameAllocator.findServices(requestId).delete()
        } catch (ex: Throwable) {
            val isExceptionExpected = ex is KubernetesClientException && ex.status?.code in setOf(400, 404)
            if (!isExceptionExpected) {
                log.warn("Caught exception while deleting jobs")
                log.warn(ex.stackTraceToString())
            }
        }
    }

    @Suppress("LongMethod", "ComplexMethod") // Just a DSL
    private fun createJobs(verifiedJob: VerifiedJob) {
        val enableKataContainers = verifiedJob.application.invocation.container?.runAsRoot == true &&
                (verifiedJob.reservation.gpu ?: 0) <= 0

        // We need to create and prepare some other resources as well
        networkPolicyService.createPolicy(verifiedJob.id, verifiedJob.peers.map { it.jobId })
        val hostAliases = hostAliasesService.findAliasesForPeers(verifiedJob.peers)
        val preparedWorkspace = workspaceService.prepare(verifiedJob)
        val containerConfig = verifiedJob.application.invocation.container ?: ContainerDescription()

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
                    val resourceRequirements = run {
                        val reservation = verifiedJob.reservation
                        val limits = HashMap<String, Quantity>()
                        if (reservation.cpu != null) {
                            limits += "cpu" to Quantity("${(reservation.cpu!! * 1000) - if (enableKataContainers) 1000 else 0}m")
                        }

                        if (reservation.memoryInGigs != null) {
                            limits += "memory" to Quantity("${reservation.memoryInGigs!! - if (enableKataContainers) 6 else 0}Gi")
                        }

                        if (reservation.gpu != null) {
                            limits += "nvidia.com/gpu" to Quantity("${reservation.gpu}")
                        }

                        if (limits.isNotEmpty()) {
                            ResourceRequirements(limits, limits)
                        } else {
                            null
                        }
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
                                withBackoffLimit(0)

                                withLabels(
                                    mapOf(
                                        K8NameAllocator.ROLE_LABEL to k8.nameAllocator.appRole,
                                        K8NameAllocator.RANK_LABEL to rank.toString(),
                                        K8NameAllocator.JOB_ID_LABEL to verifiedJob.id
                                    )
                                )

                                if (enableKataContainers) {
                                    withAnnotations(
                                        mapOf(
                                            "io.kubernetes.cri.untrusted-workload" to "true"
                                        )
                                    )
                                }
                            }

                            spec {
                                if (verifiedJob.nodes > 1) {
                                    // Insert multi-node data
                                    //
                                    // Each pod creates a single shared volume between the init container and the
                                    // work container. This shared volume can use `emptyDir`. This directory will be
                                    // mounted at `/etc/ucloud` and will contain metadata about the other nodes.

                                    withInitContainers(
                                        container {
                                            withName(MULTI_NODE_CONTAINER)
                                            withImage("alpine:latest")
                                            withRestartPolicy("Never")
                                            withCommand(
                                                "sh",
                                                "-c",
                                                "while [ ! -f $MULTI_NODE_DIRECTORY/ready.txt ]; do sleep 0.5; done;"
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
                                                    null,
                                                    null
                                                )
                                            )
                                        }
                                    )
                                }

                                val allContainers = ArrayList<Container>()

                                allContainers.add(
                                    container container@{
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

                                        val command = app.invocation.flatMap { parameter ->
                                            parameter.buildInvocationList(givenParameters)
                                        }

                                        log.debug("Container is: ${tool.container}")
                                        log.debug("Executing command: $command")

                                        withName(USER_CONTAINER)
                                        withImage(tool.container)
                                        withRestartPolicy("Never")
                                        withCommand(command)
                                        withAutomountServiceAccountToken(false)

                                        if (resourceRequirements != null) {
                                            withResources(resourceRequirements)
                                        }

                                        run {
                                            val envVars = ArrayList<EnvVar>()
                                            verifiedJob.application.invocation.environment?.forEach { (name, value) ->
                                                val resolvedValue = value.buildEnvironmentValue(givenParameters)
                                                if (resolvedValue != null) {
                                                    envVars.add(EnvVar(name, resolvedValue, null))
                                                }
                                            }

                                            withEnv(envVars)
                                        }

                                        if (containerConfig.changeWorkingDirectory) {
                                            withWorkingDir(WORKING_DIRECTORY)
                                        }

                                        this@container.withSecurityContext(
                                            SecurityContext().apply {
                                                runAsNonRoot = !containerConfig.runAsRoot
                                                allowPrivilegeEscalation = containerConfig.runAsRoot
                                            }
                                        )

                                        // NOTE(Dan): We don't force set the UID to 11042 for the container.
                                        //  This caused problems with the CoW since Kubernetes/Docker will traverse
                                        //  all files changing ownership and setting killsuid/killsgid. The result
                                        //  is a complete copy_up for all files. This defeated the entire purpose of
                                        //  the CoW. We solve the security purely with `runAsNonRoot` and
                                        //  `!allowPrivilegeEscalation`. This requires containers to use a numeric
                                        //  `USER 11042`

                                        withVolumeMounts(
                                            VolumeMount(
                                                MULTI_NODE_DIRECTORY,
                                                null,
                                                MULTI_NODE_STORAGE,
                                                true,
                                                null,
                                                null
                                            ),

                                            *preparedWorkspace.mounts.toTypedArray(),

                                            VolumeMount().apply {
                                                name = "shm"
                                                mountPath = "/dev/shm"
                                            }
                                        )

                                        withHostAliases(*hostAliases.toTypedArray())
                                    }
                                )

                                withContainers(allContainers)

                                withVolumes(
                                    volume {
                                        name = MULTI_NODE_STORAGE
                                        emptyDir = EmptyDirVolumeSource()
                                    },

                                    *preparedWorkspace.volumes.toTypedArray(),

                                    volume {
                                        name = "shm"
                                        emptyDir = EmptyDirVolumeSource().apply {
                                            this.medium = "Memory"
                                            this.sizeLimit = if (verifiedJob.reservation.memoryInGigs != null) {
                                                Quantity("${verifiedJob.reservation.memoryInGigs}Gi")
                                            } else {
                                                Quantity("1Gi")
                                            }
                                        }
                                    }
                                )

                                if (toleration != null) {
                                    withTolerations(
                                        Toleration("NoSchedule", toleration.key, "Equal", null, toleration.value)
                                    )
                                }
                            }
                        }.build()
                    )
                }
                .done()
        }
    }

    private suspend fun awaitContainerStart(verifiedJob: VerifiedJob, useInit: Boolean): List<Pod> {
        lateinit var pods: List<Pod>
        awaitCatching(retries = 36_000, time = 100) {
            pods = k8.nameAllocator.listPods(verifiedJob.id)
            pods.isNotEmpty() && pods.all { pod ->
                // Note: We are awaiting the init containers
                val state = if (useInit) {
                    pod.status.initContainerStatuses.first().state
                } else {
                    pod.status.containerStatuses.first().state
                }

                state.running != null || state.terminated != null
            }
        }
        return pods
    }

    private fun writeToFile(
        podResource: PodResource<Pod, DoneablePod>,
        path: String,
        contents: String,
        container: String? = null,
        append: Boolean = false
    ) {
        log.debug("Writing to file: $path in ${podResource.get().metadata.name} with $container")
        val contentToFileCommand = if (append) "echo $contents >> $path" else "echo $contents > $path"
        ProcessBuilder().apply {
            command(
                listOf(
                    "kubectl",
                    "-n",
                    "app-kubernetes",
                    "exec",
                    podResource.get().metadata.name,
                    "-c",
                    container,
                    "--",
                    "sh",
                    "-c",
                    contentToFileCommand
                )
            )
        }.start().waitFor()
    }

    private suspend fun runPostSubmissionHandlers(verifiedJob: VerifiedJob) {
        if (verifiedJob.nodes > 1) {
            // Configure multi-node applications

            k8.addStatus(
                verifiedJob.id,
                "Your job is currently waiting to be scheduled. This step might take a while."
            )

            val ourPods = awaitContainerStart(verifiedJob, useInit = true)

            k8.addStatus(verifiedJob.id, "Configuring multi-node application")

            log.debug("Found the following pods: ${ourPods.map { it.metadata.name }}")

            val podsWithIp = ourPods.map {
                val rankLabel = it.metadata.labels[K8NameAllocator.RANK_LABEL]!!.toInt()
                rankLabel to it.status.podIP
            }

            log.debug(podsWithIp.toString())

            ourPods.forEach { pod ->
                val podResource = k8.client.pods()
                    .inNamespace(k8.nameAllocator.namespace)
                    .withName(pod.metadata.name)

                podsWithIp.forEach { (rank, ip) ->
                    writeToFile(
                        podResource,
                        "$MULTI_NODE_DIRECTORY/node-$rank.txt",
                        ip,
                        container = MULTI_NODE_CONTAINER
                    )
                }

                writeToFile(
                    podResource,
                    "$MULTI_NODE_DIRECTORY/rank.txt",
                    pod.metadata.labels[K8NameAllocator.RANK_LABEL]!!,
                    container = MULTI_NODE_CONTAINER
                )

                writeToFile(
                    podResource,
                    "$MULTI_NODE_DIRECTORY/number_of_nodes.txt",
                    verifiedJob.nodes.toString(),
                    container = MULTI_NODE_CONTAINER
                )

                writeToFile(
                    podResource,
                    "$MULTI_NODE_DIRECTORY/job_id.txt",
                    verifiedJob.id,
                    container = MULTI_NODE_CONTAINER
                )
            }

            log.debug("Multi node configuration written to all nodes for ${verifiedJob.id}")
        }

        run {
            // Create DNS entries for all our pods
            //
            // This is done to aid applications which assume that their hostnames are routable and are as such
            // advertised to other services as being routable. Without this networking will fail in certain cases.
            // Spark is an application of such application assuming hostnames to be routable.

            val ourPods = awaitContainerStart(verifiedJob, useInit = verifiedJob.nodes > 1)

            log.debug("Found the following pods: $ourPods")

            ourPods.forEach { pod ->
                k8.client
                    .services()
                    .inNamespace(k8.nameAllocator.namespace)
                    .create(Service().apply {
                        metadata = ObjectMeta().apply {
                            name = pod.metadata.name
                            namespace = k8.nameAllocator.namespace
                            labels = mapOf(
                                K8NameAllocator.ROLE_LABEL to k8.nameAllocator.appRole,
                                K8NameAllocator.JOB_ID_LABEL to verifiedJob.id
                            )
                        }

                        spec = ServiceSpec().apply {
                            if (!devMode) {
                                type = "ClusterIP"
                                clusterIP = "None"

                                ports = listOf(
                                    ServicePort().apply {
                                        name = "placeholder"
                                        port = 80
                                        targetPort = IntOrString(80)
                                        protocol = "TCP"
                                    }
                                )
                            } else {
                                // Dev mode is made to work well with minikube and allows us to expose it quite easily
                                type = "LoadBalancer"

                                val target = verifiedJob.application.invocation.web?.port
                                    ?: verifiedJob.application.invocation.vnc?.port ?: 80

                                ports = listOf(
                                    ServicePort().apply {
                                        name = "web"
                                        port = 80
                                        targetPort = IntOrString(target)
                                        protocol = "TCP"
                                    }
                                )
                            }

                            selector = mapOf(
                                K8NameAllocator.ROLE_LABEL to k8.nameAllocator.appRole,
                                K8NameAllocator.JOB_ID_LABEL to verifiedJob.id,
                                K8NameAllocator.RANK_LABEL to pod.metadata.labels[K8NameAllocator.RANK_LABEL]
                            )
                        }
                    })
            }
        }

        if (verifiedJob.nodes > 1) {
            log.debug("Writing ready flag in all nodes")
            val ourPods = awaitContainerStart(verifiedJob, useInit = true)
            ourPods.forEach { pod ->
                val podResource =
                    k8.client.pods().inNamespace(k8.nameAllocator.namespace).withName(pod.metadata.name)

                writeToFile(
                    podResource,
                    "$MULTI_NODE_DIRECTORY/ready.txt",
                    "READY",
                    container = MULTI_NODE_CONTAINER
                )
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

private fun SimpleDuration.toSeconds(): Long {
    return (hours * 3600L) + (minutes * 60) + seconds
}
