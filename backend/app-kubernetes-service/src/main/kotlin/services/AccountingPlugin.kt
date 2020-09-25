package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.kubernetes.services.volcano.volcanoJob
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.k8.KubernetesResources
import dk.sdu.cloud.service.k8.patchResource
import io.ktor.http.*

class AccountingPlugin : JobManagementPlugin {
    override suspend fun JobManagement.onJobComplete(jobId: String, jobFromServer: VolcanoJob) {
        val now = Time.now()
        val lastTs = jobFromServer.lastAccountingTs ?: jobFromServer.jobStartedAt ?: run {
            log.warn("Found no last accounting timestamp for job with id $jobId")
            log.warn("No accounting will be performed for this job")
            return
        }

        account(jobId, now - lastTs, now)
    }

    override suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<VolcanoJob>) {
        val now = Time.now()
        loop@for (jobFromServer in jobBatch) {
            val name = jobFromServer.metadata?.name ?: continue
            val lastTs = jobFromServer.lastAccountingTs ?: jobFromServer.jobStartedAt
            if (lastTs == null) {
                log.warn("Found no last accounting timestamp for job with name '$name'")
                log.warn("No accounting will be performed for this job")
                continue@loop
            }

            account(k8.nameAllocator.jobNameToJobId(name), now - lastTs, now)
        }
    }

    private suspend fun JobManagement.account(jobId: String, timespent: Long, now: Long) {
        if (timespent <= 0L) {
            log.info("No time spent on $jobId ($timespent)")
            log.info("No accounting will be performed")
            return
        }

        val name = k8.nameAllocator.jobIdToJobName(jobId)
        val namespace = k8.nameAllocator.jobIdToNamespace(jobId)

        TODO("Do the call")

        k8.client.patchResource(
            KubernetesResources.volcanoJob.withNameAndNamespace(
                name,
                namespace
            ),
            defaultMapper.writeValueAsString(
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
    }

    private val VolcanoJob.lastAccountingTs: Long? get() {
        return metadata?.annotations?.get(LAST_PERFORMED_AT_ANNOTATION)?.toString()?.toLongOrNull()
    }
    private val VolcanoJob.jobStartedAt: Long? get() {
        return metadata?.annotations?.get(ExpiryPlugin.JOB_START)?.toString()?.toLongOrNull()
    }

    companion object : Loggable {
        override val log = logger()
        const val LAST_PERFORMED_AT_ANNOTATION = "ucloud.dk/lastAccountingTs"
    }
}