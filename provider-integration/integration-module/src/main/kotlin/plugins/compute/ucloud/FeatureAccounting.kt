package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import io.ktor.http.*
import java.util.HashMap
import kotlin.math.max

private class JobChargedCache() {
    var lastCheck: Long

    init {
        lastCheck = Time.now()
    }

    data class ChargeState(
        val charged: Boolean,
        val insertTime: Long
    )

    private var jobsCharged = HashMap<String, ChargeState>()
    fun insertIntoCache(jobId: String) {
        cleanup()
        jobsCharged[jobId] = ChargeState(true, Time.now())
    }

    fun getJobChargedStatus(jobId: String): Boolean {
        cleanup()
        val chargeState = jobsCharged[jobId] ?: return false
        return chargeState.charged
    }

    fun cleanup() {
        val now = Time.now()
        if (now - lastCheck > 10_000) {
            val fresh = jobsCharged.filter { now - it.value.insertTime > 10_000 }
            jobsCharged.clear()
            fresh.forEach {
                jobsCharged[it.key] = it.value
            }
            lastCheck = Time.now()
        }

    }
}

object FeatureAccounting : JobFeature, Loggable {
    override val log = logger()
    private val jobChargedCache = JobChargedCache()
    const val LAST_PERFORMED_AT_ANNOTATION = "ucloud.dk/lastAccountingTs"
    const val TIME_BETWEEN_ACCOUNTING = 1000L * 60 * 15

    override suspend fun JobManagement.onJobComplete(
        rootJob: Container,
        children: List<Container>
    ) {
        log.trace("Accounting because job has completed!")
        val now = Time.now()
        val lastTs = rootJob.lastAccountingTs ?: rootJob.jobStartedAt ?: run {
            log.warn("Found no last accounting timestamp for job with id ${rootJob.jobId}")
            log.info("Assuming that ${rootJob.jobId} was a very fast job")
            now - 1000L
        }
        val isCharged = jobChargedCache.getJobChargedStatus(rootJob.jobId)
        if (!isCharged) {
            jobChargedCache.insertIntoCache(rootJob.jobId)
            account(rootJob, children, lastTs, now)
        }
    }

    override suspend fun JobManagement.onJobStart(rootJob: Container, children: List<Container>) {
        val now = System.currentTimeMillis()
        val isCharged = jobChargedCache.getJobChargedStatus(rootJob.jobId)
        if (!isCharged) {
            account(rootJob, children, now, now)
        }
    }

    override suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<Container>) {
        val now = Time.now()
        val jobsById = jobBatch.groupBy { it.jobId }
        for ((jobId, children) in jobsById) {
            val rootJob = children.find { it.rank == 0 } ?: continue
            val lastTs = rootJob.lastAccountingTs ?: rootJob.jobStartedAt
            if (lastTs == null) {
                log.trace("Found no last accounting timestamp for job with name '${jobId}' (Job might not have started yet)")
                continue
            }
            if (now - lastTs < TIME_BETWEEN_ACCOUNTING) continue
            val isCharged = jobChargedCache.getJobChargedStatus(rootJob.jobId)
            if (!isCharged) {
                account(rootJob, children, lastTs, now)
            }
        }
    }

    private suspend fun JobManagement.account(rootJob: Container, children: Collection<Container>, lastTs: Long, now: Long) {
        val timespent = now - lastTs
        if (timespent < 0L) {
            log.info("No time spent on ${rootJob.jobId} ($timespent)")
            log.info("No accounting will be performed")
            return
        }
        if (timespent > 0L) {
            val replicas = children.size
            val virtualCpus = run {
                max(1, rootJob.vCpuMillis / 1000)
            }

            val insufficientFunds = JobsControl.chargeCredits.call(
                bulkRequestOf(
                    ResourceChargeCredits(
                        rootJob.jobId,
                        rootJob.jobId + "_" + lastTs.toString(),
                        replicas * virtualCpus.toLong(),
                        max(1, kotlin.math.round(timespent / (1000 * 60.0)).toLong())
                    )
                ),
                k8.serviceClient
            ).orThrow().insufficientFunds.isNotEmpty()

            if (insufficientFunds) {
                k8.addStatus(
                    rootJob.jobId,
                    "Terminating job because of insufficient funds"
                )
                children.forEach { it.cancel() }
            }
        }

        try {
            rootJob.upsertAnnotation(
                LAST_PERFORMED_AT_ANNOTATION,
                now.toString()
            )
        } catch (ex: KubernetesException) {
            if (ex.statusCode == HttpStatusCode.NotFound) {
                // Ignored
            } else {
                throw ex
            }
        }
    }

    private val Container.lastAccountingTs: Long?
        get() {
            return annotations[LAST_PERFORMED_AT_ANNOTATION]?.replace("\"", "")?.trim()?.toLongOrNull()
        }
    private val Container.jobStartedAt: Long?
        get() {
            return annotations[FeatureExpiry.JOB_START]?.replace("\"", "")?.trim()?.toLongOrNull()
        }
}
