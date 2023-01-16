package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.accounting.api.providers.*
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
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
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
    private val jobCache: VerifiedJobCache,
    private val maintenance: MaintenanceService,
    val resources: ResourceCache,
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
            runtime.scheduleGroup(listOf(builder))
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

    private suspend fun cancel(jobId: String) {
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

    @Serializable
    private data class JobEvent(
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

            // NOTE(Dan): Delay the initial scan to wait for server to be ready (needed for local dev)
            delay(15_000)

            val isAlive = true
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
                    val resources = runtime.list()

                    val events = processScan(resources)
                    k8.debug.detailD("Received ${resources.size} resources from runtime", Unit.serializer(), Unit)
                    k8.debug.detailD("Events fetched from K8", ListSerializer(String.serializer()), events.map { it.jobId })
                    // TODO It looks like this code is aware of changes but they are not successfully received by 
                    // UCloud/sent by this service

                    var debugTerminations = 0
                    var debugExpirations = 0
                    var debugUpdates = 0

                    events.forEachGraal { event ->
                        k8.debug.enterContext("Processing: ${event.jobId}") l@{
                            when {
                                event.wasDeleted -> {
                                    val oldJob = event.oldReplicas.find { it.rank == 0 } ?: return@l
                                    val expiry = oldJob.expiry
                                    if (expiry != null && Time.now() >= expiry) {
                                        // NOTE(Dan): Expiry feature will simply delete the object. This is why we must
                                        // check if the reason was expiration here.
                                        markJobAsComplete(event.jobId)
                                        k8.changeState(event.jobId, JobState.EXPIRED, "Job has expired")

                                        debugExpirations++
                                    } else {
                                        k8.changeState(
                                            event.jobId,
                                            JobState.SUCCESS, 
                                            "Job has terminated", 
                                            allowRestart = false,
                                            expectedDifferentState = true,
                                        )

                                        debugTerminations++
                                    }
                                }

                                event.updatedState != null || event.updatedRunning != null -> {
                                    val message = event.newRoot?.stateAndMessage()?.second
                                    val newState: JobState? = event.updatedState

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

                                            if (didChangeState) {
                                                features.forEach { feature ->
                                                    with(feature) {
                                                        onJobStart(event.newRoot!!, event.newReplicas)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        if (message != null) k8.addStatus(event.jobId, message)
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

    private suspend fun markJobAsComplete(jobId: String): Boolean {
        val replicas = runtime.list().filter { it.jobId == jobId }
        val job = replicas.find { it.rank == 0 } ?: return false
        features.forEach { feature ->
            with(feature) {
                onJobComplete(job, replicas)
            }
        }
        features.forEach { feature ->
            with(feature) {
                onCleanup(jobId)
            }
        }
        return true
    }

    fun verifyJobs(jobs: List<Job>) {
        k8.scope.launch {
            val knownJobs = runtime.list().groupBy { it.jobId }
            for (ucloudJob in jobs) {
                if (ucloudJob.status.state == JobState.SUSPENDED) continue
                if (ucloudJob.id !in knownJobs) {
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

    companion object : Loggable {
        override val log = logger()
    }
}
