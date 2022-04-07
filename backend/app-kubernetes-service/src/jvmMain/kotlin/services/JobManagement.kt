package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.api.ProductsBrowseRequest
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJobPhase
import dk.sdu.cloud.app.kubernetes.services.volcano.volcanoJob
import dk.sdu.cloud.app.orchestrator.api.ComputeSupport
import dk.sdu.cloud.app.orchestrator.api.InteractiveSessionType
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobUpdate
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.api.JobsProviderExtendRequestItem
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.debug.tellMeEverything
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.DistributedLock
import dk.sdu.cloud.service.DistributedLockFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.k8.KubernetesException
import dk.sdu.cloud.service.k8.KubernetesResources
import dk.sdu.cloud.service.k8.NAMESPACE_ANY
import dk.sdu.cloud.service.k8.ObjectMeta
import dk.sdu.cloud.service.k8.createResource
import dk.sdu.cloud.service.k8.deleteResource
import dk.sdu.cloud.service.k8.getResource
import dk.sdu.cloud.service.k8.listResources
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlin.random.Random
import kotlin.time.ExperimentalTime

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
    private val providerId: String,
    val k8: K8Dependencies,
    private val distributedLocks: DistributedLockFactory,
    private val logService: K8LogService,
    private val jobCache: VerifiedJobCache,
    private val maintenance: MaintenanceService,
    val resources: ResourceCache,
    private val db: DBContext,
    private val sessions: SessionDao,
    private val disableMasterElection: Boolean = false,
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

    suspend fun create(jobs: BulkRequest<Job>) {
        jobs.items.forEach { create(it) }
    }

    suspend fun create(verifiedJob: Job) {
        try {
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
            plugins.forEach {
                k8.debug?.tellMeEverything("Running plugin (onCreate): ${it.javaClass.simpleName}")
                with(it) {
                    with(k8) {
                        onCreate(verifiedJob, builder)
                    }
                }
            }

            k8.debug?.tellMeEverything("Creating resource")
            @Suppress("BlockingMethodInNonBlockingContext")
            k8.client.createResource(
                KubernetesResources.volcanoJob.withNamespace(namespace),
                defaultMapper.encodeToString(builder)
            )
            k8.debug?.tellMeEverything("Resource has been created!")
        } catch (ex: Throwable) {
            log.warn(ex.stackTraceToString())
            throw ex
        }
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
            if (ex.statusCode == io.ktor.http.HttpStatusCode.NotFound || ex.statusCode == io.ktor.http.HttpStatusCode.BadRequest) {
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
                    ResourceUpdateAndId(
                        verifiedJob.id,
                        JobUpdate(
                            JobState.FAILURE,
                            status = "An internal error occurred in UCloud/Compute. " +
                                "Job cancellation was requested but the job was not known to us."
                        )
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

    private var lastScan: Map<String, VolcanoJob> = emptyMap()

    private data class VolcanoJobEvent(
        val jobName: String,
        val oldJob: VolcanoJob?,
        val newJob: VolcanoJob?,
    ) {
        val wasDeleted: Boolean get() = newJob == null
        val updatedPhase: String?
            get() {
                val oldPhase = oldJob?.status?.state?.phase
                val newPhase = newJob?.status?.state?.phase

                return if (oldPhase != newPhase) {
                    newPhase
                } else {
                    null
                }
            }

        val updatedRunning: Int?
            get() {
                val oldRunning = oldJob?.status?.running
                val newRunning = newJob?.status?.running

                return if (oldRunning != newRunning) {
                    newRunning
                } else {
                    null
                }
            }
    }

    private fun processScan(newJobs: List<VolcanoJob>): List<VolcanoJobEvent> {
        val newJobsGrouped = newJobs
            .asSequence()
            .filter { it.metadata?.name != null }
            .associateBy { it.metadata!!.name!! }

        val result = ArrayList<VolcanoJobEvent>()
        val observed = HashSet<String>()
        for ((key, oldJob) in lastScan) {
            val newJob = newJobsGrouped[key]
            result.add(VolcanoJobEvent(key, oldJob, newJob))
            observed.add(key)
        }

        for ((key, newJob) in newJobsGrouped) {
            if (key in observed) continue
            result.add(VolcanoJobEvent(key, null, newJob))
        }

        lastScan = newJobsGrouped

        return result
    }

    @OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    private suspend fun becomeMasterAndListen(lock: DistributedLock) {
        val didAcquire = disableMasterElection || lock.acquire()
        if (didAcquire) {
            log.info("This service has become the master responsible for handling Kubernetes events!")

            // NOTE(Dan): Delay the initial scan to wait for server to be ready (needed for local dev)
            delay(15_000)

            var lastIteration = Time.now()
            while (currentCoroutineContext().isActive) {
                val now = Time.now()
                val time = now - lastIteration
                lastIteration = now
                val resources = k8.client.listResources<VolcanoJob>(
                    KubernetesResources.volcanoJob.withNamespace(NAMESPACE_ANY)
                )

                // NOTE(Dan): If we get to the point that Kubernetes cannot complete this call
                // in 60 seconds we should consider splitting this up into multiple queues.
                // To begin with we could probably also just increase the lock duration, but this
                // means that it will take longer for a new master to be elected.
                if (!renewLock(lock)) continue

                val events = processScan(resources)
                // TODO It looks like this code is aware of changes but they are not successfully received by UCloud/sent by this service
                for (event in events) {
                    val jobId = k8.nameAllocator.jobNameToJobId(event.jobName)

                    when {
                        event.wasDeleted -> {
                            val oldJob = event.oldJob!!
                            val expiry = oldJob.expiry
                            if (expiry != null && Time.now() >= expiry) {
                                // NOTE(Dan): Expiry plugin will simply delete the object. This is why we must
                                // check if the reason was expiration here.
                                markJobAsComplete(jobId, oldJob)
                                k8.changeState(jobId, JobState.EXPIRED, "Job has expired")
                            } else {
                                k8.changeState(jobId, JobState.SUCCESS, "Job has terminated")
                            }
                        }

                        event.updatedPhase != null || event.updatedRunning != null -> {
                            val job = event.newJob!!
                            val running = job.status?.running
                            val minAvailable = job.status?.minAvailable
                            val message = job.status?.state?.message

                            var newState: JobState? = null
                            var expectedDifferentState: Boolean = false

                            val statusUpdate = buildString {
                                when (job.status?.state?.phase) {
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
                                        markJobAsComplete(jobId, job)
                                        k8.client.deleteResource(
                                            KubernetesResources.volcanoJob
                                                .withNameAndNamespace(event.jobName, job.metadata!!.namespace!!)
                                        )
                                    }
                                } else {
                                    val didChangeState = k8.changeState(
                                        jobId,
                                        newState!!,
                                        statusUpdate,
                                        expectedDifferentState = expectedDifferentState
                                    )

                                    if (didChangeState) {
                                        plugins.forEach { plugin ->
                                            with(plugin) {
                                                onJobStart(jobId, job)
                                            }
                                        }
                                    }
                                }
                            } else {
                                k8.addStatus(jobId, statusUpdate)
                            }
                        }

                        else -> {
                            // Do nothing, just run the normal job monitoring.
                        }
                    }
                }

                for (plugin in plugins) {
                    with(plugin) {
                        onJobMonitoring(resources)
                    }

                    if (!renewLock(lock)) continue
                }

                if (!renewLock(lock)) break
                delay(5000)
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
                if (ex.statusCode == io.ktor.http.HttpStatusCode.NotFound) {
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
                                ResourceUpdateAndId(
                                    job,
                                    JobUpdate(
                                        state = JobState.FAILURE,
                                        status = "UCloud/Compute lost track of this job"
                                    )
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
        Products.browse.call(
            ProductsBrowseRequest(filterProvider = providerId, filterArea = ProductType.COMPUTE),
            k8.serviceClient
        ).orThrow().items.filterIsInstance<Product.Compute>()
    })

    suspend fun retrieveProductsTemporary(): BulkResponse<ComputeSupport> {
        return BulkResponse(productCache.get(Unit)?.map {
            ComputeSupport(
                ProductReference(it.name, it.category.name, it.category.provider),
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
        } ?: emptyList())
    }

    companion object : Loggable {
        override val log = logger()
    }
}
