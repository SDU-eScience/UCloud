package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.ContainerDescription
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.buildEnvironmentValue
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.batch.Job
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watcher
import io.ktor.http.HttpStatusCode
import io.ktor.util.cio.readChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.time.ZonedDateTime

const val JOB_PREFIX = "job-"
const val ROLE_LABEL = "role"
const val RANK_LABEL = "rank"
const val JOB_ID_LABEL = "job-id"
const val INPUT_DIRECTORY = "/input"
const val WORKING_DIRECTORY = "/work"
const val MULTI_NODE_DIRECTORY = "/etc/sducloud"
const val DATA_STORAGE = "workspace-storage"
const val MULTI_NODE_STORAGE = "multi-node-config"
const val MULTI_NODE_CONTAINER = "init"

private class K8JobState(val id: String) {
    private val mutex: Mutex = Mutex()
    var finished: Boolean = false
        private set

    suspend fun markAsFinished(block: suspend () -> Unit) {
        mutex.withLock {
            if (!finished) {
                block()
                finished = true
            }
        }
    }

    suspend fun withLock(block: suspend () -> Unit) {
        mutex.withLock {
            if (!finished) {
                block()
            }
        }
    }
}

// This manager will leak memory, but we shouldn't just delete from it when a job finishes. In fact we need it for
// slightly after we delete a job since we will be notified about it during deletion.
//
// Be careful when this leak is being fixed. For now we just leave it in, it is unlikely to leak a lot of memory.
private class JobManager {
    private val mutex = Mutex()
    private val states = HashMap<String, K8JobState>()

    suspend fun get(jobId: String): K8JobState {
        mutex.withLock {
            val existing = states[jobId]
            if (existing != null) return existing

            val state = K8JobState(jobId)
            states[jobId] = state
            return state
        }
    }
}

class PodService(
    private val k8sClient: KubernetesClient,
    private val serviceClient: AuthenticatedClient,
    private val networkPolicyService: NetworkPolicyService,
    private val sharedFileSystemMountService: SharedFileSystemMountService,
    private val hostAliasesService: HostAliasesService,
    private val namespace: String = "app-kubernetes",
    private val appRole: String = "sducloud-app"
) {
    private val jobManager = JobManager()

    private val isRunningInsideKubernetes: Boolean by lazy {
        runCatching {
            File("/var/run/secrets/kubernetes.io").exists()
        }.getOrNull() == true
    }

    private fun jobName(requestId: String, rank: Int): String = "$JOB_PREFIX$requestId-$rank"

    private fun reverseLookupJobName(jobName: String): String? =
        if (jobName.startsWith(JOB_PREFIX)) jobName.removePrefix(JOB_PREFIX).substringBeforeLast('-') else null

    private fun findPods(jobId: String?): List<Pod> =
        k8sClient.pods().inNamespace(namespace).withLabel(JOB_ID_LABEL, jobId).list().items

    fun initializeListeners() {
        fun handlePodEvent(job: Job) {
            val jobName = job.metadata.name
            val jobId = reverseLookupJobName(jobName) ?: return

            // Check for failure
            val firstOrNull = job.status?.conditions?.firstOrNull()
            if (firstOrNull != null && firstOrNull.type == "Failed" && firstOrNull.reason == "DeadlineExceeded") {
                GlobalScope.launch {
                    jobManager.get(jobId).markAsFinished {
                        ComputationCallbackDescriptions.requestStateChange.call(
                            StateChangeRequest(
                                jobId,
                                JobState.TRANSFER_SUCCESS,
                                job.status.conditions.first().message
                            ),
                            serviceClient
                        ).orThrow()

                        ComputationCallbackDescriptions.completed.call(
                            JobCompletedRequest(
                                jobId,
                                null,
                                false
                            ),
                            serviceClient
                        ).orThrow()
                    }
                }

                return
            }

            val resources = findPods(jobId)
            if (resources.isEmpty()) return

            var isDone = true
            var maxDurationInMillis = 0L
            var isSuccess = true
            for (resource in resources) {
                val userContainer = resource.status.containerStatuses.getOrNull(0) ?: return
                val containerState = userContainer.state.terminated

                if (containerState == null || containerState.startedAt == null) {
                    isDone = false
                    break
                }

                val startAt = ZonedDateTime.parse(containerState.startedAt).toInstant().toEpochMilli()
                val finishedAt =
                    ZonedDateTime.parse(containerState.finishedAt).toInstant().toEpochMilli()

                // We add 5 seconds for just running the application.
                // It seems unfair that a job completing instantly is accounted for nothing.
                val duration = ((finishedAt - startAt) + 5_000)
                if (duration > maxDurationInMillis) {
                    maxDurationInMillis = duration
                }

                if (containerState.exitCode != 0) {
                    isSuccess = false
                }
            }

            if (isDone) {
                val resource = resources.first()
                GlobalScope.launch {
                    jobManager.get(jobId).markAsFinished {
                        val duration = SimpleDuration.fromMillis(maxDurationInMillis)
                        log.info("App finished in $duration")

                        ComputationCallbackDescriptions.requestStateChange.call(
                            StateChangeRequest(
                                jobId,
                                JobState.TRANSFER_SUCCESS,
                                "Job has finished. Total duration: $duration."
                            ),
                            serviceClient
                        ).orThrow()

                        transferLog(jobId, resource.metadata.name)

                        log.info("Calling completed")
                        ComputationCallbackDescriptions.completed.call(
                            JobCompletedRequest(
                                jobId,
                                duration,
                                isSuccess
                            ),
                            serviceClient
                        ).orThrow()
                    }
                }
            }
        }

        // Handle old pods on start up
        k8sClient.batch().jobs().inNamespace(namespace).withLabel(ROLE_LABEL, appRole).list().items.forEach {
            handlePodEvent(it)
        }

        // Watch for new pods
        k8sClient.batch().jobs().inNamespace(namespace).withLabel(ROLE_LABEL, appRole).watch(object : Watcher<Job> {
            override fun onClose(cause: KubernetesClientException?) {
                // Do nothing
            }

            override fun eventReceived(action: Watcher.Action, resource: Job) {
                handlePodEvent(resource)
            }
        })
    }

    private suspend fun transferLog(jobId: String, podName: String) {
        log.debug("Downloading log")
        val logFile = Files.createTempFile("log", ".txt").toFile()
        k8sClient.pods().inNamespace(namespace).withName(podName)
            .logReader.use { ins ->
            logFile.writer().use { out ->
                ins.copyTo(out)
            }
        }

        ComputationCallbackDescriptions.submitFile.call(
            SubmitComputationResult(
                jobId,
                "stdout.txt",
                false,
                BinaryStream.outgoingFromChannel(logFile.readChannel(), logFile.length())
            ),
            serviceClient
        ).orThrow()
    }

    suspend fun cancel(verifiedJob: VerifiedJob) {
        val pod = findPods(verifiedJob.id).firstOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        jobManager.get(verifiedJob.id).markAsFinished {
            transferLog(verifiedJob.id, pod.metadata.name)

            k8sClient.batch().jobs()
                .inNamespace(namespace)
                .withLabel(ROLE_LABEL, appRole)
                .withLabel(JOB_ID_LABEL, verifiedJob.id)
                .delete()

            ComputationCallbackDescriptions.completed.call(
                JobCompletedRequest(verifiedJob.id, null, true),
                serviceClient
            ).orThrow()
        }
    }

    fun create(verifiedJob: VerifiedJob) {
        log.info("Creating new job with name: ${verifiedJob.id}")

        val (sharedVolumes, sharedMounts) = sharedFileSystemMountService.createVolumesAndMounts(verifiedJob)

        networkPolicyService.createPolicy(verifiedJob.id, verifiedJob.peers.map { it.jobId })
        val hostAliases = hostAliasesService.findAliasesForPeers(verifiedJob.peers)

        repeat(verifiedJob.nodes) { rank ->
            val podName = jobName(verifiedJob.id, rank)
            k8sClient.batch().jobs().inNamespace(namespace).createNew()
                .metadata {
                    withName(podName)
                    withNamespace(this@PodService.namespace)

                    withLabels(
                        mapOf(
                            ROLE_LABEL to appRole,
                            RANK_LABEL to rank.toString(),
                            JOB_ID_LABEL to verifiedJob.id
                        )
                    )
                }
                .spec {
                    val containerConfig = verifiedJob.application.invocation.container ?: ContainerDescription()

                    val deadline = verifiedJob.maxTime.toSeconds()
                    withActiveDeadlineSeconds(deadline)
                    withBackoffLimit(1)
                    withParallelism(1)

                    withTemplate(
                        PodTemplateSpecBuilder().apply {
                            metadata {
                                withName(podName)
                                withNamespace(this@PodService.namespace)

                                withLabels(
                                    mapOf(
                                        ROLE_LABEL to appRole,
                                        RANK_LABEL to rank.toString(),
                                        JOB_ID_LABEL to verifiedJob.id
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
                                        val uid = verifiedJob.uid + 1000
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

                                        val command = app.invocation.flatMap { it.buildInvocationList(givenParameters) }

                                        log.debug("Container is: ${tool.container}")
                                        log.debug("Executing command: $command")

                                        withName("user-job")
                                        withImage(tool.container)
                                        withRestartPolicy("Never")
                                        withCommand(command)
                                        withAutomountServiceAccountToken(false)

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
                                                verifiedJob.workspace?.removePrefix("/")?.removeSuffix("/")?.let { it + "/output" }
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
                                                verifiedJob.workspace?.removePrefix("/")?.removeSuffix("/")?.let { it + "/input" }
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
                                        withPersistentVolumeClaim(PersistentVolumeClaimVolumeSource("cephfs", false))
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

        GlobalScope.launch {
            log.info("Awaiting container start!")
            try {
                if (verifiedJob.nodes > 1) {
                    awaitCatching(retries = 36_000, delay = 100) {
                        val pods = findPods(verifiedJob.id)
                        pods.all { pod ->
                            // Note: We are awaiting the init containers
                            val state = pod.status.initContainerStatuses.first().state
                            state.running != null || state.terminated != null
                        }
                    }

                    // Now we initialize the files stored in MULTI_NODE_CONFIG
                    val findPods = findPods(verifiedJob.id)

                    log.info("Found the following pods: ${findPods.map { it.metadata.name }}")

                    val podsWithIp = findPods.map {
                        val rankLabel = it.metadata.labels[RANK_LABEL]!!.toInt()
                        rankLabel to it.status.podIP
                    }

                    log.info(podsWithIp.toString())

                    findPods.forEach { pod ->
                        val podResource = k8sClient.pods()
                            .inNamespace(namespace)
                            .withName(pod.metadata.name)

                        fun writeFile(path: String, contents: String) {
                            val (out, _, ins) = podResource.execWithDefaultListener(
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

                        writeFile("$MULTI_NODE_DIRECTORY/rank.txt", pod.metadata.labels[RANK_LABEL]!! + "\n")
                        writeFile("$MULTI_NODE_DIRECTORY/number_of_nodes.txt", verifiedJob.nodes.toString() + "\n")
                        writeFile("$MULTI_NODE_DIRECTORY/job_id.txt", verifiedJob.id + "\n")
                    }

                    log.debug("Multi node configuration written to all nodes for ${verifiedJob.id}")
                }

                awaitCatching(retries = 36_000, delay = 100) {
                    val pods = findPods(verifiedJob.id)
                    pods.all { pod ->
                        // Note: We are awaiting the user container
                        val state = pod.status.containerStatuses.first().state
                        state.running != null || state.terminated != null
                    }
                }

                jobManager.get(verifiedJob.id).withLock {
                    // We need to hold the lock until we get a response to avoid race conditions.
                    ComputationCallbackDescriptions.requestStateChange.call(
                        StateChangeRequest(
                            verifiedJob.id,
                            JobState.RUNNING,
                            "Your job is now running. You will be able to follow the logs while the job is running."
                        ),
                        serviceClient
                    ).orThrow()
                }
            } catch (ex: Throwable) {
                jobManager.get(verifiedJob.id).markAsFinished {
                    log.warn("Container did not start within deadline!")
                    ComputationCallbackDescriptions.requestStateChange.call(
                        StateChangeRequest(verifiedJob.id, JobState.FAILURE, "Job did not start within deadline."),
                        serviceClient
                    ).orThrow()
                }
            }
        }
    }

    fun cleanup(requestId: String) {
        try {
            networkPolicyService.deletePolicy(requestId)
        } catch (ex: KubernetesClientException) {
            // Ignored
            if (ex.status.code !in setOf(400, 404)) {
                log.warn(ex.stackTraceToString())
            }
        }

        try {
            k8sClient.batch().jobs().inNamespace(namespace).withLabel(ROLE_LABEL, appRole)
                .withLabel(JOB_ID_LABEL, requestId).delete()
        } catch (ex: KubernetesClientException) {
            when (ex.status.code) {
                400, 404 -> return
                else -> throw ex
            }
        }
    }

    fun retrieveLogs(requestId: String, startLine: Int, maxLines: Int): Pair<String, Int> {
        return try {
            // This is a stupid implementation that works with the current API. We should be using websockets.
            val pod = findPods(requestId).firstOrNull() ?: return Pair("", 0)
            val completeLog = k8sClient.pods().inNamespace(namespace).withName(pod.metadata.name).log.lines()
            val lines = completeLog.drop(startLine).take(maxLines)
            val nextLine = startLine + lines.size
            Pair(lines.joinToString("\n"), nextLine)
        } catch (ex: KubernetesClientException) {
            when (ex.status.code) {
                404, 400 -> Pair("", 0)
                else -> throw ex
            }
        }
    }

    fun createTunnel(jobId: String, localPortSuggestion: Int, remotePort: Int): Tunnel {
        val pod = findPods(jobId).firstOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        fun findPodResource() = k8sClient.pods().inNamespace(namespace).withName(pod.metadata.name)
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
                        namespace,
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

            val job = GlobalScope.launch(Dispatchers.IO) {
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
                _isAlive = {
                    k8sTunnel.isAlive
                },
                _close = {
                    k8sTunnel.destroyForcibly()
                    job.cancel()
                }
            )
        } else {
            val ipAddress = podResource.get().status.podIP
            log.debug("Running inside of kubernetes going directly to pod at $ipAddress")
            return Tunnel(
                jobId = jobId,
                ipAddress = ipAddress,
                localPort = remotePort,
                _isAlive = { runCatching { findPodResource()?.get() }.getOrNull() != null },
                _close = { }
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

class Tunnel(
    val jobId: String,
    val ipAddress: String,
    val localPort: Int,
    private val _isAlive: () -> Boolean,
    private val _close: () -> Unit
) : Closeable {
    fun isAlive() = _isAlive()
    override fun close() = _close()
}

private fun SimpleDuration.toSeconds(): Long {
    return (hours * 3600L) + (minutes * 60) + seconds
}
