package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.app.orchestrator.api.JobDataIncludeFlags
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobsControlUpdateRequestItem
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.*
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
    private val verificationService: JobVerificationService,
    private val jobOrchestrator: JobOrchestrator,
    private val providers: Providers,
    private val queryService: JobQueryService,
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
            val now = Time.now() / 1000
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
                                update jobs
                                set last_scan = to_timestamp(cast(:now as bigint))
                                where
                                    id in (
                                        select id from jobs
                                        where
                                            current_state in (select unnest(:nonFinalStates::text[])) and
                                            last_scan <= to_timestamp(cast(:before as bigint))
                                        limit 100
                                    )
                                returning id;
                            """
                        )
                        .rows
                        .map { it.getString(0)!! }
                        .toSet()
                        .let {
                            queryService
                                .retrievePrivileged(session, it, JobDataIncludeFlags(includeParameters = true))
                                .values
                        }

                    val jobsByProvider = jobs.map { it.job }.groupBy { it.specification.product.provider }
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
                        }
                    }

                    for (jobWithToken in jobs) {
                        log.debug("Checking permissions of ${jobWithToken.job.id}")
                        val (hasPermissions, file) = verificationService.hasPermissionsForExistingMounts(
                            jobWithToken
                        )

                        if (!hasPermissions) {
                            log.debug("Permission check failed for ${jobWithToken.job.id}")
                            jobOrchestrator.cancel(
                                bulkRequestOf(FindByStringId(jobWithToken.job.id)),
                                Actor.System
                            )

                            jobOrchestrator.updateState(
                                bulkRequestOf(
                                    JobsControlUpdateRequestItem(
                                        jobWithToken.job.id,
                                        status = "System initiated cancel: " +
                                            "You no longer have permissions to use '${file}'"
                                    )
                                ),
                                Actor.System
                            )
                        }
                    }
                }

                nextScan = (Time.now() + 30_000) / 1000
            }

            if (!lock.renew(90_000)) {
                log.warn("Lock was lost. We are no longer the master. Did update take too long?")
                break
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
        private const val TIME_BETWEEN_SCANS = 60 * 15
    }
}