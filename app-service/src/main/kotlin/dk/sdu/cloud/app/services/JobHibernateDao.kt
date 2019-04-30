package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.*
import io.ktor.http.HttpStatusCode
import org.hibernate.ScrollMode
import org.hibernate.annotations.NaturalId
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.Type
import org.slf4j.Logger
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "job_information")
data class JobInformationEntity(
    @Id
    @NaturalId
    val systemId: String,

    var owner: String,

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "name", column = Column(name = "application_name")),
        AttributeOverride(name = "version", column = Column(name = "application_version"))
    )
    var application: EmbeddedNameAndVersion,

    var status: String,

    @Enumerated(EnumType.STRING)
    var state: JobState,

    var nodes: Int,

    var tasksPerNode: Int,

    @Type(
        type = JSONB_MAP_TYPE,
        parameters = [
            Parameter(
                name = JSONB_MAP_PARAM_KEY_TYPE,
                value = "java.lang.String"
            ),
            Parameter(
                name = JSONB_MAP_PARAM_VALUE_TYPE,
                value = "dk.sdu.cloud.app.api.ParsedApplicationParameter"
            )
        ]
    )
    var parameters: Map<String, ParsedApplicationParameter?>,

    @Type(
        type = JSONB_LIST_TYPE,
        parameters = [
            Parameter(
                name = JSONB_LIST_PARAM_TYPE,
                value = "dk.sdu.cloud.app.api.ValidatedFileForUpload"
            )
        ]
    )
    var files: List<ValidatedFileForUpload>,

    var maxTimeHours: Int,

    var maxTimeMinutes: Int,

    var maxTimeSeconds: Int,

    var backendName: String,

    @Column(length = 4096)
    var accessToken: String,

    @Column(length = 1024)
    var archiveInCollection: String,

    @Column(length = 1024)
    var workspace: String?,

    override var createdAt: Date,

    override var modifiedAt: Date
) : WithTimestamps {

    companion object : HibernateEntity<JobInformationEntity>, WithId<String>
}

class JobHibernateDao(
    private val appDao: ApplicationDAO<HibernateSession>,
    private val toolDao: ToolDAO<HibernateSession>,
    private val tokenValidation: TokenValidation<*>
) : JobDao<HibernateSession> {
    override fun create(session: HibernateSession, jobWithToken: VerifiedJobWithAccessToken) {
        val (job, token) = jobWithToken

        val entity = JobInformationEntity(
            job.id,
            job.owner,
            job.application.metadata.toEmbedded(),
            "Verified",
            job.currentState,
            job.nodes,
            job.tasksPerNode,
            job.jobInput.asMap(),
            job.files,
            job.maxTime.hours,
            job.maxTime.minutes,
            job.maxTime.seconds,
            job.backend,
            token,
            job.archiveInCollection,
            job.workspace,
            Date(System.currentTimeMillis()),
            Date(System.currentTimeMillis())
        )

        session.save(entity)
    }

    override fun updateStatus(session: HibernateSession, systemId: String, status: String) {
        session.updateCriteria<JobInformationEntity>(
            where = { entity[JobInformationEntity::systemId] equal systemId },
            setProperties = {
                criteria.set(entity[JobInformationEntity::status], status)
                criteria.set(entity[JobInformationEntity::modifiedAt], Date(System.currentTimeMillis()))
            }
        ).executeUpdate().takeIf { it == 1 } ?: throw JobException.NotFound("job: $systemId")
    }

    override fun updateStateAndStatus(session: HibernateSession, systemId: String, state: JobState, status: String?) {
        session.updateCriteria<JobInformationEntity>(
            where = { entity[JobInformationEntity::systemId] equal systemId },
            setProperties = {
                criteria.set(entity[JobInformationEntity::modifiedAt], Date(System.currentTimeMillis()))
                criteria.set(entity[JobInformationEntity::state], state)
                if (status != null) {
                    criteria.set(entity[JobInformationEntity::status], status)
                }
            }
        ).executeUpdate().takeIf { it == 1 } ?: throw JobException.NotFound("job: $systemId")
    }

    override fun updateWorkspace(session: HibernateSession, systemId: String, workspace: String) {
        session.updateCriteria<JobInformationEntity>(
            where = { entity[JobInformationEntity::systemId] equal systemId },
            setProperties = {
                criteria.set(entity[JobInformationEntity::workspace], workspace)
            }
        ).executeUpdate().takeIf { it == 1 } ?: throw JobException.NotFound("job: $systemId")
    }

    override fun findOrNull(
        session: HibernateSession,
        systemId: String,
        owner: String?
    ): VerifiedJobWithAccessToken? {
        val result = JobInformationEntity[session, systemId]
            ?.takeIf { owner == null || it.owner == owner }
            ?.toModel(session) ?: return null

        val toolReference = result.job.application.invocation.tool
        val tool =
            toolDao.findByNameAndVersion(session, owner, toolReference.name, toolReference.version)

        return result.copy(
            job = result.job.copy(
                application = result.job.application.copy(
                    invocation = result.job.application.invocation.copy(
                        tool = ToolReference(toolReference.name, toolReference.version, tool)
                    )
                )
            )
        )
    }

    override suspend fun findJobsCreatedBefore(
        session: HibernateSession,
        timestamp: Long
    ): Sequence<VerifiedJobWithAccessToken> {
        return sequence {
            val scroller = session
                .criteria<JobInformationEntity> {
                    entity[JobInformationEntity::createdAt] lessThan Date(timestamp) and
                            (entity[JobInformationEntity::state] notEqual JobState.SUCCESS) and
                            (entity[JobInformationEntity::state] notEqual JobState.FAILURE)
                }
                .scroll(ScrollMode.FORWARD_ONLY)

            while (scroller.next()) {
                val next = scroller.get(0) as JobInformationEntity
                yield(next.toModel(session))
            }
        }
    }

    override fun list(
        session: HibernateSession,
        owner: String,
        pagination: NormalizedPaginationRequest
    ): Page<VerifiedJobWithAccessToken> {
        return session.paginatedCriteria<JobInformationEntity>(
            pagination,
            orderBy = { listOf(descending(entity[JobInformationEntity::createdAt])) },
            predicate = {
                entity[JobInformationEntity::owner] equal owner
            }
        ).mapItems { it.toModel(session) } // TODO This will do a query for every single result!
    }

    private fun JobInformationEntity.toModel(session: HibernateSession): VerifiedJobWithAccessToken =
        VerifiedJobWithAccessToken(
            VerifiedJob(
                appDao.findByNameAndVersion(session, owner, application.name, application.version),
                files,
                systemId,
                owner,
                nodes,
                tasksPerNode,
                SimpleDuration(maxTimeHours, maxTimeMinutes, maxTimeSeconds),
                VerifiedJobInput(parameters),
                backendName,
                state,
                status,
                archiveInCollection,
                tokenValidation.validateAndDecodeOrNull(accessToken)?.principal?.uid
                    ?: Long.MAX_VALUE, // TODO This is a safe value to map to, but we shouldn't just map it to long max
                workspace,
                createdAt = createdAt.time,
                modifiedAt = modifiedAt.time
            ),
            accessToken
        )

    private fun NameAndVersion.toEmbedded(): EmbeddedNameAndVersion = EmbeddedNameAndVersion(name, version)

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
