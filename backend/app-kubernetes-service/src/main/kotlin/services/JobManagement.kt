package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VOLCANO_JOB_NAME_LABEL
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJobPhase
import dk.sdu.cloud.app.kubernetes.services.volcano.volcanoJob
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.DistributedLock
import dk.sdu.cloud.service.DistributedLockFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.k8.*
import io.ktor.http.*
import io.ktor.util.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlin.collections.ArrayList
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.minutes

interface JobManagementPlugin {
    suspend fun K8Dependencies.onCreate(job: VerifiedJob, builder: VolcanoJob) {}
    suspend fun K8Dependencies.onCleanup(jobId: String) {}
}

class JobManagement(
    private val k8: K8Dependencies,
    private val distributedLocks: DistributedLockFactory,
    private val logService: K8LogService,
    private val jobCache: VerifiedJobCache,
    private val disableMasterElection: Boolean = false
) {
    private val plugins = ArrayList<JobManagementPlugin>()

    init {
        if (disableMasterElection) {
            log.warn(
                "Running with master election disabled. This should only be done in development mode with a " +
                    "single instance of this service running!"
            )
        }
    }

    fun register(plugin: JobManagementPlugin) {
        plugins.add(plugin)
    }

    suspend fun create(verifiedJob: VerifiedJob) {
        jobCache.cacheJob(verifiedJob)
        val builder = VolcanoJob()
        val namespace = k8.nameAllocator.jobIdToNamespace(verifiedJob.id)
        builder.metadata = ObjectMeta(
            name = k8.nameAllocator.jobIdToJobName(verifiedJob.id),
            namespace = namespace
        )
        builder.spec = VolcanoJob.Spec()
        plugins.forEach { with(it) { with(k8) { onCreate(verifiedJob, builder) } } }

        @Suppress("BlockingMethodInNonBlockingContext")
        k8.client.createResource(
            KubernetesResources.volcanoJob.withNamespace(namespace),
            defaultMapper.writeValueAsString(builder)
        )
    }

    suspend fun cleanup(jobId: String) {
        try {
            k8.client.deleteResource(
                KubernetesResources.volcanoJob.withNameAndNamespace(
                    k8.nameAllocator.jobIdToJobName(jobId),
                    k8.nameAllocator.jobIdToNamespace(jobId)
                )
            )
        } catch (ex: KubernetesException) {
            if (ex.statusCode == HttpStatusCode.NotFound || ex.statusCode == HttpStatusCode.BadRequest) {
                // NOTE(Dan): We ignore this status code as it simply indicates that the job has already been
                // cleaned up. If we have more than one resource we should wrap that in a new try/catch block.
            } else {
                throw ex
            }
        }

        with(k8) {
            plugins.forEach { plugin ->
                with(plugin) {
                    onCleanup(jobId)
                }
            }
        }
    }

    suspend fun cancel(verifiedJob: VerifiedJob) {
        markJobAsComplete(verifiedJob.id)
    }

    suspend fun initializeListeners() {
        k8.scope.launch {
            val lock = distributedLocks.create("app-k8-watcher", duration = 60_000)
            while (isActive) {
                try {
                    becomeMasterAndListen(lock)
                } catch (ex: Throwable) {
                    log.warn("An exception occurred while processing Kubernetes events")
                    log.warn(ex.stackTraceToString())
                }

                delay(15000 + Random.nextLong(5000))
            }
        }
    }

    private suspend fun renewLock(lock: DistributedLock): Boolean {
        if (!disableMasterElection) {
            if (!lock.renew(90_000)) {
                log.warn("Lock was lost during job monitoring")
                return false
            }
        }
        return true
    }


    @OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    private suspend fun becomeMasterAndListen(lock: DistributedLock) {
        val didAcquire = disableMasterElection || lock.acquire()
        if (didAcquire) {
            log.info("This service has become the master responsible for handling Kubernetes events!")

            coroutineScope {
                var volcanoWatch = k8.client.watchResource<WatchEvent<VolcanoJob>>(
                    this,
                    KubernetesResources.volcanoJob.withNamespace(NAMESPACE_ANY)
                )

                var podWatch = k8.client.watchResource<WatchEvent<Pod>>(
                    this,
                    KubernetesResources.pod.withNamespace(NAMESPACE_ANY),
                    mapOf("labelSelector" to VOLCANO_JOB_NAME_LABEL)
                )

                var nextFullScan = 0L

                while (currentCoroutineContext().isActive) {
                    // TODO This code doesn't work it still throws with some kind of closed exception
                    if (volcanoWatch.isClosedForReceive) {
                        log.warn("Stopped receiving new Volcano job watch events. Restarting watcher.")
                        volcanoWatch = k8.client.watchResource(this, KubernetesResources.volcanoJob)
                        continue
                    }

                    if (podWatch.isClosedForReceive) {
                        log.warn("Stopped receiving new Pod watch events. Restarting watcher.")
                        podWatch = k8.client.watchResource(this, KubernetesResources.pod)
                        continue
                    }

                    val duration = measureTime {
                        select<Unit> {
                            onTimeout(30_000) {
                                // Do nothing
                            }

                            volcanoWatch.onReceive { ev ->
                                log.info("Volcano job watch: ${ev.theObject.status}")
                                val jobName = ev.theObject.metadata?.name ?: return@onReceive
                                val jobId = k8.nameAllocator.jobNameToJobId(jobName)

                                if (ev.type == "DELETED") {
                                    k8.changeState(jobId, JobState.SUCCESS, "Job has terminated")
                                    return@onReceive
                                }

                                val jobStatus = ev.theObject.status ?: return@onReceive
                                val jobState = jobStatus.state ?: return@onReceive

                                val running = jobStatus.running
                                val minAvailable = jobStatus.minAvailable
                                val message = jobState.message
                                var newState: JobState? = null

                                val statusUpdate = buildString {
                                    when (jobState.phase) {
                                        VolcanoJobPhase.Pending -> {
                                            append("Job is waiting in the queue")
                                        }

                                        VolcanoJobPhase.Running -> {
                                            append("Job is now running")
                                            newState = JobState.RUNNING
                                        }

                                        VolcanoJobPhase.Restarting -> {
                                            append("Job is restarting")
                                        }

                                        VolcanoJobPhase.Failed -> {
                                            append("Job has failed")
                                            newState = JobState.FAILURE
                                        }

                                        VolcanoJobPhase.Terminated, VolcanoJobPhase.Terminating,
                                        VolcanoJobPhase.Completed, VolcanoJobPhase.Completing,
                                        VolcanoJobPhase.Aborted, VolcanoJobPhase.Aborting -> {
                                            append("Job is terminating")
                                            newState = JobState.SUCCESS
                                        }
                                    }

                                    if (message != null) {
                                        append(" ($message)")
                                    }

                                    if (running != null && minAvailable != null) {
                                        append(" (${running}/${minAvailable} ready)")
                                    }
                                }

                                if (newState != null) {
                                    if (newState == JobState.SUCCESS) {
                                        k8.addStatus(jobId, statusUpdate)
                                        markJobAsComplete(jobId)
                                    } else {
                                        k8.changeState(jobId, newState!!, statusUpdate)
                                    }
                                } else {
                                    k8.addStatus(jobId, statusUpdate)
                                }
                            }

                            podWatch.onReceive { ev ->
                                val status = ev.theObject.status ?: return@onReceive
                                val jobName = ev.theObject.metadata?.labels?.get(VOLCANO_JOB_NAME_LABEL)?.toString()
                                    ?: run {
                                        log.info("Could not find the label? ${ev.theObject.metadata?.labels}")
                                        return@onReceive
                                    }
                                val jobId = k8.nameAllocator.jobNameToJobId(jobName)

                                val allContainerStatuses = (status.containerStatuses ?: emptyList()) +
                                    (status.initContainerStatuses ?: emptyList())

                                val unableToPull = allContainerStatuses
                                    .find { it.state?.waiting?.reason == "ImagePullBackOff" }

                                val pulling = allContainerStatuses
                                    .filter { it.state?.waiting?.reason == "ImagePullPulling" }

                                if (unableToPull != null) {
                                    k8.changeState(
                                        jobId,
                                        JobState.FAILURE,
                                        "Unable to download software (Docker image): ${unableToPull.image}"
                                    )
                                } else if (pulling.isNotEmpty()) {
                                    k8.addStatus(
                                        jobId,
                                        "Downloading software (Docker images): " +
                                            pulling.mapNotNull { it.image }.joinToString(", ")
                                    )
                                }
                            }
                        }
                    }

                    if (duration >= 1.minutes) {
                        log.warn("Took too long to process events ($duration). We will probably lose master status.")
                    }

                    if (!renewLock(lock)) break
                }

                runCatching { volcanoWatch.cancel() }
                runCatching { podWatch.cancel() }
            }
        }
    }

    private suspend fun markJobAsComplete(jobId: String) {
        val dir = logService.downloadLogsToDirectory(jobId)
        try {
            dir?.listFiles()?.forEach { file ->
                ComputationCallbackDescriptions.submitFile.call(
                    SubmitComputationResult(
                        jobId,
                        file.name,
                        BinaryStream.outgoingFromChannel(file.readChannel(), file.length())
                    ),
                    k8.serviceClient
                )
            }

            ComputationCallbackDescriptions.completed.call(
                JobCompletedRequest(jobId, null, true),
                k8.serviceClient
            ).orThrow()
        } finally {
            dir?.deleteRecursively()
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
