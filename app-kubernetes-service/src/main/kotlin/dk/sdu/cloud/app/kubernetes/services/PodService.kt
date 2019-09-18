package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.watcher.api.JobEvents
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.ContainerDescription
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.buildEnvironmentValue
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.api.LINUX_FS_USER_UID
import dk.sdu.cloud.service.*
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.ktor.http.HttpStatusCode
import io.ktor.util.cio.readChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File
import java.io.InputStream
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

const val LOCK_PREFIX = "lock-app-k8-job-"
const val STATUS_PREFIX = "status-app-k8-job-"

class PodService(
    private val k8sClient: KubernetesClient,
    private val serviceClient: AuthenticatedClient,
    private val networkPolicyService: NetworkPolicyService,
    private val sharedFileSystemMountService: SharedFileSystemMountService,
    private val hostAliasesService: HostAliasesService,
    private val broadcastingStream: BroadcastingStream,
    private val eventStreamService: EventStreamService,
    private val lockFactory: DistributedLockFactory,
    private val stateFactory: DistributedStateFactory,
    private val namespace: String = "app-kubernetes",
    private val appRole: String = "sducloud-app"
) {
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

    private fun getLock(jobId: String): DistributedLock = lockFactory.create(LOCK_PREFIX + jobId)
    private fun getCompletionState(jobId: String): DistributedState<Boolean> =
        stateFactory.create(STATUS_PREFIX + jobId, 1000L * 60 * 60 * 24)

    fun initializeListeners() {
        eventStreamService.subscribe(JobEvents.events, EventConsumer.Immediate { (jobName, condition) ->
            val jobId = reverseLookupJobName(jobName) ?: return@Immediate

            // Check for failure
            if (condition != null && condition.type == "Failed" && condition.reason == "DeadlineExceeded") {
                GlobalScope.launch {
                    getLock(jobId).withLock {
                        val state = getCompletionState(jobId)
                        if (state.get() != true) {
                            changeState(jobId, JobState.TRANSFER_SUCCESS, "Job did not complete within deadline.")

                            ComputationCallbackDescriptions.completed.call(
                                JobCompletedRequest(
                                    jobId,
                                    null,
                                    false
                                ),
                                serviceClient
                            ).orThrow()

                            state.set(true)
                        }
                    }
                }

                return@Immediate
            }

            val allPods = findPods(jobId)
            if (allPods.isEmpty()) return@Immediate

            var isDone = true
            var maxDurationInMillis = 0L
            var isSuccess = true
            for (pod in allPods) {
                val userContainer = pod.status.containerStatuses.getOrNull(0) ?: return@Immediate
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
                val resource = allPods.first()

                GlobalScope.launch(Dispatchers.IO) {
                    getLock(jobId).withLock {
                        val state = getCompletionState(jobId)

                        if (state.get() != true) {
                            val duration = SimpleDuration.fromMillis(maxDurationInMillis)
                            log.info("App finished in $duration")

                            changeState(
                                jobId,
                                JobState.TRANSFER_SUCCESS,
                                "Job has finished. Total duration: $duration."
                            )
                            transferLogAndMarkAsCompleted(jobId, resource.metadata.name, duration, isSuccess)
                            state.set(true)
                        }
                    }
                }
            }
        })
    }

    private suspend fun transferLogAndMarkAsCompleted(
        jobId: String,
        podName: String,
        duration: SimpleDuration?,
        success: Boolean
    ) {
        try {
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
        } catch (ex: KubernetesClientException) {
            // Assume that this is because there is no log to retrieve
            if (ex.code != 400 && ex.code != 404) {
                throw ex
            }
        }

        ComputationCallbackDescriptions.completed.call(
            JobCompletedRequest(jobId, duration, success),
            serviceClient
        ).orThrow()
    }

    fun cancel(verifiedJob: VerifiedJob) {
        val pod = findPods(verifiedJob.id).firstOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        GlobalScope.launch(Dispatchers.IO) {
            getLock(verifiedJob.id).withLock {
                val state = getCompletionState(verifiedJob.id)
                if (state.get() != true) {
                    transferLogAndMarkAsCompleted(verifiedJob.id, pod.metadata.name, null, true)

                    k8sClient.batch().jobs()
                        .inNamespace(namespace)
                        .withLabel(ROLE_LABEL, appRole)
                        .withLabel(JOB_ID_LABEL, verifiedJob.id)
                        .delete()

                    state.set(true)
                }
            }
        }
    }

    fun create(verifiedJob: VerifiedJob) {
        log.info("Creating new job with name: ${verifiedJob.id}")

        createJobs(verifiedJob)
        GlobalScope.launch { runPostSubmissionHandlers(verifiedJob) }
    }

    private fun createJobs(verifiedJob: VerifiedJob) {
        // We need to create and prepare some other resources as well
        val (sharedVolumes, sharedMounts) = sharedFileSystemMountService.createVolumesAndMounts(verifiedJob)
        networkPolicyService.createPolicy(verifiedJob.id, verifiedJob.peers.map { it.jobId })
        val hostAliases = hostAliasesService.findAliasesForPeers(verifiedJob.peers)

        // Create a kubernetes job for each node in our job
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
        // TODO FIXME This will probably run out of threads real quick
        log.info("Awaiting container start!")

        @Suppress("TooGenericExceptionCaught")
        try {
            if (verifiedJob.nodes > 1) {
                configureMultiNodeJob(verifiedJob)
            }

            // We cannot really provide a better message. We truly do not know what is going on with the job.
            // It might be pulling stuff, it might be in the queue. Really, we have no idea what is happening
            // with it. It does not appear that Kubernetes is exposing any of this information to us.
            //
            // We don't really care about a failure in this one
            verifiedJob.addStatus("Your job is currently waiting to be scheduled. This step might take a while.")

            awaitCatching(retries = 36_000 * 24, time = 100) {
                val pods = findPods(verifiedJob.id)
                check(pods.isNotEmpty()) { "Found no pods for job!" }

                pods.all { pod ->
                    // Note: We are awaiting the user container
                    val state = pod.status.containerStatuses.first().state
                    state.running != null || state.terminated != null
                }
            }

            getLock(verifiedJob.id).withLock {
                // We need to hold the lock until we get a response to avoid race conditions.
                changeState(
                    verifiedJob.id,
                    JobState.RUNNING,
                    "Your job is now running. You will be able to follow the logs while the job is running."
                )
            }
        } catch (ex: Throwable) {
            getLock(verifiedJob.id).withLock {
                val state = getCompletionState(verifiedJob.id)
                if (state.get() != true) {
                    log.warn("Container did not start within deadline!")
                    changeState(verifiedJob.id, JobState.FAILURE, "Job did not start within deadline.")
                    state.set(true)
                }
            }
        }
    }

    private suspend fun configureMultiNodeJob(verifiedJob: VerifiedJob) {
        require(verifiedJob.nodes > 1)

        verifiedJob.addStatus(
            "Your job is currently waiting to be scheduled. " +
                    "This step might take a while."
        )

        awaitCatching(retries = 36_000, time = 100) {
            val pods = findPods(verifiedJob.id)
            pods.all { pod ->
                // Note: We are awaiting the init containers
                val state = pod.status.initContainerStatuses.first().state
                state.running != null || state.terminated != null
            }
        }

        verifiedJob.addStatus("Configuring multi-node application")

        // Now we initialize the files stored in MULTI_NODE_CONFIG
        val findPods = findPods(verifiedJob.id)

        log.debug("Found the following pods: ${findPods.map { it.metadata.name }}")

        val podsWithIp = findPods.map {
            val rankLabel = it.metadata.labels[RANK_LABEL]!!.toInt()
            rankLabel to it.status.podIP
        }

        log.debug(podsWithIp.toString())

        findPods.forEach { pod ->
            val podResource = k8sClient.pods()
                .inNamespace(namespace)
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

            writeFile("$MULTI_NODE_DIRECTORY/rank.txt", pod.metadata.labels[RANK_LABEL]!! + "\n")
            writeFile("$MULTI_NODE_DIRECTORY/number_of_nodes.txt", verifiedJob.nodes.toString() + "\n")
            writeFile("$MULTI_NODE_DIRECTORY/job_id.txt", verifiedJob.id + "\n")
        }

        log.debug("Multi node configuration written to all nodes for ${verifiedJob.id}")
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

    fun watchLog(requestId: String): Pair<Closeable, InputStream>? {
        val pod = findPods(requestId).firstOrNull() ?: return null
        val res = k8sClient.pods().inNamespace(namespace).withName(pod.metadata.name).watchLog()
        return Pair(res, res.output)
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
                // TODO FIXME Will run out of threads
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

    private suspend fun VerifiedJob.addStatus(message: String) {
        ComputationCallbackDescriptions.addStatus.call(
            AddStatusJob(id, message),
            serviceClient
        )
    }

    private suspend fun changeState(
        jobId: String,
        state: JobState,
        newStatus: String? = null
    ) {
        ComputationCallbackDescriptions.requestStateChange.call(
            StateChangeRequest(jobId, state, newStatus),
            serviceClient
        ).orThrow()
    }

    companion object : Loggable {
        override val log = logger()
    }
}

@Suppress("ConstructorParameterNaming")
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
