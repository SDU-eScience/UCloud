package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.Prometheus
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.debug.DebugContextType
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.debug.detail
import dk.sdu.cloud.debug.everything
import dk.sdu.cloud.debug.normal
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.toReadableStacktrace
import dk.sdu.cloud.utils.forEachGraal
import dk.sdu.cloud.utils.userHasResourcesAvailable
import dk.sdu.cloud.utils.whileGraal
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

interface JobFeature {
    /**
     * Provides a hook for features to modify the [ContainerBuilder]
     */
    suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {}

    /**
     * Provides a hook for features to clean up after a job has finished
     *
     * Note: Plugins should assume that partial cleanup might have taken place already.
     */
    suspend fun JobManagement.onCleanup(jobId: String) {}

    /**
     * Called when the job has been submitted to Kubernetes and has started
     */
    suspend fun JobManagement.onJobStart(rootJob: Container, children: List<Container>) {}

    /**
     * Called when the job completes
     */
    suspend fun JobManagement.onJobComplete(rootJob: Container, children: List<Container>) {}

    /**
     * Called when the [JobManagement] system decides a batch of [Container] is due for monitoring
     *
     * A feature may perform various actions, such as, checking if the deadline has expired or sending accounting
     * information back to UCloud app orchestration.
     */
    suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<Container>) {}
}

class JobManagement(
    val pluginName: String,
    val k8: K8Dependencies,
    val runtime: ContainerRuntime,
    val jobCache: VerifiedJobCache,
    private val maintenance: MaintenanceService,
    val resources: ResourceCache,
    val pluginConfig: ConfigSchema.Plugins.Jobs.UCloud,
    val sensitiveProjects: Set<String>,
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
        // Note (HENRIK) Checking for suspended on syncthing makes sure
        // that a new job is not created and put in queue every time it fails due to
        // locked wallet. Insufficient funds error is shown on frontend.
        // When folder is added after new funds the suspended job will be started instead of new.
        // Using maxBy in case multiple suspended synchting instances in DB, then we use the newest.
        val suspend = if (specification.application.name.contains("syncthing")) {
            JobsControl.browse.call(
                ResourceBrowseRequest(
                    flags = JobIncludeFlags(
                        filterApplication = specification.application.name,
                        filterState = JobState.SUSPENDED,
                    )
                ),
                k8.serviceClient
            ).orThrow().items.maxByOrNull { it.createdAt }
        } else {
            null
        }

        val id = suspend?.id ?: JobsControl.register.call(
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
        verifiedJob.specification.resources
        val isRunningSyncthing = verifiedJob.specification.product.category == SyncthingService.productCategory
        if (!userHasResourcesAvailable(job = verifiedJob) && !isRunningSyncthing) {
            throw RPCException(
                "Not enough resources available",
                HttpStatusCode.PaymentRequired, "NOT_ENOUGH_COMPUTE_CREDITS"
            )
        }
        try {
            if (maintenance.isPaused()) {
                throw RPCException(
                    "UCloud does not currently accept new jobs",
                    HttpStatusCode.BadRequest,
                    "CLUSTER_PAUSED"
                )
            }

            val jobAlreadyExists = runtime.retrieve(verifiedJob.id, 0) != null

            // TODO(Dan): A poorly timed double unsuspend will still cause the job to be in the queue, even though 
            // it has been restarted correctly. On the bright side, it will quickly go away.
            if (jobAlreadyExists) {
                unsuspendMutex.withLock {
                    if (!unsuspendQueue.any { it.job.id == verifiedJob.id }) {
                        unsuspendQueue.add(
                            UnsuspendItem(verifiedJob, queueExpiration ?: (Time.now() + (1000L * 60 * 2)))
                        )
                    }
                }
                return
            } else {
                unsuspendMutex.withLock {
                    val iterator = unsuspendQueue.iterator()
                    while (iterator.hasNext()) {
                        val (job, _) = iterator.next()
                        if (job.id == verifiedJob.id) iterator.remove()
                    }
                }
            }

            jobCache.cacheJob(verifiedJob)
            val builder = runtime.builder(verifiedJob.id, verifiedJob.specification.replicas)
            features.forEach {
                k8.debug?.everything("Running feature (onCreate): ${it.javaClass.simpleName}")
                with(it) {
                    onCreate(verifiedJob, builder)
                }
            }

            k8.debug?.everything("Creating resource")
            runtime.schedule(builder)
            k8.debug?.everything("Resource has been created!")
        } catch (ex: Throwable) {
            log.warn(ex.stackTraceToString())
            throw ex
        }
    }

    private suspend fun cleanup(jobId: String) {
        val resolvedJob = jobCache.findJob(jobId) ?: return
        for (i in 0 until resolvedJob.specification.replicas) {
            runtime.retrieve(jobId, i)?.cancel()
        }
    }

    suspend fun extend(job: Job, newMaxTime: SimpleDuration) {
        val rootJob = runtime.retrieve(job.id, 0) ?: return
        with(this) {
            with(FeatureExpiry) {
                extendJob(rootJob, newMaxTime)
            }
        }
    }

    suspend fun cancel(verifiedJob: Job) {
        cancel(verifiedJob.id)
    }

    suspend fun cancel(jobId: String) {
        markJobAsComplete(jobId)
        cleanup(jobId)
        k8.changeState(
            jobId,
            JobState.SUCCESS,
            "Job has been cancelled",
            allowRestart = false,
            expectedDifferentState = true,
        )
    }

    private var lastScan: Map<String, List<Container>> = emptyMap()

    private inner class JobEvent(
        val jobId: String,
        val oldReplicas: List<Container>,
        val newReplicas: List<Container>,
    ) {
        val oldRoot: Container? get() = oldReplicas.find { it.rank == 0 }
        val newRoot: Container? get() = newReplicas.find { it.rank == 0 }

        val wasDeleted: Boolean get() = newReplicas.isEmpty()
        val updatedState: JobState?
            get() {
                val oldState = oldRoot?.stateAndMessage()?.first
                val newState = newRoot?.stateAndMessage()?.first

                if (newState != oldState) return newState
                return null
            }

        val updatedRunning: Int?
            get() {
                if (oldReplicas.size != newReplicas.size) return newReplicas.size
                return null
            }

        suspend fun isDying(): Boolean {
            val looksLikeItIsDying = newReplicas.size < oldReplicas.size ||
                    newReplicas.any { it.stateAndMessage().first.isFinal() }
            if (!looksLikeItIsDying) return false
            val job = jobCache.findJob(jobId)
            if (oldReplicas.size != job?.specification?.replicas) return false
            return true
        }
    }

    private fun processScan(newJobs: List<Container>): List<JobEvent> {
        val newJobsGrouped = newJobs.groupBy { it.jobId }

        val result = ArrayList<JobEvent>()
        val observed = HashSet<String>()
        for ((jobId, oldReplicas) in lastScan) {
            val newReplicas = newJobsGrouped[jobId] ?: emptyList()

            result.add(JobEvent(jobId, oldReplicas, newReplicas))
            observed.add(jobId)
        }

        for ((jobId, replicas) in newJobsGrouped) {
            if (jobId in observed) continue

            result.add(JobEvent(jobId, emptyList(), replicas))
        }

        lastScan = newJobsGrouped

        return result
    }

    suspend fun runMonitoring() {
        val didAcquire = true
        if (didAcquire) {
            log.info("This service has become the master responsible for handling Kubernetes events!")

            val isAlive = true
            whileGraal({currentCoroutineContext().isActive && isAlive}) {
                k8.debug.useContext(DebugContextType.BACKGROUND_TASK, "Unsuspend queue", MessageImportance.IMPLEMENTATION_DETAIL) {
                    val taskName = "job_unsuspend"
                    Prometheus.countBackgroundTask(taskName)
                    val start = Time.now()

                    try {
                        // NOTE(Dan): Need to take a copy and release the lock to avoid issues with the mutex not being
                        // re-entrant.
                        var listCopy: ArrayList<UnsuspendItem>
                        unsuspendMutex.withLock {
                            listCopy = ArrayList(unsuspendQueue)
                            unsuspendQueue.clear()
                        }

                        k8.debug.detail(
                            "Items in queue",
                            defaultMapper.encodeToJsonElement(ListSerializer(UnsuspendItem.serializer()), listCopy)
                        )

                        val now = Time.now()
                        for ((job, expiry) in listCopy) {
                            k8.debug.useContext(DebugContextType.BACKGROUND_TASK, "Processing ${job.id}") {
                                if (now < expiry) {
                                    create(job, expiry)
                                }
                            }
                        }

                        k8.debug.normal("Processed ${listCopy.size} items. ${unsuspendQueue.size} items remain in queue.")
                    } finally {
                        Prometheus.measureBackgroundDuration(taskName, Time.now() - start)
                    }
                }

                k8.debug.useContext(DebugContextType.BACKGROUND_TASK, "K8 Job monitoring", MessageImportance.IMPLEMENTATION_DETAIL) {
                    val taskName = "job_monitoring"
                    val start = Time.now()
                    Prometheus.countBackgroundTask(taskName)

                    try {
                        val resources = runtime.list().toMutableList()

                        run {
                            // Remove containers from jobs we no longer recognize
                            val resourcesIterator = resources.iterator()
                            while (resourcesIterator.hasNext()) {
                                val resource = resourcesIterator.next()
                                val job = jobCache.findJob(resource.jobId) ?: continue
                                if (job.status.state.isFinal()) {
                                    resourcesIterator.remove()

                                    try {
                                        log.info("Terminating job with terminal state: ${resource.jobId} ${resource.rank}")
                                        resource.cancel(force = true)
                                    } catch (ex: Throwable) {
                                        log.info(
                                            "Exception while terminating job with terminal state: " +
                                                    "${resource.jobId} ${resource.rank}\n${ex.toReadableStacktrace()}"
                                        )
                                    }
                                }
                            }
                        }

                        val events = processScan(resources)
                        k8.debug.detail("Received ${resources.size} resources from runtime")
                        k8.debug.detail(
                            "Events fetched from K8",
                            defaultMapper.encodeToJsonElement(
                                ListSerializer(String.serializer()),
                                events.map { it.jobId })
                        )
                        // TODO It looks like this code is aware of changes but they are not successfully received by
                        // UCloud/sent by this service

                        var debugTerminations = 0
                        var debugExpirations = 0
                        var debugUpdates = 0

                        events.forEachGraal { event ->
                            k8.debug.useContext(DebugContextType.BACKGROUND_TASK, "Processing: ${event.jobId}") l@{
                                when {
                                    event.wasDeleted || event.isDying() -> {
                                        val expiry = event.oldReplicas.find { it.rank == 0 }?.expiry
                                        if (expiry != null && Time.now() >= expiry) {
                                            // NOTE(Dan): Expiry feature will simply delete the object. This is why we must
                                            // check if the reason was expiration here.
                                            k8.changeState(event.jobId, JobState.EXPIRED, "Job has expired")

                                            debugExpirations++
                                        } else {
                                            k8.changeState(
                                                event.jobId,
                                                JobState.SUCCESS,
                                                "Job has terminated",
                                                allowRestart = true,
                                                expectedDifferentState = true,
                                            )

                                            debugTerminations++
                                        }

                                        runCatching {
                                            markJobAsComplete(event.oldReplicas)
                                        }

                                        runCatching {
                                            cleanup(event.jobId)
                                        }
                                    }

                                    else -> {
                                        val message = event.newRoot?.stateAndMessage()?.second
                                        val newState: JobState? = event.newRoot?.stateAndMessage()?.first

                                        if (newState != null) {
                                            if (newState == JobState.SUCCESS) {
                                                val didChange = k8.changeState(
                                                    event.jobId,
                                                    JobState.SUCCESS,
                                                    message,
                                                    allowRestart = true,
                                                    expectedDifferentState = true,
                                                )

                                                if (didChange) {
                                                    markJobAsComplete(event.jobId)
                                                    event.newRoot?.cancel()
                                                }
                                            } else {
                                                val didChangeState = k8.changeState(
                                                    event.jobId,
                                                    newState,
                                                    message,
                                                )

                                                if (didChangeState && newState == JobState.RUNNING) {
                                                    features.forEach { feature ->
                                                        with(feature) {
                                                            onJobStart(event.newRoot!!, event.newReplicas)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        debugUpdates++
                                    }
                                }
                            }
                        }

                        features.forEachGraal { feature ->
                            with(feature) {
                                onJobMonitoring(resources)
                            }
                        }

                        k8.debug.normal(
                            buildString {
                                if (debugUpdates > 0) {
                                    append(" Updates = ")
                                    append(debugUpdates)
                                }

                                if (debugTerminations > 0) {
                                    append(" Terminations = ")
                                    append(debugTerminations)
                                }

                                if (debugExpirations > 0) {
                                    append(" Expirations = ")
                                    append(debugExpirations)
                                }
                            },
                        )
                    } finally {
                        Prometheus.measureBackgroundDuration(taskName, Time.now() - start)
                    }

                    val end = Time.now()
                    val delayTime = 5000 - (end - start)
                    if (delayTime > 0) delay(delayTime)
                }
            }
        }
    }

    private suspend fun markJobAsComplete(replicas: List<Container>): Boolean {
        val job = replicas.find { it.rank == 0 } ?: return false
        features.forEach { feature ->
            with(feature) {
                onJobComplete(job, replicas)
            }
        }
        features.forEach { feature ->
            with(feature) {
                onCleanup(job.jobId)
            }
        }
        return true
    }

    private suspend fun markJobAsComplete(jobId: String): Boolean {
        runtime.removeJobFromQueue(jobId)
        val replicas = runtime.list().filter { it.jobId == jobId }
        val didDelete = markJobAsComplete(replicas)

        if (!didDelete) {
            // NOTE(Dan): This is not the cleanest of ways to perform a cleanup, but it will work for now. This code
            // has been copy & pasted from markJobAsComplete which receives a list of containers. This was causing an
            // issue since some cleanup functions need to act on other resources that have already been created prior
            // to the container being created.
            
            features.forEach { feature ->
                with(feature) {
                    onCleanup(jobId)
                }
            }
        }
        return didDelete
    }

    fun verifyJobs(jobs: List<Job>) {
        k8.scope.launch {
            val knownJobs = runtime.list().groupBy { it.jobId }
            for (ucloudJob in jobs) {
                if (ucloudJob.status.state == JobState.SUSPENDED) continue
                if (ucloudJob.id !in knownJobs) {
                    // NOTE(Dan): Added here to make absolutely sure that we are not sending a failure for a job
                    // which is still known. This will normally try to query something like the Volcano resource,
                    // which might exist before and after the pod.
                    if (!runtime.isJobKnown(ucloudJob.id)) {
                        JobsControl.update.call(
                            bulkRequestOf(
                                ResourceUpdateAndId(
                                    ucloudJob.id,
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

    companion object : Loggable {
        override val log = logger()
    }
}
