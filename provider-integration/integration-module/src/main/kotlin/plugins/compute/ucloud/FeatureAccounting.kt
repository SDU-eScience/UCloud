package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object FeatureAccounting : JobFeature, Loggable {
    override val log = logger()
    const val LAST_PERFORMED_AT_ANNOTATION = "ucloud.dk/lastAccountingTs"
    const val TIME_BETWEEN_ACCOUNTING = 1000L * 60 * 15

    override suspend fun JobManagement.onJobComplete(jobId: String, jobFromServer: VolcanoJob) {
        log.trace("Accounting because job has completed!")
        val now = Time.now()
        val lastTs = jobFromServer.lastAccountingTs ?: jobFromServer.jobStartedAt ?: run {
            log.warn("Found no last accounting timestamp for job with id $jobId")
            log.info("Assuming that $jobId was a very fast job")
            now - 1000L
        }

        account(jobId, jobFromServer, lastTs, now)
    }

    override suspend fun JobManagement.onJobStart(jobId: String, jobFromServer: VolcanoJob) {
        val now = System.currentTimeMillis()
        account(jobId, jobFromServer, now, now)
    }

    override suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<VolcanoJob>) {
        val now = Time.now()
        for (jobFromServer in jobBatch) {
            val name = jobFromServer.metadata?.name ?: continue
            val lastTs = jobFromServer.lastAccountingTs ?: jobFromServer.jobStartedAt
            if (lastTs == null) {
                log.trace("Found no last accounting timestamp for job with name '$name' (Job might not have started yet)")
                continue
            }

            if (now - lastTs < TIME_BETWEEN_ACCOUNTING) continue

            account(k8.nameAllocator.jobNameToJobId(name), jobFromServer, lastTs, now)
        }
    }

    private suspend fun JobManagement.account(jobId: String, jobFromServer: VolcanoJob, lastTs: Long, now: Long) {
        val timespent = now - lastTs
        if (timespent < 0L) {
            log.info("No time spent on $jobId ($timespent)")
            log.info("No accounting will be performed")
            return
        }

        val name = k8.nameAllocator.jobIdToJobName(jobId)
        val namespace = k8.nameAllocator.namespace()

        if (timespent > 0L) {
            val replicas = jobFromServer.spec?.tasks?.getOrNull(0)?.replicas?.toLong() ?: 1L
            val virtualCpus = run {
                val cpuString = jobFromServer.spec?.tasks?.getOrNull(0)?.template?.spec?.containers?.getOrNull(0)
                    ?.resources?.limits?.get("cpu")?.jsonPrimitive?.contentOrNull ?: "1000m"

                kotlin.math.max(1, (cpuString.removeSuffix("m").toIntOrNull() ?: 1000) / 1000)
            }

            k8.scope.launch {
                // NOTE(Dan): Pushing this into a separate coroutine since we have seen quite bad performance from the
                // accounting system. Doing this, we run the risk of things happening in a surprising order. For
                // example, we might perform accounting of job completion and then perform accounting of the job. We
                // choose to accept this risk and not attempt to handle it. Instead, we rely solely on the duplicate
                // transaction detection to save us from charging too much. We live with the risk of charging too
                // little.
                val insufficientFunds = JobsControl.chargeCredits.call(
                    bulkRequestOf(
                        ResourceChargeCredits(
                            jobId,
                            jobId + "_" + lastTs.toString(),
                            replicas * virtualCpus,
                            kotlin.math.ceil(timespent / (1000 * 60.0)).toLong()
                        )
                    ),
                    k8.serviceClient
                ).orThrow().insufficientFunds.isNotEmpty()

                if (insufficientFunds) {
                    k8.client.deleteResource(KubernetesResources.volcanoJob.withNameAndNamespace(name, namespace))
                }

                try {
                    log.trace("Attaching accounting timestamp: $jobId $now")
                    k8.client.patchResource(
                        KubernetesResources.volcanoJob.withNameAndNamespace(
                            name,
                            namespace
                        ),
                        defaultMapper.encodeToString(
                            ListSerializer(JsonObject.serializer()),
                            // http://jsonpatch.com/
                            listOf(
                                JsonObject(
                                    mapOf(
                                        "op" to JsonPrimitive("add"),
                                        // https://tools.ietf.org/html/rfc6901#section-3
                                        "path" to JsonPrimitive(
                                            "/metadata/annotations/${LAST_PERFORMED_AT_ANNOTATION.replace("/", "~1")}"
                                        ),
                                        "value" to JsonPrimitive(now.toString())
                                    )
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
        }

        
    }

    private val VolcanoJob.lastAccountingTs: Long?
        get() {
            return (metadata?.annotations?.get(LAST_PERFORMED_AT_ANNOTATION) as? JsonPrimitive)?.content?.toLongOrNull()
        }
    private val VolcanoJob.jobStartedAt: Long?
        get() {
            return (metadata?.annotations?.get(FeatureExpiry.JOB_START) as? JsonPrimitive)?.content?.toLongOrNull()
        }
}
