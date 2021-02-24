package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.kubernetes.services.volcano.volcanoJob
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.api.JobsControlChargeCreditsRequest
import dk.sdu.cloud.app.orchestrator.api.JobsControlChargeCreditsRequestItem
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.k8.KubernetesException
import dk.sdu.cloud.service.k8.KubernetesResources
import dk.sdu.cloud.service.k8.patchResource
import io.ktor.http.*
import kotlinx.serialization.encodeToString

object AccountingPlugin : JobManagementPlugin, Loggable {
    override val log = logger()
    const val LAST_PERFORMED_AT_ANNOTATION = "ucloud.dk/lastAccountingTs"

    override suspend fun JobManagement.onJobComplete(jobId: String, jobFromServer: VolcanoJob) {
        log.info("Accounting because job has completed!")
        val now = Time.now()
        val lastTs = jobFromServer.lastAccountingTs ?: jobFromServer.jobStartedAt ?: run {
            log.warn("Found no last accounting timestamp for job with id $jobId")
            log.info("Assuming that $jobId was a very fast job")
            now - 1000L
        }

        account(jobId, lastTs, now)
    }

    override suspend fun JobManagement.onJobStart(jobId: String, jobFromServer: VolcanoJob) {
        val now = System.currentTimeMillis()
        account(jobId, now, now)
    }

    override suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<VolcanoJob>) {
        val now = Time.now()
        loop@ for (jobFromServer in jobBatch) {
            val name = jobFromServer.metadata?.name ?: continue
            val lastTs = jobFromServer.lastAccountingTs ?: jobFromServer.jobStartedAt
            if (lastTs == null) {
                log.info("Found no last accounting timestamp for job with name '$name' (Job might not have started yet)")
                continue@loop
            }

            account(k8.nameAllocator.jobNameToJobId(name), lastTs, now)
        }
    }

    private suspend fun JobManagement.account(jobId: String, lastTs: Long, now: Long) {
        val timespent = now - lastTs
        if (timespent < 0L) {
            log.info("No time spent on $jobId ($timespent)")
            log.info("No accounting will be performed")
            return
        }

        val name = k8.nameAllocator.jobIdToJobName(jobId)
        val namespace = k8.nameAllocator.jobIdToNamespace(jobId)

        if (timespent > 0L) {
            JobsControl.chargeCredits.call(
                bulkRequestOf(
                    JobsControlChargeCreditsRequestItem(
                        jobId,
                        lastTs.toString(),
                        SimpleDuration.fromMillis(timespent)
                    )
                ),
                k8.serviceClient
            ).orThrow()
        }

        try {
            log.debug("Attaching accounting timestamp: $jobId $now")
            k8.client.patchResource(
                KubernetesResources.volcanoJob.withNameAndNamespace(
                    name,
                    namespace
                ),
                defaultMapper.encodeToString(
                    // http://jsonpatch.com/
                    listOf(
                        mapOf(
                            "op" to "add",
                            // https://tools.ietf.org/html/rfc6901#section-3
                            "path" to "/metadata/annotations/${LAST_PERFORMED_AT_ANNOTATION.replace("/", "~1")}",
                            "value" to now.toString()
                        )
                    )
                ),
                ContentType("application", "json-patch+json")
            )
        } catch (ex: KubernetesException) {
            if (ex.statusCode == HttpStatusCode.NotFound) {
                // Ignored
            } else {
                throw ex
            }
        }
    }

    private val VolcanoJob.lastAccountingTs: Long?
        get() {
            return metadata?.annotations?.get(LAST_PERFORMED_AT_ANNOTATION)?.toString()?.toLongOrNull()
        }
    private val VolcanoJob.jobStartedAt: Long?
        get() {
            return metadata?.annotations?.get(ExpiryPlugin.JOB_START)?.toString()?.toLongOrNull()
        }
}