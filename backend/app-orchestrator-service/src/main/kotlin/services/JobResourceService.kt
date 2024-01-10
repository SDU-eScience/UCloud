package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.appCache
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.productCache
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.db
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.backgroundScope
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.exporter
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.fileCollections
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.idCards
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.payment
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.providers
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.mail.api.Mail
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.mail.api.SendRequestItem
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.actorAndProject
import dk.sdu.cloud.service.db.async.*
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import org.cliffc.high_scale_lib.NonBlockingHashMapLong
import java.util.*
import java.util.concurrent.CancellationException
import kotlin.collections.ArrayList
import kotlin.math.min
import kotlin.random.Random

// `Job`s in UCloud are the core abstraction used to describe units of computation. The code in this file implements
// the orchestrating part of Jobs. We suggest you read more about jobs before trying to understand this file.
//
// The overall goal of this file is to "orchestrate". In practice this means a few things:
//
// 1. Receive requests from the end-user and providers
// 2a. Perform authorization on the request
// 2b. Validate that the request makes sense semantically
// 3. If the request is from an end-user, then notify the provider about the request
// 4. Store information about the job in persistent storage, using a ResourceStore

// The ResourceStore component plays a large role in the implementation of this. The store is capable of storing data
// persistently and enforcing authorization. The store contains basic metadata of every job along with internal state
// used by this file. The state is defined below, it contains basic information about how a job was started and its
// current state:

data class InternalJobState(
    var specification: JobSpecification,
    var state: JobState = JobState.IN_QUEUE,
    var outputFolder: String? = null,
    var startedAt: Long? = null,
    var allowRestart: Boolean = false,
    var jobParameters: ExportedParameters? = null,
)

data class JobNotificationInfo(
    val type: JobState,
    val jobId: String,
    val appTitle: String,
    val jobName: String? = null,
)

class JobResourceService(
    private val serviceClient: AuthenticatedClient
) {
    private val jobNotifications = mutableMapOf<String, MutableList<JobNotificationInfo>>()
    private val jobMailNotifications = mutableMapOf<String, MutableList<JobNotificationInfo>>()
    private val notificationMutex = Mutex()

    // Listeners and lifetime events
    // =================================================================================================================
    // Components can hook into lifetime events of a job. This is primarily used to implement features such as
    // public links.
    interface JobListener {
        // A job is considered verified after the user has requested the job to be started and this component has
        // verified that the provider can fulfill the request. The provider has not yet been notified.
        // The listener is now allowed to further inspect the request and can abort the request if they deem it
        // necessary. The exception will be propagated to the end-user.
        suspend fun onVerified(actorAndProject: ActorAndProject, specification: JobSpecification) {}

        // This handler is called after the provider has been notified of a new job but before the end-user has been
        // notified about the result. This event contains the full information about the job, including its ID.
        // Listeners should avoid throwing exceptions in this function. If they do, then an attempt will be made at
        // cleaning up the job.
        suspend fun onCreate(job: Job) {}

        // This handler is called when a job transitions to a final state (see `JobState#isFinal()`).
        suspend fun onTermination(job: Job) {}
    }

    private val listeners = ArrayList<JobListener>()

    fun addListener(listener: JobListener) {
        listeners.add(listener)
    }

    // Basic read operations
    // =================================================================================================================
    // Jobs, like any other resource, have several read operations. These operations fetch either specific jobs (by ID)
    // or a paginated list by some criteria. We also have specialized operations which can fetch very specific
    // information efficiently (e.g. currently running jobs).
    //
    // Summary:
    // - `retrieveBulk`/`retrieve`: fetches one or more jobs by ID
    // - `browseBy`               : fetches a paginated list of jobs by some query
    // - `listRunningJobs`        : efficiently fetches a list of all running jobs
    // - `validatePermission`     : efficiently returns which jobs from a list of jobs an actor has access to

    // Data can be retrieved by ID. The implementation simply needs to ask the ResourceStore.
    // You should prefer the retrieveBulk() function if you need to retrieve several jobs at the same time.
    suspend fun retrieveBulk(
        actorAndProject: ActorAndProject,
        jobIds: LongArray,
        permission: Permission,
    ): List<Job> {
        val card = idCards.fetchIdCard(actorAndProject)
        val result = ArrayList<Job>()
        ResourceOutputPool.withInstance { pool ->
            check(jobIds.size <= pool.size) { "too many items requested at the same time: ${jobIds.size}" }
            val count = documents.retrieveBulk(card, jobIds, pool, permission)
            for (i in 0 until count) result.add(docMapper.map(card, pool[i]))
        }
        return result
    }

    suspend fun retrieve(actorAndProject: ActorAndProject, request: ResourceRetrieveRequest<JobIncludeFlags>): Job? {
        val id = request.id.toLongOrNull() ?: return null
        return retrieveBulk(actorAndProject, longArrayOf(id), Permission.READ).singleOrNull()
    }

    // browseBy is a generic function for retrieving a paginated list of jobs. This function will be invoked by
    // both providers and end-users. This function mostly implements the filtering logic along with choosing a strategy
    // for fetching data.
    suspend fun browseBy(
        actorAndProject: ActorAndProject,
        request: WithBrowseRequest<JobIncludeFlags>,
        query: String? = null,
    ): PageV2<Job> {
        val card = idCards.fetchIdCard(actorAndProject)

        val filterFunction = object : FilterFunction<InternalJobState> {
            override fun filter(doc: ResourceDocument<InternalJobState>): Boolean {
                val data = doc.data!!
                val spec = data.specification
                val flags = request.flags

                if (!optcmp(flags.filterApplication, spec.application.name)) return false
                if (!optcmp(flags.filterState, data.state)) return false
                if (!query.isNullOrBlank()) {
                    val jobName = spec.name
                    val appName = spec.application.name

                    val matches = when {
                        jobName != null && jobName.contains(query, ignoreCase = true) -> true
                        appName.contains(query, ignoreCase = true) -> true
                        doc.id.toString().contains(query) -> true
                        else -> false
                    }

                    if (!matches) return false
                }

                return true
            }
        }

        val strategy = when {
            card is IdCard.Provider && request.flags.filterState == JobState.RUNNING -> {
                BrowseStrategy.Index(allActiveJobs.keys)
            }

            request.sortBy == "name" -> { // The name of the job (not the name of the application)
                BrowseStrategy.Custom(
                    object : DocKeyExtractor<InternalJobState, String?> {
                        override fun extract(doc: ResourceDocument<InternalJobState>): String? {
                            return doc.data?.specification?.name
                        }
                    },
                    Comparator.nullsLast(Comparator.naturalOrder<String>())
                )
            }

            else -> {
                BrowseStrategy.Default()
            }
        }

        return documents.browseWithStrategy(docMapper, card, request, filterFunction, strategy)
    }

    fun listActiveJobs(): List<Long> {
        return allActiveJobs.keys().toList()
    }

    suspend fun validatePermission(
        actorAndProject: ActorAndProject,
        ids: Collection<String>,
        permission: Permission,
    ): List<String> {
        val result = ArrayList<String>()
        ResourceOutputPool.withInstance<InternalJobState, Unit> { pool ->
            val count = documents.retrieveBulk(
                idCards.fetchIdCard(actorAndProject),
                ids.mapNotNull { it.toLongOrNull() }.toLongArray(),
                pool,
                permission = permission
            )

            for (i in 0 until count) {
                result.add(pool[i].id.toString())
            }
        }
        return result
    }

    // Basic write operations
    // =================================================================================================================
    // The following section contains a number of operations related to the creation and mutation of jobs.
    // Jobs can be created by either an end-user _or_ a provider.
    //
    // Summary:
    // - `create`   : invoked by end-users when they wish to create a job, providers are notified of the request
    // - `register` : invoked by providers to put a job in the list
    // - `updateAcl`: updates the acl of a job allowing users to collaborate on a single job
    // - `addUpdate`: updates from the provider, primary way for providers to mutate the state of a job

    // Validation of user input is implemented in the JobVerificationService:
    private val validation = JobVerificationService(appCache, this, fileCollections)

    // The create call is invoked when an end-user wishes to start a job. The request contains which application to
    // start and how to run it. This function will authorize, verify the request and proxy it to the provider(s).
    suspend fun create(
        actorAndProject: ActorAndProject,
        request: BulkRequest<JobSpecification>,
    ): BulkResponse<FindByStringId?> {
        val card = idCards.fetchIdCard(actorAndProject)

        // First step of the function is to run various checks to ensure that the user is authorized to perform this
        // action. We also verify that the provider is capable of fulfilling the request.
        providers.runChecksForCreate(actorAndProject, Jobs, request, "start job") { support, job ->
            val (_, tool, block) = findSupportBlock(job, support)
            block.checkEnabled()

            val supportedProviders = tool.description.supportedProviders
            if (supportedProviders != null) {
                if (job.product.provider !in supportedProviders) {
                    error("The application is not supported by this provider. Try selecting a different application.")
                }
            }
        }

        for (job in request.items) {
            validation.verifyOrThrow(actorAndProject, job)

            // We invoke the listeners once all other steps have passed. Be aware that listeners are allowed to throw
            // an exception if they do not believe that processing should proceed.
            //
            // (please keep this at the end of this block).
            listeners.forEach { it.onVerified(actorAndProject, job) }
        }

        // The remaining code will notify the provider and store the job in persistent storage. We allow failures and
        // propagate a null for the ID in the response. The only exception to this is if the user only requested to
        // create a single job, in that case the error is sent to the user instead.
        var firstException: Throwable? = null
        val result = request.items.map { job ->
            try {
                // The exported parameters are given to the providers. Some providers expose this information directly
                // to the application running on the provider. Applications can use to configure itself according to
                // user's configuration. This can, for example, be used to correctly configure software based on how
                // much memory is available.
                val exportedParameters = exporter.exportParameters(job)

                val initialState = InternalJobState(job, jobParameters = exportedParameters)

                val provider = job.product.provider
                documents.createViaProvider(card, job.product, initialState, addOwnerToAcl = true) { doc ->
                    val mapped = docMapper.map(null, doc)
                    providers.call(provider, actorAndProject, { JobsProvider(it).create }, bulkRequestOf(mapped))
                        .singleIdOrNull()
                        ?.also { _ -> listeners.forEach { it.onCreate(mapped) } }
                        ?.also { allActiveJobs[it.toLong()] = Unit }
                }
            } catch (ex: Throwable) {
                if (firstException == null) firstException = ex
                log.info("Caught exception while creating job: ${ex.toReadableStacktrace()}")
                null
            }
        }.asFindByIdResponseOptional()

        if (request.items.size == 1 && firstException != null) throw firstException!!
        return result
    }

    // Register is invoked by the provider and simply needs to save the job in the store.
    suspend fun register(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ProviderRegisteredResource<JobSpecification>>,
    ): BulkResponse<FindByStringId> {
        val card = idCards.fetchIdCard(actorAndProject)
        for (reqItem in request.items) {
            val onBehalfOf = ActorAndProject(Actor.SystemOnBehalfOfUser(reqItem.createdBy ?: "ghost"), reqItem.project)
            validation.verifyOrThrow(onBehalfOf, reqItem.spec)
            listeners.forEach { it.onVerified(onBehalfOf, reqItem.spec) }
        }

        val doc = ResourceDocument<InternalJobState>()

        return request.items.map {
            documents.register(card, it, InternalJobState(it.spec), output = doc).also { id ->
                val mapped = docMapper.map(null, doc)
                listeners.forEach { it.onCreate(mapped) }
                allActiveJobs[id] = Unit
            }
        }.asFindByIdResponse()
    }

    // Jobs have an ACL which allows multiple users to collaborate on the same job. This is mostly useful for shares
    // resources, such as virtual machines.
    suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdatedAcl>
    ) {
        val card = idCards.fetchIdCard(actorAndProject)
        var didFindAny = false
        for (reqItem in request.items) {
            val id = reqItem.id.toLongOrNull() ?: continue
            val success = documents.updateAcl(
                card,
                id,
                reqItem.deleted.map { NumericAclEntry.fromAclEntity(idCards, it) },
                reqItem.added.flatMap { NumericAclEntry.fromAclEntry(idCards, it) }
            )

            if (success) didFindAny = true
        }

        if (!didFindAny) {
            throw RPCException(
                "Could not apply update, the request did not return any results! Do you have permission to do this?",
                HttpStatusCode.NotFound
            )
        }

        // TODO Do we really need to contact the provider about this?
    }

    // Updates are used primarily for two purposes: communicating to the end-user and changing the internal state.
    // Communication to the end-user is handled entirely by the store, as a result this function only needs to manage
    // the state.
    suspend fun addUpdate(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ResourceUpdateAndId<JobUpdate>>
    ) {
        val card = idCards.fetchIdCard(actorAndProject)
        val updatesByJob = request.items.groupBy { it.id }.mapValues { it.value.map { it.update } }

        // Jobs which need to be restarted or are terminated by the update needs additional work. We track which jobs
        // receive these updates for further processing.
        val jobsToRestart = HashSet<Long>()
        val terminatedJobs = HashSet<Long>()

        for ((jobId, updates) in updatesByJob) {
            documents.addUpdate(
                card,
                jobId.toLongOrNull() ?: continue,
                updates.map {
                    ResourceDocumentUpdate(
                        it.status,
                        defaultMapper.encodeToJsonElement(JobUpdate.serializer(), it)
                    )
                },
                consumer = f@{ job, uIdx, arrIdx ->
                    val uid = createdBy[arrIdx]
                    val pid = project[arrIdx]

                    with(updates[uIdx]) {
                        // Each update comes with a number of fields. Several fields describe how the state should
                        // change, while other properties determine if the update should be applied at all. We start out
                        // by checking if the update should be applied. If it shouldn't, we return false early in the
                        // function (before applying any changes).
                        if (expectedState != null && job.state != expectedState) {
                            return@f false
                        } else if (expectedDifferentState == true && state != null && job.state == state) {
                            return@f false
                        }

                        // The rest of the function simply applies state changes and tracks which jobs should be
                        // restarted and which have been terminated.
                        newTimeAllocation?.also { job.specification.timeAllocation = SimpleDuration.fromMillis(it) }
                        allowRestart?.also { job.allowRestart = it }
                        outputFolder?.also { job.outputFolder = it }

                        state?.also { newState ->
                            job.state = newState

                            if (newState == JobState.RUNNING) job.startedAt = Time.now()

                            // Handle notifications for user
                            backgroundScope.launch {
                                addNotification(
                                    idCards.lookupUid(uid),
                                    newState,
                                    jobId,
                                    job.specification
                                )

                                var timer = 0
                                var timeUntilSendNotifications = 10_000
                                var timeUntilSendMails = 60_000 * 10

                                notificationMutex.withLock {
                                    while (timer < timeUntilSendNotifications && jobNotifications.values.any { it.isNotEmpty() }) {
                                        delay(1_000)
                                        timer += 1_000

                                        if (timer >= timeUntilSendNotifications && jobNotifications.values.any { it.isNotEmpty() }) {
                                            sendNotifications()
                                            timer = timeUntilSendNotifications
                                        }
                                    }

                                    while (timer < timeUntilSendMails && jobMailNotifications.values.any { it.isNotEmpty() }) {
                                        delay(1_000)
                                        timer += 1_000

                                        if (timer >= timeUntilSendMails && jobMailNotifications.values.any { it.isNotEmpty() }) {
                                            sendMails()
                                            timer = timeUntilSendMails
                                        }
                                    }
                                }
                            }

                            if (!newState.isFinal()) {
                                allActiveJobs[jobId.toLongOrNull()] = Unit
                            } else {
                                allActiveJobs.remove(jobId.toLongOrNull())
                            }

                            if (job.specification.restartOnExit == true && newState.isFinal() && allowRestart == true) {
                                job.state = JobState.SUSPENDED
                                jobsToRestart.add(id[arrIdx])
                            }

                            if (job.state.isFinal()) {
                                terminatedJobs.add(id[arrIdx])
                            }
                        }

                        newMounts?.also { newMounts ->
                            // NOTE(Dan, 17/08/23): This is used primarily by Syncthing at the moment. This code needs
                            // to find which mounts among the list are still valid for the user. When we restart the
                            // job later, then the job will receive the new list of mounts by inspecting the resources.
                            val allResources = job.specification.resources ?: emptyList()
                            val nonMountResources = allResources.filter { it !is AppParameterValue.File }
                            val validMountResources = runBlocking {
                                // TODO(Dan): This is really not ideal, we should not be blocking here.
                                val createdBy = idCards.lookupUid(uid)?.let {
                                    Actor.SystemOnBehalfOfUser(it)
                                } ?: Actor.guest

                                val project = if (pid != 0) {
                                    idCards.lookupPid(pid)
                                } else {
                                    null
                                }

                                validation.checkAndReturnValidFiles(
                                    ActorAndProject(createdBy, project),
                                    newMounts.map { AppParameterValue.File(it) }
                                )
                            }

                            job.specification = job.specification.copy(
                                resources = nonMountResources + validMountResources
                            )
                        }
                    }

                    return@f true
                }
            )
        }

        // Below we process the jobs we tracked earlier. These happen in a background thread and generally speaking
        // try to progress even when an exception is thrown.

        if (jobsToRestart.isNotEmpty()) {
            backgroundScope.launch {
                val jobs = retrieveBulk(ActorAndProject.System, jobsToRestart.toLongArray(), Permission.READ)
                performUnsuspension(jobs)
            }
        }

        if (terminatedJobs.isNotEmpty()) {
            val jobs = retrieveBulk(ActorAndProject.System, terminatedJobs.toLongArray(), Permission.READ)
            backgroundScope.launch {
                for (job in jobs) {
                    try {
                        for (listener in listeners) {
                            listener.onTermination(job)
                        }
                    } catch (ex: Throwable) {
                        log.info("Caught exception while running termination handler:\n${ex.toReadableStacktrace()}")
                    }
                }
            }
        }
    }

    private suspend fun addNotification(user: String?, newState: JobState, jobId: String, jobSpecification: JobSpecification) {
        if (user == null) return;

        val appTitle = appCache.resolveApplication(jobSpecification.application)!!.metadata.title

        if (!jobNotifications.containsKey(user)) {
            jobNotifications[user] = mutableListOf()
            jobMailNotifications[user] = mutableListOf()
        }

        jobNotifications[user]!!.add(
            JobNotificationInfo(
                newState,
                jobId,
                appTitle,
                jobSpecification.name,
            )
        )

        jobMailNotifications[user]!!.add(
            JobNotificationInfo(
                newState,
                jobId,
                appTitle,
                jobSpecification.name,
            )
        )
    }

    private suspend fun sendNotifications() {
        val sentNotifications = mutableListOf<String>()
        for (user in jobNotifications.keys) {
            val handledTypes = mutableListOf<JobState>()
            val notifications = jobNotifications[user] ?: continue

            val summarizedNotifications: List<Notification> = notifications.mapNotNull { notification ->
                if (handledTypes.contains(notification.type)) return@mapNotNull null

                val sameType = notifications.filter { notification.type == it.type }
                handledTypes.add(notification.type)

                sameType.forEach {
                    sentNotifications.add("$user-${it.type.name}-${it.jobId}")
                }

                val jobIds = sameType.map { JsonPrimitive(it.jobId) }
                val appTitles = sameType.map { JsonPrimitive(it.appTitle) }

                val type = when (notification.type) {
                    JobState.SUCCESS -> "JOB_COMPLETED"
                    JobState.RUNNING -> "JOB_STARTED"
                    JobState.FAILURE -> "JOB_FAILED"
                    JobState.EXPIRED -> "JOB_EXPIRED"
                    else -> return;
                }

                Notification(
                    type,
                    "",
                    meta = JsonObject(
                        mapOf(
                            "jobIds" to JsonArray(jobIds),
                            "appTitles" to JsonArray(appTitles),
                        )
                    )
                )
            }

            summarizedNotifications.forEach { notification ->
                NotificationDescriptions.create.call(
                    CreateNotification(
                        user,
                        notification
                    ),
                    serviceClient
                )
            }
        }

        sentNotifications.forEach { sent ->
            for (user in jobNotifications.keys) {
                jobNotifications[user]?.removeIf { "$user-${it.type.name}-${it.jobId}" == sent}
            }
        }
    }

    private suspend fun sendMails() {
        val sentNotifications = mutableListOf<String>()
        for (user in jobMailNotifications.keys) {
            val notifications = jobMailNotifications[user] ?: continue
            if (notifications.isEmpty()) continue

            val jobIds = notifications.map {it.jobId }
            val jobNames = notifications.map { it.jobName }
            val appTitles = notifications.map { it.appTitle }

            val types = notifications.mapNotNull { notification ->
                when (notification.type) {
                    JobState.SUCCESS -> "JOB_COMPLETED"
                    JobState.RUNNING -> "JOB_STARTED"
                    JobState.FAILURE -> "JOB_FAILED"
                    JobState.EXPIRED -> "JOB_EXPIRED"
                    else -> null;
                }
            }

            notifications.forEach {
                sentNotifications.add("$user-${it.type.name}-${it.jobId}")
            }

            val subject = if (types.size > 1) {
                if (notifications.all { it.type == JobState.RUNNING }) {
                    "${notifications.size} of your jobs on UCloud have started"
                } else if (notifications.all { it.type == JobState.SUCCESS }) {
                    "${notifications.size} of your jobs on UCloud have completed"
                } else if (notifications.all { it.type == JobState.FAILURE }) {
                    "${notifications.size} of your jobs on UCloud have failed"
                } else if (notifications.all { it.type == JobState.EXPIRED }) {
                    "${notifications.size} of your jobs on UCloud have expired"
                } else {
                    "The state of ${notifications.size} of your jobs on UCloud has changed"
                }
            } else {
                when (notifications.first().type) {
                    JobState.RUNNING -> "One of your jobs on UCloud has started"
                    JobState.SUCCESS -> "One of your jobs on UCloud has completed"
                    JobState.FAILURE -> "One of your jobs on UCloud has failed"
                    JobState.EXPIRED -> "One of your jobs on UCloud has expired"
                    else -> "The state of one of your jobs on UCloud has changed"
                }
            }

            MailDescriptions.sendToUser.call(
                bulkRequestOf(
                    SendRequestItem(
                        user,
                        Mail.JobEvents(
                            jobIds,
                            jobNames,
                            appTitles,
                            types,
                            subject = subject
                        )
                    )
                ),
                serviceClient
            )
        }

        sentNotifications.forEach { sent ->
            for (user in jobMailNotifications.keys) {
                jobMailNotifications[user]?.removeIf { "$user-${it.type.name}-${it.jobId}" == sent}
            }
        }
    }

    // Job specific read operations
    // =================================================================================================================
    // The following section contains read operations that are specific to jobs.
    //
    // Summary:
    // - `retrieveProducts`   : fetches a list of relevant products with a manifest describing what each product can do
    // - `retrieveUtilization`: fetches utilization from a cluster for a specific product category
    // - `follow`             : subscription to job updates which includes stdout/stderr streams from the job

    suspend fun retrieveProducts(
        actorAndProject: ActorAndProject,
    ): SupportByProvider<Product.Compute, ComputeSupport> {
        return providers.retrieveProducts(actorAndProject, Jobs)
    }

    // The `retrieveUtilization` endpoint allows the end-user to ask the provider how busy the cluster is for a given
    // product category. This allows the provider to report back if the cluster is extremely busy.
    //
    // NOTE(Dan, 17/08/23): This endpoint is currently not used at all, we likely want to change how this work. I
    // suspect, we will want to collect this information in a different format and for all product categories at once.
    // We should then cache and simply respond to the end-user with the information in the cache.
    suspend fun retrieveUtilization(
        actorAndProject: ActorAndProject,
        request: JobsRetrieveUtilizationRequest
    ): JobsRetrieveUtilizationResponse {
        val jobId = request.jobId.toLongOrNull() ?: throw RPCException("Unknown job", HttpStatusCode.NotFound)
        var response: JobsRetrieveUtilizationResponse? = null
        proxy(
            actorAndProject,
            longArrayOf(jobId),
            "retrieve cluster utilization",
            featureValidator { it.utilization },
            Permission.READ,
            fn = { provider, jobs ->
                response = providers.call(
                    provider,
                    actorAndProject,
                    { JobsProvider(it).retrieveUtilization },
                    JobsProviderUtilizationRequest(
                        jobs.single().specification.product.category,
                    ),
                )
            }
        )
        return response ?: throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
    }

    // The follow endpoint is a (WebSocket) streaming endpoint. It starts by receiving a request from the end-user which
    // contains the job the user wants to follow. The orchestrator is then supposed to send back any relevant updates
    // it receives from the job. The stream is terminated once the user closes the connection or the job reaches a final
    // state.
    //
    // This endpoint will attempt to send out the following type of updates:
    //
    // - Resource updates (from `addUpdate`)
    // - Log messages from the job (if supported by the provider)
    // - State transitions (e.g. `IN_QUEUE` -> `RUNNING` -> `SUCCESS`)
    //
    // The function is implemented by launching multiple coroutines, in the same scope, which track the individual
    // types of updates. As soon as any of the coroutines terminate, then the remaining are cancelled. We do this to
    // avoid leaking tasks in the background.
    //
    // Updates are sent using the `sendWSMessage` function, which is thread-safe and can be used without any
    // synchronization between the coroutines.
    suspend fun follow(
        callHandler: CallHandler<JobsFollowRequest, JobsFollowResponse, *>,
    ): Unit = with(callHandler) {
        val card = idCards.fetchIdCard(callHandler.actorAndProject)

        val jobId = request.id.toLongOrNull()
            ?: throw RPCException("Unknown job: ${request.id}", HttpStatusCode.NotFound)

        val initialJob = documents.retrieve(card, jobId)?.let { docMapper.map(card, it) }
            ?: throw RPCException("Unknown job: ${request.id}", HttpStatusCode.NotFound)

        val logsSupported = runCatching {
            providers.requireSupport(Jobs, listOf(initialJob.specification.product), "follow logs") { support ->
                val block = findSupportBlock(initialJob.specification, support).block
                block.checkFeature(block.logs)
            }
        }.isSuccess

        withContext<WSCall> {
            // NOTE(Dan): We do _not_ send the initial list of updates, instead we assume that clients will
            // retrieve them by themselves.
            sendWSMessage(JobsFollowResponse(newStatus = initialJob.status))

            var lastUpdate = initialJob.updates.maxByOrNull { it.timestamp }?.timestamp ?: 0L
            var currentState = initialJob.status.state
            var streamId: String? = null
            val provider = initialJob.specification.product.provider

            coroutineScope {
                // This coroutine tracks the log output of a job by asking the provider. In this case we are simply
                // proxying the information directly from the provider to the end-user.
                val logJob = if (!logsSupported) {
                    null
                } else {
                    launch {
                        while (isActive) {
                            try {
                                providers.invokeSubscription(
                                    provider,
                                    callHandler.actorAndProject,
                                    { JobsProvider(it).follow },
                                    JobsProviderFollowRequest.Init(initialJob),
                                    handler = { message: JobsProviderFollowResponse ->
                                        if (streamId == null) streamId = message.streamId

                                        sendWSMessage(
                                            JobsFollowResponse(
                                                log = listOf(JobsLog(message.rank, message.stdout, message.stderr))
                                            )
                                        )
                                    }
                                )
                            } catch (ignore: CancellationException) {
                                break
                            } catch (ex: Throwable) {
                                log.debug("Caught exception while following logs:\n${ex.stackTraceToString()}")
                                break
                            }
                        }
                    }
                }

                // This coroutine polls the resource store for the job and sends the end-user the delta between the
                // previous version and the current version.
                val updateJob = launch {
                    try {
                        var lastStatus: JobStatus? = null
                        while (isActive && !currentState.isFinal()) {
                            val newJob = documents.retrieve(card, jobId) ?: break
                            val data = newJob.data ?: break

                            currentState = data.state

                            val updates = newJob.update
                                .asSequence()
                                .filterNotNull()
                                .filter { it.createdAt > lastUpdate }
                                .mapNotNull { update ->
                                    update.extra?.let {
                                        defaultMapper.decodeFromJsonElement(JobUpdate.serializer(), it)
                                    }?.also {
                                        it.timestamp = update.createdAt
                                        it.status = update.update
                                    }
                                }
                                .toList()

                            if (updates.isNotEmpty()) {
                                sendWSMessage(JobsFollowResponse(updates = updates))
                                lastUpdate = updates.maxByOrNull { it.timestamp }?.timestamp ?: 0L
                            }

                            val newStatus = docMapper.map(null, newJob).status
                            if (lastStatus != newStatus) {
                                sendWSMessage(JobsFollowResponse(newStatus = newStatus))
                            }

                            lastStatus = newStatus
                            delay(1000)
                        }
                    } catch (ex: Throwable) {
                        if (ex !is CancellationException) {
                            log.warn(ex.stackTraceToString())
                        }
                    }
                }

                // The select clause waits for the first coroutine to finish. Once this is done, we proceed with the
                // termination of all other coroutines.
                select {
                    if (logJob != null) logJob.onJoin {}
                    updateJob.onJoin {}
                }

                val capturedId = streamId
                if (capturedId != null) {
                    providers.call(
                        provider,
                        actorAndProject,
                        { JobsProvider(it).follow },
                        JobsProviderFollowRequest.CancelStream(capturedId),
                        useHttpClient = false,
                    )
                }

                runCatching { logJob?.cancel("No longer following or EOF") }
                runCatching { updateJob.cancel("No longer following or EOF") }
            }
        }
    }

    // Job specific write operations
    // =================================================================================================================
    // The following section contains write operations that are specific to jobs.
    //
    // Summary:
    // - `chargeOrCheckCredits`  : tracks usage of a job (accounting)
    // - `openInteractiveSession`: establishes an interactive session (e.g. remote desktop)
    // - `extend`                : extends the time allocation of a job
    // - `terminate`             : signals the provider to stop a job
    // - `suspendOrUnsuspend`    : signal the provider to temporarily suspend the job
    // - `performUnsuspension`   : actually performs the task of resuming the job after suspension
    // - `initializeProviders`   : signals the provider that an end-user needs to be initialized in their system

    suspend fun chargeOrCheckCredits(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ResourceChargeCredits>,
        checkOnly: Boolean
    ): ResourceChargeCreditsResponse {
        return payment.chargeOrCheckCredits(idCards, productCache, documents, actorAndProject, request, checkOnly)
    }

    // The openInteractiveSession call establishes some kind of interactive session between the end-user and the job
    // running at the provider. Several types of sessions exits and how they work vary widely between different types.
    // Common examples of this includes: interactive shells, web interfaces and remote desktops via VNC.
    //
    // Fortunately, in the provider none of this really matters since the session itself must be established directly
    // between the end-user and the provider. This is important to ensure that the orchestrator do not receive
    // information it shouldn't have.
    suspend fun openInteractiveSession(
        actorAndProject: ActorAndProject,
        request: BulkRequest<JobsOpenInteractiveSessionRequestItem>,
    ): BulkResponse<OpenSessionWithProvider?> {
        if (request.items.isEmpty()) return BulkResponse(emptyList())

        var firstException: Throwable? = null
        val responses = ArrayList<OpenSessionWithProvider?>()
        proxy(
            actorAndProject,
            request.extractIds { it.id },
            "open interface",
            featureValidation = { job, support ->
                val (app, tool, block) = findSupportBlock(job.specification, support)
                for (reqItem in request.items) {
                    if (reqItem.id != job.id) continue
                    when (reqItem.sessionType) {
                        InteractiveSessionType.WEB -> {
                            require(app.invocation.web != null)
                            require(block is ComputeSupport.WithWeb)
                            block.checkFeature(block.web)
                        }

                        InteractiveSessionType.VNC -> {
                            require(app.invocation.vnc != null)
                            block.checkFeature(block.vnc)
                        }

                        InteractiveSessionType.SHELL -> {
                            block.checkFeature(block.terminal)
                        }
                    }
                }
            },
            fn = { provider, jobs ->
                val providerRequest = request.items
                    .asSequence()
                    .filter { req -> jobs.any { it.id == req.id } }
                    .map { req ->
                        val job = jobs.find { it.id == req.id }!!
                        JobsProviderOpenInteractiveSessionRequestItem(
                            job,
                            req.rank,
                            req.sessionType
                        )
                    }
                    .toList()

                val providerDomain = providers.retrieveProviderHostInfo(provider).toString()
                try {
                    responses.addAll(
                        providers
                            .call(
                                provider,
                                actorAndProject,
                                { JobsProvider(it).openInteractiveSession },
                                BulkRequest(providerRequest),
                            )
                            .responses
                            .asSequence()
                            .filterNotNull()
                            .map { OpenSessionWithProvider(providerDomain, provider, it) }
                    )
                } catch (ex: Throwable) {
                    if (firstException == null) firstException = ex
                    log.info("Caught exception while opening interactive session (${jobs.map { it.id }}): " +
                            "${ex.toReadableStacktrace()}")
                    responses.addAll(jobs.map { null })
                }
            }
        )

        if (responses.count { it != null } == 0 && firstException != null) throw firstException!!
        return BulkResponse(responses)
    }

    // Time extension is supported by some providers. As the name implies, it extends the reservation by some amount. We
    // expect the provider to notify us of the result through an update (See `JobUpdate#newTimeAllocation`).
    suspend fun extend(
        actorAndProject: ActorAndProject,
        request: BulkRequest<JobsExtendRequestItem>,
    ) {
        val ids = request.extractIds { it.jobId }

        var anySuccess = false
        var firstException: Throwable? = null
        proxy(actorAndProject, ids, "extend duration", featureValidator { it.timeExtension }) { provider, jobs ->
            val providerRequest = jobs.map { job ->
                JobsProviderExtendRequestItem(
                    job,
                    request.items.findLast { it.jobId == job.id }!!.requestedTime,
                )
            }

            try {
                providers.call(
                    provider,
                    actorAndProject,
                    { JobsProvider(provider).extend },
                    BulkRequest(providerRequest)
                )
                anySuccess = true
            } catch (ex: Throwable) {
                if (firstException == null) firstException = ex
                log.info("Caught exception while trying to extend job: ${ex.toReadableStacktrace()}")
            }
        }

        if (!anySuccess && firstException != null) throw firstException!!
    }

    // The terminate call tells the provider to shut down a job. We do not need to perform any state changes since we
    // expect the provider to notify us when the job has successfully terminated.
    suspend fun terminate(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ) {
        var anySuccess = false
        var firstException: Throwable? = null
        proxy(actorAndProject, request.extractIds { it.id }, "terminate job") { provider, jobs ->
            try {
                providers.call(provider, actorAndProject, { JobsProvider(provider).terminate }, BulkRequest(jobs))
                anySuccess = true
            } catch (ex: Throwable) {
                if (firstException == null) firstException = ex
                log.info("Caught exception while trying to terminate job: ${ex.toReadableStacktrace()}")
            }
        }

        if (!anySuccess && firstException != null) throw firstException!!
    }

    // The suspend and unsuspend endpoints are very similar to the terminate endpoint. The main difference here is that
    // we expect the provider to put us in a `SUSPENDED` state instead of a final state. That is, this operation,
    // is reversible unlike terminate. A common example of this would be a virtual machine being powered off, but
    // retaining all of its data.
    suspend fun suspendOrUnsuspendJob(
        actorAndProject: ActorAndProject,
        request: BulkRequest<JobsSuspendRequestItem>,
        shouldSuspend: Boolean,
    ) {
        var anySuccess = false
        var firstException: Throwable? = null
        proxy(
            actorAndProject,
            request.extractIds { it.id },
            if (shouldSuspend) "suspend job" else "unsuspend job",
            featureValidation = { job, support ->
                val block = findSupportBlock(job.specification, support).block
                if (block !is ComputeSupport.VirtualMachine) error("Feature not supported")
                block.checkFeature(block.suspension)
            },
            fn = { provider, jobs ->
                val providerRequest = jobs.map { JobsProviderSuspendRequestItem(it) }
                val call = if (shouldSuspend) JobsProvider(provider).suspend else JobsProvider(provider).unsuspend

                try {
                    providers.call(provider, actorAndProject, { call }, BulkRequest(providerRequest))
                    anySuccess = true
                } catch (ex: Throwable) {
                    if (firstException == null) firstException = ex
                    log.info("Caught exception while trying to suspend/unsuspend a job: ${ex.toReadableStacktrace()}")
                }
            }
        )

        if (!anySuccess && firstException != null) throw firstException!!
    }

    suspend fun performUnsuspension(jobs: List<Job>) {
        for (job in jobs) {
            try {
                val actorAndProject = job.owner.toActorAndProject()

                val output = Array<ResourceDocument<InternalJobState>>(1) { ResourceDocument() }
                documents.modify(
                    IdCard.System,
                    output,
                    longArrayOf(job.id.toLong()),
                    Permission.READ,
                    consumer = { arrIdx, doc ->
                        val data = doc.data!!
                        data.specification = job.specification
                    }
                )

                // TODO(Dan): We do not have any signed intent from the user which cause an issue with #3367
                providers.call(
                    job.specification.product.provider,
                    actorAndProject,
                    { JobsProvider(it).unsuspend },
                    BulkRequest(listOf(JobsProviderUnsuspendRequestItem(job))),
                )
            } catch (ex: Throwable) {
                log.info("Failed to restart job: $job\n${ex.stackTraceToString()}")
            }
        }
    }

    suspend fun initializeProviders(actorAndProject: ActorAndProject) {
        providers.forEachRelevantProvider(actorAndProject) { provider ->
            try {
                providers.call(
                    provider,
                    actorAndProject,
                    { JobsProvider(it).init },
                    ResourceInitializationRequest(
                        ResourceOwner(
                            actorAndProject.actor.safeUsername(),
                            actorAndProject.project
                        )
                    )
                )
            } catch (ex: Throwable) {
                log.info("Failed to initialize jobs at provider: $provider. ${ex.toReadableStacktrace()}")
            }
        }
    }

    // Provider utilities
    // =================================================================================================================
    // Following is a number of utility functions related to communicating with providers and verifying if an operation
    // is supported.
    //
    // Summary:
    // - `proxy`           : proxies an operation from the end-user to the relevant providers
    // - `findSupportBlock`: utility function for finding the appropriate support information for a given job + operation
    // - `featureValidator`: utility function for creating `FeatureValidator`s (used by `proxy`)

    private suspend fun <T> proxy(
        actorAndProject: ActorAndProject,
        ids: LongArray,
        actionDescription: String,
        featureValidation: FeatureValidator? = null,
        permission: Permission = Permission.EDIT,
        fn: suspend (providerId: String, jobs: List<Job>) -> T
    ) {
        proxy.send(
            actorAndProject,
            ids.toList(),
            permission,
            "trying to $actionDescription",
            featureValidation = { job, support -> featureValidation?.invoke(job, support as ComputeSupport) },
            fn
        )
    }

    private fun featureValidator(fn: (ComputeSupport.UniversalBackendSupport) -> Boolean?): FeatureValidator {
        return { job, support ->
            val b = findSupportBlock(job.specification, support)
            b.block.checkFeature(fn(b.block))
        }
    }

    private data class SupportInfo(
        val application: Application,
        val tool: Tool,
        val block: ComputeSupport.UniversalBackendSupport,
    )

    private suspend fun findSupportBlock(
        spec: JobSpecification,
        support: ComputeSupport
    ): SupportInfo {
        val (appName, appVersion) = spec.application
        val application = appCache.resolveApplication(appName, appVersion)
            ?: error("Unknown application")

        val tool = application.invocation.tool.tool ?: error("No tool")

        val block = when (tool.description.backend) {
            ToolBackend.SINGULARITY -> error("unsupported tool backend")
            ToolBackend.DOCKER -> support.docker
            ToolBackend.VIRTUAL_MACHINE -> support.virtualMachine
            ToolBackend.NATIVE -> support.native
        }

        return SupportInfo(application, tool, block)
    }


    // Test data
    // =================================================================================================================
    // Used for generating test data for use in dev environments. Not intended to be used in tests. Does not run in
    // production for obvious reasons. The generated data is only guaranteed to be valid enough that this service can
    // work with it. It does not guarantee that the provider would ever have accepted such a scenario. No communication
    // is made to the provider. No accounting charges are made.
    data class TestDataObject(
        val projectId: String,
        val categoryName: String,
        val provider: String,
        val usageInCoreMinutes: Long,
    )

    @Suppress("DEPRECATION")
    suspend fun generateTestData(
        objects: List<TestDataObject>,
        spreadOverDays: Int = 7,
        clusterCount: Int = 10
    ) {
        require(AppOrchestratorServices.micro.developmentModeEnabled) { "devMode must be enabled" }

        val stepSize = 1000L * 60 * 10
        val duration = 1000L * 60 * 60 * 24 * spreadOverDays
        val stepCount = duration / stepSize
        val endOfPeriod = Time.now()
        val startOfPeriod = endOfPeriod - duration
        val charges = ArrayList<Unit>()

        val applications = ArrayList<NameAndVersion>()
        run {
            db.withSession { session ->
                val rows = session.sendPreparedStatement(
                    {},
                    """
                        select app.name, app.version
                        from app_store.applications app
                    """
                ).rows

                for (row in rows) {
                    applications.add(NameAndVersion(row.getString(0)!!, row.getString(1)!!))
                }
            }
        }

        for (obj in objects) {
            with(obj) {
                val piUsername = run {
                    db.withSession { session ->
                        session.sendPreparedStatement(
                            { setParameter("project_id", projectId) },
                            """
                                select username
                                from project.project_members pm
                                where project_id = :project_id and role = 'PI'
                                limit 1
                            """
                        ).rows.single().getString(0)!!
                    }
                }

                val card = idCards.fetchIdCard(
                    ActorAndProject(
                        Actor.SystemOnBehalfOfUser(piUsername),
                        projectId
                    )
                )

                val allTimestamps = HashSet<Long>()

                for (cluster in LongArray(clusterCount) { Random.nextLong(stepCount - 10) }) {
                    for (i in 0 until 10) {
                        allTimestamps.add(stepSize * (cluster + i) + startOfPeriod)
                    }
                }

                val relevantProducts = ArrayList<Product.Compute>()
                for (id in productCache.productCategoryToProductIds(obj.categoryName) ?: emptyList()) {
                    val product = productCache.productIdToProduct(id)?.toV1() ?: continue
                    if (product !is Product.Compute) continue
                    if (product.category.provider != provider) continue
                    relevantProducts.add(product)
                }
                val smallestProduct = relevantProducts.minBy { it.cpu ?: 1 }

                val minimumSize = (usageInCoreMinutes / allTimestamps.size) / 2
                // Takes some bad luck to hit the min case here
                val maxSize = min(usageInCoreMinutes, minimumSize * 3)

                var charged = 0L
                for ((index, timestamp) in allTimestamps.withIndex()) {
                    var product = relevantProducts.random()
                    var usage = min(usageInCoreMinutes - charged, Random.nextLong(minimumSize, maxSize))
                    if (index == allTimestamps.size - 1) {
                        product = smallestProduct
                        usage = usageInCoreMinutes - charged
                    }

                    val usageInWallMinutes = usage / (product.cpu ?: 1)
                    val application = applications.random()

                    charged += usageInWallMinutes * (product.cpu ?: 1)

                    val docId = documents.create(
                        card,
                        product.toReference(),
                        InternalJobState(
                            JobSpecification(
                                application,
                                product.toReference(),
                                parameters = emptyMap(),
                                timeAllocation = SimpleDuration(200, 0, 0),
                                resources = emptyList()
                            ),
                            JobState.SUCCESS,
                            startedAt = timestamp
                        ),
                        null,
                    )

                    documents.modify(
                        card,
                        Array(1) { ResourceDocument() },
                        longArrayOf(docId),
                        Permission.READ
                    ) { arrIdx, doc ->
                        this.createdAt[arrIdx] = timestamp
                    }

                    val timeInQueue = Random.nextLong(1000L * 120)
                    documents.addUpdate(
                        IdCard.System,
                        docId,
                        listOf(
                            ResourceDocumentUpdate(
                                "Mark job as running (test data/fake job)",
                                defaultMapper.encodeToJsonElement(JobUpdate.serializer(), JobUpdate(JobState.RUNNING)),
                                timestamp + timeInQueue,
                            ),
                            ResourceDocumentUpdate(
                                "Mark job as done (test data/fake job)",
                                defaultMapper.encodeToJsonElement(JobUpdate.serializer(), JobUpdate(JobState.SUCCESS)),
                                timestamp + timeInQueue + (usageInWallMinutes * 1000L * 60),
                            )
                        )
                    )
                }
            }
        }
    }


    // Persistent storage and indices
    // =================================================================================================================
    // All data is stored persistently using a ResourceStore. The following sections configure this store and
    // establishes indices for efficient lookups (see `browseBy` for details).

    // This component keeps track of all jobs that are not in a final state. This is needed to give providers a quick
    // way of accessing running jobs (a common query). The only other alternative is to load all jobs ever and filter
    // them.
    //
    // This state is loaded at startup and then periodically maintained as we receive state updates from the provider.
    private val allActiveJobs = NonBlockingHashMapLong<Unit>()
    private suspend fun initializeActiveJobsIndex() {
        db.withSession { session ->
            val rows = session.sendPreparedStatement(
                {},
                """
                    select j.resource
                    from app_orchestrator.jobs j
                    where
                        j.current_state != 'SUCCESS' and
                        j.current_state != 'FAILURE'
                """
            ).rows

            rows.forEach { row ->
                val id = row.getLong(0)!!
                allActiveJobs[id] = Unit
            }
        }
    }

    private val documents = ResourceStore(
        "job",
        db,
        productCache,
        idCards,
        backgroundScope,
        object : ResourceStore.Callbacks<InternalJobState> {
            override suspend fun loadState(
                transaction: Any,
                count: Int,
                resources: LongArray
            ): Array<InternalJobState> {
                val state = arrayOfNulls<InternalJobState>(count)
                val session = transaction as AsyncDBConnection

                session.sendPreparedStatement(
                    { setParameter("ids", resources.slice(0 until count)) },
                    """
                        with
                            params as (
                                select param.job_id, jsonb_object_agg(param.name, param.value) as params
                                from app_orchestrator.job_input_parameters param
                                where param.job_id = some(:ids)
                                group by job_id
                            ),
                            resources as (
                                select r.job_id, jsonb_agg(r.resource) as resources
                                from app_orchestrator.job_resources r
                                where r.job_id = some(:ids)
                                group by job_id
                            )
                        select
                            j.resource, j.application_name, j.application_version, j.name, j.replicas,
                            j.time_allocation_millis, j.opened_file, j.restart_on_exit, j.ssh_enabled,
                            j.output_folder, j.current_state, p.params, r.resources,
                            floor(extract(epoch from j.started_at) * 1000)::int8,
                            j.allow_restart, j.job_parameters
                        from
                            app_orchestrator.jobs j
                            left join params p on j.resource = p.job_id
                            left join resources r on j.resource = r.job_id
                        where
                            j.resource = some(:ids);
                    """
                ).rows.forEach { row ->
                    val id = row.getLong(0)!!
                    val slot = resources.indexOf(id)
                    val currentState = JobState.valueOf(row.getString(10)!!)

                    if (!currentState.isFinal()) allActiveJobs[id] = Unit
                    else allActiveJobs.remove(id)

                    state[slot] = InternalJobState(
                        JobSpecification(
                            product = ProductReference("", "", ""), // Filled out by doc store
                            application = NameAndVersion(
                                name = row.getString(1)!!,
                                version = row.getString(2)!!,
                            ),
                            name = row.getString(3),
                            replicas = row.getInt(4)!!,
                            allowDuplicateJob = false,
                            parameters = row.getString(11)?.let { text ->
                                defaultMapper.decodeFromString(
                                    MapSerializer(String.serializer(), AppParameterValue.serializer()),
                                    text
                                )
                            } ?: emptyMap(),
                            resources = row.getString(12)?.let { text ->
                                defaultMapper.decodeFromString(
                                    ListSerializer(AppParameterValue.serializer()),
                                    text
                                )
                            } ?: emptyList(),
                            timeAllocation = row.getLong(5)?.let { SimpleDuration.fromMillis(it) },
                            openedFile = row.getString(6),
                            restartOnExit = row.getBoolean(7),
                            sshEnabled = row.getBoolean(8),
                        ),
                        state = currentState,
                        outputFolder = row.getString(9),
                        startedAt = row.getLong(13),
                        allowRestart = row.getBoolean(14) ?: false,
                        jobParameters = row.getString(15)?.let { text ->
                            defaultMapper.decodeFromString(ExportedParameters.serializer(), text)
                        },
                    )
                }

                @Suppress("UNCHECKED_CAST")
                return state as Array<InternalJobState>
            }

            override suspend fun saveState(
                transaction: Any,
                store: ResourceStoreBucket<InternalJobState>,
                indices: IntArray,
                length: Int
            ) {
                val session = transaction as AsyncDBConnection

                val jobsToDelete = ArrayList<Long>()
                for (i in 0 until length) {
                    val arrIdx = indices[i]
                    if (store.flaggedForDelete[arrIdx]) jobsToDelete.add(store.id[arrIdx])
                }

                if (jobsToDelete.isNotEmpty()) {
                    session.sendPreparedStatement(
                        { setParameter("job_ids", jobsToDelete) },
                        "delete from app_orchestrator.job_resources where job_id = some(:job_ids::bigint[])"
                    )

                    session.sendPreparedStatement(
                        { setParameter("job_ids", jobsToDelete) },
                        "delete from app_orchestrator.job_input_parameters where job_id = some(:job_ids::bigint[])"
                    )

                    session.sendPreparedStatement(
                        { setParameter("job_ids", jobsToDelete) },
                        "delete from app_orchestrator.jobs where resource = some(:job_ids::bigint[])"
                    )
                }

                if (jobsToDelete.size != length) {
                    session.sendPreparedStatement(
                        {
                            splitCollection(0 until length) {
                                skipIf { store.flaggedForDelete[indices[it]] }

                                into("job_id") { store.id[indices[it]] }
                                into("application_name") { store.data(indices[it])?.specification?.application?.name }
                                into("application_version") { store.data(indices[it])?.specification?.application?.version }
                                into("time_allocation") { store.data(indices[it])?.specification?.timeAllocation?.toMillis() }
                                into("name") { store.data(indices[it])?.specification?.name }
                                into("output_folder") { store.data(indices[it])?.outputFolder }
                                into("current_state") { store.data(indices[it])?.state?.name }
                                into("started_at") { store.data(indices[it])?.startedAt }
                                into("opened_file") { store.data(indices[it])?.specification?.openedFile }
                                into("job_parameters") {
                                    store.data(indices[it])?.jobParameters?.let {
                                        defaultMapper.encodeToString(ExportedParameters.serializer(), it)
                                    }
                                }
                            }
                        },
                        """
                            with
                                data as (
                                    select
                                        unnest(:application_name::text[]) application_name,
                                        unnest(:application_version::text[]) application_version,
                                        unnest(:time_allocation::int8[]) time_allocation,
                                        unnest(:name::text[]) name,
                                        unnest(:output_folder::text[]) output_folder,
                                        unnest(:current_state::text[]) current_state,
                                        to_timestamp(unnest(:started_at::int8[]) / 1000) started_at,
                                        unnest(:job_id::int8[]) job_id,
                                        unnest(:job_parameters::jsonb[]) job_parameters,
                                        unnest(:opened_file::text[][]) opened_file
                                )
                            insert into app_orchestrator.jobs 
                                (application_name, application_version, time_allocation_millis, name, output_folder, 
                                current_state, started_at, resource, job_parameters, opened_file, last_update) 
                            select 
                                data.application_name, application_version, time_allocation, name, output_folder, 
                                current_state, started_at, job_id, job_parameters, opened_file, now()
                            from data
                            on conflict (resource) do update set 
                                application_name = excluded.application_name,
                                application_version = excluded.application_version,
                                time_allocation_millis = excluded.time_allocation_millis,
                                name = excluded.name,
                                output_folder = excluded.output_folder,
                                current_state = excluded.current_state,
                                started_at = excluded.started_at,
                                job_parameters = excluded.job_parameters,
                                opened_file = excluded.opened_file,
                                last_update = excluded.last_update
                        """
                    )

                    session.sendPreparedStatement(
                        {
                            val names = setParameterList<String>("name")
                            val values = setParameterList<String>("value")
                            val jobIds = setParameterList<Long>("job_id")

                            for (i in 0 until length) {
                                val arrIdx = indices[i]
                                val jobId = store.id[arrIdx]
                                val job = store.data(arrIdx) ?: continue

                                for ((name, value) in job.specification.parameters ?: emptyMap()) {
                                    jobIds.add(jobId)
                                    names.add(name)
                                    values.add(
                                        defaultMapper.encodeToString(
                                            AppParameterValue.serializer(),
                                            value
                                        )
                                    )
                                }
                            }
                        },
                        """
                            with
                                data as (
                                    select
                                        unnest(:job_id::int8[]) job_id,
                                        unnest(:name::text[]) name,
                                        unnest(:value::jsonb[]) value
                                ),
                                deleted_entries as (
                                    delete from app_orchestrator.job_input_parameters p
                                    using data d
                                    where p.job_id = d.job_id
                                )
                            insert into app_orchestrator.job_input_parameters (name, value, job_id) 
                            select name, value, job_id
                            from data
                        """
                    )

                    session.sendPreparedStatement(
                        {
                            val jobIds = setParameterList<Long>("job_id")
                            val resources = setParameterList<String>("resource")

                            for (i in 0 until length) {
                                val arrIdx = indices[i]
                                val jobId = store.id[arrIdx]
                                val job = store.data(arrIdx) ?: continue

                                for (value in job.specification.resources ?: emptyList()) {
                                    jobIds.add(jobId)
                                    resources.add(defaultMapper.encodeToString(AppParameterValue.serializer(), value))
                                }
                            }
                        },
                        """
                            with
                                data as (
                                    select
                                        unnest(:job_id::int8[]) job_id,
                                        unnest(:resource::jsonb[]) resource
                                ),
                                deleted_entries as (
                                    delete from app_orchestrator.job_resources r
                                    using data d
                                    where r.job_id = d.job_id
                                )
                            insert into app_orchestrator.job_resources (resource, job_id) 
                            select d.resource, d.job_id
                            from data d
                        """
                    )
                }
            }
        }
    )

    private val docMapper = DocMapper<InternalJobState, Job>(idCards, productCache, providers, Jobs) {
        val jobFlags = flags as? JobIncludeFlags?

        Job(
            id,
            owner,
            updates?.map { update ->
                val decoded = update.extra?.let {
                    defaultMapper.decodeFromJsonElement<JobUpdate>(it)
                } ?: JobUpdate()

                decoded.also {
                    it.timestamp = update.timestamp
                    it.status = update.status
                }
            } ?: emptyList(),
            data.specification.copy(
                product = productReference,
                parameters = if (jobFlags != null && jobFlags.includeParameters != true) {
                    null
                } else {
                    data.specification.parameters
                },
                resources = if (jobFlags != null && jobFlags.includeParameters != true) {
                    null
                } else {
                    data.specification.resources
                }
            ),
            JobStatus(
                data.state,
                if (jobFlags != null && jobFlags.includeParameters != true) {
                    null
                } else {
                    data.jobParameters
                },
                data.startedAt,
                if (data.startedAt != null && data.specification.timeAllocation != null) {
                    (data.startedAt ?: 0L) + (data.specification.timeAllocation?.toMillis() ?: 0L)
                } else {
                    null
                },
                if (jobFlags != null && jobFlags.includeApplication != true) {
                    null
                } else {
                    data.specification.application.let { (name, version) ->
                        appCache.resolveApplication(name, version)
                    }
                },
                resolvedSupport as ResolvedSupport<Product.Compute, ComputeSupport>?,
                resolvedProduct as Product.Compute?,
                data.allowRestart,
            ),
            createdAt,
            JobOutput(outputFolder = data.outputFolder),
            permissions,
        )
    }

    private val proxy = ProxyToProvider(idCards, documents, docMapper, productCache, providers)

    init {
        documents.initializeBackgroundTasks(backgroundScope)
        documents.initializeEvictionTriggersIfRelevant("app_orchestrator.jobs", "resource")
        documents.initializeEvictionTriggersIfRelevant("app_orchestrator.job_resources", "job_id")
        documents.initializeEvictionTriggersIfRelevant("app_orchestrator.job_input_parameters", "job_id")

        runBlocking {
            initializeActiveJobsIndex()
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

private typealias FeatureValidator = suspend (job: Job, support: ComputeSupport) -> Unit

private inline fun <T : Any> BulkRequest<T>.extractIds(fn: (T) -> String?): LongArray =
    items.mapNotNull { fn(it)?.toLongOrNull() }.toSet().toLongArray()
