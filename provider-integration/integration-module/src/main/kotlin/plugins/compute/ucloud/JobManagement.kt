package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.accounting.api.ProductsBrowseRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.debug.enterContext
import dk.sdu.cloud.debug.everything
import dk.sdu.cloud.debug.detailD
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.forEachGraal
import dk.sdu.cloud.utils.whileGraal
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

interface JobFeature {
    /**
     * Provides a hook for features to modify the [VolcanoJob]
     */
    suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {}

    /**
     * Provides a hook for features to cleanup after a job has finished
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
     * A feature may perform various actions, such as, checking if the deadline has expired or sending accounting
     * information back to UCloud app orchestration.
     */
    suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<VolcanoJob>) {}
}

class JobManagement(
    private val providerId: String,
    val k8: K8Dependencies,
    private val jobCache: VerifiedJobCache,
    private val maintenance: MaintenanceService,
    val resources: ResourceCache,
    private val db: DBContext,
    private val sessions: SessionDao,
) {
    private val features = ArrayList<JobFeature>()
    val readOnlyFeatures: List<JobFeature>
        get() = features

    @Serializable
    private data class UnsuspendItem(val job: Job, val expiration: Long)
    private val unsuspendMutex = Mutex()
    private val unsuspendQueue = ArrayList<UnsuspendItem>()

    suspend fun registerApplication(
        specification: JobSpecification,
        username: String,
        project: String? = null,
        providerId: String? = null,
    ): Job {
        val id = JobsControl.register.call(
            bulkRequestOf(
                ProviderRegisteredResource(
                    specification,
                    providerId,
                    username,
                    project
                )
            ),
            k8.serviceClient
        ).orThrow().responses.singleOrNull()?.id ?: throw RPCException(
            "Unable to start a job at the moment. It looks like UCloud is not responsive.",
            HttpStatusCode.BadGateway
        )

        // TODO Failing here requires rolling back the registered resource
        val actualJob = JobsControl.retrieve.call(
            ResourceRetrieveRequest(
                JobIncludeFlags(
                    includeProduct = true,
                    includeApplication = true,
                    includeParameters = true,
                ),
                id
            ),
            k8.serviceClient
        ).orThrow()

        // TODO Failing here requires rolling back the registered resource
        create(actualJob)

        return actualJob
    }

    fun register(feature: JobFeature) {
        features.add(feature)
    }

    inline fun <reified F : JobFeature> featureOrNull(): F? {
        return readOnlyFeatures.filterIsInstance<F>().singleOrNull()
    }

    suspend fun create(jobs: BulkRequest<Job>) {
        jobs.items.forEach { create(it) }
    }

    suspend fun create(verifiedJob: Job, queueExpiration: Long? = null) {
        try {
            if (maintenance.isPaused()) {
                throw RPCException(
                    "UCloud does not currently accept new jobs",
                    HttpStatusCode.BadRequest,
                    "CLUSTER_PAUSED"
                )
            }

            val name = k8.nameAllocator.jobIdToJobName(verifiedJob.id)
            val namespace = k8.nameAllocator.namespace()

            val jobAlreadyExists = try {
                k8.client.getResource(
                    VolcanoJob.serializer(),
                    KubernetesResources.volcanoJob.withNameAndNamespace(name, namespace)
                )
                true
            } catch (ex: KubernetesException) {
                if (ex.statusCode.value == HttpStatusCode.NotFound.value) {
                    false
                } else {
                    throw ex
                }
            }

            // TODO(Dan): A poorly timed double unsuspend will still cause the job to be in the queue, even though 
            // it has been restarted correctly. On the bright side, it will quickly go away.
            if (jobAlreadyExists) {
                unsuspendMutex.withLock {
                    if (!unsuspendQueue.any { it.job.id == verifiedJob.id }) {
                        unsuspendQueue.add(UnsuspendItem(verifiedJob, queueExpiration ?: Time.now() + (1000L * 60 * 2)))
                    }
                }
                return
            } else {
                unsuspendMutex.withLock {
                    val iterator = unsuspendQueue.iterator()
                    while (iterator.hasNext()) {
                        val (job, expiry) = iterator.next()
                        if (job.id == verifiedJob.id) iterator.remove()
                    }
                }
            }

            jobCache.cacheJob(verifiedJob)
            val builder = VolcanoJob()
            builder.metadata = ObjectMeta(
                name = name,
                namespace = namespace
            )
            builder.spec = VolcanoJob.Spec(schedulerName = "volcano")
            features.forEach {
                k8.debug?.everything("Running feature (onCreate): ${it.javaClass.simpleName}")
                with(it) {
                    with(k8) {
                        onCreate(verifiedJob, builder)
                    }
                }
            }

            k8.debug?.everything("Creating resource")
            @Suppress("BlockingMethodInNonBlockingContext")
            k8.client.createResource(
                KubernetesResources.volcanoJob.withNamespace(namespace),
                defaultMapper.encodeToString(VolcanoJob.serializer(), builder)
            )
            k8.debug?.everything("Resource has been created!")
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
                    k8.nameAllocator.namespace()
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
        FeatureExpiry.extendJob(k8, job.id, newMaxTime)
    }

    suspend fun cancel(request: BulkRequest<Job>) {
        request.items.forEach { job ->
            cancel(job)
        }
    }

    suspend fun cancel(verifiedJob: Job) {
        cancel(verifiedJob.id)
    }

    suspend fun cancel(jobId: String) {
        val exists = markJobAsComplete(jobId, null)
        cleanup(jobId)
        if (exists) {
            k8.changeState(
                jobId, 
                JobState.SUCCESS, 
                "Job has been cancelled",
                allowRestart = false,
                expectedDifferentState = true,
            )
        }
    }

    private var lastScan: Map<String, VolcanoJob> = emptyMap()

    @Serializable
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

    suspend fun runMonitoring() {
        val didAcquire = true
        if (didAcquire) {
            log.info("This service has become the master responsible for handling Kubernetes events!")

            // NOTE(Dan): Delay the initial scan to wait for server to be ready (needed for local dev)
            delay(15_000)

            var lastIteration = Time.now()
            var isAlive = true
            whileGraal({currentCoroutineContext().isActive && isAlive}) {
                k8.debug.enterContext("Unsuspend queue") {
                    // NOTE(Dan): Need to take a copy and release the lock to avoid issues with the mutex not being 
                    // re-entrant.
                    var listCopy: ArrayList<UnsuspendItem>
                    unsuspendMutex.withLock {
                        listCopy = ArrayList(unsuspendQueue)
                        unsuspendQueue.clear()
                    }

                    k8.debug.detailD("Items in queue", ListSerializer(UnsuspendItem.serializer()), listCopy)

                    val now = Time.now()
                    for ((job, expiry) in listCopy) {
                        k8.debug.enterContext("Processing ${job.id}") {
                            if (now < expiry) {
                                create(job, expiry)
                            }
                        }
                    }

                    logExit(
                        "Processed ${listCopy.size} items. ${unsuspendQueue.size} items remain in queue.",
                        level = if (listCopy.isNotEmpty()) {
                            MessageImportance.THIS_IS_NORMAL
                        } else {
                            MessageImportance.IMPLEMENTATION_DETAIL
                        }
                    )
                }

                k8.debug.enterContext("K8 Job monitoring") {
                    val now = Time.now()
                    val time = now - lastIteration
                    lastIteration = now
                    val resources = k8.client.listResources(
                        VolcanoJob.serializer(),
                        KubernetesResources.volcanoJob.withNamespace(NAMESPACE_ANY)
                    )

                    val events = processScan(resources)
                    k8.debug.detailD("Events fetched from K8", ListSerializer(VolcanoJobEvent.serializer()), events)
                    // TODO It looks like this code is aware of changes but they are not successfully received by 
                    // UCloud/sent by this service

                    var debugTerminations = 0
                    var debugExpirations = 0
                    var debugDeletions = 0
                    var debugUpdates = 0

                    events.forEachGraal { event ->
                        k8.debug.enterContext("Processing: ${event.jobName}") {
                            val jobId = k8.nameAllocator.jobNameToJobId(event.jobName)

                            when {
                                event.wasDeleted -> {
                                    val oldJob = event.oldJob!!
                                    val expiry = oldJob.expiry
                                    if (expiry != null && Time.now() >= expiry) {
                                        // NOTE(Dan): Expiry feature will simply delete the object. This is why we must
                                        // check if the reason was expiration here.
                                        markJobAsComplete(jobId, oldJob)
                                        k8.changeState(jobId, JobState.EXPIRED, "Job has expired")

                                        debugExpirations++
                                    } else {
                                        k8.changeState(
                                            jobId, 
                                            JobState.SUCCESS, 
                                            "Job has terminated", 
                                            allowRestart = false,
                                            expectedDifferentState = true,
                                        )

                                        debugTerminations++
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
                                            val didChange = k8.changeState(
                                                jobId, 
                                                JobState.SUCCESS, 
                                                statusUpdate,
                                                allowRestart = true,
                                                expectedDifferentState = true,
                                            )

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
                                                features.forEach { feature ->
                                                    with(feature) {
                                                        onJobStart(jobId, job)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        k8.addStatus(jobId, statusUpdate)
                                    }

                                    debugUpdates++
                                }

                                else -> {
                                    // Do nothing, just run the normal job monitoring.
                                }
                            }

                            logExit("Done", level = MessageImportance.IMPLEMENTATION_DETAIL)
                        }
                    }

                    features.forEachGraal { feature ->
                        with(feature) {
                            onJobMonitoring(resources)
                        }
                    }

                    logExit(
                        buildString {
                            if (debugUpdates > 0) {
                                append(" Updates = ")
                                append(debugUpdates)
                            }

                            if (debugTerminations > 0)  {
                                append(" Terminations = ")
                                append(debugTerminations)
                            }

                            if (debugExpirations > 0) {
                                append(" Expirations = ")
                                append(debugExpirations)
                            }
                        },
                        level = if (debugUpdates > 0 || debugTerminations > 0 || debugExpirations > 0) {
                            MessageImportance.THIS_IS_NORMAL
                        } else {
                            MessageImportance.IMPLEMENTATION_DETAIL
                        }
                    )
                    delay(5000)
                }
            }
        }
    }

    private suspend fun markJobAsComplete(jobId: String, volcanoJob: VolcanoJob?): Boolean {
        val job = volcanoJob ?: run {
            val name = k8.nameAllocator.jobIdToJobName(jobId)
            val namespace = k8.nameAllocator.namespace()
            try {
                k8.client.getResource(
                    VolcanoJob.serializer(),
                    KubernetesResources.volcanoJob.withNameAndNamespace(name, namespace)
                )
            } catch (ex: KubernetesException) {
                if (ex.statusCode == io.ktor.http.HttpStatusCode.NotFound) {
                    log.info("Job no longer exists: $jobId")
                    return false
                }
                throw ex
            }
        }
        features.forEach { feature ->
            with(feature) {
                with(k8) {
                    onJobComplete(jobId, job)
                }
            }
        }
        with(k8) {
            features.forEach { feature ->
                with(feature) {
                    onCleanup(jobId)
                }
            }
        }
        return true
    }

    fun verifyJobs(jobs: List<Job>) {
        k8.scope.launch {
            val jobsByNamespace = jobs.map { it.id }.groupBy { k8.nameAllocator.namespace() }
            for ((ns, jobIds) in jobsByNamespace) {
                val knownJobs = k8.client
                    .listResources(VolcanoJob.serializer(), KubernetesResources.volcanoJob.withNamespace(ns))
                    .mapNotNull { k8.nameAllocator.jobNameToJobId(it.metadata?.name ?: return@mapNotNull null) }
                    .toSet()

                for (jobId in jobIds) {
                    val job = jobs.find { it.id == jobId } ?: error("Corrupt data in verifyJobs")
                    if (job.status.state == JobState.SUSPENDED) continue

                    if (jobId !in knownJobs) {
                        log.info("We appear to have lost the following job: ${jobId}")
                        JobsControl.update.call(
                            bulkRequestOf(
                                ResourceUpdateAndId(
                                    jobId,
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
