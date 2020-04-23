package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.ParsedApplicationParameter
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.ToolReference
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.JSONB_LIST_PARAM_TYPE
import dk.sdu.cloud.service.db.JSONB_LIST_TYPE
import dk.sdu.cloud.service.db.JSONB_MAP_PARAM_KEY_TYPE
import dk.sdu.cloud.service.db.JSONB_MAP_PARAM_VALUE_TYPE
import dk.sdu.cloud.service.db.JSONB_MAP_TYPE
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.WithTimestamps
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.db.updateCriteria
import kotlinx.coroutines.runBlocking
import org.hibernate.ScrollMode
import org.hibernate.annotations.NaturalId
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.Type
import org.slf4j.Logger
import java.util.*
import javax.persistence.AttributeOverride
import javax.persistence.AttributeOverrides
import javax.persistence.Column
import javax.persistence.Embedded
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.criteria.Predicate

@Entity
@Table(name = "job_information")
data class JobInformationEntity(
    @Id
    @NaturalId
    val systemId: String,

    var owner: String,

    var name: String?,

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "name", column = Column(name = "application_name")),
        AttributeOverride(name = "version", column = Column(name = "application_version"))
    )
    var application: EmbeddedNameAndVersion,

    var status: String,

    @Enumerated(EnumType.STRING)
    var state: JobState,

    @Enumerated(EnumType.STRING)
    var failedState: JobState?,

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
                value = "dk.sdu.cloud.app.store.api.ParsedApplicationParameter"
            )
        ]
    )
    var parameters: Map<String, ParsedApplicationParameter?>,

    @Type(
        type = JSONB_LIST_TYPE,
        parameters = [
            Parameter(
                name = JSONB_LIST_PARAM_TYPE,
                value = "dk.sdu.cloud.app.orchestrator.api.ValidatedFileForUpload"
            )
        ]
    )
    var files: List<ValidatedFileForUpload>,

    @Type(
        type = JSONB_LIST_TYPE,
        parameters = [
            Parameter(
                name = JSONB_LIST_PARAM_TYPE,
                value = "dk.sdu.cloud.app.orchestrator.api.ValidatedFileForUpload"
            )
        ]
    )
    var mounts: List<ValidatedFileForUpload>,

    var maxTimeHours: Int,

    var maxTimeMinutes: Int,

    var maxTimeSeconds: Int,

    @Column(length = 4096)
    var accessToken: String?,

    @Column(length = 1024)
    var archiveInCollection: String,

    var backendName: String,

    var startedAt: Date?,

    override var modifiedAt: Date,

    override var createdAt: Date,

    @Type(
        type = JSONB_LIST_TYPE,
        parameters = [
            Parameter(
                name = JSONB_LIST_PARAM_TYPE,
                value = "dk.sdu.cloud.app.orchestrator.api.ApplicationPeer"
            )
        ]
    )
    var peers: List<ApplicationPeer>?,

    @Column(length = 1024)
    var refreshToken: String?,

    var reservationType: String,

    var reservedCpus: Int?,

    var reservedMemoryInGigs: Int?,

    var reservedGpus: Int?,

    var outputFolder: String?,

    var url: String?,

    var project: String?
) : WithTimestamps {

    companion object : HibernateEntity<JobInformationEntity>, WithId<String>
}

class JobHibernateDao(
    private val appStoreService: AppStoreService,
    private val toolStoreService: ToolStoreService
) : JobDao<HibernateSession> {
    override fun create(session: HibernateSession, jobWithToken: VerifiedJobWithAccessToken) {
        val (job, token, refreshToken) = jobWithToken

        val entity = JobInformationEntity(
            systemId = job.id,
            owner = job.owner,
            name = job.name,
            application = job.application.metadata.toEmbedded(),
            status = "Verified",
            state = job.currentState,
            failedState = job.failedState,
            nodes = job.nodes,
            tasksPerNode = job.tasksPerNode,
            parameters = job.jobInput.asMap(),
            files = job.files.toList(),
            mounts = job.mounts.toList(),
            maxTimeHours = job.maxTime.hours,
            maxTimeMinutes = job.maxTime.minutes,
            maxTimeSeconds = job.maxTime.seconds,
            accessToken = token,
            archiveInCollection = job.archiveInCollection,
            backendName = job.backend,
            startedAt = null,
            modifiedAt = Date(job.modifiedAt),
            createdAt = Date(job.createdAt),
            peers = job.peers.toList(),
            refreshToken = refreshToken,
            reservationType = job.reservation.name,
            reservedCpus = job.reservation.cpu,
            reservedMemoryInGigs = job.reservation.memoryInGigs,
            reservedGpus = job.reservation.gpu,
            outputFolder = job.outputFolder,
            url = job.url,
            project = job.project
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

    override fun updateStateAndStatus(
        session: HibernateSession,
        systemId: String,
        state: JobState,
        status: String?,
        failedState: JobState?
    ) {
        session.updateCriteria<JobInformationEntity>(
            where = { entity[JobInformationEntity::systemId] equal systemId },
            setProperties = {
                criteria.set(entity[JobInformationEntity::modifiedAt], Date(System.currentTimeMillis()))
                criteria.set(entity[JobInformationEntity::state], state)
                criteria.set(entity[JobInformationEntity::failedState], failedState)
                if (status != null) {
                    criteria.set(entity[JobInformationEntity::status], status)
                }

                if (state == JobState.RUNNING) {
                    criteria.set(entity[JobInformationEntity::startedAt], Date(System.currentTimeMillis()))
                }
            }
        ).executeUpdate().takeIf { it == 1 } ?: throw JobException.NotFound("job: $systemId")
    }

    override suspend fun find(
        session: HibernateSession,
        systemIds: List<String>,
        owner: SecurityPrincipalToken?
    ): List<VerifiedJobWithAccessToken> {
        return session.criteria<JobInformationEntity> {
            val ownerPredicate = if (owner == null) {
                literal(true).toPredicate()
            } else {
                (entity[JobInformationEntity::owner] equal owner.principal.username)
            }

            ownerPredicate and (entity[JobInformationEntity::systemId] isInCollection systemIds)
        }.resultList.mapNotNull { it.toModel(resolveTool = true) }
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
                val value = runBlocking { next.toModel(resolveTool = true) }
                if (value != null) yield(value!!)
            }
        }
    }

    override suspend fun list(
        session: HibernateSession,
        owner: SecurityPrincipalToken,
        pagination: NormalizedPaginationRequest,
        query: JobQuery,
        projectContext: ProjectContext?
    ): Page<VerifiedJobWithAccessToken> {
        with (query) {
            return session.paginatedCriteria<JobInformationEntity>(
                pagination,
                orderBy = {
                    val field = when (sortBy) {
                        JobSortBy.NAME -> JobInformationEntity::name
                        JobSortBy.STATE -> JobInformationEntity::state
                        JobSortBy.APPLICATION -> JobInformationEntity::application
                        JobSortBy.STARTED_AT -> JobInformationEntity::startedAt
                        JobSortBy.LAST_UPDATE -> JobInformationEntity::modifiedAt
                        JobSortBy.CREATED_AT -> JobInformationEntity::createdAt
                    }

                    when (order) {
                        SortOrder.ASCENDING -> listOf(ascending(entity[field]))
                        SortOrder.DESCENDING -> listOf(descending(entity[field]))
                    }
                },
                predicate = {
                    val canView = run {
                        val canViewAsOwner = if (projectContext?.role?.isAdmin() != true) {
                            entity[JobInformationEntity::owner] equal owner.principal.username
                        } else {
                            literal(true).toPredicate()
                        }

                        val projectPredicate = if (projectContext == null) {
                            entity[JobInformationEntity::project].isNull
                        } else {
                            entity[JobInformationEntity::project] equal projectContext.project
                        }

                        allOf(canViewAsOwner, projectPredicate)
                    }

                    // Time ranges
                    val lowerTime = entity[JobInformationEntity::createdAt] greaterThanEquals Date(minTimestamp ?: 0)
                    val upperTime =
                        entity[JobInformationEntity::createdAt] lessThanEquals Date(maxTimestamp ?: Date().time)
                    val matchesLowerFilter = literal(minTimestamp == null).toPredicate() or lowerTime
                    val matchesUpperFilter = literal(maxTimestamp == null).toPredicate() or upperTime

                    // AppState filter
                    val appState = entity[JobInformationEntity::state] equal (filter ?: JobState.VALIDATED)
                    val appStateFilter = literal(filter == null).toPredicate() or appState

                    // By application name (and version)
                    val byNameAndVersionFilter = if (application != null) {
                        allOf(
                            *ArrayList<Predicate>().apply {
                                val app = entity[JobInformationEntity::application]

                                add(
                                    app[EmbeddedNameAndVersion::name] equal application
                                )

                                if (version != null) {
                                    add(
                                        app[EmbeddedNameAndVersion::version] equal version
                                    )
                                }
                            }.toTypedArray()
                        )
                    } else {
                        literal(true).toPredicate()
                    }

                    allOf(
                        matchesLowerFilter,
                        matchesUpperFilter,
                        appStateFilter,
                        byNameAndVersionFilter,
                        canView
                    )
                }
            ).mapItemsNotNull { it.toModel() }
        }
    }

    override suspend fun list10LatestActiveJobsOfApplication(
        session: HibernateSession,
        owner: SecurityPrincipalToken,
        application: String,
        version: String
    ): List<VerifiedJobWithAccessToken> {
        val validStates = listOf(JobState.SCHEDULED, JobState.RUNNING, JobState.PREPARED, JobState.VALIDATED)
        return session.criteria<JobInformationEntity>(
            orderBy = {
                listOf(descending(entity[JobInformationEntity::createdAt]))
            },
            predicate = {
                allOf(
                    entity[JobInformationEntity::state] isInCollection validStates,
                    entity[JobInformationEntity::application][EmbeddedNameAndVersion::name] equal application,
                    entity[JobInformationEntity::application][EmbeddedNameAndVersion::version] equal version,
                    entity[JobInformationEntity::owner] equal owner.principal.username
                )
            }
        ).resultList.take(10).mapNotNull { it.toModel() }

    }

    private inline fun <T, R : Any> Page<T>.mapItemsNotNull(mapper: (T) -> R?): Page<R> {
        val newItems = items.mapNotNull(mapper)
        return Page(
            itemsInTotal,
            itemsPerPage,
            pageNumber,
            newItems
        )
    }

    private suspend fun JobInformationEntity.toModel(
        resolveTool: Boolean = false
    ): VerifiedJobWithAccessToken? {
        val withoutTool = VerifiedJobWithAccessToken(
            VerifiedJob(
                systemId, // return null in case application no longer exists (issue #915)
                name,
                owner,
                application = appStoreService.findByNameAndVersion(application.name, application.version)
                    ?: return null,
                backend = backendName,
                nodes = nodes,
                maxTime = SimpleDuration(maxTimeHours, maxTimeMinutes, maxTimeSeconds),
                tasksPerNode = tasksPerNode,
                reservation = MachineReservation(reservationType, reservedCpus, reservedMemoryInGigs),
                jobInput = VerifiedJobInput(parameters),
                files = files.toSet(),
                _mounts = mounts.toSet(),
                _peers = peers?.toSet(),
                currentState = state,
                failedState = failedState,
                status = status,
                archiveInCollection = archiveInCollection,
                outputFolder = outputFolder,
                createdAt = createdAt.time,
                modifiedAt = modifiedAt.time,
                startedAt = startedAt?.time,
                url = url,
                project = project
            ),
            accessToken,
            refreshToken
        )

        if (!resolveTool) return withoutTool

        val toolReference = withoutTool.job.application.invocation.tool
        val tool =
            toolStoreService.findByNameAndVersion(toolReference.name, toolReference.version)

        return withoutTool.copy(
            job = withoutTool.job.copy(
                application = withoutTool.job.application.copy(
                    invocation = withoutTool.job.application.invocation.copy(
                        tool = ToolReference(toolReference.name, toolReference.version, tool)
                    )
                )
            )
        )
    }

    private fun NameAndVersion.toEmbedded(): EmbeddedNameAndVersion = EmbeddedNameAndVersion(name, version)

    companion object : Loggable {
        override val log: Logger = logger()
    }

    override suspend fun findFromUrlId(
        session: HibernateSession,
        urlId: String,
        owner: SecurityPrincipalToken?
    ): VerifiedJobWithAccessToken? {
        return session.criteria<JobInformationEntity> {
            val ownerPredicate = if (owner == null) {
                literal(true).toPredicate()
            } else {
                (entity[JobInformationEntity::owner] equal owner.principal.username)
            }
            ownerPredicate and (
                (entity[JobInformationEntity::systemId] equal urlId) or (entity[JobInformationEntity::url] equal urlId)
            ) and (
                (entity[JobInformationEntity::state] notEqual JobState.SUCCESS) and (entity[JobInformationEntity::state] notEqual JobState.FAILURE)
            )
        }.singleResult.toModel()
    }

    override suspend fun isUrlOccupied(
        session: HibernateSession,
        urlId: String
    ): Boolean {
        return session.criteria<JobInformationEntity> {
            ((entity[JobInformationEntity::systemId] equal urlId) or
                    (entity[JobInformationEntity::url] equal urlId)) and
                    ((entity[JobInformationEntity::state] notEqual JobState.SUCCESS) and
                            (entity[JobInformationEntity::state] notEqual JobState.FAILURE))
        }.list().isNotEmpty()
    }
}
