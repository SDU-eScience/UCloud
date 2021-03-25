package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.app.kubernetes.api.AppK8IntegrationTesting
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJobPhase
import dk.sdu.cloud.app.kubernetes.services.volcano.volcanoJob
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withHttpBody
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.k8.*
import io.ktor.http.*
import io.ktor.util.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.onReceiveOrNull
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlin.collections.ArrayList
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.minutes

interface JobManagementPlugin {
    /**
     * Provides a hook for plugins to modify the [VolcanoJob]
     */
    suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {}

    /**
     * Provides a hook for plugins to cleanup after a job has finished
     *
     * Note: Plugins should assume that partial cleanup might have taken place already.
     */
    suspend fun JobManagement.onCleanup(jobId: String) {}

    /**
     * Called when the job has been submitted to Kubernetes and has started
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
    private val maintenance: MaintenanceService,
    val resources: ResourceCache,
    private val db: DBContext,
    private val sessions: SessionDao,
    private val disableMasterElection: Boolean = false,
    private val fullScanFrequency: Long = 1000L * 60 * 5,
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

    suspend fun create(jobs: BulkRequest<JobsProviderCreateRequestItem>) {
        jobs.items.forEach { create(it) }
    }

    suspend fun create(verifiedJob: Job) {
        if (maintenance.isPaused()) {
            throw RPCException(
                "UCloud does not currently accept new jobs",
                HttpStatusCode.BadRequest,
                "CLUSTER_PAUSED"
            )
        }

        jobCache.cacheJob(verifiedJob)
        val builder = VolcanoJob()
        val namespace = k8.nameAllocator.jobIdToNamespace(verifiedJob.id)
        builder.metadata = ObjectMeta(
            name = k8.nameAllocator.jobIdToJobName(verifiedJob.id),
            namespace = namespace
        )
        builder.spec = VolcanoJob.Spec(schedulerName = "volcano")
        plugins.forEach { with(it) { with(k8) { onCreate(verifiedJob, builder) } } }

        @Suppress("BlockingMethodInNonBlockingContext")
        k8.client.createResource(
            KubernetesResources.volcanoJob.withNamespace(namespace),
            defaultMapper.encodeToString(builder)
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

    }

    suspend fun extend(request: BulkRequest<JobsProviderExtendRequestItem>) {
        request.items.forEach { extension ->
            extend(extension.job, extension.requestedTime)
        }
    }

    suspend fun extend(job: Job, newMaxTime: SimpleDuration) {
        ExpiryPlugin.extendJob(k8, job.id, newMaxTime)
    }

    suspend fun cancel(request: BulkRequest<Job>) {
        request.items.forEach { job ->
            cancel(job)
        }
    }

    suspend fun cancel(verifiedJob: Job) {
        val exists = markJobAsComplete(verifiedJob.id, null)
        cleanup(verifiedJob.id)
        if (!exists) {
            JobsControl.update.call(
                bulkRequestOf(
                    JobsControlUpdateRequestItem(
                        verifiedJob.id,
                        JobState.FAILURE,
                        "An internal error occurred in UCloud/Compute. " +
                            "Job cancellation was requested but the job was not known to us."
                    )
                ),
                k8.serviceClient
            )
        }
    }

    suspend fun initializeListeners() {
        k8.scope.launch {
            val lock = distributedLocks.create("app-k8-watcher", duration = 60_000)
            while (isActive) {
                try {
                    if (!AppK8IntegrationTesting.isKubernetesReady) {
                        delay(100)
                    }
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

                // NOTE(Dan): Delay the initial scan to wait for server to be ready (needed for local dev)
                var nextFullScan = Time.now() + 15_000

                listenerLoop@ while (currentCoroutineContext().isActive) {
                    if (volcanoWatch.isClosedForReceive) {
                        log.info("Stopped receiving new Volcano job watch events. Restarting watcher.")
                        volcanoWatch = k8.client.watchResource(
                            this,
                            KubernetesResources.volcanoJob.withNamespace(NAMESPACE_ANY)
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
                                log.debug("Volcano job watch: ${ev.theObject.status}")
                                val jobName = ev.theObject.metadata?.name ?: return@r
                                val jobId = k8.nameAllocator.jobNameToJobId(jobName)
                                val jobNamespace = ev.theObject.metadata?.namespace ?: return@r

                                if (ev.type == "DELETED") {
                                    val expiry = ev.theObject.expiry
                                    if (expiry != null && Time.now() >= expiry) {
                                        // NOTE(Dan): Expiry plugin will simply delete the object. This is why we must
                                        // check if the reason was expiration here.
                                        markJobAsComplete(jobId, ev.theObject)
                                        k8.changeState(jobId, JobState.EXPIRED, "Job has expired")
                                    } else {
                                        k8.changeState(jobId, JobState.SUCCESS, "Job has terminated")
                                    }
                                    return@r
                                }

                                val jobStatus = ev.theObject.status ?: return@r
                                val jobState = jobStatus.state ?: return@r

                                val running = jobStatus.running
                                val minAvailable = jobStatus.minAvailable
                                val message = jobState.message
                                var newState: JobState? = null
                                var expectedState: JobState? = null
                                var expectedDifferentState: Boolean = false

                                val statusUpdate = buildString {
                                    when (jobState.phase) {
                                        VolcanoJobPhase.Pending -> {
                                            append("Job is waiting in the queue")
                                        }

                                        VolcanoJobPhase.Running -> {
                                            append("Job is now running")
                                            newState = JobState.RUNNING
                                            expectedDifferentState = true
                                        }

                                        VolcanoJobPhase.Restarting -> {
                                            append("Job is restarting")
                                        }

                                        VolcanoJobPhase.Failed -> {
                                            append("Job is terminating (exit code ≠ 0 or terminated by UCloud/compute)")
                                            newState = JobState.SUCCESS
                                        }

                                        VolcanoJobPhase.Terminated, VolcanoJobPhase.Terminating,
                                        VolcanoJobPhase.Completed, VolcanoJobPhase.Completing,
                                        VolcanoJobPhase.Aborted, VolcanoJobPhase.Aborting,
                                        -> {
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
                                        if (didChange) {
                                            markJobAsComplete(jobId, ev.theObject)
                                            try {
                                                k8.client.deleteResource(
                                                    KubernetesResources.volcanoJob
                                                        .withNameAndNamespace(jobName, jobNamespace)
                                                )
                                            } catch (ex: KubernetesException) {
                                                if (ex.statusCode == HttpStatusCode.NotFound) {
                                                    // Ignored
                                                } else {
                                                    throw ex
                                                }
                                            }
                                        }
                                    } else {
                                        val didChangeState = k8.changeState(
                                            jobId,
                                            newState!!,
                                            statusUpdate,
                                            expectedState = expectedState,
                                            expectedDifferentState = expectedDifferentState
                                        )

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
                        }
                    }

                    if (duration >= 1.minutes) {
                        log.warn("Took too long to process events ($duration). We will probably lose master status.")
                    }

                    if (!renewLock(lock)) break
                }

                runCatching { volcanoWatch.cancel() }
            }
        }
    }

    private suspend fun markJobAsComplete(jobId: String, volcanoJob: VolcanoJob?): Boolean {
        val job = volcanoJob ?: run {
            val name = k8.nameAllocator.jobIdToJobName(jobId)
            val namespace = k8.nameAllocator.jobIdToNamespace(jobId)
            try {
                k8.client.getResource(KubernetesResources.volcanoJob.withNameAndNamespace(name, namespace))
            } catch (ex: KubernetesException) {
                if (ex.statusCode == HttpStatusCode.NotFound) {
                    log.info("Job no longer exists: $jobId")
                    return false
                }
                throw ex
            }
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
                JobsControl.submitFile.call(
                    JobsControlSubmitFileRequest(
                        jobId,
                        file.name
                    ),
                    k8.serviceClient.withHttpBody(
                        ContentType.Application.OctetStream,
                        file.length(),
                        file.readChannel()
                    )
                )
            }
        } finally {
            dir?.deleteRecursively()
        }

        with(k8) {
            plugins.forEach { plugin ->
                with(plugin) {
                    onCleanup(jobId)
                }
            }
        }

        return true
    }

    fun verifyJobs(jobs: List<Job>) {
        k8.scope.launch {
            val jobsByNamespace = jobs.map { it.id }.groupBy { k8.nameAllocator.jobIdToNamespace(it) }
            for ((ns, jobs) in jobsByNamespace) {
                val knownJobs = k8.client
                    .listResources<VolcanoJob>(KubernetesResources.volcanoJob.withNamespace(ns))
                    .mapNotNull { k8.nameAllocator.jobNameToJobId(it.metadata?.name ?: return@mapNotNull null) }
                    .toSet()

                for (job in jobs) {
                    if (job !in knownJobs) {
                        log.info("We appear to have lost the following job: ${job}")
                        JobsControl.update.call(
                            bulkRequestOf(
                                JobsControlUpdateRequestItem(
                                    job,
                                    state = JobState.FAILURE,
                                    status = "UCloud/Compute lost track of this job"
                                )
                            ),
                            k8.serviceClient
                        ).orThrow()
                    }
                }
            }
        }
    }

    suspend fun openShellSession(
        jobs: List<JobAndRank>
    ): List<String> {
        return db.withSession { session ->
            jobs.map { job ->
                sessions.createSession(session, job, InteractiveSessionType.SHELL)
            }
        }
    }

    private val productCache = SimpleCache<Unit, List<Product.Compute>>(lookup = {
        Products.retrieveAllFromProvider.call(
            RetrieveAllFromProviderRequest(UCLOUD_PROVIDER),
            k8.serviceClient
        ).orThrow().filterIsInstance<Product.Compute>()
    })

    suspend fun retrieveProductsTemporary(): JobsProviderRetrieveProductsResponse {
        return JobsProviderRetrieveProductsResponse(productCache.get(Unit)?.map {
            ComputeProductSupport(
                ProductReference(it.id, it.category.id, it.category.provider),
                ComputeSupport(
                    ComputeSupport.Docker(
                        enabled = true,
                        web = true,
                        vnc = true,
                        logs = true,
                        terminal = true,
                        peers = true,
                        timeExtension = true,
                    )
                )
            )
        } ?: emptyList())
    }

    companion object : Loggable {
        override val log = logger()
    }
}
