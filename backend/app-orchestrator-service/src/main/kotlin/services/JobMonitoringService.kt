package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.debug.DebugContextType
import dk.sdu.cloud.debug.DebugSystem
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.file.orchestrator.api.extractPathMetadata
import dk.sdu.cloud.file.orchestrator.service.FileCollectionService
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.DistributedLock
import dk.sdu.cloud.service.DistributedLockFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class JobMonitoringService(
    private val debug: DebugSystem,
    private val scope: BackgroundScope,
    private val distributedLocks: DistributedLockFactory,
    private val db: AsyncDBSessionFactory,
    private val jobOrchestrator: JobOrchestrator,
    private val providers: Providers<ComputeCommunication>,
    private val fileCollectionService: FileCollectionService,
    private val ingressService: IngressService,
    private val networkIPService: NetworkIPService,
    private val licenseService: LicenseService
) {
    suspend fun initialize(useDistributedLocks: Boolean) {
        scope.launch {
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

        while (isActive) {
            val now = Time.now()
            if (now >= nextScan) {
                debug.useContext(DebugContextType.BACKGROUND_TASK, "Job monitoring", MessageImportance.TELL_ME_EVERYTHING) {
                    db.withSession { session ->
                        val jobs = session
                            .sendPreparedStatement(
                                {
                                    setParameter(
                                        "nonFinalStates",
                                        JobState.values().filter { !it.isFinal() }.map { it.name }
                                    )
                                },
                                """
                                update app_orchestrator.jobs
                                set last_scan = now()
                                where
                                    resource in (
                                        select resource
                                        from
                                            app_orchestrator.jobs j join
                                            provider.resource r on r.id = j.resource
                                        where
                                            current_state = some(:nonFinalStates::text[]) and
                                            last_scan <= now() - '30 seconds'::interval and
                                            r.confirmed_by_provider = true
                                        limit 100
                                    )
                                returning resource;
                            """,
                                "job monitoring loop"
                            )
                            .rows
                            .map { it.getLong(0)!! }
                            .toSet()
                            .let { set ->
                                jobOrchestrator.retrieveBulk(
                                    actorAndProject = ActorAndProject(Actor.System, null),
                                    set.map { id -> id.toString() },
                                    permissionOneOf = listOf(Permission.READ)
                                )
                            }

                        val jobsByProvider = jobs.map { it }.groupBy { it.specification.product.provider }
                        scope.launch {
                            jobsByProvider.forEach { (provider, jobs) ->
                                val comm = providers.prepareCommunication(provider)
                                val resp = comm.api.verify.call(
                                    bulkRequestOf(jobs),
                                    comm.client
                                )

                                if (!resp.statusCode.isSuccess()) {
                                    log.info("Failed to verify block in $provider. Jobs: ${jobs.map { it.id }}")
                                }

                                val requiresRestart = jobs.filter {
                                    it.status.state == JobState.SUSPENDED && it.status.allowRestart == true
                                }
                                if (requiresRestart.isNotEmpty()) {
                                    for (job in requiresRestart) {
                                        try {
                                            jobOrchestrator.performUnsuspension(listOf(job))
                                        } catch (ex: Throwable) {
                                            log.info("Failed to restart job: ${job.id}\n  Reason: ${ex.message}")
                                        }
                                    }
                                }
                            }
                        }

                        for (job in jobs) {
                            // NOTE(Dan): Suspended jobs are re-verified when they are unsuspended. We don't verify them
                            // right now.
                            if (job.status.state == JobState.SUSPENDED) continue

                            log.trace("Checking permissions of ${job.id}")
                            val (hasPermissions, files) = hasPermissionsForExistingMounts(job, session)
                            if (!hasPermissions) {
                                terminateAndUpdateJob(job, files)
                            }

                            val (resourceAvailable, resource) = hasResources(job, session)
                            if (!resourceAvailable) {
                                terminateAndUpdateJob(job, resource)
                            }
                        }
                    }
                }
                nextScan = Time.now() + TIME_BETWEEN_SCANS / 2
            }

            if (lock != null && !lock.renew(90_000)) {
                log.warn("Lock was lost. We are no longer the master. Did update take too long?")
                break
            }
            delay(1000)
        }
    }

    private suspend fun terminateAndUpdateJob(
        job: Job,
        lostPermissionTo: List<String>?
    ) {
        log.trace("Permission check failed for ${job.id}")
        jobOrchestrator.terminate(
            ActorAndProject(Actor.System, null),
            bulkRequestOf(FindByStringId(job.id))
        )

        jobOrchestrator.addUpdate(
            ActorAndProject(Actor.System, null),
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
    }

    data class HasResource(val availability: Boolean, val resourceToMessage: List<String>?)

    private suspend fun hasResources(
        job: Job,
        session: DBContext
    ): HasResource {
        val ingress = job.ingressPoints.map { it.id }.toSet()
        val networkIPs = job.networks.map { it.id }.toSet()
        val licenses = job.licences.map { it.id }.toSet()

        if (ingress.isNotEmpty()) {
            val available = ingressService.retrieveBulk(
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
            val available = networkIPService.retrieveBulk(
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

        if (licenses.isNotEmpty()) {
            val available = licenseService.retrieveBulk(
                ActorAndProject(Actor.SystemOnBehalfOfUser(job.owner.createdBy), job.owner.project),
                licenses,
                listOf(Permission.READ),
                requireAll = false,
                ctx = session
            )
            if (available.size != licenses.size) {
                val licensesAvailable = available.map { it.id }
                val lostPermissionTo = licenses.filterNot { licensesAvailable.contains(it) }

                val lostLicenses =
                    available.filter { lostPermissionTo.contains(it.id) }.map { it.specification.product.id }
                log.debug("Failed access to license (${available.size} != ${licenses.size})")
                return HasResource(false, lostLicenses)
            }
        }

        return HasResource(true, null)
    }

    data class HasPermissionForExistingMounts(
        val hasPermission: Boolean, val files: List<String>?
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

        val canAccess = fileCollectionService.retrieveBulk(
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
