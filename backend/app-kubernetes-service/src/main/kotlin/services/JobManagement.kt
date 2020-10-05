package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VOLCANO_JOB_NAME_LABEL
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJobPhase
import dk.sdu.cloud.app.kubernetes.services.volcano.volcanoJob
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.DistributedLock
import dk.sdu.cloud.service.DistributedLockFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.k8.*
import io.ktor.http.*
import io.ktor.util.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.onReceiveOrNull
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.collections.ArrayList
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.minutes

interface JobManagementPlugin {
    suspend fun JobManagement.onCreate(job: VerifiedJob, builder: VolcanoJob) {}
    suspend fun JobManagement.onCleanup(jobId: String) {}

    /**
     * Called when the job starts
     *
     * Note: Unlike [onCreate] the value from [jobFromServer] should not be mutated as updates will not be pushed to
     * the Kubernetes server.
     */
    suspend fun JobManagement.onJobStart(jobId: String, jobFromServer: VolcanoJob) {}

    /**
     * Called when the job completes
     *
     * Note: Unlike [onCreate] the value from [jobFromServer] should not be mutated as updates will not be pushed to
     * the Kubernetes server.
     */
    suspend fun JobManagement.onJobComplete(jobId: String, jobFromServer: VolcanoJob) {}

    /**
     * Called when the [JobManagement] system decides a batch of [VolcanoJob] is due for monitoring
     *
     * A plugin may perform various actions, such as, checking if the deadline has expired or sending accounting
     * information back to UCloud app orchestration.
     */
    suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<VolcanoJob>) {}
}

class JobManagement(
    val k8: K8Dependencies,
    private val distributedLocks: DistributedLockFactory,
    private val logService: K8LogService,
    private val jobCache: VerifiedJobCache,
    private val disableMasterElection: Boolean = false,
    private val fullScanFrequency: Long = 1000L * 60 * 15
) {
    private val plugins = ArrayList<JobManagementPlugin>()
    data class ScheduledMonitoringJob(val jobId: String, val timestamp: Long) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return (other as? ScheduledMonitoringJob)?.jobId == jobId
        }

        override fun hashCode(): Int {
            return jobId.hashCode()
        }
    }
    private val scheduledForMonitoring = HashSet<ScheduledMonitoringJob>()
    private val mutex = Mutex()

    init {
        if (disableMasterElection) {
            log.warn(
                "Running with master election disabled. This should only be done in development mode with a " +
                    "single instance of this service running!"
            )
        }
    }

    /**
     * Schedules a monitoring of a job identified by [jobId]
     *
     * [JobManagementPlugin.onJobMonitoring] will be called by [JobManagement] at a later point in time, but no earlier
     * than [timestamp]. Plugins will not be notified if the job no longer exists at this point.
     *
     * Only one monitoring event can exist for each [jobId]. Calling this function will override existing entries
     * with the same [jobId].
     *
     * This function does not save the information to persistent storage.
     */
    suspend fun scheduleJobMonitoring(jobId: String, timestamp: Long) {
        mutex.withLock {
            scheduledForMonitoring.add(ScheduledMonitoringJob(jobId, timestamp))
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

    suspend fun extend(job: VerifiedJob, newMaxTime: SimpleDuration) {
        ExpiryPlugin.extendJob(k8, job.id, newMaxTime)
    }

    suspend fun cancel(verifiedJob: VerifiedJob) {
        markJobAsComplete(verifiedJob.id, null)
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

                listenerLoop@while (currentCoroutineContext().isActive) {
                    if (volcanoWatch.isClosedForReceive) {
                        log.info("Stopped receiving new Volcano job watch events. Restarting watcher.")
                        volcanoWatch = k8.client.watchResource(
                            this,
                            KubernetesResources.volcanoJob.withNamespace(NAMESPACE_ANY)
                        )
                        continue
                    }

                    if (podWatch.isClosedForReceive) {
                        log.info("Stopped receiving new Pod watch events. Restarting watcher.")
                        podWatch = k8.client.watchResource(
                            this,
                            KubernetesResources.pod.withNamespace(NAMESPACE_ANY),
                            mapOf("labelSelector" to VOLCANO_JOB_NAME_LABEL)
                        )
                        continue
                    }

                    val duration = measureTime {
                        select<Unit> {
                            onTimeout(30_000) {
                                if (Time.now() >= nextFullScan) {
                                    // Perform full scan
                                    nextFullScan = Time.now() + fullScanFrequency

                                    val resources = k8.client.listResources<VolcanoJob>(
                                        KubernetesResources.volcanoJob.withNamespace(NAMESPACE_ANY)
                                    )

                                    // NOTE(Dan): If we get to the point that Kubernetes cannot complete this call
                                    // in 60 seconds we should consider splitting this up into multiple queues.
                                    // To begin with we could probably also just increase the lock duration, but this
                                    // means that it will take longer for a new master to be elected.
                                    if (!renewLock(lock)) return@onTimeout

                                    for (plugin in plugins) {
                                        with(plugin) {
                                            onJobMonitoring(resources)
                                        }

                                        if (!renewLock(lock)) return@onTimeout
                                    }
                                } else {
                                    // Check job scheduling
                                    val lookupIds = ArrayList<String>()
                                    mutex.withLock {
                                        val now = Time.now()
                                        val iterator = scheduledForMonitoring.iterator()
                                        while (iterator.hasNext()) {
                                            val scheduledJob = iterator.next()
                                            if (now >= scheduledJob.timestamp) {
                                                lookupIds.add(scheduledJob.jobId)
                                                iterator.remove()
                                            }
                                        }
                                    }

                                    if (lookupIds.isNotEmpty()) {
                                        for (chunk in lookupIds.chunked(16)) {
                                            val jobs = chunk.map { jobId ->
                                                async {
                                                    try {
                                                        k8.client.getResource<VolcanoJob>(
                                                            KubernetesResources.volcanoJob.withNameAndNamespace(
                                                                k8.nameAllocator.jobIdToJobName(jobId),
                                                                k8.nameAllocator.jobIdToNamespace(jobId)
                                                            )
                                                        )
                                                    } catch (ex: KubernetesException) {
                                                        if (ex.statusCode == HttpStatusCode.NotFound) {
                                                            null
                                                        } else {
                                                            throw ex
                                                        }
                                                    }
                                                }
                                            }.awaitAll()

                                            val foundJobs = jobs.filterNotNull()
                                            if (foundJobs.isNotEmpty()) {
                                                plugins.forEach { plugin ->
                                                    with(plugin) {
                                                        onJobMonitoring(foundJobs)
                                                    }
                                                }
                                            }

                                            if (!renewLock(lock)) break
                                        }
                                    }
                                }
                            }

                            // NOTE(Dan): The Kotlin compiler appears to find the wrong overload at the moment.
                            // Doing it like this ensures that the extension function is picked over the member
                            // function.
                            val volcanoWatchOnReceiveOrNull = volcanoWatch.onReceiveOrNull()
                            volcanoWatchOnReceiveOrNull r@{ ev ->
                                if (ev == null) return@r
                                log.info("Volcano job watch: ${ev.theObject.status}")
                                val jobName = ev.theObject.metadata?.name ?: return@r
                                val jobId = k8.nameAllocator.jobNameToJobId(jobName)

                                if (ev.type == "DELETED") {
                                    k8.changeState(jobId, JobState.SUCCESS, "Job has terminated")
                                    return@r
                                }

                                val jobStatus = ev.theObject.status ?: return@r
                                val jobState = jobStatus.state ?: return@r

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
                                        val didChange = k8.addStatus(jobId, statusUpdate)
                                        println("Job is done! didChange = $didChange")
                                        if (didChange) {


                                            markJobAsComplete(jobId, ev.theObject)
                                        }
                                    } else {
                                        val didChangeState = k8.changeState(jobId, newState!!, statusUpdate)
                                        if (didChangeState) {
                                            plugins.forEach { plugin ->
                                                with(plugin) {
                                                    with(k8) {
                                                        onJobStart(jobId, ev.theObject)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    k8.addStatus(jobId, statusUpdate)
                                }
                            }

                            val podWatchOnReceiveOrNull = podWatch.onReceiveOrNull()
                            podWatchOnReceiveOrNull r@{ ev ->
                                if (ev == null) return@r
                                val status = ev.theObject.status ?: return@r
                                val jobName = ev.theObject.metadata?.labels?.get(VOLCANO_JOB_NAME_LABEL)?.toString()
                                    ?: run {
                                        log.info("Could not find the label? ${ev.theObject.metadata?.labels}")
                                        return@r
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

    private suspend fun markJobAsComplete(jobId: String, volcanoJob: VolcanoJob?) {
        val job = volcanoJob ?: run {
            val name = k8.nameAllocator.jobIdToJobName(jobId)
            val namespace = k8.nameAllocator.jobIdToNamespace(jobId)
            k8.client.getResource(KubernetesResources.volcanoJob.withNameAndNamespace(name, namespace))
        }

        plugins.forEach { plugin ->
            with(plugin) {
                with(k8) {
                    onJobComplete(jobId, job)
                }
            }
        }

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
                // Accounting is done by the AccountingPlugin, don't charge anything additional here.
                JobCompletedRequest(jobId, SimpleDuration.fromMillis(0), true),
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
