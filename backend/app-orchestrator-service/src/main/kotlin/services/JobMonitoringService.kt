package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.ComputeVerifyJobsRequest
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobStateChange
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.DistributedLock
import dk.sdu.cloud.service.DistributedLockFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.random.Random

class JobMonitoringService(
    private val db: DBContext,
    private val scope: BackgroundScope,
    private val distributedLocks: DistributedLockFactory,
    private val applicationService: ApplicationService,
    private val verificationService: JobVerificationService,
    private val jobOrchestrator: JobOrchestrator,
    private val computationBackendService: ComputationBackendService,
    private val serviceClient: AuthenticatedClient,
) {
    suspend fun initialize() {
        scope.launch {
            val lock = distributedLocks.create("app-orchestrator-watcher", duration = 60_000)
            while (isActive) {
                val didAcquire = lock.acquire()
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

    private suspend fun CoroutineScope.runMonitoringLoop(lock: DistributedLock) {
        var nextScan = 0L
        while (isActive) {
            val now = Time.now()
            if (now >= nextScan) {
                db.withSession { session ->
                    val jobs = session
                        .sendPreparedStatement(
                            {
                                setParameter(
                                    "nonFinalStates",
                                    JobState.values().filter { !it.isFinal() }.map { it.name }
                                )
                                setParameter("before", now - TIME_BETWEEN_SCANS)
                                setParameter("now", now)
                            },
                            """
                                update job_information
                                set last_scan = to_timestamp(cast(:now as bigint))
                                where
                                    system_id in (
                                        select system_id from job_information
                                        where
                                            state in (select unnest(:nonFinalStates::text[])) and
                                            last_scan <= to_timestamp(cast(:before as bigint))
                                        limit 100
                                    )
                                returning *;
                            """
                        )
                        .rows
                        .mapNotNull { it.toVerifiedJob(false, applicationService) }

                    val jobsByBackend = jobs.map { it.job }.groupBy { it.backend }
                    scope.launch {
                        jobsByBackend.forEach { (backend, jobs) ->
                            val service = computationBackendService.getAndVerifyByName(backend)
                            val resp = service.verifyJobs.call(ComputeVerifyJobsRequest(jobs), serviceClient)
                            if (!resp.statusCode.isSuccess()) {
                                log.info("Failed to verify block in $backend. Jobs: ${jobs.map { it.id }}")
                            }
                        }
                    }

                    for (jobWithToken in jobs) {
                        log.debug("Checking permissions of ${jobWithToken.job.id}")
                        val (hasPermissions, file) = verificationService.hasPermissionsForExistingMounts(
                            jobWithToken
                        )

                        if (!hasPermissions) {
                            jobOrchestrator.handleProposedStateChange(
                                JobStateChange(jobWithToken.job.id, JobState.CANCELING),
                                "System initiated cancel: You no longer have permissions to use '${file}'"
                            )
                        }
                    }
                }

                nextScan = Time.now() + 30_000
            }

            if (!lock.renew(90_000)) {
                log.warn("Lock was lost. We are no longer the master. Did update take too long?")
                break
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
        private const val TIME_BETWEEN_SCANS = 1_000 * 60 * 15
    }
}