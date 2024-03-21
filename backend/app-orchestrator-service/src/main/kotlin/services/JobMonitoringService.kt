package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.backgroundScope
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.db
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.debug
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.distributedLocks
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.fileCollections
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.jobs
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.licenses
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.providers
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.publicIps
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.publicLinks
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.debug.DebugContextType
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.file.orchestrator.api.extractPathMetadata
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.DistributedLock
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class JobMonitoringService {
    suspend fun initialize(useDistributedLocks: Boolean) {
        backgroundScope.launch {
            val lock =
                if (useDistributedLocks) distributedLocks.create("app-orchestrator-watcher", duration = 60_000)
                else null

            while (isActive) {
                val didAcquire = if (lock == null) true else lock.acquire()
                if (didAcquire) {
                    try {
                        runMonitoringLoop(lock)
                    } catch (ex: Throwable) {
                        log.warn("Caught exception while monitoring jobs")
                        log.warn(ex.stackTraceToString())
                    }
                } else {
                    delay(15000 + Random.nextLong(5000))
                }
            }
        }
    }

    private suspend fun CoroutineScope.runMonitoringLoop(lock: DistributedLock?) {
        var nextScan = 0L
        var nextVerify = 0L

        while (isActive) {
            var currentAction = "Nothing"

            try {
                val now = Time.now()
                if (now >= nextScan) {
                    val taskName = "job_watcher"
                    Prometheus.countBackgroundTask(taskName)
                    try {
                        debug.useContext(
                            DebugContextType.BACKGROUND_TASK,
                            "Job monitoring",
                            MessageImportance.TELL_ME_EVERYTHING
                        ) {
                            currentAction = "Listing jobs"
                            jobs.listActiveJobs().chunked(ResourceOutputPool.CAPACITY).forEach { jobIds ->
                                currentAction = "Retrieving job info"
                                val allJobs = jobs.retrieveBulk(
                                    ActorAndProject.System,
                                    jobIds.toLongArray(),
                                    Permission.READ
                                )

                                val jobsByProvider = allJobs
                                    .filter { !it.status.state.isFinal() }
                                    .groupBy { it.specification.product.provider }

                                currentAction = "Launching verify job"
                                backgroundScope.launch {
                                    jobsByProvider.forEach { (provider, localJobs) ->
                                        if (now >= nextVerify) {
                                            try {
                                                providers.call(
                                                    provider,
                                                    ActorAndProject.System,
                                                    { JobsProvider(it).verify },
                                                    bulkRequestOf(localJobs),
                                                    isUserRequest = false
                                                )
                                            } catch (ex: Throwable) {
                                                if (provider != "aau") {
                                                    log.info("Failed to verify block in $provider. Jobs: ${localJobs.map { it.id }}")
                                                }
                                            }

                                            nextVerify = now + (1000L * 60 * 15)
                                        }

                                        val requiresRestart = localJobs.filter {
                                            it.status.state == JobState.SUSPENDED && it.status.allowRestart
                                        }
                                        if (requiresRestart.isNotEmpty()) {
                                            for (job in requiresRestart) {
                                                try {
                                                    jobs.performUnsuspension(listOf(job))
                                                } catch (ex: Throwable) {
                                                    log.info("Failed to restart job: ${job.id}\n  Reason: ${ex.message}")
                                                }
                                            }
                                        }
                                    }
                                }

                                db.withSession { session ->
                                    for (job in allJobs) {
                                        // NOTE(Dan): Suspended jobs are re-verified when they are unsuspended. We don't verify them
                                        // right now.
                                        if (job.status.state == JobState.SUSPENDED) continue

                                        currentAction = "Checking file permissions"
                                        log.trace("Checking permissions of ${job.id}")
                                        val (hasPermissions, files) = hasPermissionsForExistingMounts(job, session)
                                        if (!hasPermissions) {
                                            currentAction = "Terminating jobs (file perms)"
                                            terminateAndUpdateJob(job, files)
                                        }

                                        currentAction = "Checking for resources"
                                        val (resourceAvailable, resource) = hasResources(job, session)
                                        if (!resourceAvailable) {
                                            currentAction = "Terminating jobs (resource perms)"
                                            terminateAndUpdateJob(job, resource)
                                        }
                                    }
                                }
                            }
                            nextScan = Time.now() + TIME_BETWEEN_SCANS / 2
                        }
                    } finally {
                        Prometheus.measureBackgroundDuration(taskName, Time.now() - now)
                    }
                }

                if (lock != null && !lock.renew(90_000)) {
                    log.warn("Lock was lost. We are no longer the master. Did update take too long?")
                    break
                }
                delay(1000)
            } catch (ex: Throwable) {
                log.warn("Caught exception while monitoring jobs ($currentAction)\n${ex.toReadableStacktrace()}")
            }
        }
    }

    private suspend fun terminateAndUpdateJob(
        job: Job,
        lostPermissionTo: List<String>?
    ) {
        try {
            log.trace("Permission check failed for ${job.id}")
            jobs.terminate(
                ActorAndProject.System,
                bulkRequestOf(FindByStringId(job.id))
            )

            jobs.addUpdate(
                ActorAndProject.System,
                bulkRequestOf(
                    ResourceUpdateAndId(
                        job.id,
                        JobUpdate(
                            status = "System initiated cancel: " +
                                    "You no longer have permissions to use '${lostPermissionTo?.joinToString()}'"

                        )
                    )
                )
            )
        } catch (ex: Throwable) {
            log.info("Could not terminate job: ${job.id} ${ex.toReadableStacktrace()}")
        }
    }

    data class HasResource(val availability: Boolean, val resourceToMessage: List<String>?)

    private suspend fun hasResources(
        job: Job,
        session: DBContext
    ): HasResource {
        val ingress = job.ingressPoints.map { it.id }.toSet()
        val networkIPs = job.networks.map { it.id }.toSet()
        val allLicenses = job.licences.map { it.id }.toSet()

        if (ingress.isNotEmpty()) {
            val available = publicLinks.retrieveBulk(
                ActorAndProject(Actor.SystemOnBehalfOfUser(job.owner.createdBy), job.owner.project),
                ingress,
                listOf(Permission.READ),
                requireAll = false,
                ctx = session
            )
            if (available.size != ingress.size) {
                val ingressAvailable = available.map { it.id }
                val lostPermissionTo = ingress.filterNot { ingressAvailable.contains(it) }

                val lostIngresses =
                    available.filter { lostPermissionTo.contains(it.id) }.map { it.specification.product.id }
                log.debug("Failed access to ingress (${available.size} != ${ingress.size})")
                return HasResource(false, lostIngresses)
            }

        }

        if (networkIPs.isNotEmpty()) {
            val available = publicIps.retrieveBulk(
                ActorAndProject(Actor.SystemOnBehalfOfUser(job.owner.createdBy), job.owner.project),
                networkIPs,
                listOf(Permission.READ),
                requireAll = false,
                ctx = session
            )
            if (available.size != networkIPs.size) {
                val networkIPsAvailable = available.map { it.id }
                val lostPermissionTo = ingress.filterNot { networkIPsAvailable.contains(it) }

                val lostNetworkIPs =
                    available.filter { lostPermissionTo.contains(it.id) }.map { it.specification.product.id }
                log.debug("Failed access to IP (${available.size} != ${networkIPs.size})")
                return HasResource(false, lostNetworkIPs)
            }
        }

        if (allLicenses.isNotEmpty()) {
            val available = licenses.retrieveBulk(
                ActorAndProject(Actor.SystemOnBehalfOfUser(job.owner.createdBy), job.owner.project),
                allLicenses,
                listOf(Permission.READ),
                requireAll = false,
                ctx = session
            )
            if (available.size != allLicenses.size) {
                val licensesAvailable = available.map { it.id }
                val lostPermissionTo = allLicenses.filterNot { licensesAvailable.contains(it) }

                val lostLicenses =
                    available.filter { lostPermissionTo.contains(it.id) }.map { it.specification.product.id }
                log.debug("Failed access to license (${available.size} != ${allLicenses.size})")
                return HasResource(false, lostLicenses)
            }
        }

        return HasResource(true, null)
    }

    data class HasPermissionForExistingMounts(
        val hasPermission: Boolean,
        val files: List<String>?
    )

    private suspend fun hasPermissionsForExistingMounts(
        job: Job,
        session: DBContext
    ): HasPermissionForExistingMounts {
        val outputFolder = run {
            val path = job.output?.outputFolder
            if (path != null) {
                listOf(AppParameterValue.File(path, false))
            } else {
                emptyList()
            }
        }
        val parameters = job.specification.parameters ?: return HasPermissionForExistingMounts(false, listOf("/"))
        val resources = job.specification.resources ?: return HasPermissionForExistingMounts(false, listOf("/"))
        val allFiles =
            parameters.values.filterIsInstance<AppParameterValue.File>() +
                    resources.filterIsInstance<AppParameterValue.File>() +
                    outputFolder

        log.trace("ALL FILES: ${allFiles.map { extractPathMetadata(it.path).collection }}")

        val readOnlyFiles = allFiles.filter { it.readOnly }.map { extractPathMetadata(it.path).collection }.toSet()
        val readWriteFiles = allFiles.filter { !it.readOnly }.map { extractPathMetadata(it.path).collection }.toSet()

        log.trace("readonly: ${readOnlyFiles.size}, write: ${readWriteFiles.size}")

        val permissionToRead = checkFiles(session, true, readOnlyFiles, job)
        if (!permissionToRead.hasPermission) {
            return permissionToRead
        }
        val permissionToWrite = checkFiles(session, false, readWriteFiles, job)
        if (!permissionToWrite.hasPermission) {
            return permissionToWrite
        }
        return HasPermissionForExistingMounts(true, null)
    }

    private suspend fun checkFiles(
        session: DBContext,
        readOnly: Boolean,
        files: Set<String>,
        job: Job
    ): HasPermissionForExistingMounts {
        if (files.isEmpty()) return HasPermissionForExistingMounts(true, null)

        val canAccess = fileCollections.retrieveBulk(
            ActorAndProject(Actor.SystemOnBehalfOfUser(job.owner.createdBy), job.owner.project),
            files,
            if (readOnly) listOf(Permission.READ) else listOf(Permission.EDIT),
            requireAll = false,
            ctx = session
        )
        log.trace(canAccess.joinToString())
        log.trace("${canAccess.size} vs ${files.size}")
        if (canAccess.size != files.size) {
            val accessibleFiles = canAccess.map { it.id }
            val lostPermissionTo = files.filterNot { accessibleFiles.contains(it) }
            return HasPermissionForExistingMounts(false, lostPermissionTo)
        }
        return HasPermissionForExistingMounts(true, null)
    }

    companion object : Loggable {
        override val log = logger()
        private const val TIME_BETWEEN_SCANS = 10_000
    }
}
