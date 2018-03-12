package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.api.AppRequest
import dk.sdu.cloud.app.api.JobStatus
import dk.sdu.cloud.app.api.JobWithStatus
import dk.sdu.cloud.app.api.JobWithStatusAndInvocation
import dk.sdu.cloud.service.KafkaRequest
import dk.sdu.cloud.service.RequestHeader
import dk.sdu.cloud.service.MappedEventProducer
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*

class JobService(
    private val dao: JobsDAO,
    private val jobRequestProducer: MappedEventProducer<String, KafkaRequest<AppRequest>>
) {
    fun recentJobs(who: DecodedJWT): List<JobWithStatus> = transaction { dao.findAllJobsWithStatus(who.subject) }

    fun findJob(id: String, who: DecodedJWT): JobWithStatusAndInvocation? =
        transaction { dao.findJobWithStatusById(id)?.takeIf { it.jobInfo.owner == who.subject } }

    fun findJobWithStatusBySlurmId(slurmId: Long): JobWithStatusAndInvocation? {
        return transaction {
            dao.findJobWithStatusBySlurmId(slurmId)
        }
    }


    fun findSystemIdFromSlurmId(slurmId: Long): String? {
        return transaction {
            dao.findSystemIdFromSlurmId(slurmId)
        }
    }

    fun createJob(
        systemId: String,
        owner: String,
        slurmId: Long,
        appName: String,
        appVersion: String,
        workingDirectory: String,
        jobDirectory: String,
        parameters: Map<String, Any>,
        createdAt: Long,
        initialStatus: JobStatus = JobStatus.PENDING
    ) {
        transaction {
            dao.createJob(
                systemId,
                owner,
                slurmId,
                appName,
                appVersion,
                workingDirectory,
                jobDirectory,
                parameters,
                createdAt,
                initialStatus
            )
        }
    }

    fun updateJobBySystemId(systemId: String, newStatus: JobStatus, modifiedAt: Long): Boolean {
        return transaction { dao.updateJobBySystemId(systemId, newStatus, modifiedAt) }
    }

    suspend fun startJob(req: AppRequest.Start, principal: DecodedJWT): String {
        val uuid = UUID.randomUUID().toString()
        jobRequestProducer.emit(
            KafkaRequest(
                RequestHeader(
                    uuid,
                    principal.token
                ),
                req
            )
        )
        return uuid
    }

    companion object {
        private val log = LoggerFactory.getLogger(JobService::class.java)
    }
}